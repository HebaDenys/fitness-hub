package io.github.hebadenys.fitnesshub.feature.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h640dp")
@LooperMode(LooperMode.Mode.PAUSED)
class OnboardingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun continueAndSkipAreExplicitAndFunctional() {
        var continued = 0
        var skipped = 0
        compose.setContent {
            MaterialTheme {
                OnboardingScreen(onContinue = { continued++ }, onSkip = { skipped++ })
            }
        }

        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("onboarding").performScrollToNode(hasTestTag("onboarding_skip"))
        compose.onNodeWithTag("onboarding_skip").performClick()
        compose.runOnIdle {
            assertEquals(1, continued)
            assertEquals(1, skipped)
        }
    }
}
