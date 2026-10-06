package io.github.hebadenys.fitnesshub.feature.nutrition

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams camera frames to the bundled on-device barcode model.
 *
 * The bundled ML Kit model works fully offline, so scanning never needs a
 * network connection. Only the first decodable barcode is reported and analysis
 * then pauses, so a held camera does not fire the callback repeatedly.
 */
class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit
) : CaptureAnalyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
    )
    private val consumed = AtomicBoolean(false)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || consumed.get()) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                if (value != null && consumed.compareAndSet(false, true)) {
                    onBarcode(value)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun reset() = consumed.set(false)

    override fun close() = scanner.close()
}
