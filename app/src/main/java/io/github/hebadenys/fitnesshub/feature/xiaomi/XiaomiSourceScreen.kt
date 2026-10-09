package io.github.hebadenys.fitnesshub.feature.xiaomi

import android.graphics.BitmapFactory
import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.xiaomi.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun XiaomiSourceScreen(onBack: () -> Unit, viewModel: XiaomiSourceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.refresh()
        onStopOrDispose { viewModel.onHidden() }
    }
    XiaomiSourceContent(state, onBack, viewModel::refresh, viewModel::login, viewModel::discover,
        viewModel::select, viewModel::confirmSelection, viewModel::sync, viewModel::cancel,
        viewModel::disconnect, viewModel::loadMoreHistory, viewModel::submitCaptcha)
}

@Composable
internal fun XiaomiSourceEntry(onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.xiaomi_source_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.xiaomi_source_entry))
            OutlinedButton(onClick, Modifier.fillMaxWidth().testTag("xiaomi_source_open")) {
                Text(stringResource(R.string.xiaomi_source_open))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun XiaomiSourceContent(
    state: XiaomiSourceUiState,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onLogin: (XiaomiRegion, String, CharArray) -> Unit = { _, _, key -> key.fill('\u0000') },
    onDiscover: (String, Boolean) -> Unit = { _, _ -> },
    onSelect: (String) -> Unit = {},
    onConfirm: () -> Unit = {},
    onSync: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onMoreHistory: () -> Unit = {},
    onCaptcha: (Long, String) -> Boolean = { _, _ -> false }
) {
    val context = LocalContext.current
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull() }
    DisposableEffect(activity) {
        val window = activity?.window
        val alreadySecure = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var regionCode by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    // Credentials are never put into SavedStateHandle, rememberSaveable, navigation args or ViewModel state.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var confirmKey by remember { mutableStateOf<String?>(null) }
    var logoutDialog by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<XiaomiRecordDetail?>(null) }
    LifecycleStartEffect(Unit) {
        onStopOrDispose { username = ""; password = ""; consent = false; confirmKey = null; detail = null }
    }
    val overview = state.overview
    LaunchedEffect(overview?.binding, overview?.account) {
        val bound = overview?.binding
        (bound?.scope?.region ?: overview?.account?.region)?.let { regionCode = it.wireName }
        bound?.let { model = it.scope.model }
    }
    val region = XiaomiRegion.entries.singleOrNull { it.wireName == regionCode }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.xiaomi_source_title)) }, navigationIcon = {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back)) }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("xiaomi_source_list"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.xiaomi_source_description)) }
            if (overview == null && state.errorCode == null) item { CircularProgressIndicator() }
            if (overview?.block != null) item {
                SourceSection {
                    Text(stringResource(R.string.xiaomi_source_blocked), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("xiaomi_blocked"))
                    Text(stringResource(R.string.xiaomi_source_signing))
                }
            }
            state.captcha?.let { challenge -> item {
                XiaomiCaptchaCard(challenge, onCaptcha, onCancel)
            } }
            state.errorCode?.let { code -> item {
                Text(stringResource(errorText(code)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("xiaomi_error").semantics { liveRegion = LiveRegionMode.Polite })
            } }
            if (state.notice != XiaomiNotice.NONE) item { Text(stringResource(noticeText(state.notice)), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (overview != null) {
                item {
                    SourceSection {
                        Text(stringResource(when {
                            overview.account == null -> R.string.xiaomi_source_signed_out
                            overview.binding == null -> R.string.xiaomi_source_needs_selection
                            state.canSync -> R.string.xiaomi_source_ready
                            else -> R.string.xiaomi_source_mismatch
                        }))
                        overview.account?.let { Text(stringResource(R.string.xiaomi_source_account, it.userId)) }
                        overview.sessionProblem?.let { Text(stringResource(errorText(it.name)), color = MaterialTheme.colorScheme.error) }
                        StringChoice(stringResource(R.string.xiaomi_source_region), regionCode,
                            XiaomiRegion.entries.map { it.wireName }, !state.busy && overview.account == null && overview.binding == null) { regionCode = it }
                        Text(stringResource(R.string.xiaomi_source_region_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (overview.block == null && overview.account == null) item {
                    SourceSection {
                        OutlinedTextField(username, { if (it.length <= 320) username = it },
                            label = { Text(stringResource(R.string.xiaomi_source_username)) }, singleLine = true,
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("xiaomi_username"))
                        OutlinedTextField(password, { if (it.length <= 1024) password = it },
                            label = { Text(stringResource(R.string.xiaomi_source_password)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("xiaomi_password"))
                        Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { consent = !consent }) {
                            Checkbox(consent, { consent = it }, enabled = !state.busy)
                            Text(stringResource(R.string.xiaomi_source_consent), modifier = Modifier.padding(top = 12.dp))
                        }
                        Button(onClick = {
                            val selectedRegion = region
                            if (selectedRegion != null && consent) {
                                val key = password.toCharArray()
                                password = ""
                                onLogin(selectedRegion, username, key)
                                username = ""; consent = false
                            }
                        }, enabled = !state.busy && region != null && username.isNotBlank() && password.isNotEmpty() && consent,
                            modifier = Modifier.fillMaxWidth().testTag("xiaomi_login")) { Text(stringResource(R.string.xiaomi_source_login)) }
                    }
                }
                if (overview.account != null && overview.binding == null && overview.block == null) {
                    item { SourceSection {
                        StringChoice(stringResource(R.string.xiaomi_source_known_models), model,
                            XiaomiModels.knownProtocolModels, !state.busy) { model = it }
                        OutlinedTextField(model, { if (it.length <= 128) model = it },
                            label = { Text(stringResource(R.string.xiaomi_source_model)) }, singleLine = true,
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("xiaomi_model"))
                        Text(stringResource(R.string.xiaomi_source_model_hint), style = MaterialTheme.typography.bodySmall)
                        if (model.isNotBlank() && model !in XiaomiModels.knownProtocolModels) Text(stringResource(R.string.xiaomi_source_model_unknown))
                        Button(onClick = { onDiscover(model, false) }, enabled = !state.busy && XiaomiModels.valid(model),
                            modifier = Modifier.fillMaxWidth().testTag("xiaomi_discover")) { Text(stringResource(R.string.xiaomi_source_discover)) }
                    } }
                    state.discovery?.let { found ->
                        item {
                            Text(stringResource(R.string.xiaomi_source_discovery_scope))
                            Text(stringResource(R.string.xiaomi_source_discovery_count, found.pagesScanned, found.rowsScanned, found.unresolvedRows))
                            if (found.candidates.isEmpty()) Text(stringResource(R.string.xiaomi_source_no_candidates))
                        }
                        items(found.candidates, key = { "candidate_${it.key}" }) { candidate ->
                            SourceSection(Modifier.selectable(selected = state.selectedKey == candidate.key, enabled = !state.busy,
                                role = Role.RadioButton, onClick = { onSelect(candidate.key) }).testTag("candidate_${candidate.key}")) {
                                Row { RadioButton(state.selectedKey == candidate.key, null, enabled = !state.busy)
                                    Text(candidate.displayName ?: stringResource(R.string.xiaomi_source_unnamed), Modifier.padding(12.dp)) }
                                Text(stringResource(R.string.xiaomi_source_identity, candidate.subject.uid, candidate.subject.accountId))
                                Text(stringResource(R.string.xiaomi_source_device, candidate.deviceId))
                                Text(stringResource(R.string.xiaomi_source_candidate_time, displayTime(candidate.latestAtMillis)))
                            }
                        }
                        item {
                            Button(onClick = { confirmKey = state.selectedKey }, enabled = !state.busy && state.selectedKey != null,
                                modifier = Modifier.fillMaxWidth().testTag("xiaomi_confirm")) { Text(stringResource(R.string.xiaomi_source_confirm)) }
                            if (found.nextBeforeMillis != null) OutlinedButton(onClick = { onDiscover(model, true) }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.xiaomi_source_older)) }
                        }
                    }
                }
                overview.binding?.let { bound -> item { SourceSection {
                    Text(stringResource(R.string.xiaomi_source_binding), style = MaterialTheme.typography.titleMedium)
                    Text(bound.scope.model)
                    Text(stringResource(R.string.xiaomi_source_identity, bound.subject.uid, bound.subject.accountId))
                    Text(stringResource(R.string.xiaomi_source_device, bound.deviceId))
                    Button(onSync, enabled = state.canSync && !state.busy, modifier = Modifier.fillMaxWidth().testTag("xiaomi_sync")) {
                        Text(stringResource(if (overview.pendingHistory) R.string.xiaomi_source_resume else R.string.xiaomi_source_sync))
                    }
                } } }
                if (overview.account != null || overview.sessionProblem != null) item {
                    OutlinedButton(onClick = { logoutDialog = true }, enabled = state.work != XiaomiWork.LOGOUT,
                        modifier = Modifier.fillMaxWidth().testTag("xiaomi_logout")) { Text(stringResource(R.string.xiaomi_source_logout)) }
                }
            }
            item {
                if (state.busy) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.xiaomi_source_working))
                    if (state.work != XiaomiWork.LOGOUT) OutlinedButton(onCancel) { Text(stringResource(R.string.xiaomi_source_stop)) }
                } else OutlinedButton(onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.xiaomi_source_refresh)) }
            }
            if (overview != null) {
                item {
                    Text(stringResource(R.string.xiaomi_source_history), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.xiaomi_source_snapshot_count, overview.snapshotCount))
                    overview.committedAtMillis?.let { Text(stringResource(R.string.xiaomi_source_last_commit, displayTime(it), overview.committedPages)) }
                    if (overview.oldestAtMillis != null) Text(stringResource(R.string.xiaomi_source_range,
                        displayTime(overview.oldestAtMillis), displayTime(overview.newestAtMillis)))
                    if (state.records.isEmpty()) Text(stringResource(R.string.xiaomi_source_no_history))
                }
                items(state.records, key = { "record_${it.hash}" }) { row ->
                    OutlinedButton(onClick = { detail = row }, modifier = Modifier.fillMaxWidth().testTag("record_${row.hash}")) {
                        Text(displayTime(row.atMillis))
                    }
                }
                if (state.hasMoreHistory) item { OutlinedButton(onMoreHistory, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.xiaomi_source_history_more)) } }
            }
        }
    }
    if (confirmKey != null) AlertDialog(onDismissRequest = { confirmKey = null },
        title = { Text(stringResource(R.string.xiaomi_source_confirm)) },
        text = { Text(stringResource(R.string.xiaomi_source_confirm_warning)) },
        confirmButton = { TextButton(onClick = {
            if (confirmKey == state.selectedKey && !state.busy) onConfirm()
            confirmKey = null
        }, modifier = Modifier.testTag("xiaomi_confirm_dialog")) { Text(stringResource(R.string.xiaomi_source_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmKey = null }) { Text(stringResource(R.string.action_dismiss)) } })
    if (logoutDialog) AlertDialog(onDismissRequest = { logoutDialog = false },
        text = { Text(stringResource(R.string.xiaomi_source_logout_warning)) },
        confirmButton = { TextButton(onClick = { logoutDialog = false; onDisconnect() }) { Text(stringResource(R.string.xiaomi_source_logout)) } },
        dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text(stringResource(R.string.action_dismiss)) } })
    detail?.let { row -> AlertDialog(onDismissRequest = { detail = null },
        title = { Text(stringResource(R.string.xiaomi_source_detail)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(displayTime(row.atMillis))
            Text(stringResource(R.string.xiaomi_source_detail_hint))
            if (row.unreadable) Text(stringResource(R.string.xiaomi_source_unreadable))
            row.metrics.forEach { metric ->
                Text("${metric.path}: ${metric.value} ${unitText(metric.unit)}")
                Text(stringResource(when (metric.method) {
                    XiaomiMethod.VENDOR_REPORTED_WEIGHT -> R.string.xiaomi_method_weight
                    XiaomiMethod.VENDOR_ESTIMATE -> R.string.xiaomi_method_estimate
                    XiaomiMethod.VENDOR_REPORTED_VITAL -> R.string.xiaomi_method_vital
                    XiaomiMethod.UNKNOWN -> R.string.xiaomi_method_unknown
                }), style = MaterialTheme.typography.bodySmall)
                if (metric.issues.isNotEmpty()) Text(stringResource(R.string.xiaomi_source_issues, metric.issues.joinToString()))
            }
            if (row.issues.isNotEmpty()) Text(stringResource(R.string.xiaomi_source_issues, row.issues.joinToString()))
        } }, confirmButton = { TextButton(onClick = { detail = null }) { Text(stringResource(R.string.action_dismiss)) } }) }
}

