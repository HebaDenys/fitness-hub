package io.github.hebadenys.fitnesshub.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.analytics.InsightCard
import io.github.hebadenys.fitnesshub.core.analytics.InsightsRepository
import io.github.hebadenys.fitnesshub.core.backup.BackupCrypto
import io.github.hebadenys.fitnesshub.core.backup.BackupService
import io.github.hebadenys.fitnesshub.core.export.CsvWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiModel(
    val cards: List<InsightCard>,
    val weightCsv: String
)

sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState
    data class Created(val archive: String) : BackupState
    data class Restored(val rows: Int) : BackupState
    data class Failed(val reason: String) : BackupState
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: InsightsRepository,
    private val backupService: BackupService
) : ViewModel() {

    private val backupState = MutableStateFlow<BackupState>(BackupState.Idle)

    val backup: StateFlow<BackupState> = backupState.asStateFlow()

    val uiState: StateFlow<InsightsUiModel?> = repository.observeInsights()
        .map { data ->
            InsightsUiModel(
                cards = data.cards,
                weightCsv = CsvWriter.document(
                    headers = listOf("date", "weightKg"),
                    rows = data.weightTrend.map { point ->
                        listOf(point.date.toString(), CsvWriter.number(point.value, 2))
                    }
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createBackup(passphrase: String) {
        val key = passphrase.toCharArray()
        viewModelScope.launch {
            backupState.value = BackupState.Working
            backupState.value = runCatching { BackupState.Created(backupService.createBackup(key)) }
                .getOrElse { BackupState.Failed(it.readableReason()) }
        }
    }

    fun restoreBackup(archive: String, passphrase: String) {
        val key = passphrase.toCharArray()
        viewModelScope.launch {
            backupState.value = BackupState.Working
            backupState.value = runCatching {
                BackupState.Restored(backupService.restoreBackup(archive.trim(), key).totalRows)
            }.getOrElse { BackupState.Failed(it.readableReason()) }
        }
    }

    fun dismissBackupState() {
        backupState.value = BackupState.Idle
    }

    /**
     * Backup failures surface as a stable reason key, never as a raw message: a
     * decryption or integrity error must not leak internals into the UI, and the
     * user gets an actionable label instead.
     */
    private fun Throwable.readableReason(): String = when (this) {
        is BackupCrypto.InvalidBackupException -> "invalid_backup"
        else -> "backup_failed"
    }
}
