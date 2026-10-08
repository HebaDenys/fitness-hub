package io.github.hebadenys.fitnesshub.core.scale

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Passive BLE scanner for Xiaomi body composition scales.
 *
 * Scanning is receive-only: no GATT connection is opened, so the scale keeps
 * its normal pairing with Xiaomi Home and never has to disconnect from it.
 *
 * The scanner hands raw advertisement bytes to [onAdvertisement] and owns no
 * protocol knowledge, which is what keeps [S400ScaleConnector] testable off-device.
 */
class ScaleBleScanner(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onAdvertisement: (ByteArray?, Instant) -> Unit
) {

    private var scanJob: Job? = null

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED

    fun isBluetoothAvailable(): Boolean {
        val local = adapter ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission()) {
                // Hardware can exist before the user grants scan access. Avoid
                // BluetoothAdapter.isEnabled here: Android 12+ may require
                // BLUETOOTH_CONNECT, which this receive-only fallback does not request.
                true
            } else {
                runCatching { local.bluetoothLeScanner != null }.getOrDefault(false)
            }
        } else {
            runCatching { local.isEnabled }.getOrDefault(false)
        }
    }

    /** Starts scanning; a second call while already scanning is ignored. */
    fun start(): Boolean {
        if (scanJob != null) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "BLUETOOTH_SCAN permission not granted")
            return false
        }
        val scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull()
        if (scanner == null) {
            Log.w(TAG, "BLE scanner unavailable")
            return false
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanJob = scope.launch {
            runCatching { scanner.startScan(filters, settings, callback) }
                .onFailure { error ->
                    Log.w(TAG, "Scan could not start (${error::class.java.simpleName})")
                }
        }
        return true
    }

    fun stop() {
        val scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { scanner?.stopScan(callback) }
        }
        scanJob?.cancel()
        scanJob = null
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord?.getServiceData(android.os.ParcelUuid(SERVICE_UUID))
            if (serviceData != null) onAdvertisement(serviceData, Instant.now())
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "Scan failed with code $errorCode")
        }
    }

    private companion object {
        const val TAG = "FitnessHubScale"
        val SERVICE_UUID: java.util.UUID = java.util.UUID.fromString("0000fe95-0000-1000-8000-00805f9b34fb")
    }
}
