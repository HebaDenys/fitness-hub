package io.github.hebadenys.fitnesshub.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** The raw text a provider returned, or a failure that carries no internals. */
sealed interface AiResult {
    data class Success(val body: String) : AiResult
    data class Failed(val reason: String) : AiResult
}

/**
 * Performs the single request the user explicitly approved.
 *
 * There is no retry, no queue, no background sending and no shared session: a
 * call happens once, with a payload the user has already seen rendered in full.
 * Failures collapse to a reason key so an HTTP body or stack trace cannot leak
 * into the UI or a log.
 */
class AiTransport {

    suspend fun send(payload: AiRequestPayload): AiResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(payload.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty(payload.provider.headerName, payload.provider.authorizationValue(payload.apiKey))
            }
            connection.outputStream.use { it.write(payload.body.toByteArray(Charsets.UTF_8)) }

            when (val status = connection.responseCode) {
                in 200..299 -> AiResult.Success(connection.inputStream.bufferedReader().use(BufferedReader::readText))
                else -> AiResult.Failed("http_$status")
            }
        } catch (_: Exception) {
            AiResult.Failed("network_error")
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
    }
}
