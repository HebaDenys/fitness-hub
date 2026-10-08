package io.github.hebadenys.fitnesshub.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.hebadenys.fitnesshub.core.scale.Sex
import io.github.hebadenys.fitnesshub.feature.scale.*
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import io.github.hebadenys.fitnesshub.ui.theme.PerformanceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
class LocalProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun profile() = ScaleUiModel(false, false, false, null, null, 175.0, 30, Sex.FEMALE)

    @Test fun existingProfileIsPreloadedAndDecimalCommaSavesValidatedValues() {
        var savedHeight: Double? = null
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ScaleProfileEditor(profile(), ProfileSaveResult.IDLE, onSave = { height, _, _ -> savedHeight = height })
                    }
                }
            }
        }
        compose.onNodeWithTag("profile_height").assertTextContains("175.0")
        compose.onNodeWithTag("profile_age").assertTextContains("30")
        compose.onNodeWithTag("profile_sex_FEMALE").assertIsSelected()
        compose.onNodeWithTag("profile_height").performTextReplacement("181,5")
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(181.5, savedHeight!!, 0.0) }
    }

    @Test fun invalidFieldsShowErrorAndNeverInvokeSave() {
        var saves = 0
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ScaleProfileEditor(profile(), ProfileSaveResult.IDLE, onSave = { _, _, _ -> saves++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("profile_height").performTextReplacement("NaN")
        compose.onNodeWithTag("profile_save").performScrollTo().performClick()
        compose.onNodeWithTag("profile_validation").assertExists()
        compose.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun unsavedFormSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            FitnessHubTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ScaleProfileEditor(profile(), ProfileSaveResult.IDLE, onSave = { _, _, _ -> })
                }
            }
        }
        compose.onNodeWithTag("profile_height").performTextReplacement("183.5")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("profile_height").assertTextContains("183.5")
    }

    @Test fun backOffersCancelOrDiscardWithoutSaving() {
        val vm = mock<ScaleViewModel>()
        whenever(vm.uiState).thenReturn(MutableStateFlow<ScreenState<ScaleUiModel>>(ScreenState.Content(profile())))
        whenever(vm.profileSaveResult).thenReturn(MutableStateFlow(ProfileSaveResult.IDLE))
        var backs = 0
        compose.setContent {
            FitnessHubTheme { PerformanceTheme { LocalProfileScreen(onBack = { backs++ }, viewModel = vm) } }
        }
        compose.onNodeWithTag("profile_height").performTextReplacement("180")
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("profile_height").assertTextContains("180")
        compose.runOnIdle { assertEquals(0, backs) }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            verify(vm, never()).saveProfile(anyOrNull(), anyOrNull(), anyOrNull())
        }
    }

    @Test fun profileSaveAndSexChoicesRemainReachableAtDoubleFontScale() {
        var saved = 0
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            ScaleProfileEditor(profile(), ProfileSaveResult.IDLE, onSave = { _, _, _ -> saved++ })
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("profile_sex_MALE").performScrollTo().performClick()
        compose.onNodeWithTag("profile_save").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }

    @Test fun hubActionsRemainReachableAtDoubleFontScaleWithoutPermissions() {
        var profile = 0
        var manual = 0
        var health = 0
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        SettingsHubContent(
                            SettingsUiModel(false, false, false, emptyList()),
                            onProfile = { profile++ },
                            onManualBody = { manual++ },
                            onHealthConnect = { health++ }
                        )
                    }
                }
            }
        }
        listOf("settings_profile", "source_health_connect", "settings_manual_body").forEach { tag ->
            compose.onNodeWithTag("settings_hub").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsEnabled().performClick()
        }
        compose.runOnIdle {
            assertEquals(1, profile)
            assertEquals(1, manual)
            assertEquals(1, health)
        }
    }
}
