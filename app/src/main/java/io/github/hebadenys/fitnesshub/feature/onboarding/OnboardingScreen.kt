package io.github.hebadenys.fitnesshub.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val spacing = FitnessHubTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("onboarding"),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        item {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.onboarding_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OnboardingCard(
                Icons.Default.Home,
                stringResource(R.string.onboarding_local_title),
                stringResource(R.string.onboarding_local_description)
            )
        }
        item {
            OnboardingCard(
                Icons.Default.Favorite,
                stringResource(R.string.onboarding_sources_title),
                stringResource(R.string.onboarding_sources_description)
            )
        }
        item {
            OnboardingCard(
                Icons.Default.Lock,
                stringResource(R.string.onboarding_control_title),
                stringResource(R.string.onboarding_control_description)
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_continue")
                ) {
                    Text(stringResource(R.string.onboarding_continue))
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_skip")
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(icon: ImageVector, title: String, description: String) {
    val spacing = FitnessHubTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
