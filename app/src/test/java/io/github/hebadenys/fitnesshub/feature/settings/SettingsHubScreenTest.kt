package io.github.hebadenys.fitnesshub.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h640dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsHubScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun model(granted: Set<String> = setOf(HealthMetrics.STEPS, HealthMetrics.WEIGHT)) =
        SettingsUiModel(
            isClientAvailable = true,
            hasAnyPermission = granted.isNotEmpty(),
            historyGranted = true,
            metricStatuses = HealthMetrics.ALL.map { MetricPermissionStatus(it, it in granted) },
            lastLocalSyncMillis = 1_791_286_400_000L
        )

    @Test fun compactHubRoutesEachMajorSourceWithoutInlineLegacyForms() {
        var health = 0
        var xiaomi = 0
        var scale = 0
        compose.setContent {
            MaterialTheme {
                SettingsHubContent(
                    model(),
                    onHealthConnect = { health++ },
                    onXiaomi = { xiaomi++ },
                    onScale = { scale++ }
                )
            }
        }

        compose.onNodeWithText("2 of 11 metric groups authorized").assertExists()
        compose.onNodeWithTag("source_health_connect").performClick()
        compose.onNodeWithTag("source_xiaomi").performClick()
        compose.onNodeWithTag("source_scale").performClick()
        compose.runOnIdle {
            assertEquals(1, health)
            assertEquals(1, xiaomi)
            assertEquals(1, scale)
        }
    }

    @Test fun healthDetailExposesStatusPermissionManagementAndManualSync() {
        var permissions = 0
        var sync = 0
        var refresh = 0
        compose.setContent {
            MaterialTheme {
                HealthConnectSourceContent(
                    model(),
                    onPermissions = { permissions++ },
                    onSync = { sync++ },
                    onRefresh = { refresh++ }
                )
            }
        }

        compose.onNodeWithTag("health_permissions").assertIsEnabled().performClick()
        compose.onNodeWithTag("health_check_access").assertIsEnabled().performClick()
        compose.onNodeWithTag("health_connect_source").performScrollToNode(hasTestTag("health_sync"))
        compose.onNodeWithTag("health_sync").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, permissions)
            assertEquals(1, sync)
            assertEquals(1, refresh)
        }
    }

    @Test fun unavailableHealthConnectCannotRequestPermissionsOrSync() {
        val unavailable = model(emptySet()).copy(isClientAvailable = false)
        compose.setContent { MaterialTheme { HealthConnectSourceContent(unavailable) } }

        compose.onNodeWithTag("health_permissions").assertIsNotEnabled()
        compose.onNodeWithTag("health_sync").assertIsNotEnabled()
    }
}
