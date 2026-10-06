package io.github.hebadenys.fitnesshub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import io.github.hebadenys.fitnesshub.ui.theme.ProvenanceEstimateContainerDark
import io.github.hebadenys.fitnesshub.ui.theme.ProvenanceEstimateContainerLight
import io.github.hebadenys.fitnesshub.ui.theme.ProvenanceEstimateTextDark
import io.github.hebadenys.fitnesshub.ui.theme.ProvenanceEstimateTextLight

@Composable
fun MetricCard(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    delta: BaselineDelta? = null,
    provenance: String? = null,
    algorithm: String? = null,
    subtitle: String? = null,
    isPermissionMissing: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val spacing = FitnessHubTheme.spacing
    val cornerRadius = FitnessHubTheme.cornerRadius
    val dimensions = FitnessHubTheme.dimensions
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val displayValue = when {
        isPermissionMissing -> stringResource(R.string.value_permission_denied)
        value != null -> value
        else -> stringResource(R.string.value_unavailable)
    }

    val isNullOrMissing = isPermissionMissing || value == null

    val cardModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = dimensions.cardMinHeight)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .semantics(mergeDescendants = true) {
            this.contentDescription = "$label: $displayValue"
        }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(cornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(dimensions.iconLarge)
                                .clip(RoundedCornerShape(cornerRadius.sm))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(dimensions.iconMedium)
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (provenance == DailySummary.PROVENANCE_ESTIMATE) {
                    val estContainer = if (isDark) ProvenanceEstimateContainerDark else ProvenanceEstimateContainerLight
                    val estText = if (isDark) ProvenanceEstimateTextDark else ProvenanceEstimateTextLight
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(cornerRadius.xs))
                            .background(estContainer)
                            .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                    ) {
                        Text(
                            text = stringResource(R.string.provenance_estimate),
                            style = MaterialTheme.typography.labelSmall,
                            color = estText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isNullOrMissing) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (algorithm != null && provenance == DailySummary.PROVENANCE_ESTIMATE) {
                        Text(
                            text = stringResource(R.string.provenance_algorithm, algorithm),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (delta != null && !isNullOrMissing) {
                    BaselineDeltaIndicator(delta = delta)
                }
            }
        }
    }
}
