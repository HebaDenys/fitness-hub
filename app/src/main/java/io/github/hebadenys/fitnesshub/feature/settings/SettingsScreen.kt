package io.github.hebadenys.fitnesshub.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.PrivacyRationaleActivity
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

internal enum class SourceTone { GOOD, ATTENTION, NEUTRAL, BLOCKED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToHealthConnect: () -> Unit,
    onNavigateToXiaomi: () -> Unit,
    onNavigateToScale: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToAi: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        ScreenStateHandler(state = state, modifier = Modifier.padding(padding)) { model ->
            SettingsHubContent(
                model = model,
                onHealthConnect = onNavigateToHealthConnect,
                onXiaomi = onNavigateToXiaomi,
                onScale = onNavigateToScale,
                onInsights = onNavigateToInsights,
                onAi = onNavigateToAi,
                onPrivacy = { context.startActivity(Intent(context, PrivacyRationaleActivity::class.java)) }
            )
        }
    }
}

@Composable
internal fun SettingsHubContent(
    model: SettingsUiModel,
    onHealthConnect: () -> Unit = {},
    onXiaomi: () -> Unit = {},
    onScale: () -> Unit = {},
    onInsights: () -> Unit = {},
    onAi: () -> Unit = {},
    onPrivacy: () -> Unit = {}
) {
    val spacing = FitnessHubTheme.spacing
    val healthStatus = when {
        !model.isClientAvailable -> R.string.source_status_unavailable to SourceTone.BLOCKED
        model.hasAnyPermission -> R.string.source_status_connected to SourceTone.GOOD
        else -> R.string.source_status_setup to SourceTone.ATTENTION
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_hub"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        item {
            Text(
                stringResource(R.string.settings_hub_sources_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.settings_hub_sources_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            HubCard(
                icon = Icons.Default.Favorite,
                title = stringResource(R.string.settings_hc_section),
                description = stringResource(
                    R.string.source_health_summary,
                    model.grantedMetricCount,
                    HealthMetrics.ALL.size
                ),
                status = stringResource(healthStatus.first),
                tone = healthStatus.second,
                onClick = onHealthConnect,
                testTag = "source_health_connect"
            )
        }

        item {
            HubCard(
                icon = Icons.Default.Home,
                title = stringResource(R.string.xiaomi_source_title),
                description = stringResource(R.string.source_xiaomi_description),
                status = stringResource(R.string.source_status_signing_required),
                tone = SourceTone.ATTENTION,
                onClick = onXiaomi,
                testTag = "source_xiaomi"
            )
        }

        item {
            HubCard(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.source_scale_title),
                description = stringResource(R.string.source_scale_description),
                status = stringResource(R.string.source_status_optional),
                tone = SourceTone.NEUTRAL,
                onClick = onScale,
                testTag = "source_scale"
            )
        }

        item {
            Spacer(Modifier.size(spacing.xs))
            Text(
                stringResource(R.string.settings_hub_tools_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.settings_hub_tools_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            HubCard(
                icon = Icons.Default.Info,
                title = stringResource(R.string.insights_title),
                description = stringResource(R.string.insights_settings_description),
                status = stringResource(R.string.source_status_local),
                tone = SourceTone.GOOD,
                onClick = onInsights,
                testTag = "settings_insights"
            )
        }

        item {
            HubCard(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.ai_section_title),
                description = stringResource(R.string.source_ai_description),
                status = stringResource(R.string.source_status_unavailable),
                tone = SourceTone.BLOCKED,
                onClick = onAi,
                testTag = "settings_ai",
                enabled = false
            )
        }

        item {
            HubCard(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_privacy_section),
                description = stringResource(R.string.source_privacy_description),
                status = stringResource(R.string.source_status_local),
                tone = SourceTone.GOOD,
                onClick = onPrivacy,
                testTag = "settings_privacy"
            )
        }
    }
}

@Composable
private fun HubCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: String,
    tone: SourceTone,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true
) {
    val spacing = FitnessHubTheme.spacing
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    SourceStatus(status, tone)
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SourceStatus(text: String, tone: SourceTone) {
    val colors = when (tone) {
        SourceTone.GOOD -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SourceTone.ATTENTION -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        SourceTone.BLOCKED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        SourceTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = colors.first, contentColor = colors.second, shape = RoundedCornerShape(999.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}
