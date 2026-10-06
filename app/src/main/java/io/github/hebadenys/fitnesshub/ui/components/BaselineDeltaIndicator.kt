package io.github.hebadenys.fitnesshub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.theme.DeltaNegativeDark
import io.github.hebadenys.fitnesshub.ui.theme.DeltaNegativeLight
import io.github.hebadenys.fitnesshub.ui.theme.DeltaNeutralDark
import io.github.hebadenys.fitnesshub.ui.theme.DeltaNeutralLight
import io.github.hebadenys.fitnesshub.ui.theme.DeltaPositiveDark
import io.github.hebadenys.fitnesshub.ui.theme.DeltaPositiveLight
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

sealed interface BaselineDelta {
    data object None : BaselineDelta
    data object Unchanged : BaselineDelta
    data class Positive(val formattedDifference: String) : BaselineDelta
    data class Negative(val formattedDifference: String) : BaselineDelta
}

@Composable
fun BaselineDeltaIndicator(
    delta: BaselineDelta,
    modifier: Modifier = Modifier
) {
    val spacing = FitnessHubTheme.spacing
    val cornerRadius = FitnessHubTheme.cornerRadius
    val dimensions = FitnessHubTheme.dimensions
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val positiveColor = if (isDark) DeltaPositiveDark else DeltaPositiveLight
    val negativeColor = if (isDark) DeltaNegativeDark else DeltaNegativeLight
    val neutralColor = if (isDark) DeltaNeutralDark else DeltaNeutralLight

    val (icon, contentColor, text, cd) = when (delta) {
        is BaselineDelta.Positive -> {
            Quad(
                Icons.Default.KeyboardArrowUp,
                positiveColor,
                stringResource(R.string.delta_positive, delta.formattedDifference),
                stringResource(R.string.cd_delta_increased, delta.formattedDifference)
            )
        }
        is BaselineDelta.Negative -> {
            Quad(
                Icons.Default.KeyboardArrowDown,
                negativeColor,
                stringResource(R.string.delta_negative, delta.formattedDifference),
                stringResource(R.string.cd_delta_decreased, delta.formattedDifference)
            )
        }
        is BaselineDelta.Unchanged -> {
            Quad(
                Icons.Default.Clear,
                neutralColor,
                stringResource(R.string.delta_no_change),
                stringResource(R.string.cd_delta_unchanged)
            )
        }
        is BaselineDelta.None -> {
            Quad(
                null,
                neutralColor,
                stringResource(R.string.delta_no_baseline),
                stringResource(R.string.cd_delta_no_baseline)
            )
        }
    }

    val containerColor = contentColor.copy(alpha = 0.12f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.sm))
            .background(containerColor)
            .padding(horizontal = spacing.sm, vertical = spacing.xs)
            .semantics(mergeDescendants = true) {
                this.contentDescription = cd
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(dimensions.iconSmall)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
