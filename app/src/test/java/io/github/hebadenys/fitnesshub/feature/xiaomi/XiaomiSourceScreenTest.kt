package io.github.hebadenys.fitnesshub.feature.xiaomi

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.xiaomi.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Real Compose semantics/actions under Robolectric; not screenshots or physical-device tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w412dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class XiaomiSourceScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun blockedBuildHasNoPasswordOrLoginInput() {
        compose.setContent { MaterialTheme {
            XiaomiSourceContent(XiaomiSourceUiState(overview = sourceOverview(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED, selected = false, signedIn = false)))
        } }
        compose.onNodeWithTag("xiaomi_blocked").assertExists()
        compose.onNodeWithTag("xiaomi_password").assertDoesNotExist()
        compose.onNodeWithTag("xiaomi_login").assertDoesNotExist()
    }

    @Test fun candidateRequiresSelectionAndSecondExplicitConfirmation() {
        val fake = SourceGatewayFake()
        val state = mutableStateOf(XiaomiSourceUiState(overview = sourceOverview(selected = false),
            discovery = XiaomiDiscoveryState(listOf(fake.candidate), null, 1, 1, 0)))
        var confirmed = 0
        compose.setContent { MaterialTheme {
            XiaomiSourceContent(state.value, onSelect = { state.value = state.value.copy(selectedKey = it) }, onConfirm = { confirmed++ })
        } }
        compose.onNodeWithTag("xiaomi_source_list").performScrollToNode(hasTestTag("xiaomi_confirm"))
        compose.onNodeWithTag("xiaomi_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("xiaomi_source_list").performScrollToNode(hasTestTag("candidate_fixture-key"))
        compose.onNodeWithTag("candidate_fixture-key").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(0, confirmed) }
        compose.onNodeWithTag("xiaomi_source_list").performScrollToNode(hasTestTag("xiaomi_confirm"))
        compose.onNodeWithTag("xiaomi_confirm").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(0, confirmed) }
        compose.onNodeWithTag("xiaomi_confirm_dialog").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun archivedRecordCanBeReadWhileCloudLoginIsBlocked() {
        val row = XiaomiRecordDetail("fixture-hash", 1791287999000L,
            listOf(XiaomiMetricDetail("bfp", "18.0", XiaomiUnit.PERCENT, XiaomiMethod.VENDOR_ESTIMATE, emptyList())), emptyList())
        compose.setContent { MaterialTheme {
            XiaomiSourceContent(XiaomiSourceUiState(overview = sourceOverview(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED, signedIn = false), records = listOf(row)))
        } }
        compose.onNodeWithTag("xiaomi_source_list").performScrollToNode(hasTestTag("record_fixture-hash"))
        compose.onNodeWithTag("record_fixture-hash").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.xiaomi_method_estimate)).assertExists()
        compose.onNodeWithText("bfp: 18.0 %").assertExists()
    }

    @Test fun passwordIsMaskedAndNotRestoredWithSaveableState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme {
            XiaomiSourceContent(XiaomiSourceUiState(overview = sourceOverview(selected = false, signedIn = false)))
        } }
        compose.onNodeWithTag("xiaomi_source_list").performScrollToNode(hasTestTag("xiaomi_password"))
        compose.onNodeWithTag("xiaomi_password").performTextInput("fixture-password")
        compose.onNodeWithTag("xiaomi_password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("xiaomi_source_list").performScrollToNode(hasTestTag("xiaomi_password"))
        compose.onNodeWithTag("xiaomi_password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun secureWindowFlagIsRestoredWhenSourceScreenIsRemoved() {
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme {
            if (visible.value) XiaomiSourceContent(XiaomiSourceUiState(overview = sourceOverview(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED)))
        } }
        compose.runOnIdle {
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            visible.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test fun settingsEntryActuallyInvokesNavigationCallback() {
        var opened = 0
        compose.setContent { MaterialTheme { XiaomiSourceEntry { opened++ } } }
        compose.onNodeWithTag("xiaomi_source_open").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
