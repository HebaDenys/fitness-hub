package io.github.hebadenys.fitnesshub.feature.xiaomi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.xiaomi.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal enum class XiaomiWork { IDLE, REFRESH, LOGIN, DISCOVERY, BINDING, SYNC, HISTORY, LOGOUT }
internal enum class XiaomiNotice { NONE, SIGNED_IN, BOUND, SYNC_FINISHED, SYNC_PAUSED, SIGNED_OUT, CANCELLED }

internal data class XiaomiSourceUiState(
    val overview: XiaomiSourceOverview? = null,
    val work: XiaomiWork = XiaomiWork.IDLE,
    val discovery: XiaomiDiscoveryState? = null,
    val selectedKey: String? = null,
    val records: List<XiaomiRecordDetail> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val historyOffset: Int = 0,
    val errorCode: String? = null,
    val notice: XiaomiNotice = XiaomiNotice.NONE
) : PrivateXiaomiValue() {
    val busy: Boolean get() = work != XiaomiWork.IDLE
    val canSync: Boolean get() {
        val value = overview ?: return false
        val source = value.binding ?: return false
        val account = value.account ?: return false
        return value.block == null && source.scope.connectionId == account.connectionId &&
            source.scope.region == account.region && source.scope.loginUid == account.userId
    }
}

@HiltViewModel
internal class XiaomiSourceViewModel @Inject constructor(private val gateway: XiaomiSourceGateway) : ViewModel() {
    private val mutableState = MutableStateFlow(XiaomiSourceUiState())
    val state = mutableState.asStateFlow()
    private var running: Job? = null
    private var generation = 0L

    fun refresh() { start(XiaomiWork.REFRESH) { reload() } }

    fun login(region: XiaomiRegion, username: String, password: CharArray) {
        val job = start(XiaomiWork.LOGIN) {
            mutableState.update { it.copy(discovery = null, selectedKey = null) }
            try { gateway.login(region, username, password) } finally { password.fill('\u0000') }
            reload()
            mutableState.update { it.copy(notice = XiaomiNotice.SIGNED_IN) }
        }
        if (job == null) password.fill('\u0000') else job.invokeOnCompletion { password.fill('\u0000') }
    }

    fun discover(model: String, older: Boolean = false) {
        start(XiaomiWork.DISCOVERY) {
            if (!older) mutableState.update { it.copy(discovery = null, selectedKey = null) }
            val found = gateway.discover(model.trim(), older)
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(discovery = found,
                selectedKey = it.selectedKey?.takeIf { key -> found.candidates.any { row -> row.key == key } }) }
        }
    }

    fun select(key: String) {
        val state = mutableState.value
        if (!state.busy && state.discovery?.candidates?.any { it.key == key } == true) {
            mutableState.update { it.copy(selectedKey = key, errorCode = null) }
        }
    }

    fun confirmSelection() {
        val key = mutableState.value.selectedKey ?: return
        start(XiaomiWork.BINDING) {
            gateway.confirm(key)
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(discovery = null, selectedKey = null) }
            reload()
            mutableState.update { it.copy(notice = XiaomiNotice.BOUND) }
        }
    }

    fun sync() {
        if (!mutableState.value.canSync) return
        start(XiaomiWork.SYNC) {
            val result = gateway.sync()
            currentCoroutineContext().ensureActive()
            reload()
            mutableState.update { it.copy(notice = when (result) {
                is XiaomiHistoryResult.Completed -> XiaomiNotice.SYNC_FINISHED
                is XiaomiHistoryResult.Paused -> XiaomiNotice.SYNC_PAUSED
            }) }
        }
    }

    fun loadMoreHistory() {
        if (!mutableState.value.hasMoreHistory) return
        start(XiaomiWork.HISTORY) {
            val offset = mutableState.value.historyOffset
            val page = gateway.history(offset)
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(records = (it.records + page.records).distinctBy { row -> row.hash },
                historyOffset = offset + page.records.size, hasMoreHistory = page.hasMore) }
        }
    }

    fun disconnect() {
        cancel()
        start(XiaomiWork.LOGOUT) {
            gateway.disconnect()
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(discovery = null, selectedKey = null) }
            reload()
            mutableState.update { it.copy(notice = XiaomiNotice.SIGNED_OUT) }
        }
    }

    fun cancel() {
        if (mutableState.value.work == XiaomiWork.LOGOUT) return
        generation++
        running?.cancel()
        running = null
        mutableState.update { it.copy(work = XiaomiWork.IDLE, notice = XiaomiNotice.CANCELLED) }
    }

    fun onHidden() {
        if (mutableState.value.busy && mutableState.value.work != XiaomiWork.LOGOUT) cancel()
    }

    private suspend fun reload() {
        val overview = gateway.overview()
        val history = gateway.history()
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(overview = overview, records = history.records, historyOffset = history.records.size,
            hasMoreHistory = history.hasMore) }
    }

    private fun start(work: XiaomiWork, block: suspend () -> Unit): Job? {
        if (running?.isActive == true || mutableState.value.busy) return null
        val epoch = ++generation
        mutableState.update { it.copy(work = work, errorCode = null, notice = XiaomiNotice.NONE) }
        return viewModelScope.launch {
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                if (epoch == generation) mutableState.update { it.copy(errorCode = "TIMEOUT") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (epoch == generation) {
                    val code = when (failure) {
                        is XiaomiAccessException -> failure.reason.name
                        is XiaomiArchiveException -> failure.reason.name
                        is XiaomiProtocolException -> "PROTOCOL_ERROR"
                        else -> "UNEXPECTED_ERROR"
                    }
                    val current = try { gateway.overview() } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) { null }
                    currentCoroutineContext().ensureActive()
                    mutableState.update { it.copy(errorCode = code, overview = current, discovery = null, selectedKey = null) }
                }
            } finally {
                if (epoch == generation) mutableState.update { it.copy(work = XiaomiWork.IDLE) }
            }
        }.also { running = it }
    }
}
