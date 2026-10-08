package io.github.hebadenys.fitnesshub.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.feature.scale.ProfileSaveResult
import io.github.hebadenys.fitnesshub.feature.scale.ScaleProfileEditor
import io.github.hebadenys.fitnesshub.feature.scale.ScaleViewModel
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalProfileScreen(onBack: () -> Unit, viewModel: ScaleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val result by viewModel.profileSaveResult.collectAsStateWithLifecycle()
    var dirty by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val saving = result == ProfileSaveResult.SAVING
    val requestBack = { if (dirty) discard = true else onBack() }
    BackHandler(enabled = dirty || saving) {
        if (!saving) discard = true
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.profile_local_title)) },
            navigationIcon = {
                IconButton(onClick = requestBack, enabled = !saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }) { padding ->
        ScreenStateHandler(state = state, modifier = Modifier.padding(padding)) { model ->
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                    .padding(20.dp).testTag("local_profile")
            ) {
                ScaleProfileEditor(
                    model = model,
                    result = result,
                    onSave = viewModel::saveProfile,
                    onEdit = viewModel::dismissProfileResult,
                    onDirtyChange = { dirty = it }
                )
            }
        }
    }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.profile_discard_title)) },
            text = { Text(stringResource(R.string.profile_discard_description)) },
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
