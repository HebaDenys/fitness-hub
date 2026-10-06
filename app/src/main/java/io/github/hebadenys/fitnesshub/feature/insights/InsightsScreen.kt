package io.github.hebadenys.fitnesshub.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.analytics.InsightCard
import io.github.hebadenys.fitnesshub.core.analytics.TrendAnalytics
import io.github.hebadenys.fitnesshub.ui.components.ChartPoint
import io.github.hebadenys.fitnesshub.ui.components.ChartSkeleton
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.components.TrendChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onNavigateBack: () -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val model by viewModel.uiState.collectAsStateWithLifecycle()
    val backupState by viewModel.backup.collectAsStateWithLifecycle()
    var range by remember { mutableStateOf(TimeRange.SEVEN_DAYS) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.insights_title)) }, navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
            }
        })
    }) { padding ->
        val content = model
        if (content == null) { ChartSkeleton(); return@Scaffold }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content.cards.forEach { card -> InsightCardView(card, range) { range = it } }
            BackupSection(backupState, content.weightCsv, viewModel::createBackup, viewModel::restoreBackup, viewModel::dismissBackupState)
        }
    }
}

@Composable
private fun InsightCardView(card: InsightCard, range: TimeRange, onRangeSelected: (TimeRange) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.insights_sample_size, card.sampleSize), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TrendChart(title = stringResource(R.string.insights_chart_title, card.title),
                points = card.primarySeries.map { ChartPoint(it.date, it.value) }, selectedRange = range, onRangeSelected = onRangeSelected)
            Text(card.correlation?.let { correlation ->
                stringResource(R.string.insights_correlation_value, correlation.coefficient,
                    stringResource(correlation.strength.labelRes()), correlation.xLabel, correlation.yLabel)
            } ?: stringResource(R.string.insights_correlation_none), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.insights_advisory), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun TrendAnalytics.Strength.labelRes(): Int = when (this) {
    TrendAnalytics.Strength.STRONG -> R.string.insights_strength_strong
    TrendAnalytics.Strength.MODERATE -> R.string.insights_strength_moderate
    TrendAnalytics.Strength.NONE -> R.string.insights_strength_none
}
