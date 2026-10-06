package io.github.hebadenys.fitnesshub.feature.insights

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.backup.BackupDocumentIo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BackupSection(state: BackupState, weightCsv: String, onCreate: (String) -> Unit,
    onRestore: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val io = remember(context) { BackupDocumentIo(context.contentResolver) }
    var passphrase by remember { mutableStateOf("") } // Never save secrets in the instance-state Bundle.
    var archive by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var fileWorking by remember { mutableStateOf(false) }
    val busy = fileWorking || state is BackupState.Working

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = pendingBackup
        pendingBackup = null
        if (uri != null) scope.launch {
            fileWorking = true
            try {
                if (payload == null) error("Export interrupted")
                io.write(uri, payload)
                status = context.getString(R.string.backup_file_saved)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { status = context.getString(R.string.backup_file_failed)
            } finally { fileWorking = false }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            fileWorking = true
            archive = ""
            pasted = ""
            try {
                archive = io.read(uri)
                status = context.getString(R.string.backup_file_loaded)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { status = context.getString(R.string.backup_document_read_failed_v2)
            } finally { fileWorking = false }
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val payload = pendingCsv
        pendingCsv = null
        if (uri != null) scope.launch {
            fileWorking = true
            try {
                if (payload == null) error("Export interrupted")
                io.write(uri, payload)
                status = context.getString(R.string.export_csv_saved)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { status = context.getString(R.string.backup_file_failed)
            } finally { fileWorking = false }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_coverage_v2), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.backup_merge_policy_v2), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = passphrase, onValueChange = { if (it.length <= 1024) passphrase = it },
                label = { Text(stringResource(R.string.backup_passphrase)) }, singleLine = true,
                enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Button(onClick = { onCreate(passphrase); passphrase = "" }, enabled = passphrase.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_create)) }
            when (state) {
                BackupState.Working -> CircularProgressIndicator()
                is BackupState.Created -> Button(onClick = {
                    pendingBackup = state.archive; save.launch("FitnessHub-backup-v2.fhub")
                }, enabled = !fileWorking, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_save_file)) }
                is BackupState.Restored -> {
                    Text(stringResource(R.string.backup_restored_v2, state.rows, state.identicalRows))
                    if (state.legacyPartial) Text(stringResource(R.string.backup_legacy_partial_v2), color = MaterialTheme.colorScheme.error)
                }
                is BackupState.Failed -> Text(
                    if (state.reason == "backup_conflict") stringResource(R.string.backup_conflict_v2)
                    else stringResource(R.string.backup_failed, state.reason), color = MaterialTheme.colorScheme.error)
                BackupState.Idle -> Unit
            }
            Button(onClick = { open.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_open_file)) }
            if (archive.isNotEmpty()) Text(stringResource(R.string.backup_document_ready_v2))
            // Large files are not rendered in a text field; limited paste keeps older manual exports usable.
            OutlinedTextField(value = pasted, onValueChange = {
                if (it.length <= 64 * 1024) { pasted = it; archive = "" }
            }, label = { Text(stringResource(R.string.backup_paste_v2)) }, enabled = !busy,
                supportingText = { Text(stringResource(R.string.backup_paste_hint_v2)) },
                minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            val restoreText = archive.ifEmpty { pasted }
            Button(onClick = { onRestore(restoreText, passphrase); passphrase = "" },
                enabled = restoreText.isNotBlank() && passphrase.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_restore)) }
            Button(onClick = { pendingCsv = weightCsv; saveCsv.launch("FitnessHub-weight.csv") },
                enabled = weightCsv.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_weight_csv)) }
            if (fileWorking) CircularProgressIndicator()
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state !is BackupState.Idle) Button(onClick = {
                passphrase = ""; archive = ""; pasted = ""; status = null; onDismiss()
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}
