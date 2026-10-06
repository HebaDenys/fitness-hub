package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

private class TestHttpsConnection(
    private val status: Int = 200,
    private val bytes: ByteArray = "fixture".toByteArray(),
    private val declared: Long = bytes.size.toLong(),
    private val blockRead: Boolean = false
) : HttpsURLConnection(URL(XiaomiUrlPolicy.START)) {
    val readStarted = CountDownLatch(1)
    val disconnected = CountDownLatch(1)
    private val release = CountDownLatch(1)
    var inputReads = 0
    var streamClosed = false
    val output = ByteArrayOutputStream()
    override fun getResponseCode() = status
    override fun getContentLengthLong() = declared
    override fun getHeaderFields(): Map<String, List<String>> = emptyMap()
    override fun getHeaderField(name: String?): String? = if (name == "Location") "https://evil.test/" else null
    override fun getInputStream(): InputStream {
        inputReads++
        readStarted.countDown()
        if (blockRead) release.await(5, TimeUnit.SECONDS)
        return object : ByteArrayInputStream(bytes) { override fun close() { streamClosed = true; super.close() } }
    }
    override fun getOutputStream() = output
    override fun disconnect() { release.countDown(); disconnected.countDown() }
    override fun connect() = Unit
    override fun usingProxy() = false
    override fun getCipherSuite() = "test-fixture"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates(): Array<Certificate> = emptyArray()
}

class XiaomiHttpsTransportTest {
    @Test fun productionGateStopsBeforeOpeningAnyConnection() = runBlocking {
        var opened = false
        val transport = XiaomiHttpsTransport(XiaomiNetworkGate.AwaitingPrivateSigning) { opened = true; error("not expected") }
        expectAccess(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED) {
            transport.execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START))
        }
        assertFalse(opened)
    }

    @Test fun adapterDisablesRedirectsAndCachingAndClosesStreams() = runBlocking {
        val connection = TestHttpsConnection()
        val transport = XiaomiHttpsTransport(XiaomiNetworkFixtures.allow) { connection }
        val response = transport.execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START))
        assertEquals("fixture", response.text())
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertTrue(connection.streamClosed)
        assertTrue(connection.disconnected.await(2, TimeUnit.SECONDS))
        assertEquals(10_000, connection.connectTimeout)
        assertEquals(15_000, connection.readTimeout)
    }

    @Test fun overlargeContentLengthIsRejectedBeforeReadingBody() = runBlocking {
        val connection = TestHttpsConnection(declared = 64 * 1024 + 1L)
        expectAccess(XiaomiAccessFailure.RESPONSE_TOO_LARGE) {
            XiaomiHttpsTransport(XiaomiNetworkFixtures.allow) { connection }
                .execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START))
        }
        assertEquals(0, connection.inputReads)
    }

    @Test fun chunkedOrMisreportedBodyCannotExceedLimit() = runBlocking {
        val connection = TestHttpsConnection(bytes = ByteArray(64 * 1024 + 1), declared = -1)
        expectAccess(XiaomiAccessFailure.RESPONSE_TOO_LARGE) {
            XiaomiHttpsTransport(XiaomiNetworkFixtures.allow) { connection }
                .execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START))
        }
        assertTrue(connection.streamClosed)
    }

    @Test fun errorsAndRedirectsAreReturnedWithoutReadingPrivateBodies() = runBlocking {
        for (status in listOf(302, 401, 429, 500)) {
            val connection = TestHttpsConnection(status)
            val response = XiaomiHttpsTransport(XiaomiNetworkFixtures.allow) { connection }
                .execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START))
            assertEquals(status, response.status)
            assertTrue(response.body.isEmpty())
            assertEquals(0, connection.inputReads)
        }
    }

    @Test fun cancellationDisconnectsInflightIo() = runBlocking {
        val connection = TestHttpsConnection(blockRead = true)
        val result = async(Dispatchers.Default) {
            XiaomiHttpsTransport(XiaomiNetworkFixtures.allow) { connection }
                .execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START))
        }
        assertTrue(connection.readStarted.await(5, TimeUnit.SECONDS))
        result.cancel()
        result.join()
        assertTrue(result.isCancelled)
        assertTrue(connection.disconnected.await(2, TimeUnit.SECONDS))
    }

    @Test fun tlsAndNetworkExceptionMessagesAreNotExposed() = runBlocking {
        expectAccess(XiaomiAccessFailure.TLS_ERROR) {
            XiaomiHttpsTransport(XiaomiNetworkFixtures.allow) { throw SSLException("fixture-secret-host") }
                .execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START))
        }
        expectAccess(XiaomiAccessFailure.NETWORK_ERROR) {
            XiaomiHttpsTransport(XiaomiNetworkFixtures.allow) { error("fixture-secret-token") }
                .execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START))
        }
    }
}
