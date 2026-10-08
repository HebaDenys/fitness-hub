package io.github.hebadenys.fitnesshub.feature.body

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver.Observation
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun bodyObservationValue(value: Observation): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 15 }.format(value.value) + " " + value.unit

internal fun bodyObservationTime(value: Observation): String =
    if (!bodyHasExactTime(value)) bodyObservationDay(value).toString()
    else DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm:ss.SSS xxx")
        .withZone(ZoneId.systemDefault()).format(value.measuredAt)

internal fun bodyObservationKey(value: Observation, index: Int): String =
    value.sourceId?.let { "${value.metric}:${value.source}:$it" }
        ?: "${value.metric}:${value.measuredAt}:${value.value}:$index"

@Composable
internal fun BodyObservationDetails(value: Observation) {
    val source = when (value.source) {
        CanonicalBodyMetricResolver.Source.MANUAL -> R.string.body_source_manual
        CanonicalBodyMetricResolver.Source.SCALE -> R.string.body_source_scale
        CanonicalBodyMetricResolver.Source.HEALTH_CONNECT -> R.string.settings_hc_section
        CanonicalBodyMetricResolver.Source.XIAOMI -> R.string.body_source_xiaomi
    }
    val method = when (value.method) {
        CanonicalBodyMetricResolver.Method.MEASURED -> R.string.body_method_recorded
        CanonicalBodyMetricResolver.Method.VENDOR_ESTIMATE -> R.string.body_method_vendor
        CanonicalBodyMetricResolver.Method.LOCAL_ESTIMATE -> R.string.body_method_local
        CanonicalBodyMetricResolver.Method.UNKNOWN -> R.string.body_method_unknown
    }
    Text(bodyObservationTime(value), style = MaterialTheme.typography.bodyMedium)
    if (!bodyHasExactTime(value)) {
        Text(stringResource(R.string.body_time_unavailable), style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        stringResource(R.string.body_source_and_method, stringResource(source), stringResource(method)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun BodyMeasurementCard(title: String, value: Observation?, prominent: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                value?.let(::bodyObservationValue) ?: stringResource(R.string.value_unavailable),
                style = if (prominent) MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 48.sp)
                    else MaterialTheme.typography.headlineMedium
            )
            value?.let { BodyObservationDetails(it) }
        }
    }
}
