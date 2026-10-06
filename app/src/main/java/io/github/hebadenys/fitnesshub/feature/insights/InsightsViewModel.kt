package io.github.hebadenys.fitnesshub.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.analytics.InsightCard
import io.github.hebadenys.fitnesshub.core.analytics.InsightsRepository
import io.github.hebadenys.fitnesshub.core.backup.BackupCrypto
import io.github.hebadenys.fitnesshub.core.backup.DatabaseBackupService
import io.github.hebadenys.fitnesshub.core.export.CsvWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiModel(val cards: List<InsightCard>, val weightCsv: String)

sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState
    data class Created(val archive: String) : BackupState
    data class Restored(val rows: Int, val identicalRows: Int = 0, val legacyPartial: Boolean = false) : BackupState
    data class Failed(val reason: String) : BackupState
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: InsightsRepository,
    private val backupService: DatabaseBackupService
) : ViewModel() {
    private val backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backup: StateFlow<BackupState> = backupState.asStateFlow()
    val uiState: StateFlow<InsightsUiModel?> = repository.observeInsights().map { data ->
        InsightsUiModel(data.cards, CsvWriter.document(
            headers = listOf("date", "weightKg"),
            rows = data.weightTrend.map { point -> listOf(point.date.toString(), CsvWriter.number(point.value, 2)) }
        ))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createBackup(passphrase: String) {
        if (backupState.value is BackupState.Working) return
        val key = passphrase.toCharArray()
        backupState.value = BackupState.Working
        viewModelScope.launch {
            try { backupState.value = BackupState.Created(backupService.createBackup(key))
            } catch (cancelled: CancellationException) { backupState.value = BackupState.Idle; throw cancelled
            } catch (error: Exception) { backupState.value = BackupState.Failed(error.readableReason())
            } finally { key.fill('\u0000') }
        }
    }

    fun restoreBackup(archive: String, passphrase: String) {
        if (backupState.value is BackupState.Working) return
        val key = passphrase.toCharArray()
        backupState.value = BackupState.Working
        viewModelScope.launch {
            try {
                val result = backupService.restoreBackup(archive.trim(), key)
                backupState.value = BackupState.Restored(result.totalRows, result.identicalRows, result.legacyPartial)
            } catch (cancelled: CancellationException) { backupState.value = BackupState.Idle; throw cancelled
            } catch (error: Exception) { backupState.value = BackupState.Failed(error.readableReason())
            } finally { key.fill('\u0000') }
        }
    }

    fun dismissBackupState() { if (backupState.value !is BackupState.Working) backupState.value = BackupState.Idle }
    private fun Throwable.readableReason() = when (this) {
        is BackupCrypto.InvalidBackupException -> "invalid_backup"
        is DatabaseBackupService.ArchiveException -> reason
        else -> "backup_failed"
    }
}
