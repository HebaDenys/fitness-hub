package io.github.hebadenys.fitnesshub.feature.nutrition

import androidx.camera.core.ImageAnalysis

/** Shared contract for the capture analyzers so the preview can treat them uniformly. */
interface CaptureAnalyzer : ImageAnalysis.Analyzer {
    /** Allows a new capture session to receive frames again. */
    fun reset()

    /** Releases the underlying on-device model. */
    fun close()
}
