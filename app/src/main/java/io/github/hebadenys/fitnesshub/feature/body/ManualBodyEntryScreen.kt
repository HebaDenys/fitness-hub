package io.github.hebadenys.fitnesshub.feature.body

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.state.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBodyEntryScreen(onBack: () -> Unit, viewModel: BodyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entry by viewModel.manualEntryState.collectAsStateWithLifecycle()
    val model = (state as? ScreenState.Content)?.data
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.health.permissionContract) {
        viewModel.refreshPermissions()
    }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val working = entry is ManualBodyEntryState.Working
    val dirtyDraft = dirty && entry !is ManualBodyEntryState.Saved
    val requestBack = { if (dirtyDraft) discard = true else onBack() }
    BackHandler(enabled = dirtyDraft || working) { if (!working) discard = true }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.body_manual_title)) },
            navigationIcon = {
                IconButton(onClick = requestBack, enabled = !working) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(20.dp).testTag("manual_entry_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ManualBodyEntryCard(
                state = entry,
                healthConnectAvailable = model?.healthConnectAvailable == true,
                hasHealthConnectPermission = model?.hasHealthConnectBodyPermission == true,
                onGrantPermissions = { permissionLauncher.launch(viewModel.health.permissions) },
                onSave = viewModel::saveManualMeasurement,
                onDismissState = viewModel::dismissManualEntryState,
                onDirtyChange = { dirty = it }
            )
            OutlinedButton(
                onClick = requestBack, enabled = !working,
                modifier = Modifier.fillMaxWidth().testTag("manual_close")
            ) { Text(stringResource(R.string.body_entry_close)) }
        }
    }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.body_discard_title)) },
            text = { Text(stringResource(R.string.body_discard_description)) },
            confirmButton = {
                TextButton(onClick = { discard = false; onBack() }) {
                    Text(stringResource(R.string.profile_discard_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
