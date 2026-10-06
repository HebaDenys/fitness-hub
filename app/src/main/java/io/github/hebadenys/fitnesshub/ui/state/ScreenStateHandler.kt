package io.github.hebadenys.fitnesshub.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

@Composable
fun <T> ScreenStateHandler(
    state: ScreenState<T>,
    modifier: Modifier = Modifier,
    onGrantPermissions: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    val spacing = FitnessHubTheme.spacing
    val dimensions = FitnessHubTheme.dimensions

    when (state) {
        is ScreenState.Loading -> {
            if (loadingContent != null) {
                loadingContent()
            } else {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimensions.iconXLarge)
                        )
                        Text(
                            text = stringResource(R.string.state_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is ScreenState.Empty -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.cd_status_icon),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(dimensions.iconXLarge)
                    )
                    Text(
                        text = stringResource(state.titleRes ?: R.string.state_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(state.messageRes ?: R.string.state_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (onAction != null) {
                        Spacer(modifier = Modifier.height(spacing.sm))
                        Button(
                            onClick = onAction,
                            modifier = Modifier.defaultMinSize(minHeight = dimensions.minTouchTarget)
                        ) {
                            Text(text = stringResource(state.actionLabelRes ?: R.string.action_sync_now))
                        }
                    }
                }
            }
        }

        is ScreenState.PermissionMissing -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.cd_status_icon),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dimensions.iconXLarge)
                    )
                    Text(
                        text = stringResource(state.titleRes ?: R.string.state_permission_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(state.messageRes ?: R.string.state_permission_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (onGrantPermissions != null) {
                        Spacer(modifier = Modifier.height(spacing.sm))
                        Button(
                            onClick = onGrantPermissions,
                            modifier = Modifier.defaultMinSize(minHeight = dimensions.minTouchTarget)
                        ) {
                            Text(text = stringResource(R.string.action_grant_permissions))
                        }
                    }
                }
            }
        }

        is ScreenState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.cd_status_icon),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(dimensions.iconXLarge)
                    )
                    Text(
                        text = stringResource(R.string.state_error_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = state.message ?: state.messageRes?.let { stringResource(it) }
                            ?: stringResource(R.string.state_error_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (onRetry != null) {
                        Spacer(modifier = Modifier.height(spacing.sm))
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.defaultMinSize(minHeight = dimensions.minTouchTarget)
                        ) {
                            Text(text = stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        }

        is ScreenState.Content -> {
            content(state.data)
        }
    }
}
