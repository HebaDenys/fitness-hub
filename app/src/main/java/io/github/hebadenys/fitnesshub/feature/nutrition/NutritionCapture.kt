package io.github.hebadenys.fitnesshub.feature.nutrition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

/**
 * Camera capture for barcode scanning and label reading.
 *
 * Both models run on-device; the screen never uploads an image anywhere.
 */
@Composable
fun NutritionCaptureOverlay(
    state: CaptureState,
    onBarcode: (String) -> Unit,
    onLabelText: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = FitnessHubTheme.spacing
    val cornerRadius = FitnessHubTheme.cornerRadius
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleRes = when (state) {
                    is CaptureState.ScanningBarcode -> R.string.nutrition_scan_barcode
                    is CaptureState.CapturingLabel -> R.string.nutrition_capture_label
                    else -> R.string.nutrition_capture
                }
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        contentDescription = ""
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Spacer(Modifier.height(spacing.sm))

            if (hasCameraPermission) {
                CameraPreview(
                    labelMode = state is CaptureState.CapturingLabel,
                    onBarcode = onBarcode,
                    onLabelText = onLabelText
                )
            } else {
                Text(
                    text = stringResource(R.string.nutrition_camera_permission_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    labelMode: Boolean,
    onBarcode: (String) -> Unit,
    onLabelText: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analyzer = remember(labelMode) {
        if (labelMode) LabelTextAnalyzer(onLabelText) else BarcodeAnalyzer(onBarcode)
    }
    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    )

    LaunchedEffect(previewView, lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(ContextCompat.getMainExecutor(context), analyzer) }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
    }
}
@Composable
fun NutritionQuickActions(
    onScanBarcode: () -> Unit,
    onCaptureLabel: () -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = FitnessHubTheme.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Button(onClick = onScanBarcode, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.height(spacing.xs))
            Text(stringResource(R.string.nutrition_scan_barcode_short))
        }
        Button(onClick = onCaptureLabel, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.TextFields, contentDescription = null)
            Spacer(Modifier.height(spacing.xs))
            Text(stringResource(R.string.nutrition_capture_label_short))
        }
        Button(onClick = onManualEntry, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.nutrition_manual_entry))
        }
    }
}

@Composable
fun NutritionCameraUnavailable(onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.nutrition_camera_permission_required))
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    }
}
