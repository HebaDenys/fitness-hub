package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/** Pixels and a local round identifier only; never cookies, account IDs, URLs or login forms. */
internal class XiaomiCaptchaChallenge(val id: Long, val image: ByteArray) : PrivateXiaomiValue()
internal fun interface XiaomiCaptchaResponder {
    suspend fun answer(image: ByteArray): String
}

/** In-memory, one-shot user input; cancellation/timeout disposes the exact pending round. */
internal class XiaomiCaptchaController : XiaomiCaptchaResponder {
    private val sequence = AtomicLong()
    private val mutableChallenge = MutableStateFlow<XiaomiCaptchaChallenge?>(null)
    val challenge = mutableChallenge.asStateFlow()
    private var pending: Pair<Long, CompletableDeferred<String>>? = null

    override suspend fun answer(image: ByteArray): String {
        currentCoroutineContext().ensureActive()
        val id = sequence.incrementAndGet()
        val result = CompletableDeferred<String>()
        synchronized(this) {
            if (pending != null) accessFailure(XiaomiAccessFailure.SESSION_CHANGED)
            pending = id to result
            mutableChallenge.value = XiaomiCaptchaChallenge(id, image.copyOf())
        }
        return try {
            withTimeout(TIMEOUT_MILLIS) { result.await() }
        } catch (_: TimeoutCancellationException) {
            accessFailure(XiaomiAccessFailure.CHALLENGE_EXPIRED)
        } finally {
            synchronized(this) {
                if (pending?.first == id) {
                    pending = null
                    mutableChallenge.value?.image?.fill(0)
                    mutableChallenge.value = null
                }
            }
            result.cancel()
        }
    }

    fun submit(id: Long, answer: String): Boolean = synchronized(this) {
        val value = answer.trim()
        if (!validAnswer(value)) return false
        val current = pending ?: return false
        if (current.first != id || current.second.isCompleted) return false
        // Clear the visible round immediately so repeated taps cannot submit twice.
        mutableChallenge.value?.image?.fill(0)
        mutableChallenge.value = null
        pending = null
        current.second.complete(value)
    }

    fun cancel() = synchronized(this) {
        val current = pending
        pending = null
        mutableChallenge.value?.image?.fill(0)
        mutableChallenge.value = null
        current?.second?.cancel()
        Unit
    }

    companion object {
        const val TIMEOUT_MILLIS = 5 * 60 * 1000L
        fun validAnswer(value: String) = value.matches(Regex("[A-Za-z0-9]{1,16}"))
    }
}
