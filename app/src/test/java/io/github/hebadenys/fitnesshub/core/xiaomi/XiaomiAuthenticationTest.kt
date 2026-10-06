package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal suspend fun expectAccess(reason: XiaomiAccessFailure, block: suspend () -> Any?) {
    val failure = try { block(); null } catch (error: XiaomiAccessException) { error }
    assertNotNull(failure, "Expected safe access failure")
    assertEquals(reason, failure!!.reason)
    assertNull(failure.cause)
}

private class ScriptedXiaomiHttp(vararg responses: XiaomiHttpResponse) : XiaomiHttpExchange {
    val calls = mutableListOf<XiaomiHttpRequest>()
    private val remaining = ArrayDeque(responses.toList())
    override suspend fun execute(request: XiaomiHttpRequest): XiaomiHttpResponse {
        XiaomiUrlPolicy.check(request)
        calls += request
        return remaining.removeFirst()
    }
}

class XiaomiAuthenticationTest {
    private val f get() = XiaomiNetworkFixtures

    @Test fun threeStepsProduceScopedSessionWithoutPersistingPasswordOrPassToken() = runTest {
        val http = ScriptedXiaomiHttp(f.start(), f.authenticated(), f.ticket())
        val password = "password".toCharArray()
        val session = XiaomiAuthentication(http, f.clock).login(f.scope.connectionId, f.scope.region, "fixture@example.test", password)
        assertEquals(f.session(), session)
        assertTrue(password.all { it == '\u0000' })
        assertEquals(3, http.calls.size)
        val form = f.form(http.calls[1].form!!)
        assertEquals("5F4DCC3B5AA765D61D8327DEB882CF99", form["hash"])
        assertEquals("xiaomiio", form["sid"])
        assertFalse(http.calls[2].headers.containsKey("Cookie"))
        val serialized = String(XiaomiSessionCodec.encode(session))
        assertFalse(serialized.contains("not-to-be-stored"))
        assertFalse(serialized.contains("password"))
    }

    @Test fun unauthenticatedStartCodeDoesNotMeanWrongPassword() = runTest {
        val start = f.response("""{"code":70016,"sid":"xiaomiio","_sign":"fixture-sign","qs":"q","callback":"https://sts.api.io.mi.com/sts"}""")
        val http = ScriptedXiaomiHttp(start, f.authenticated(), f.ticket())
        XiaomiAuthentication(http, f.clock).login(f.scope.connectionId, f.scope.region, "fixture", "x".toCharArray())
        assertEquals(3, http.calls.size)
    }

    @Test fun unsafeCallbackStopsBeforePostingPasswordHash() = runTest {
        val http = ScriptedXiaomiHttp(f.response("""{"code":0,"sid":"xiaomiio","_sign":"x","qs":"x","callback":"https://evil.test/sts"}"""))
        val password = "fixture-secret".toCharArray()
        expectAccess(XiaomiAccessFailure.UNSAFE_ENDPOINT) { XiaomiAuthentication(http, f.clock).login("fixture", f.scope.region, "fixture", password) }
        assertEquals(1, http.calls.size)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test fun unsafeTicketStopsWithoutSendingTokensToAnotherHost() = runTest {
        val http = ScriptedXiaomiHttp(f.start(), f.authenticated("https://evil.test/sts"))
        expectAccess(XiaomiAccessFailure.UNSAFE_ENDPOINT) { XiaomiAuthentication(http, f.clock).login("fixture", f.scope.region, "fixture", "x".toCharArray()) }
        assertEquals(2, http.calls.size)
    }

    @Test fun captchaAndVerificationAreExplicitAndNeverRetried() = runTest {
        for ((body, reason) in listOf(
            """{"code":87001,"captchaUrl":"/pass/getCode?fixture"}""" to XiaomiAccessFailure.CAPTCHA_REQUIRED,
            """{"code":0,"notificationUrl":"https://account.xiaomi.com/identity/authStart?fixture"}""" to XiaomiAccessFailure.VERIFICATION_REQUIRED,
            """{"code":70016,"description":"private server detail"}""" to XiaomiAccessFailure.AUTH_REJECTED,
            """{"code":99999,"description":"unknown"}""" to XiaomiAccessFailure.PROTOCOL_CHANGED
        )) {
            val http = ScriptedXiaomiHttp(f.start(), f.response(body))
            expectAccess(reason) { XiaomiAuthentication(http, f.clock).login("fixture", f.scope.region, "fixture", "x".toCharArray()) }
            assertEquals(2, http.calls.size)
        }
    }

    @Test fun missingTokenConflictingIdentityAndUntrustedCookieDomainFailClosed() = runTest {
        for ((headers, reason) in listOf(
            emptyList<String>() to XiaomiAccessFailure.PROTOCOL_CHANGED,
            listOf("serviceToken=fixture; Domain=evil.test") to XiaomiAccessFailure.UNSAFE_ENDPOINT,
            listOf("serviceToken=fixture", "userId=20002") to XiaomiAccessFailure.PROTOCOL_CHANGED,
            listOf("serviceToken=fixture", "serviceToken=second") to XiaomiAccessFailure.PROTOCOL_CHANGED,
            listOf("serviceToken=fixture; Max-Age=0") to XiaomiAccessFailure.AUTH_REQUIRED
        )) {
            val http = ScriptedXiaomiHttp(f.start(), f.authenticated(), XiaomiHttpResponse(200, setCookies = headers))
            expectAccess(reason) { XiaomiAuthentication(http, f.clock).login("fixture", f.scope.region, "fixture", "x".toCharArray()) }
        }
    }

    @Test fun localExpiryIsCappedEvenIfVendorCookieHasNoExpiry() = runTest {
        val http = ScriptedXiaomiHttp(f.start(), f.authenticated(), XiaomiHttpResponse(200, setCookies = listOf("serviceToken=fixture")))
        val session = XiaomiAuthentication(http, f.clock).login("fixture", f.scope.region, "fixture", "x".toCharArray())
        assertEquals(XiaomiSession.MAX_LOCAL_AGE_MILLIS, session.validUntilMillis - session.issuedAtMillis)
    }

    @Test fun malformedPrefixAndDuplicateJsonKeysAreNotAccepted() = runTest {
        for (response in listOf(XiaomiHttpResponse(200, "{}".toByteArray()), f.response("""{"code":0,"code":0}"""))) {
            expectAccess(XiaomiAccessFailure.PROTOCOL_CHANGED) {
                XiaomiAuthentication(ScriptedXiaomiHttp(response), f.clock).login("fixture", f.scope.region, "fixture", "x".toCharArray())
            }
        }
    }

    @Test fun cancellationPropagatesAndWipesCallerPassword() = runTest {
        val password = "fixture-secret".toCharArray()
        var cancelled = false
        try {
            XiaomiAuthentication(XiaomiHttpExchange { throw CancellationException("fixture") }, f.clock)
                .login("fixture", f.scope.region, "fixture", password)
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test fun httpStatusCodesRemainActionableWithoutResponseDetails() = runTest {
        for ((status, reason) in listOf(302 to XiaomiAccessFailure.REDIRECT_REJECTED, 401 to XiaomiAccessFailure.AUTH_REQUIRED,
            429 to XiaomiAccessFailure.RATE_LIMITED, 503 to XiaomiAccessFailure.REMOTE_UNAVAILABLE)) {
            expectAccess(reason) {
                XiaomiAuthentication(ScriptedXiaomiHttp(XiaomiHttpResponse(status, "fixture private detail".toByteArray())), f.clock)
                    .login("fixture", f.scope.region, "fixture", "x".toCharArray())
            }
        }
    }
}
