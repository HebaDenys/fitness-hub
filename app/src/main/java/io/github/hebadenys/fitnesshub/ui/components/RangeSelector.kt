package io.github.hebadenys.fitnesshub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

enum class TimeRange(val days: Int, val labelRes: Int) {
    SEVEN_DAYS(7, R.string.range_7d),
    THIRTY_DAYS(30, R.string.range_30d),
    NINETY_DAYS(90, R.string.range_90d)
}

@Composable
fun RangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = FitnessHubTheme.spacing
    val cornerRadius = FitnessHubTheme.cornerRadius
    val dimensions = FitnessHubTheme.dimensions
    val cdRange = stringResource(R.string.cd_range_selector)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(spacing.xxs)
            .semantics {
                this.contentDescription = cdRange
            },
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TimeRange.entries.forEach { range ->
            val isSelected = range == selectedRange
            val backgroundModifier = if (isSelected) {
                Modifier
                    .clip(RoundedCornerShape(cornerRadius.sm))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            } else {
                Modifier.clip(RoundedCornerShape(cornerRadius.sm))
            }

            val label = stringResource(range.labelRes)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = dimensions.minTouchTarget)
                    .then(backgroundModifier)
                    .clickable(role = Role.Tab) {
                        onRangeSelected(range)
                    }
                    .semantics {
                        this.selected = isSelected
                        this.role = Role.Tab
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
