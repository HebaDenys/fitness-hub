package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Read-only vendor adapter: no custom TrustManager/HostnameVerifier, redirects, cache,
 * global cookie jar or logging. Blocking IO is bounded and never runs on the UI thread.
 */
internal class XiaomiHttpsTransport(
    private val gate: XiaomiNetworkGate,
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) : XiaomiHttpExchange {
    override suspend fun execute(request: XiaomiHttpRequest): XiaomiHttpResponse {
        gate.requireAllowed()
        XiaomiUrlPolicy.check(request)
        return withTimeout(30_000) {
            slots.withPermit {
                suspendCancellableCoroutine { continuation ->
                    val active = AtomicReference<HttpsURLConnection?>()
                    val future = executor.submit {
                        try {
                            if (!continuation.isActive) return@submit
                            val connection = open(URL(request.url))
                            active.set(connection)
                            if (!continuation.isActive) return@submit
                            connection.instanceFollowRedirects = false
                            connection.useCaches = false
                            connection.connectTimeout = 10_000
                            connection.readTimeout = 15_000
                            connection.requestMethod = request.method
                            connection.setRequestProperty("Accept-Encoding", "identity")
                            request.headers.forEach(connection::setRequestProperty)
                            request.form?.let { form ->
                                val bytes = form.toByteArray(Charsets.UTF_8)
                                try {
                                    connection.doOutput = true
                                    connection.setFixedLengthStreamingMode(bytes.size)
                                    connection.outputStream.use { it.write(bytes) }
                                } finally { bytes.fill(0) }
                            }
                            val status = connection.responseCode
                            val cookies = if (request.endpoint in setOf(XiaomiEndpoint.SERVICE_TICKET, XiaomiEndpoint.CAPTCHA_IMAGE)) {
                                connection.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                                    .flatMap { it.value.orEmpty() }
                            } else emptyList()
                            if (cookies.size > 16 || cookies.sumOf { it.length } > 32 * 1024) {
                                accessFailure(XiaomiAccessFailure.RESPONSE_TOO_LARGE)
                            }
                            val location = if (status in 300..399) connection.getHeaderField("Location") else null
                            if ((location?.length ?: 0) > 8192) accessFailure(XiaomiAccessFailure.RESPONSE_TOO_LARGE)
                            // Do not read or retain error bodies, which often contain account details.
                            val body = if (status == 200) {
                                val declared = connection.contentLengthLong
                                if (declared > request.responseLimit) accessFailure(XiaomiAccessFailure.RESPONSE_TOO_LARGE)
                                connection.inputStream.use { readLimited(it, request.responseLimit) }
                            } else byteArrayOf()
                            val result = XiaomiHttpResponse(status, body, cookies, location)
                            if (continuation.isActive) continuation.resume(result) else body.fill(0)
                        } catch (failure: XiaomiAccessException) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        } catch (_: SSLException) {
                            if (continuation.isActive) continuation.resumeWithException(XiaomiAccessException(XiaomiAccessFailure.TLS_ERROR))
                        } catch (_: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(XiaomiAccessException(XiaomiAccessFailure.NETWORK_ERROR))
                        } finally {
                            active.getAndSet(null)?.disconnect()
                        }
                    }
                    continuation.invokeOnCancellation {
                        future.cancel(true)
                        active.getAndSet(null)?.disconnect()
                    }
                }
            }
        }
    }

    companion object {
        private val slots = Semaphore(2)
        private val executor = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "xiaomi-https").apply { isDaemon = true }
        }

        internal fun readLimited(input: InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream(minOf(limit, 8192))
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
                if (count == -1) break
                total += count
                if (total > limit) accessFailure(XiaomiAccessFailure.RESPONSE_TOO_LARGE)
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}
