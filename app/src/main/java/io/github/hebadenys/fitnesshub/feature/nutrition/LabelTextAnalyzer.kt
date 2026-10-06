package io.github.hebadenys.fitnesshub.feature.nutrition

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams camera frames to the bundled on-device text recogniser and reports the
 * first block of recognised text, which the caller feeds to the label parser.
 *
 * Recognition stops after the first successful read so the user is not forced to
 * hold the camera while the model keeps re-running on every frame.
 */
class LabelTextAnalyzer(
    private val onText: (String) -> Unit
) : CaptureAnalyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val consumed = AtomicBoolean(false)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || consumed.get()) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val text = result.text
                if (text.isNotBlank() && consumed.compareAndSet(false, true)) {
                    onText(text)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun reset() = consumed.set(false)

    override fun close() = recognizer.close()
}
