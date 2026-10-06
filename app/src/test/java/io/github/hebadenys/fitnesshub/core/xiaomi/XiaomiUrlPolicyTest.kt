package io.github.hebadenys.fitnesshub.core.xiaomi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class XiaomiUrlPolicyTest {
    @Test fun exactReadOnlyEndpointsAreAccepted() {
        XiaomiUrlPolicy.check(XiaomiUrlPolicy.START, XiaomiEndpoint.LOGIN_START)
        XiaomiUrlPolicy.check(XiaomiUrlPolicy.PASSWORD, XiaomiEndpoint.LOGIN_PASSWORD)
        XiaomiUrlPolicy.check(XiaomiUrlPolicy.CALLBACK + "?ticket=fixture", XiaomiEndpoint.SERVICE_TICKET)
        XiaomiUrlPolicy.check("https://api.io.mi.com/app/eco/scale/getData", XiaomiEndpoint.SCALE_HISTORY)
        for (region in listOf("de", "i2", "ru", "sg", "us")) {
            XiaomiUrlPolicy.check("https://$region.api.io.mi.com/app/eco/common/scale/getUserDataByPage", XiaomiEndpoint.SCALE_HISTORY)
        }
    }

    @Test fun callbackCannotRedirectToOtherOriginsOrAmbiguousUrls() {
        for (url in listOf("http://sts.api.io.mi.com/sts", "https://sts.api.io.mi.com.evil.test/sts",
            "https://user:secret@sts.api.io.mi.com/sts", "https://sts.api.io.mi.com:443/sts",
            "https://sts.api.io.mi.com/sts#ticket", "https://sts.api.io.mi.com./sts",
            "https://sts.api.io.mi.com/%73ts", "https://sts.api.io.mi.com/a/../sts",
            "https://127.0.0.1/sts", "https://account.xiaomi.com/sts", "https://sts.api.io.mi.com/sts\n")) {
            val failure = assertThrows(XiaomiAccessException::class.java) { XiaomiUrlPolicy.check(url, XiaomiEndpoint.SERVICE_TICKET) }
            assertEquals(XiaomiAccessFailure.UNSAFE_ENDPOINT, failure.reason)
            assertFalse(failure.message!!.contains(url))
            assertNull(failure.cause)
        }
    }

    @Test fun credentialsAndOtherVendorApisAreNotAcceptedAsHistoryRequests() {
        for (url in listOf("https://api.io.mi.com/app/home/device_list", "https://api.io.mi.com/app/eco/scale/deleteData",
            "https://de.api.io.mi.com/app/eco/scale/getData", "https://api.io.mi.com/app/eco/scale/getData?uid=other")) {
            assertThrows(XiaomiAccessException::class.java) { XiaomiUrlPolicy.check(url, XiaomiEndpoint.SCALE_HISTORY) }
        }
    }

    @Test fun headerInjectionAndCookieForwardingToTicketAreRejected() {
        for (headers in listOf(mapOf("Cookie" to "serviceToken=fixture"), mapOf("Host" to "evil.test"), mapOf("Accept" to "text/plain\r\nCookie: x"))) {
            assertThrows(XiaomiAccessException::class.java) {
                XiaomiUrlPolicy.check(XiaomiHttpRequest(XiaomiEndpoint.SERVICE_TICKET, XiaomiUrlPolicy.CALLBACK, headers))
            }
        }
        assertThrows(XiaomiAccessException::class.java) {
            XiaomiUrlPolicy.check(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_PASSWORD, XiaomiUrlPolicy.PASSWORD))
        }
    }

    @Test fun redactedObjectsAndStaticFailuresDoNotPrintSecrets() {
        val session = XiaomiNetworkFixtures.session()
        val request = XiaomiHttpRequest(XiaomiEndpoint.LOGIN_PASSWORD, XiaomiUrlPolicy.PASSWORD, mapOf("Cookie" to "fixture-secret"), "hash=fixture-secret")
        for (value in listOf(session.toString(), request.toString(), XiaomiHttpResponse(200, "fixture-secret".toByteArray()).toString())) {
            assertTrue(value.contains("redacted"))
            assertFalse(value.contains("fixture-secret"))
            assertFalse(value.contains("fixture-service-token"))
        }
    }

    @Test fun malformedUtf8IsRejectedInsteadOfSilentlyReplaced() {
        assertThrows(XiaomiAccessException::class.java) { XiaomiHttpResponse(200, byteArrayOf(0xc3.toByte(), 0x28)).text() }
    }
}
