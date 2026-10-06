package io.github.hebadenys.fitnesshub.core.xiaomi

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class XiaomiScaleProtocolTest {
    private val now = Instant.parse("2026-10-06T12:00:00Z")
    private val parser = XiaomiScaleProtocol(Clock.fixed(now, ZoneOffset.UTC))
    private val scope = XiaomiScope("fixture-connection", XiaomiRegion.DE, "yunmai.scales.ms104", "900001")
    private val before = now.toEpochMilli()

    private fun fixture(name: String) = javaClass.getResourceAsStream("/xiaomi/$name.json")!!
        .bufferedReader().use { it.readText() }
    private fun decode(text: String, selected: XiaomiScope = scope) = parser.parse(text, selected, before)
    private fun row(data: String = "{\"weight\":84.25}", extra: String = "", time: Long = 1750000000000): String =
        """{"model":"yunmai.scales.ms104","uid":900001,"accountId":7,"did":"fixture-scale-a","createTime":$time,"data":$data,"fromSource":2$extra}"""
    private fun expect(failure: XiaomiFailure, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        check(error is XiaomiProtocolException && error.failure == failure) { "Expected static protocol failure $failure" }
        check(error.cause == null)
    }

    @Test fun globalNumericStringsKeepMeaningAndSourceMetadata() {
        val record = decode(fixture("global-page")).records.single()
        check(record.subject == XiaomiSubject("900001", "7"))
        check(record.deviceId == "fixture-scale-a" && record.serial == "fixture-record-a" && record.dataVersion == 2L)
        check(record.metrics["weight"]?.value == 84.25 && record.metrics["weight"]?.unit == XiaomiUnit.KG)
        check(record.metrics["bfp"]?.method == XiaomiMethod.VENDOR_ESTIMATE)
        check(record.metrics["slm"]?.value == 59.1 && record.metrics["ffm"]?.value == 66.14)
        check(record.measuredAt == Instant.ofEpochMilli(1750000000000))
    }

    @Test fun chinaSourceOneUsesOuterTimestampNotBuggySeconds() {
        val result = decode(fixture("cn-page"), scope.copy(region = XiaomiRegion.CN, model = "yunmai.scales.ms103"))
        val record = result.records.single()
        check(record.measuredAt?.toEpochMilli() == 1750000000000)
        check(record.vendorFields.fields["time"] == XiaomiJson.Number("1750000000"))
        check(record.metrics["bfp"]?.value == 21.5)
    }

    @Test fun sourceThreeReadsNestedCompositionAndSeparateMeasurementTime() {
        val record = decode(fixture("source3-page"), scope.copy(model = "yunmai.scales.ms103")).records.single()
        check(record.measuredAt?.toEpochMilli() == 1749999940000)
        check(record.createTimeMillis == 1750000000000)
        check(record.metrics["bfp"]?.vendorPath == "bodyResData.bfp")
        check(record.metrics["bfp"]?.value == 21.5)
        check((record.vendorFields.fields["bodyResData"] as XiaomiJson.Object).fields["newNumericField"] == XiaomiJson.Text("3.4"))
    }

    @Test fun unknownNumericExtensionsPreserveRepresentationWithoutInventingUnits() {
        val record = decode(fixture("global-page")).records.single()
        val segment = record.vendorFields.fields["newSegmentScore"] as XiaomiJson.Object
        check(segment.fields["left"] == XiaomiJson.Text("8.25"))
        check(segment.fields["right"] == XiaomiJson.Number("8.4"))
        check("newSegmentScore" !in record.metrics)
        check(record.metrics["bmr"]?.unit == XiaomiUnit.KCAL_PERIOD_UNVERIFIED)
    }

    @Test fun missingNullAndMeasuredZeroAreDifferent() {
        val record = decode("[${row("""{"weight":null,"bfp":0}""")}]").records.single()
        check("heartRate" !in record.metrics)
        check(record.metrics["weight"]?.value == null && record.metrics["weight"]?.raw == XiaomiJson.Null)
        check(record.metrics["bfp"]?.value == 0.0)
    }

    @Test fun invalidMetricsAreFlaggedNotClampedOrFabricated() {
        val record = decode("[${row("""{"weight":-12,"bfp":120,"bwp":"NaN","heartRate":"Infinity"}""")}]").records.single()
        check(record.metrics.values.all { it.value == null })
        check(record.metrics["bfp"]?.raw == XiaomiJson.Number("120"))
        check(XiaomiIssue.INVALID_PERCENTAGE in record.metrics.getValue("bfp").issues)
        check(XiaomiIssue.NONPOSITIVE_VALUE in record.metrics.getValue("weight").issues)
    }

    @Test fun hugeNumbersRemainRawButCannotBecomeInfiniteNormalizedValues() {
        val record = decode("[${row("""{"weight":1e9999}""")}]").records.single()
        check(record.metrics["weight"]?.value == null)
        check(record.metrics["weight"]?.raw == XiaomiJson.Number("1e9999"))
    }

    @Test fun unknownUnitOverrideCannotProduceAssumedKilograms() {
        val record = decode("[${row("""{"weight":180,"weightUnit":"lb"}""")}]").records.single()
        check(record.metrics["weight"]?.value == null && record.metrics["weight"]?.unit == XiaomiUnit.UNKNOWN)
    }

    @Test fun unknownModelDoesNotClaimS400ProCompatibility() {
        val response = "[${row()}]".replace("yunmai.scales.ms104", "yunmai.scales.unverified")
        val record = decode(response, scope.copy(model = "yunmai.scales.unverified")).records.single()
        check(XiaomiIssue.MODEL_UNVERIFIED in record.issues && record.metrics["weight"]?.value == null)
        check(record.vendorFields.fields["weight"] == XiaomiJson.Number("84.25"))
    }

    @Test fun modelMismatchBlocksNormalization() {
        val record = decode("[${row()}]", scope.copy(model = "yunmai.scales.ms103")).records.single()
        check(XiaomiIssue.MODEL_MISMATCH in record.issues && record.metrics["weight"]?.value == null)
    }

    @Test fun profilesWithSameNameRemainDifferentSubjects() {
        val data = """{"weight":84.25,"user":{"name":"Synthetic same name"}}"""
        val records = decode("[${row(data)},${row(data).replace("\"accountId\":7", "\"accountId\":8")}]").records
        check(records[0].subject != records[1].subject)
        check(records.all { "user" !in it.vendorFields.fields })
    }

    @Test fun missingSubjectIsNotReplacedByLoginUidOrName() {
        val record = decode("[${row().replace("\"accountId\":7,", "")}]").records.single()
        check(record.subject == null && XiaomiIssue.IDENTITY_INCOMPLETE in record.issues)
    }

    @Test fun contradictorySubjectIsUnresolved() {
        val record = decode("[${row("""{"weight":84.25,"user":{"accountId":"8"}}""")}]").records.single()
        check(record.subject == null && XiaomiIssue.IDENTITY_CONFLICT in record.issues)
    }

    @Test fun accountZeroIsAnExplicitIdNotAMissingValue() {
        val record = decode("[${row().replace("\"accountId\":7", "\"accountId\":0")}]").records.single()
        check(record.subject?.accountId == "0")
    }

    @Test fun secretsAreNotStoredInVendorExtensionsOrToString() {
        val record = decode("[${row("""{"weight":84.25,"password":"PRIVATE_SENTINEL","cookie":123,"extra":{"accessToken":"PRIVATE_SENTINEL","testMetric":2},"unknownText":"PRIVATE_SENTINEL"}""")}]").records.single()
        check("PRIVATE_SENTINEL" !in record.vendorFields.encode())
        check("password" !in record.vendorFields.fields && "cookie" !in record.vendorFields.fields)
        check("84.25" !in record.toString() && "900001" !in scope.toString())
        check("84.25" !in record.metrics.getValue("weight").toString())
    }

    @Test fun vendorErrorsCannotMasqueradeAsEmptyHistoryOrLeakMessage() {
        expect(XiaomiFailure.VENDOR_ERROR) { decode("""{"code":-3,"message":"PRIVATE_SENTINEL","result":[]}""") }
        expect(XiaomiFailure.UNEXPECTED_RESPONSE) { decode("""{"code":0,"result":null}""") }
        expect(XiaomiFailure.UNEXPECTED_RESPONSE) { decode("{}") }
    }

    @Test fun unsupportedFromSourceIsPreservedButNotNormalized() {
        val record = decode("[${row().replace("\"fromSource\":2", "\"fromSource\":99")}]").records.single()
        check(XiaomiIssue.UNSUPPORTED_FORMAT in record.issues && record.metrics["weight"]?.value == null)
        check(record.vendorFields.fields["weight"] == XiaomiJson.Number("84.25"))
    }

    @Test fun futureMeasurementIsFlaggedWithoutChangingItsValue() {
        val future = before + 10_000
        val page = parser.parse("[${row(time = future)}]", scope, future + 1)
        check(XiaomiIssue.FUTURE_TIMESTAMP in page.records.single().issues)
        check(page.records.single().measuredAt?.toEpochMilli() == future)
    }

    @Test fun sourceThreeSecondsAreNotSilentlyMultiplied() {
        val record = decode("[${row("""{"weight":84.25,"time":"1750000000"}""").replace("\"fromSource\":2", "\"fromSource\":3")}]").records.single()
        check(record.measuredAt == null && XiaomiIssue.INVALID_VALUE in record.issues)
    }

    @Test fun fullPageCursorUsesOuterCreateTimeWithoutSubtractingOne() {
        val response = (0..19).joinToString(",", "[", "]") { row(time = 1750000000000 - it * 1000L) }
        val page = decode(response)
        check(page.nextBeforeMillis == 1749999981000L && !page.terminalByShortPage)
        check(page.records.size == 20)
    }

    @Test fun emptyAndShortPagesHaveExplicitTerminationHeuristic() {
        check(decode("[]").terminalByShortPage)
        val page = decode("[${row()}]")
        check(page.nextBeforeMillis == null && page.terminalByShortPage)
    }

    @Test fun stoppedCursorFailsRatherThanLoopingOrSkippingEqualTimestamps() {
        val response = (0..19).joinToString(",", "[", "]") { row(time = before) }
        expect(XiaomiFailure.STALLED_CURSOR) { decode(response) }
    }

    @Test fun changedOrderOrPageSizeRequiresProtocolReview() {
        expect(XiaomiFailure.UNORDERED_PAGE) { decode("[${row(time = 1750000000000)},${row(time = 1750000001000)}]") }
        expect(XiaomiFailure.UNEXPECTED_PAGE_SIZE) { decode((0..20).joinToString(",", "[", "]") { row() }) }
        expect(XiaomiFailure.INVALID_TIMESTAMP) { decode("[${row(time = before + 1)}]") }
    }

    @Test fun requestRegionPathsAndUidTypesMatchPinnedProtocol() {
        for (region in XiaomiRegion.entries) {
            val req = XiaomiScaleRequest.history(scope.copy(region = region), before)
            val body = XiaomiJsonReader(req.dataJson).read() as XiaomiJson.Object
            if (region == XiaomiRegion.CN) {
                check(req.baseUrl == "https://api.io.mi.com/app" && req.signaturePath == "/eco/scale/getData")
                check(body.fields["uid"] == XiaomiJson.Number("900001"))
                check((body.fields["param"] as XiaomiJson.Object).fields["endTime"] == XiaomiJson.Number("1"))
            } else {
                check(req.baseUrl == "https://${region.wireName}.api.io.mi.com/app")
                check(req.signaturePath == "/eco/common/scale/getUserDataByPage")
                check(body.fields["uid"] == XiaomiJson.Text("900001"))
                check(body.fields["accountId"] == XiaomiJson.Number("0"))
            }
            check(body.fields["did"] == XiaomiJson.Number("0"))
            check(req.modelHeader == scope.model && "900001" !in req.toString())
        }
    }

    @Test fun unsafeScopeAndCursorAreRejectedBeforeCreatingRequests() {
        expect(XiaomiFailure.INVALID_SCOPE) { scope.copy(model = "https://example.invalid") }
        expect(XiaomiFailure.INVALID_SCOPE) { scope.copy(loginUid = "1\r\nHeader:value") }
        expect(XiaomiFailure.INVALID_CURSOR) { XiaomiScaleRequest.history(scope, 0) }
    }
}
