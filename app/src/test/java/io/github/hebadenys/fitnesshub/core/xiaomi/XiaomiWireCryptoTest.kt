package io.github.hebadenys.fitnesshub.core.xiaomi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.SecureRandom

class XiaomiWireCryptoTest {
    @Test fun wireVectorMatchesIndependentGoStandardLibraryImplementation() {
        // Generated using Go crypto/rc4, crypto/sha1 and crypto/sha256, not this Kotlin implementation.
        val key = XiaomiWireCrypto.signedNonce(ByteArray(16) { it.toByte() }, ByteArray(12) { it.toByte() })
        assertEquals("Nz6YMO70n75ITJeXTWpiyXXLp6IBZ0yT0/hEJB0FDOg=", XiaomiWireCrypto.b64(key))
        val path = "/eco/scale/getData"
        val raw = """{"beginTime":1790000000000,"endTime":1}"""
        val rawHash = XiaomiWireCrypto.signature(path, raw, null, key)
        assertEquals("WP2XVl7sjib/aLvTyFzT2L7gfus=", rawHash)
        val data = XiaomiWireCrypto.b64(XiaomiWireCrypto.crypt(key, raw.toByteArray()))
        val hash = XiaomiWireCrypto.b64(XiaomiWireCrypto.crypt(key, rawHash.toByteArray()))
        assertEquals("Gjc73s7YQQyQTPGd6C/UX8doYbRn3WrbItkbXzHudTx7c4mptvzC", data)
        assertEquals("NkVr4//dGCuTSPaQs1KVMo4eK9BloW2MdJxEQA==", hash)
        assertEquals("kk54XJbF87+/B4gWLBplwGE7Hz4=", XiaomiWireCrypto.signature(path, data, hash, key))
        assertEquals(raw, String(XiaomiWireCrypto.crypt(key, XiaomiWireCrypto.unbase64(data))))
    }

    @Test fun nonceContainsRandomPrefixAndBigEndianMinutes() {
        val random = object : SecureRandom() { override fun nextBytes(bytes: ByteArray) { bytes.fill(7) } }
        val nonce = XiaomiWireCrypto.nonce(XiaomiNetworkFixtures.clock, random)
        assertArrayEquals(ByteArray(8) { 7 }, nonce.copyOfRange(0, 8))
        assertEquals(XiaomiNetworkFixtures.clock.instant().epochSecond / 60, ByteBuffer.wrap(nonce, 8, 4).int.toLong())
    }

    @Test fun passwordHashUsesVendorUppercaseMd5() {
        assertEquals("5F4DCC3B5AA765D61D8327DEB882CF99", XiaomiWireCrypto.passwordHash("password".toCharArray()))
    }

    @Test fun base64AndInputsAreBoundedAndStrict() {
        for (value in listOf("not!base64", "YWJj\n", "a", "AA==tail")) {
            assertThrows(XiaomiAccessException::class.java) { XiaomiWireCrypto.unbase64(value) }
        }
        assertThrows(XiaomiAccessException::class.java) { XiaomiWireCrypto.signedNonce(byteArrayOf(), ByteArray(12)) }
        assertThrows(XiaomiAccessException::class.java) { XiaomiWireCrypto.crypt(byteArrayOf(), byteArrayOf()) }
        assertThrows(XiaomiAccessException::class.java) { XiaomiWireCrypto.unbase64("AAAA", 3) }
    }

    @Test fun formEncodingPreservesUnicodePlusAndAmpersandWithoutAddingParameters() {
        val fields = mapOf("user" to "fixture+test@example.test", "qs" to "a&b=c", "value" to "á + / =")
        assertEquals(fields, XiaomiNetworkFixtures.form(XiaomiWireCrypto.form(fields)))
    }
}
