package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic metadata, never copied account responses. */
internal fun sourceRow(account: String = "11", device: String = "fixture-scale", name: String = "Fixture person",
    at: Long = XiaomiNetworkFixtures.clock.millis() - 1000, includeUid: Boolean = true): XiaomiJson.Object {
    fun text(value: String) = XiaomiJson.Text(value)
    val fields = linkedMapOf<String, XiaomiJson>(
        "model" to text("yunmai.scales.ms103"), "fromSource" to XiaomiJson.Number("1"),
        "accountId" to XiaomiJson.Number(account), "did" to text(device), "sn" to text("fixture-$at-$account"),
        "dataVersion" to XiaomiJson.Number("1"), "createTime" to XiaomiJson.Number(at.toString()),
        "data" to XiaomiJson.Object(mapOf("weight" to XiaomiJson.Number("73.5"), "bfp" to XiaomiJson.Number("18"),
            "user" to XiaomiJson.Object(mapOf("accountId" to text(account), "name" to text(name)))))
    )
    if (includeUid) fields["uid"] = XiaomiJson.Number("10001")
    return XiaomiJson.Object(fields)
}

internal fun sourceResponse(rows: List<XiaomiJson.Object>): String = XiaomiJson.Object(mapOf(
    "code" to XiaomiJson.Number("0"), "result" to XiaomiJson.Array(rows))).encode()

class XiaomiDiscoveryTest {
    private val f get() = XiaomiNetworkFixtures
    private suspend fun discover(vararg rows: XiaomiJson.Object) = XiaomiDiscoveryReader(f.clock)
        .read(XiaomiPageSource { sourceResponse(rows.toList()) }, f.scope, f.clock.millis())

    @Test fun sameNamesAndWeightsDoNotMergeDifferentSubjectsOrDevices() = runTest {
        val page = discover(sourceRow(account = "11"), sourceRow(account = "12"), sourceRow(account = "11", device = "second-scale"))
        assertEquals(3, page.candidates.size)
        assertEquals(3, page.candidates.map { it.key }.distinct().size)
        assertEquals(setOf("11", "12"), page.candidates.map { it.subject.accountId }.toSet())
    }

    @Test fun repeatedSubjectDeviceKeepsOneCandidateWithLatestName() = runTest {
        val page = discover(sourceRow(name = "New label"), sourceRow(name = "Old label", at = f.clock.millis() - 5000))
        assertEquals(1, page.candidates.size)
        assertEquals("New label", page.candidates.single().displayName)
        assertEquals(2, page.rowsScanned)
    }

    @Test fun missingUidIsUnresolvedRatherThanInferredFromLoggedInAccount() = runTest {
        val page = discover(sourceRow(includeUid = false))
        assertTrue(page.candidates.isEmpty())
        assertEquals(1, page.unresolvedRows)
        assertEquals(1, page.rowsScanned)
    }

    @Test fun maliciousOrOverlongDisplayNameDoesNotBecomeAnIdentityLabel() = runTest {
        for (name in listOf("fixture\u202esecret", "line\nsecond", "a".repeat(81))) {
            assertNull(discover(sourceRow(name = name)).candidates.single().displayName)
        }
    }

    @Test fun fullPageExposesContinuationInsteadOfClaimingWholeHouseholdWasFound() = runTest {
        val rows = (1..20).map { sourceRow(at = f.clock.millis() - it * 1000) }
        val page = XiaomiDiscoveryReader(f.clock).read(XiaomiPageSource { sourceResponse(rows) }, f.scope, f.clock.millis())
        assertEquals(rows.last().fields["createTime"], XiaomiJson.Number(page.nextBeforeMillis.toString()))
        assertEquals(20, page.rowsScanned)
    }

    @Test fun emptyPageIsDistinctFromMissingIdentity() = runTest {
        val page = discover()
        assertEquals(0, page.rowsScanned)
        assertEquals(0, page.unresolvedRows)
        assertNull(page.nextBeforeMillis)
    }

    @Test fun candidateKeyIncludesConnectionRegionAccountModelSubjectAndDevice() {
        val subject = XiaomiSubject("10001", "11")
        val original = XiaomiDiscoveryReader.candidateKey(f.scope, subject, "fixture")
        for (scope in listOf(f.scope.copy(connectionId = "second"), f.scope.copy(region = XiaomiRegion.US),
            f.scope.copy(loginUid = "20002"), f.scope.copy(model = "yunmai.scales.ms104"))) {
            assertNotEquals(original, XiaomiDiscoveryReader.candidateKey(scope, subject, "fixture"))
        }
        assertNotEquals(original, XiaomiDiscoveryReader.candidateKey(f.scope, subject.copy(accountId = "12"), "fixture"))
        assertNotEquals(original, XiaomiDiscoveryReader.candidateKey(f.scope, subject, "other"))
    }

    @Test fun metadataStringRepresentationsAreRedacted() = runTest {
        val page = discover(sourceRow(name = "Fixture private label"))
        assertFalse(page.toString().contains("10001"))
        assertFalse(page.candidates.single().toString().contains("Fixture private label"))
        assertFalse(XiaomiAccountInfo("fixture", XiaomiRegion.DE, "10001").toString().contains("10001"))
    }
}
