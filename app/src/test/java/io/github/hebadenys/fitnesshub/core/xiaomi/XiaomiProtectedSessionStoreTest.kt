package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class TestXiaomiKeys : XiaomiSessionKeys {
    var key: SecretKey? = null
    var creations = 0
    override fun existing() = key
    override fun create(): SecretKey {
        creations++
        return key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
    }
    override fun delete() { key = null }
}

internal class TestXiaomiSessionFile : XiaomiSessionFile {
    var bytes: ByteArray? = null
    var failWrite = false
    override fun read() = bytes?.copyOf()
    override fun write(bytes: ByteArray) {
        if (failWrite) error("synthetic storage failure")
        this.bytes = bytes.copyOf()
    }
    override fun delete() { bytes = null }
}

internal class TestXiaomiSessionStore : XiaomiSessionStore {
    var session: XiaomiSession? = null
    override suspend fun load() = session
    override suspend fun save(session: XiaomiSession) { this.session = session }
    override suspend fun clear() { session = null }
}

class XiaomiProtectedSessionStoreTest {
    @Test fun encryptedSessionRoundTripsAcrossStoreInstancesWithoutPlaintextTokens() = runTest {
        val file = TestXiaomiSessionFile()
        val keys = TestXiaomiKeys()
        val session = XiaomiNetworkFixtures.session()
        XiaomiProtectedSessionStore(file, XiaomiSessionCipher(keys)).save(session)
        val sealed = file.bytes!!
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains(session.serviceToken))
        assertEquals(session, XiaomiProtectedSessionStore(file, XiaomiSessionCipher(keys)).load())
        assertEquals(1, keys.creations)
    }

    @Test fun encryptionUsesFreshNonceForEverySave() = runTest {
        val file = TestXiaomiSessionFile()
        val store = XiaomiProtectedSessionStore(file, XiaomiSessionCipher(TestXiaomiKeys()))
        store.save(XiaomiNetworkFixtures.session())
        val first = file.bytes!!.copyOf()
        store.save(XiaomiNetworkFixtures.session())
        assertFalse(first.contentEquals(file.bytes!!))
    }

    @Test fun tamperedCiphertextIsRejectedAndOnlySessionIsDiscarded() = runTest {
        val file = TestXiaomiSessionFile()
        val keys = TestXiaomiKeys()
        val store = XiaomiProtectedSessionStore(file, XiaomiSessionCipher(keys))
        store.save(XiaomiNetworkFixtures.session())
        file.bytes!![file.bytes!!.lastIndex] = (file.bytes!!.last().toInt() xor 1).toByte()
        expectAccess(XiaomiAccessFailure.SESSION_UNREADABLE) { store.load() }
        assertNull(file.bytes)
        assertNull(keys.key)
    }

    @Test fun missingKeyNeverTriggersSilentRegenerationOnRead() = runTest {
        val file = TestXiaomiSessionFile()
        val keys = TestXiaomiKeys()
        val store = XiaomiProtectedSessionStore(file, XiaomiSessionCipher(keys))
        store.save(XiaomiNetworkFixtures.session())
        keys.delete()
        expectAccess(XiaomiAccessFailure.SESSION_UNREADABLE) { store.load() }
        assertEquals(1, keys.creations)
        assertNull(file.bytes)
    }

    @Test fun oldFormatAndTruncatedSessionFailClosed() = runTest {
        for (bytes in listOf(byteArrayOf(1, 2), ByteArray(32))) {
            val file = TestXiaomiSessionFile().apply { this.bytes = bytes }
            val store = XiaomiProtectedSessionStore(file, XiaomiSessionCipher(TestXiaomiKeys()))
            expectAccess(XiaomiAccessFailure.SESSION_UNREADABLE) { store.load() }
        }
    }

    @Test fun failedWriteDoesNotBecomeSuccessfulLoginOrDestroyPreviousSession() = runTest {
        val file = TestXiaomiSessionFile()
        val store = XiaomiProtectedSessionStore(file, XiaomiSessionCipher(TestXiaomiKeys()))
        store.save(XiaomiNetworkFixtures.session())
        file.failWrite = true
        expectAccess(XiaomiAccessFailure.STORAGE_ERROR) { store.save(XiaomiNetworkFixtures.session().copy(serviceToken = "fixture-renewed")) }
        assertEquals(XiaomiNetworkFixtures.session(), store.load())
    }

    @Test fun disconnectRemovesCiphertextAndEncryptionKey() = runTest {
        val file = TestXiaomiSessionFile()
        val keys = TestXiaomiKeys()
        val store = XiaomiProtectedSessionStore(file, XiaomiSessionCipher(keys))
        store.save(XiaomiNetworkFixtures.session())
        store.clear()
        assertNull(file.bytes)
        assertNull(keys.key)
        assertNull(store.load())
    }

    @Test fun malformedSessionSchemaAndCookieInjectionCannotBeStored() {
        assertThrows(XiaomiAccessException::class.java) { XiaomiSessionCodec.decode("{}".toByteArray()) }
        assertThrows(XiaomiAccessException::class.java) { XiaomiNetworkFixtures.session().copy(serviceToken = "fixture; another=cookie") }
        assertThrows(XiaomiAccessException::class.java) { XiaomiNetworkFixtures.session().copy(userId = "0010001") }
    }
}
