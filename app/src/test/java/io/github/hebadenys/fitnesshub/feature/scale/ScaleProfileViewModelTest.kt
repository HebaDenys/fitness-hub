package io.github.hebadenys.fitnesshub.feature.scale

import android.content.Context
import androidx.lifecycle.ViewModelStore
import io.github.hebadenys.fitnesshub.core.scale.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleProfileViewModelTest {
    private lateinit var connector: S400ScaleConnector
    private lateinit var dao: ScaleDao
    private lateinit var model: ScaleViewModel
    private lateinit var holder: ViewModelStore

    @BeforeEach fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        connector = mock()
        dao = mock()
        whenever(dao.observeMeasurements(1)).thenReturn(flowOf(emptyList()))
        whenever(dao.observeEstimates(1)).thenReturn(flowOf(emptyList()))
        whenever(dao.observeProfile()).thenReturn(flowOf(null))
        model = ScaleViewModel(connector, dao, mock<ScaleHistoryCsvImporter>(), mock<Context>())
        holder = ViewModelStore().also { it.put("profile", model) }
    }

    @AfterEach fun tearDown() { holder.clear(); Dispatchers.resetMain() }

    @Test fun invalidProfileNeverReplacesSavedValues() = runTest {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 100.0, 301.0).forEach {
            model.saveProfile(it, 30, Sex.FEMALE)
            assertEquals(ProfileSaveResult.INVALID, model.profileSaveResult.value)
        }
        model.saveProfile(175.0, 0, Sex.MALE)
        model.saveProfile(175.0, 30, null)
        advanceUntilIdle()
        verify(connector, never()).saveProfile(anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test fun repeatedSaveStartsOneOperationAndReportsSuccess() = runTest {
        whenever(dao.measurementsSince(0L)).thenReturn(emptyList())
        model.saveProfile(175.5, 30, Sex.FEMALE)
        model.saveProfile(175.5, 30, Sex.FEMALE)
        assertEquals(ProfileSaveResult.SAVING, model.profileSaveResult.value)
        advanceUntilIdle()
        verify(connector, times(1)).saveProfile(175.5, 30, Sex.FEMALE)
        assertEquals(ProfileSaveResult.SAVED, model.profileSaveResult.value)
        model.dismissProfileResult()
        assertEquals(ProfileSaveResult.IDLE, model.profileSaveResult.value)
    }

    @Test fun storageFailureIsReportedWithoutRefreshingEstimates() = runTest {
        whenever(connector.saveProfile(175.0, 30, Sex.MALE)).thenThrow(IllegalStateException("synthetic"))
        model.saveProfile(175.0, 30, Sex.MALE)
        advanceUntilIdle()
        assertEquals(ProfileSaveResult.FAILED, model.profileSaveResult.value)
        verify(dao, never()).measurementsSince(any())
    }

    @Test fun savedProfileIsNotMisreportedAsLostWhenEstimateRefreshFails() = runTest {
        whenever(dao.measurementsSince(0L)).thenThrow(IllegalStateException("synthetic"))
        model.saveProfile(175.0, 30, Sex.MALE)
        advanceUntilIdle()
        verify(connector).saveProfile(175.0, 30, Sex.MALE)
        assertEquals(ProfileSaveResult.SAVED_ESTIMATES_PENDING, model.profileSaveResult.value)
    }

    @Test fun acceptedInputBoundsMatchTheEditor() {
        assertTrue(validScaleProfile(100.1, 10, Sex.FEMALE))
        assertTrue(validScaleProfile(300.0, 120, Sex.MALE))
        assertFalse(validScaleProfile(175.0, 121, Sex.MALE))
        assertFalse(validScaleProfile(null, 30, Sex.MALE))
    }
}
