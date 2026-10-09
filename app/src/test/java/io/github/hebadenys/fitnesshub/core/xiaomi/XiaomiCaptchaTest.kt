package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

private class CaptchaHttp(vararg responses: XiaomiHttpResponse) : XiaomiHttpExchange {
    val calls = mutableListOf<XiaomiHttpRequest>()
    private val remaining = ArrayDeque(responses.toList())
    override suspend fun execute(request: XiaomiHttpRequest): XiaomiHttpResponse {
        XiaomiUrlPolicy.check(request)
        calls += request
        return remaining.removeFirst()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class XiaomiCaptchaTest {
    private val f get() = XiaomiNetworkFixtures
    private fun required(url: String = "/pass/getCode?icodeType=login") =
        f.response("""{"code":87001,"captchaUrl":"$url"}""")
    private fun image(cookie: String = "ick=fixture-ick; Domain=.xiaomi.com; Path=/pass; Max-Age=300") =
        XiaomiHttpResponse(200, Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jN1sAAAAASUVORK5CYII="), listOf(cookie))

    @Test fun noPasswordResubmissionBeforeExplicitAnswerAndOriginalContextIsPreserved() = runTest {
        val input = XiaomiCaptchaController()
        val http = CaptchaHttp(f.start(), required(), image(), f.authenticated(), f.ticket())
        val password = "fixture-password".toCharArray()
        val login = async { XiaomiAuthentication(http, f.clock, captcha = input).login("fixture", f.scope.region, "fixture", password) }
        runCurrent()
        assertTrue(password.all { it == '\u0000' })
        assertEquals(3, http.calls.size)
        val challenge = input.challenge.value!!
        assertFalse(input.submit(challenge.id + 1, "abc"))
        assertFalse(input.submit(challenge.id, "bad;cookie"))
        assertEquals(3, http.calls.size)
        assertTrue(input.submit(challenge.id, "aB12"))
        assertFalse(input.submit(challenge.id, "aB12"))
        runCurrent()
        assertEquals("10001", login.await().userId)
        val initial = f.form(http.calls[1].form!!)
        val resumed = f.form(http.calls[3].form!!)
        assertEquals(initial, resumed - "captCode")
        assertEquals("aB12", resumed["captCode"])
        assertEquals(http.calls[1].headers["Cookie"] + "; ick=fixture-ick", http.calls[3].headers["Cookie"])
        assertNull(http.calls[4].headers["Cookie"])
        assertNull(input.challenge.value)
    }

    @Test fun cancellingThenStartingNewAttemptRejectsStaleAnswers() = runTest {
        val input = XiaomiCaptchaController()
        val first = async { input.answer(byteArrayOf(1, 2, 3)) }
        runCurrent()
        val old = input.challenge.value!!
        first.cancelAndJoin()
        assertNull(input.challenge.value)
        assertTrue(old.image.all { it == 0.toByte() })
        val second = async { input.answer(byteArrayOf(4, 5, 6)) }
        runCurrent()
        val next = input.challenge.value!!
        assertNotEquals(old.id, next.id)
        assertFalse(input.submit(old.id, "ABC"))
        assertTrue(input.submit(next.id, "DEF"))
        assertEquals("DEF", second.await())
        assertNull(input.challenge.value)
    }

    @Test fun expiryStopsWithoutAnotherPost() = runTest {
        val input = XiaomiCaptchaController()
        val http = CaptchaHttp(f.start(), required(), image())
        val login = async { expectAccess(XiaomiAccessFailure.CHALLENGE_EXPIRED) {
            XiaomiAuthentication(http, f.clock, captcha = input).login("fixture", f.scope.region, "fixture", "x".toCharArray())
        } }
        runCurrent()
        advanceTimeBy(XiaomiCaptchaController.TIMEOUT_MILLIS)
        runCurrent()
        login.await()
        assertEquals(3, http.calls.size)
        assertNull(input.challenge.value)
    }

    @Test fun cancellationDoesNotPostOrLeaveChallenge() = runTest {
        val input = XiaomiCaptchaController()
        val http = CaptchaHttp(f.start(), required(), image())
        val login = async { XiaomiAuthentication(http, f.clock, captcha = input).login("fixture", f.scope.region, "fixture", "x".toCharArray()) }
        runCurrent()
        login.cancelAndJoin()
        assertNull(input.challenge.value)
        assertEquals(3, http.calls.size)
    }

    @Test fun notificationIsDistinctEvenWhenBothChallengeFieldsExist() = runTest {
        val http = CaptchaHttp(f.start(), f.response("""{"code":0,"captchaUrl":"/pass/getCode?x","notificationUrl":"https://account.xiaomi.com/fe/service/identity/authStart?context=fixture"}"""))
        var prompted = false
        expectAccess(XiaomiAccessFailure.VERIFICATION_REQUIRED) {
            XiaomiAuthentication(http, f.clock, captcha = XiaomiCaptchaResponder { prompted = true; "ABC" })
                .login("fixture", f.scope.region, "fixture", "x".toCharArray())
        }
        assertFalse(prompted)
        assertEquals(2, http.calls.size)
    }

    @Test fun unsafeImageUrlsNeverReceiveCookiesOrRequests() = runTest {
        for (url in listOf("https://evil.test/pass/getCode?x", "https://account.xiaomi.com:444/pass/getCode?x",
            "https://user@account.xiaomi.com/pass/getCode?x", "https://account.xiaomi.com/pass/%67etCode?x",
            "https://account.xiaomi.com/other?x", "https://account.xiaomi.com/pass/getCode?x#fragment")) {
            val http = CaptchaHttp(f.start(), required(url))
            expectAccess(XiaomiAccessFailure.UNSAFE_ENDPOINT) {
                XiaomiAuthentication(http, f.clock, captcha = XiaomiCaptchaResponder { "ABC" })
                    .login("fixture", f.scope.region, "fixture", "x".toCharArray())
            }
            assertEquals(2, http.calls.size)
        }
    }

    @Test fun malformedExpiredDuplicateAndForeignCookiesCannotContinue() = runTest {
        for ((cookies, reason) in listOf(
            emptyList<String>() to XiaomiAccessFailure.PROTOCOL_CHANGED,
            listOf("ick=one", "ick=two") to XiaomiAccessFailure.PROTOCOL_CHANGED,
            listOf("ick=one; Max-Age=0") to XiaomiAccessFailure.PROTOCOL_CHANGED,
            listOf("ick=one; Domain=evil.test") to XiaomiAccessFailure.UNSAFE_ENDPOINT,
            listOf("ick=\"one; injected=two\"") to XiaomiAccessFailure.PROTOCOL_CHANGED
        )) {
            val http = CaptchaHttp(f.start(), required(), image().copy(setCookies = cookies))
            var prompted = false
            expectAccess(reason) {
                XiaomiAuthentication(http, f.clock, captcha = XiaomiCaptchaResponder { prompted = true; "ABC" })
                    .login("fixture", f.scope.region, "fixture", "x".toCharArray())
            }
            assertFalse(prompted)
            assertEquals(3, http.calls.size)
        }
    }

    @Test fun invalidAndOversizedImageBodiesAreRejectedBeforePrompt() = runTest {
        for (body in listOf("<html>fixture</html>".toByteArray(), ByteArray(256 * 1024 + 1))) {
            val http = CaptchaHttp(f.start(), required(), image().copy(body = body))
            expectAccess(XiaomiAccessFailure.PROTOCOL_CHANGED) {
                XiaomiAuthentication(http, f.clock, captcha = XiaomiCaptchaResponder { fail<String>("Must not prompt") })
                    .login("fixture", f.scope.region, "fixture", "x".toCharArray())
            }
            assertEquals(3, http.calls.size)
        }
    }

    @Test fun repeatedWrongAnswersAreBoundedAndDeadlineIsNotRestarted() = runTest {
        val http = CaptchaHttp(f.start(), required(), image(), required(), image())
        var prompts = 0
        expectAccess(XiaomiAccessFailure.CHALLENGE_EXPIRED) {
            XiaomiAuthentication(http, f.clock, captcha = XiaomiCaptchaResponder { prompts++; delay(200_000); "ABC" })
                .login("fixture", f.scope.region, "fixture", "x".toCharArray())
        }
        assertEquals(2, prompts)
        assertEquals(5, http.calls.size)
    }

    @Test fun threeExplicitRejectedRoundsStopBeforeFourthImage() = runTest {
        val http = CaptchaHttp(f.start(), required(), image(), required(), image(), required(), image(), required())
        var prompts = 0
        expectAccess(XiaomiAccessFailure.RATE_LIMITED) {
            XiaomiAuthentication(http, f.clock, captcha = XiaomiCaptchaResponder { prompts++; "ABC" })
                .login("fixture", f.scope.region, "fixture", "x".toCharArray())
        }
        assertEquals(3, prompts)
        assertEquals(8, http.calls.size)
    }
}