@Composable
private fun SourceSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
private fun StringChoice(title: String, value: String, values: List<String>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(if (value.isBlank()) title else "$title: $value")
    }
        DropdownMenu(expanded, { expanded = false }) { values.forEach { option ->
            DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onChange(option) })
        } }
    }
}

@Composable
private fun displayTime(value: Long?): String = if (value == null) stringResource(R.string.xiaomi_source_unknown_time)
else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))

@Composable
private fun unitText(unit: XiaomiUnit): String = when (unit) {
    XiaomiUnit.KG -> "kg"
    XiaomiUnit.PERCENT -> "%"
    XiaomiUnit.BPM -> "bpm"
    XiaomiUnit.KCAL_PERIOD_UNVERIFIED -> stringResource(R.string.xiaomi_unit_kcal_period)
    XiaomiUnit.UNKNOWN -> stringResource(R.string.xiaomi_unit_unverified)
    else -> unit.name
}

private fun noticeText(notice: XiaomiNotice): Int = when (notice) {
    XiaomiNotice.SIGNED_IN -> R.string.xiaomi_source_logged_in
    XiaomiNotice.BOUND -> R.string.xiaomi_source_bound
    XiaomiNotice.SYNC_FINISHED -> R.string.xiaomi_source_sync_finished
    XiaomiNotice.SYNC_PAUSED -> R.string.xiaomi_source_sync_paused
    XiaomiNotice.SIGNED_OUT -> R.string.xiaomi_source_disconnected
    else -> R.string.xiaomi_source_cancelled
}

