package io.github.hebadenys.fitnesshub.core.scale

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Imports historical scale data exported by SmartScaleConnect.
 *
 * SmartScaleConnect can read Xiaomi Home S400 history and export it as CSV.
 * Fitness Hub intentionally imports the resulting local file instead of asking
 * for Xiaomi credentials itself. This keeps the Android app local-first while
 * still covering measurements that predate the BLE listener.
 */
class ScaleHistoryCsvImporter(
    private val dao: ScaleDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    suspend fun import(csv: String, selectedUser: String? = null): ScaleHistoryImportResult {
        val table = runCatching { parse(csv) }
            .getOrElse { return ScaleHistoryImportResult.Failure("invalid_csv") }

        if ("Date" !in table.header || "Weight" !in table.header) {
            return ScaleHistoryImportResult.Failure("unsupported_format")
        }

        val users = table.rows
            .mapNotNull { it["User"]?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()

        val filter = selectedUser?.trim()?.takeIf(String::isNotEmpty)
        if (users.size > 1 && filter == null) {
            return ScaleHistoryImportResult.MultipleUsers(users)
        }
        if (filter != null && users.isNotEmpty() && users.none { it.equals(filter, ignoreCase = true) }) {
            return ScaleHistoryImportResult.Failure("user_not_found")
        }

        var imported = 0
        var duplicates = 0
        var skipped = 0

        table.rows.forEach { row ->
            val user = row["User"]?.trim().orEmpty()
            if (filter != null && !user.equals(filter, ignoreCase = true)) return@forEach

            val date = parseDate(row["Date"]) ?: run {
                skipped++
                return@forEach
            }
            val weight = parsePositive(row["Weight"]) ?: run {
                skipped++
                return@forEach
            }
            val measuredAt = date.atZone(zoneId).toInstant().toEpochMilli()
            val source = row["Source"]?.trim().takeUnless { it.isNullOrBlank() } ?: SOURCE_ID

            val inserted = dao.insertMeasurement(
                ScaleMeasurementEntity(
                    deviceAddress = source,
                    measuredAtMillis = measuredAt,
                    weightKg = weight,
                    impedanceOhms = null,
                    heartRateBpm = parsePositive(row["HeartRate"])?.toLong(),
                    profileSlot = null,
                    provenance = ScaleMeasurementEntity.PROVENANCE_IMPORTED,
                    algorithm = FORMAT_ID
                )
            )
            if (inserted == -1L) duplicates++ else imported++

            val bodyFat = parsePositive(row["BodyFat"])
            val bodyWater = parsePositive(row["BodyWater"])
            val bmr = parsePositive(row["BasalMetabolism"])
            val visceral = parsePositive(row["VisceralFat"])
            if (bodyFat != null || bodyWater != null || bmr != null || visceral != null) {
                dao.upsertEstimate(
                    BodyCompositionEstimateEntity(
                        measuredAtMillis = measuredAt,
                        bodyFatPercent = bodyFat,
                        leanMassKg = null,
                        bodyWaterPercent = bodyWater,
                        basalMetabolicRateKcal = bmr,
                        visceralFatIndex = visceral,
                        provenance = ScaleMeasurementEntity.PROVENANCE_IMPORTED,
                        algorithm = FORMAT_ID
                    )
                )
            }
        }

        return ScaleHistoryImportResult.Success(
            importedRows = imported,
            duplicateRows = duplicates,
            skippedRows = skipped,
            users = users
        )
    }

    private fun parseDate(raw: String?): LocalDateTime? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return DATE_FORMATS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(value, formatter) }.getOrNull()
        }
    }

    private fun parsePositive(raw: String?): Double? =
        raw?.trim()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 && it.isFinite() }

    private data class CsvTable(
        val header: Set<String>,
        val rows: List<Map<String, String>>
    )

    private fun parse(csv: String): CsvTable {
        val records = parseRecords(csv)
        require(records.isNotEmpty()) { "CSV is empty" }
        val headers = records.first().map { it.trim().removePrefix("\uFEFF") }
        val rows = records.drop(1).filter { record -> record.any { it.isNotBlank() } }.map { record ->
            headers.mapIndexed { index, name -> name to record.getOrElse(index) { "" } }.toMap()
        }
        return CsvTable(headers.toSet(), rows)
    }

    /**
     * Small RFC-4180 compatible reader. It supports quoted commas, escaped
     * quotes and CRLF without adding a dependency solely for CSV import.
     */
    private fun parseRecords(csv: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        fun finishField() {
            row.add(field.toString())
            field.setLength(0)
        }
        fun finishRow() {
            finishField()
            records.add(row)
            row = mutableListOf()
        }

        while (index < csv.length) {
            val ch = csv[index]
            if (quoted) {
                when {
                    ch == '"' && index + 1 < csv.length && csv[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    ch == '"' -> quoted = false
                    else -> field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> quoted = true
                    ',' -> finishField()
                    '\n' -> finishRow()
                    '\r' -> if (index + 1 >= csv.length || csv[index + 1] != '\n') finishRow()
                    else -> field.append(ch)
                }
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        require(!quoted) { "Unterminated quoted field" }
        return records
    }

    companion object {
        const val SOURCE_ID = "xiaomi_home_csv"
        const val FORMAT_ID = "smartscaleconnect_csv"
        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )
    }
}

sealed interface ScaleHistoryImportResult {
    data class Success(
        val importedRows: Int,
        val duplicateRows: Int,
        val skippedRows: Int,
        val users: List<String>
    ) : ScaleHistoryImportResult

    data class MultipleUsers(val users: List<String>) : ScaleHistoryImportResult
    data class Failure(val reason: String) : ScaleHistoryImportResult
}
