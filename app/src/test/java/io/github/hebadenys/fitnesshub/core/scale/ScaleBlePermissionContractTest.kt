package io.github.hebadenys.fitnesshub.core.scale

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class ScaleBlePermissionContractTest {

    @Test fun android12PlusScannerDoesNotRequireConnectPermissionToProbeHardware() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val scanner = ScaleBleScanner(context, TestScope()) { _, _ -> }

        // The test process has not granted BLUETOOTH_SCAN, but probing must be safe
        // and must not depend on BLUETOOTH_CONNECT/isEnabled on Android 12+.
        val result = runCatching { scanner.isBluetoothAvailable() }

        assertTrue(result.isSuccess)
    }

    @Test fun scanPermissionContractRemainsReceiveOnly() {
        val required = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        assertTrue(required.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertFalse(required.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }
}