private fun errorText(code: String): Int = when (code) {
    "PRIVATE_SIGNING_REQUIRED" -> R.string.xiaomi_source_signing
    "AUTH_REJECTED", "AUTH_REQUIRED", "SESSION_MISSING", "SESSION_EXPIRED", "SESSION_UNREADABLE" -> R.string.xiaomi_source_error_auth
    "CAPTCHA_REQUIRED" -> R.string.xiaomi_source_error_captcha
    "VERIFICATION_REQUIRED" -> R.string.xiaomi_source_error_verification
    "CHALLENGE_EXPIRED" -> R.string.xiaomi_source_error_challenge_expired
    "SESSION_SCOPE_MISMATCH", "SESSION_CHANGED", "BINDING_CONFLICT", "SCOPE_MISMATCH" -> R.string.xiaomi_source_error_identity
    "NETWORK_ERROR", "REMOTE_UNAVAILABLE", "TIMEOUT" -> R.string.xiaomi_source_error_network
    "RATE_LIMITED" -> R.string.xiaomi_source_error_rate
    "STORAGE_ERROR", "UNEXPECTED_ERROR" -> R.string.xiaomi_source_error_storage
    else -> R.string.xiaomi_source_error_protocol
}

@Composable
private fun XiaomiCaptchaCard(challenge: XiaomiCaptchaChallenge, onSubmit: (Long, String) -> Boolean, onCancel: () -> Unit) {
    var answer by remember(challenge.id) { mutableStateOf("") }
    var submitted by remember(challenge.id) { mutableStateOf(false) }
    val bitmap = remember(challenge.id) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(challenge.image, 0, challenge.image.size, options)
        if (options.outWidth in 1..1024 && options.outHeight in 1..1024 &&
            options.outWidth.toLong() * options.outHeight <= 1_048_576) {
            BitmapFactory.decodeByteArray(challenge.image, 0, challenge.image.size)?.asImageBitmap()
        } else null
    }
    SourceSection(Modifier.testTag("xiaomi_captcha")) {
        Text(stringResource(R.string.xiaomi_captcha_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(stringResource(R.string.xiaomi_captcha_hint))
        if (bitmap != null) {
            Image(bitmap, stringResource(R.string.xiaomi_captcha_image), Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 200.dp))
            OutlinedTextField(answer, { if (it.length <= 16) answer = it }, singleLine = true,
                label = { Text(stringResource(R.string.xiaomi_captcha_answer)) },
                enabled = !submitted, modifier = Modifier.fillMaxWidth().testTag("xiaomi_captcha_answer"))
            Button(onClick = {
                if (!submitted && onSubmit(challenge.id, answer.trim())) { submitted = true; answer = "" }
            }, enabled = !submitted && XiaomiCaptchaController.validAnswer(answer.trim()),
                modifier = Modifier.fillMaxWidth().testTag("xiaomi_captcha_submit")) {
                Text(stringResource(R.string.xiaomi_captcha_confirm))
            }
        } else Text(stringResource(R.string.xiaomi_captcha_invalid_image), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { answer = ""; onCancel() }, modifier = Modifier.fillMaxWidth().testTag("xiaomi_captcha_cancel")) {
            Text(stringResource(R.string.xiaomi_source_stop))
        }
    }
}
