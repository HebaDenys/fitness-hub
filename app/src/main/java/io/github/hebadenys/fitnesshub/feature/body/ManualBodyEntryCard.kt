package io.github.hebadenys.fitnesshub.feature.body

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.body.ManualBodyInputError
import io.github.hebadenys.fitnesshub.core.body.formatManualBodyTimestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualBodyEntryCard(
    state: ManualBodyEntryState,
    healthConnectAvailable: Boolean,
    hasHealthConnectPermission: Boolean,
    onGrantPermissions: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDismissState: () -> Unit,
    onDirtyChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val formatter = remember { DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT) }
    var weight by rememberSaveable { mutableStateOf("") }
    var bodyFat by rememberSaveable { mutableStateOf("") }
    var initialTimestamp by rememberSaveable { mutableStateOf(formatManualBodyTimestamp(Instant.now(), zone)) }
    var timestamp by rememberSaveable { mutableStateOf(initialTimestamp) }
    val working = state is ManualBodyEntryState.Working
    val error = (state as? ManualBodyEntryState.Invalid)?.error
    val dirty = weight.isNotBlank() || bodyFat.isNotBlank() || timestamp != initialTimestamp

    LaunchedEffect(dirty) { onDirtyChange(dirty) }
    LaunchedEffect(state) {
        if (state is ManualBodyEntryState.Saved) {
            weight = ""
            bodyFat = ""
            initialTimestamp = formatManualBodyTimestamp(Instant.now(), zone)
            timestamp = initialTimestamp
        }
    }
    fun currentTime() = runCatching { LocalDateTime.parse(timestamp, formatter) }.getOrElse { LocalDateTime.now() }
    fun updateTime(value: LocalDateTime) { timestamp = value.format(formatter); onDismissState() }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("manual_body_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.body_manual_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.body_manual_description), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.body_entry_source),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = weight, onValueChange = { weight = it.take(32); onDismissState() },
                label = { Text(stringResource(R.string.body_manual_weight)) },
                isError = error == ManualBodyInputError.INVALID_WEIGHT || error == ManualBodyInputError.MISSING_VALUES,
                enabled = !working, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("manual_weight")
            )
            OutlinedTextField(
                value = bodyFat, onValueChange = { bodyFat = it.take(32); onDismissState() },
                label = { Text(stringResource(R.string.body_manual_body_fat)) },
                isError = error == ManualBodyInputError.INVALID_BODY_FAT || error == ManualBodyInputError.MISSING_VALUES,
                enabled = !working, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("manual_body_fat")
            )
            OutlinedTextField(
                value = timestamp, onValueChange = { timestamp = it.take(64); onDismissState() },
                label = { Text(stringResource(R.string.body_manual_timestamp)) },
                supportingText = { Text(stringResource(R.string.body_manual_timestamp_hint), style = MaterialTheme.typography.bodyMedium) },
                isError = error == ManualBodyInputError.INVALID_TIMESTAMP || error == ManualBodyInputError.FUTURE_TIMESTAMP,
                enabled = !working, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("manual_timestamp")
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val value = currentTime()
                    DatePickerDialog(context, { _, year, month, day ->
                        updateTime(LocalDateTime.of(year, month + 1, day, value.hour, value.minute))
                    }, value.year, value.monthValue - 1, value.dayOfMonth).show()
                }, enabled = !working) { Text(stringResource(R.string.body_pick_date)) }
                OutlinedButton(onClick = {
                    val value = currentTime()
                    TimePickerDialog(context, { _, hour, minute ->
                        updateTime(value.withHour(hour).withMinute(minute))
                    }, value.hour, value.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                }, enabled = !working) { Text(stringResource(R.string.body_pick_time)) }
            }
            Text(stringResource(R.string.body_entry_zone, zone.id), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { onSave(weight, bodyFat, timestamp) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth().testTag("manual_save")
            ) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp))
                Text(stringResource(if (working) R.string.body_entry_saving else R.string.body_manual_save))
            }
            val notice = when (state) {
                is ManualBodyEntryState.Saved -> R.string.body_manual_saved
                is ManualBodyEntryState.Invalid -> manualErrorString(state.error)
                ManualBodyEntryState.StorageError -> R.string.body_manual_storage_error
                else -> null
            }
            notice?.let {
                Text(
                    stringResource(it),
                    color = if (state is ManualBodyEntryState.Saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("manual_notice").semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            if (healthConnectAvailable && !hasHealthConnectPermission) {
                Text(stringResource(R.string.body_manual_hc_optional), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = onGrantPermissions, enabled = !working,
                    modifier = Modifier.fillMaxWidth().testTag("body_optional_health_connect")
                ) { Text(stringResource(R.string.body_manual_connect_hc)) }
            }
        }
    }
}

private fun manualErrorString(error: ManualBodyInputError): Int = when (error) {
    ManualBodyInputError.MISSING_VALUES -> R.string.body_manual_error_missing
    ManualBodyInputError.INVALID_WEIGHT -> R.string.body_manual_error_weight
    ManualBodyInputError.INVALID_BODY_FAT -> R.string.body_manual_error_body_fat
    ManualBodyInputError.INVALID_TIMESTAMP -> R.string.body_manual_error_timestamp
    ManualBodyInputError.FUTURE_TIMESTAMP -> R.string.body_manual_error_future
}
