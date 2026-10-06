package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class XiaomiAuthenticatedPageSourceTest {
    private val f get() = XiaomiNetworkFixtures

    @Test fun authenticatedRequestSignsEncryptsAndDecryptsVendorEnvelope() = runTest {
        val http = XiaomiHttpExchange { request ->
            XiaomiUrlPolicy.check(request)
            assertEquals("https://de.api.io.mi.com/app/eco/common/scale/getUserDataByPage", request.url)
            assertEquals(f.scope.model, request.headers["MIOT-REQUEST-MODEL"])
            assertEquals(f.session().cookieHeader(), request.headers["Cookie"])
            val form = f.form(request.form!!)
            val key = XiaomiWireCrypto.signedNonce(XiaomiWireCrypto.unbase64(f.security), XiaomiWireCrypto.unbase64(form.getValue("_nonce")))
            val plaintext = String(XiaomiWireCrypto.crypt(key, XiaomiWireCrypto.unbase64(form.getValue("data"))))
            assertEquals(XiaomiScaleRequest.history(f.scope, f.clock.millis()).dataJson, plaintext)
            assertEquals(XiaomiWireCrypto.signature("/eco/common/scale/getUserDataByPage", form.getValue("data"), form.getValue("rc4_hash__"), key), form["signature"])
            f.encrypted(request, """{"code":0,"result":[]}""")
        }
        val text = XiaomiAuthenticatedPageSource(http, f.session(), f.scope, f.clock)
            .fetch(XiaomiScaleRequest.history(f.scope, f.clock.millis()))
        assertEquals("""{"code":0,"result":[]}""", text)
    }

    @Test fun alteredScopeOrRequestIsRejectedBeforeNetwork() = runTest {
        var calls = 0
        val http = XiaomiHttpExchange { calls++; error("not expected") }
        val source = XiaomiAuthenticatedPageSource(http, f.session(), f.scope, f.clock)
        expectAccess(XiaomiAccessFailure.UNSAFE_ENDPOINT) {
            source.fetch(XiaomiScaleRequest.history(f.scope, f.clock.millis()).copy(dataJson = "{}"))
        }
        expectAccess(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH) {
            XiaomiAuthenticatedPageSource(http, f.session(), f.scope.copy(loginUid = "20002"), f.clock)
                .fetch(XiaomiScaleRequest.history(f.scope, f.clock.millis()))
        }
        assertEquals(0, calls)
    }

    @Test fun expiredSessionIsNotSent() = runTest {
        val later = Clock.fixed(Instant.ofEpochMilli(f.session().validUntilMillis), ZoneOffset.UTC)
        expectAccess(XiaomiAccessFailure.SESSION_EXPIRED) {
            XiaomiAuthenticatedPageSource(XiaomiHttpExchange { error("not expected") }, f.session(), f.scope, later)
                .fetch(XiaomiScaleRequest.history(f.scope, later.millis()))
        }
    }

    @Test fun corruptAndVendorErrorResponsesDoNotBecomeEmptyHistory() = runTest {
        for (http in listOf(
            XiaomiHttpExchange { XiaomiHttpResponse(200, "not base64".toByteArray()) },
            XiaomiHttpExchange { f.encrypted(it, "not json") },
            XiaomiHttpExchange { f.encrypted(it, """{"code":99999,"message":"fixture-private"}""") }
        )) {
            expectAccess(XiaomiAccessFailure.PROTOCOL_CHANGED) {
                XiaomiAuthenticatedPageSource(http, f.session(), f.scope, f.clock)
                    .fetch(XiaomiScaleRequest.history(f.scope, f.clock.millis()))
            }
        }
    }

    @Test fun readerPreservesSafeRateLimitReasonAndDoesNotAdvanceCheckpoint() = runTest {
        var commits = 0
        val reader = XiaomiHistoryReader(XiaomiPageSource { accessFailure(XiaomiAccessFailure.RATE_LIMITED) },
            XiaomiPageCommitter { _, _, _ -> commits++ })
        expectAccess(XiaomiAccessFailure.RATE_LIMITED) { reader.read(f.scope, f.clock.millis()) }
        assertEquals(0, commits)
    }
}
