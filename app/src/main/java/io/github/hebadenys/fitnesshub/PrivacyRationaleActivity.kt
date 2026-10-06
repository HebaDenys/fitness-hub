package io.github.hebadenys.fitnesshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

class PrivacyRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PrivacyRationaleScreen(onContinue = { finish() })
            }
        }
    }
}

@Composable
private fun PrivacyRationaleScreen(onContinue: () -> Unit) {
    val purposes = listOf(
        R.string.privacy_rationale_purpose_activity,
        R.string.privacy_rationale_purpose_sleep,
        R.string.privacy_rationale_purpose_heart_rate,
        R.string.privacy_rationale_purpose_oxygen,
        R.string.privacy_rationale_purpose_weight,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = stringResource(R.string.privacy_rationale_icon_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.privacy_rationale_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.privacy_rationale_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.privacy_rationale_purpose_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        purposes.forEach { purpose ->
            Text(
                text = stringResource(purpose),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.privacy_rationale_continue))
        }
    }
}
