package io.github.hebadenys.fitnesshub.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.feature.scale.ScaleSettingsSection
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScaleSettingsScreen(onBack: () -> Unit) {
    val spacing = FitnessHubTheme.spacing
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.source_scale_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.md)
        ) {
            Text(
                stringResource(R.string.source_scale_advanced_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = spacing.md)
            )
            ScaleSettingsSection()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val spacing = FitnessHubTheme.spacing
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.ai_section_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.md)
        ) {
            AiSettingsSection()
        }
    }
}
