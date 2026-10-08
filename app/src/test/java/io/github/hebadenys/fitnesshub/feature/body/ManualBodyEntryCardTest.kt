package io.github.hebadenys.fitnesshub.feature.body

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ManualBodyEntryCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun manualEntryWorksWithoutHealthConnectPermission() {
        var savedWeight = ""
        var savedFat = ""
        var savedTime = ""
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                ManualBodyEntryCard(
                    state = ManualBodyEntryState.Idle,
                    healthConnectAvailable = true,
                    hasHealthConnectPermission = false,
                    onGrantPermissions = {},
                    onSave = { weight, fat, time ->
                        savedWeight = weight
                        savedFat = fat
                        savedTime = time
                    },
                    onDismissState = {}
                )
                }
            }
        }

        compose.onNodeWithTag("manual_weight").performTextInput("80.5")
        compose.onNodeWithTag("manual_body_fat").performTextInput("18.2")
        compose.onNodeWithTag("manual_save").performScrollTo().assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals("80.5", savedWeight)
            assertEquals("18.2", savedFat)
            assert(savedTime.isNotBlank())
        }
        compose.onNodeWithTag("body_optional_health_connect").assertExists()
    }
}
