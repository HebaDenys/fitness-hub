package io.github.hebadenys.fitnesshub.core.scale

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Local SmartScaleConnect CSV import. Xiaomi credentials never enter this class. */
class ScaleHistoryCsvImporter(
    private val dao: ScaleDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    suspend fun import(csv: String, selectedUser: String? = null): ScaleHistoryImportResult =
        withContext(Dispatchers.IO) {
            if (csv.length > MAX_CSV_CHARACTERS) {
                return@withContext ScaleHistoryImportResult.Failure("file_too_large")
            }
            val table = try {
                parse(csv)
            } catch (_: IllegalArgumentException) {
                return@withContext ScaleHistoryImportResult.Failure("invalid_csv")
            }
            if (!table.headers.containsAll(listOf("Date", "Weight"))) {
                return@withContext ScaleHistoryImportResult.Failure("unsupported_format")
            }
            val users = table.rows.mapNotNull { it["User"]?.trim()?.takeIf(String::isNotEmpty) }
                .distinct().sorted()
            val requested = selectedUser?.trim()?.takeIf(String::isNotEmpty)
            if (requested == null && users.size > 1) {
                return@withContext ScaleHistoryImportResult.MultipleUsers(users)
            }
            if (requested != null && requested !in users) {
                return@withContext ScaleHistoryImportResult.Failure("user_not_found")
            }
            // Exact matching matters: profiles named "Alex" and "alex" are not interchangeable.
            val owner = requested ?: users.singleOrNull()
            var skipped = 0
            val rows = mutableListOf<ScaleHistoryRow>()
            for (record in table.rows) {
                val user = record["User"]?.trim().orEmpty()
                if (owner != null && user != owner) {
                    if (user.isEmpty()) skipped++
                    continue
                }
                val timestamp = parseInstant(record["Date"])
                val weight = record["Weight"]?.toNumber()?.takeIf { it > 0 }
                if (timestamp == null || weight == null) {
                    skipped++
                    continue
                }
                val source = record["Source"]?.trim()?.takeIf(String::isNotEmpty) ?: SOURCE_ID
                val bodyFat = record["BodyFat"]?.toNumber()?.takeIf { it in 0.0..100.0 }
                val bodyWater = record["BodyWater"]?.toNumber()?.takeIf { it in 0.0..100.0 }
                val bmr = record["BasalMetabolism"]?.toNumber()?.takeIf { it > 0 }
                val visceral = record["VisceralFat"]?.toNumber()?.takeIf { it >= 0 }
                val heartRate = record["HeartRate"]?.toNumber()
                    ?.takeIf { it > 0 && it <= Long.MAX_VALUE.toDouble() && it % 1.0 == 0.0 }?.toLong()
                val composition = if (listOf(bodyFat, bodyWater, bmr, visceral).any { it != null }) {
                    BodyCompositionEstimateEntity(
                        measuredAtMillis = timestamp.toEpochMilli(),
                        bodyFatPercent = bodyFat,
                        leanMassKg = null,
                        bodyWaterPercent = bodyWater,
                        basalMetabolicRateKcal = bmr,
                        visceralFatIndex = visceral,
                        provenance = ScaleMeasurementEntity.PROVENANCE_IMPORTED,
                        algorithm = FORMAT_ID
                    )
                } else null
                rows += ScaleHistoryRow(
                    measurement = ScaleMeasurementEntity(
                        deviceAddress = source,
                        measuredAtMillis = timestamp.toEpochMilli(),
                        weightKg = weight,
                        impedanceOhms = null,
                        heartRateBpm = heartRate,
                        profileSlot = null,
                        provenance = ScaleMeasurementEntity.PROVENANCE_IMPORTED,
                        algorithm = FORMAT_ID
                    ),
                    composition = composition
                )
            }
            try {
                val imported = if (rows.isEmpty()) 0 else dao.importHistoryRows(rows)
                ScaleHistoryImportResult.Success(imported, rows.size - imported, skipped, users)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The DAO transaction has rolled back. Never leak a health value from a SQL error.
                ScaleHistoryImportResult.Failure("storage_error")
            }
        }

    private fun parseInstant(raw: String?): Instant? {
        val value = raw?.trim().orEmpty()
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()?.let { return it }
        val local = DATE_FORMATS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(value, formatter) }.getOrNull()
        } ?: return null
        // Exported local timestamps have no offset. Refuse DST gaps/ambiguity instead of guessing.
        val offsets = zoneId.rules.getValidOffsets(local)
        return offsets.singleOrNull()?.let { local.toInstant(it) }
    }

    private fun String.toNumber(): Double? = trim().replace(',', '.')
        .toDoubleOrNull()?.takeIf { it.isFinite() }

    private data class CsvTable(val headers: Set<String>, val rows: List<Map<String, String>>)

    private fun parse(csv: String): CsvTable {
        val records = parseRecords(csv.removePrefix("\uFEFF"))
        require(records.isNotEmpty())
        val headers = records.first().map(String::trim)
        require(headers.none(String::isEmpty) && headers.distinct().size == headers.size)
        val rows = records.drop(1).filter { it.any(String::isNotBlank) }.map { record ->
            // A truncated row can shift User/Source. Do not silently pad or truncate it.
            require(record.size == headers.size)
            headers.zip(record).toMap()
        }
        return CsvTable(headers.toSet(), rows)
    }

    private fun parseRecords(csv: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var index = 0
        fun finishField() {
            row.add(field.toString())
            field.setLength(0)
            closedQuote = false
        }
        fun finishRow() {
            finishField()
            result.add(row)
            row = mutableListOf()
        }
        while (index < csv.length) {
            val ch = csv[index]
            if (quoted) {
                if (ch == '"') {
                    if (index + 1 < csv.length && csv[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                        closedQuote = true
                    }
                } else field.append(ch)
            } else {
                require(!closedQuote || ch == ',' || ch == '\n' || ch == '\r')
                when (ch) {
                    '"' -> { require(field.isEmpty()); quoted = true }
                    ',' -> finishField()
                    '\n' -> finishRow()
                    '\r' -> {
                        finishRow()
                        if (index + 1 < csv.length && csv[index + 1] == '\n') index++
                    }
                    else -> field.append(ch)
                }
            }
            index++
        }
        require(!quoted)
        if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) finishRow()
        return result
    }

    companion object {
        const val SOURCE_ID = "xiaomi_home_csv"
        const val FORMAT_ID = "smartscaleconnect_csv"
        const val MAX_CSV_CHARACTERS = 5 * 1024 * 1024
        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withResolverStyle(ResolverStyle.STRICT)
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
