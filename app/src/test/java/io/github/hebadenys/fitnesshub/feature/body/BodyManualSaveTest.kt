package io.github.hebadenys.fitnesshub.feature.body

import androidx.lifecycle.ViewModelStore
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyData
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyRepository
import io.github.hebadenys.fitnesshub.core.database.HealthDao
import io.github.hebadenys.fitnesshub.core.database.ManualBodyMeasurementEntity
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class BodyManualSaveTest {
    private lateinit var dao: HealthDao
    private lateinit var vm: BodyViewModel
    private lateinit var holder: ViewModelStore
    @BeforeEach fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        dao = mock()
        val health = mock<HealthConnectManager>()
        runBlocking { whenever(health.grantedMetrics()).thenReturn(emptySet()) }
        val repository = mock<CanonicalBodyRepository>()
        whenever(repository.observe()).thenReturn(flowOf(CanonicalBodyData(null, null, emptyList(), emptyList(), emptyList())))
        vm = BodyViewModel(health, repository, dao)
        holder = ViewModelStore().also { it.put("body", vm) }
    }
    @AfterEach fun cleanup() { holder.clear(); Dispatchers.resetMain() }

    @Test fun commaAndZeroAreStoredOnceDespiteRepeatedSaveTaps() = runTest {
        whenever(dao.insertManualBodyMeasurement(any())).thenReturn(77L)
        vm.saveManualMeasurement("80,5", "0", "2020-01-02 12:34")
        vm.saveManualMeasurement("80,5", "0", "2020-01-02 12:34")
        assertEquals(ManualBodyEntryState.Working, vm.manualEntryState.value)
        advanceUntilIdle()
        val value = argumentCaptor<ManualBodyMeasurementEntity>()
        verify(dao, times(1)).insertManualBodyMeasurement(value.capture())
        assertEquals(80.5, value.firstValue.weightKg)
        assertEquals(0.0, value.firstValue.bodyFatPercent)
        assertEquals(ManualBodyEntryState.Saved(77), vm.manualEntryState.value)
    }

    @Test fun invalidInputDoesNotWriteAndStorageFailureCanBeRetried() = runTest {
        vm.saveManualMeasurement("74,2.5", "", "2020-01-02 12:34")
        advanceUntilIdle()
        assertTrue(vm.manualEntryState.value is ManualBodyEntryState.Invalid)
        verify(dao, never()).insertManualBodyMeasurement(any())
        whenever(dao.insertManualBodyMeasurement(any())).thenThrow(IllegalStateException("synthetic"))
        vm.saveManualMeasurement("74.2", "", "2020-01-02 12:34")
        advanceUntilIdle()
        assertEquals(ManualBodyEntryState.StorageError, vm.manualEntryState.value)
        doReturn(78L).whenever(dao).insertManualBodyMeasurement(any())
        vm.saveManualMeasurement("74.2", "", "2020-01-02 12:34")
        advanceUntilIdle()
        assertEquals(ManualBodyEntryState.Saved(78), vm.manualEntryState.value)
    }

    @Test fun coroutineCancellationIsNotReportedAsAStorageFailure() = runTest {
        whenever(dao.insertManualBodyMeasurement(any())).thenThrow(CancellationException("synthetic"))
        vm.saveManualMeasurement("74.2", "", "2020-01-02 12:34")
        advanceUntilIdle()
        assertEquals(ManualBodyEntryState.Idle, vm.manualEntryState.value)
    }
}
