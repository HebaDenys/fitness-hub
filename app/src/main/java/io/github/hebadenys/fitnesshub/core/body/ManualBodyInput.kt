package io.github.hebadenys.fitnesshub.core.body

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

data class ManualBodyInput(
    val measuredAtMillis: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?
)

enum class ManualBodyInputError {
    MISSING_VALUES,
    INVALID_WEIGHT,
    INVALID_BODY_FAT,
    INVALID_TIMESTAMP,
    FUTURE_TIMESTAMP
}

sealed interface ManualBodyInputResult {
    data class Valid(val input: ManualBodyInput) : ManualBodyInputResult
    data class Invalid(val error: ManualBodyInputError) : ManualBodyInputResult
}

private val MANUAL_BODY_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

fun formatManualBodyTimestamp(instant: Instant, zone: ZoneId): String =
    MANUAL_BODY_TIME_FORMAT.format(instant.atZone(zone))

fun parseManualBodyInput(
    weightRaw: String,
    bodyFatRaw: String,
    timestampRaw: String,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now()
): ManualBodyInputResult {
    fun number(raw: String): Double? =
        raw.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    val weightText = weightRaw.trim()
    val fatText = bodyFatRaw.trim()
    if (weightText.isEmpty() && fatText.isEmpty()) {
        return ManualBodyInputResult.Invalid(ManualBodyInputError.MISSING_VALUES)
    }

    val weight = number(weightText)
    if (weightText.isNotEmpty() && (weight == null || !weight.isFinite() || weight !in 1.0..500.0)) {
        return ManualBodyInputResult.Invalid(ManualBodyInputError.INVALID_WEIGHT)
    }

    val bodyFat = number(fatText)
    if (fatText.isNotEmpty() && (bodyFat == null || !bodyFat.isFinite() || bodyFat !in 0.0..100.0)) {
        return ManualBodyInputResult.Invalid(ManualBodyInputError.INVALID_BODY_FAT)
    }

    val local = try {
        LocalDateTime.parse(timestampRaw.trim(), MANUAL_BODY_TIME_FORMAT)
    } catch (_: DateTimeParseException) {
        return ManualBodyInputResult.Invalid(ManualBodyInputError.INVALID_TIMESTAMP)
    }
    if (zone.rules.getValidOffsets(local).size != 1) {
        return ManualBodyInputResult.Invalid(ManualBodyInputError.INVALID_TIMESTAMP)
    }
    val zoned = local.atZone(zone)
    if (zoned.toLocalDateTime() != local) {
        return ManualBodyInputResult.Invalid(ManualBodyInputError.INVALID_TIMESTAMP)
    }
    val instant = zoned.toInstant()
    if (instant.isAfter(now.plusSeconds(5 * 60))) {
        return ManualBodyInputResult.Invalid(ManualBodyInputError.FUTURE_TIMESTAMP)
    }
    val earliest = LocalDateTime.of(1900, 1, 1, 0, 0).atZone(zone).toInstant()
    if (instant.isBefore(earliest)) {
        return ManualBodyInputResult.Invalid(ManualBodyInputError.INVALID_TIMESTAMP)
    }

    return ManualBodyInputResult.Valid(
        ManualBodyInput(
            measuredAtMillis = instant.toEpochMilli(),
            weightKg = weight,
            bodyFatPercent = bodyFat
        )
    )
}
