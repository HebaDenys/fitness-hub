package io.github.hebadenys.fitnesshub.ui.state

sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>

    data class Empty(
        val titleRes: Int? = null,
        val messageRes: Int? = null,
        val actionLabelRes: Int? = null
    ) : ScreenState<Nothing>

    data class PermissionMissing(
        val missingMetrics: Set<String> = emptySet(),
        val titleRes: Int? = null,
        val messageRes: Int? = null
    ) : ScreenState<Nothing>

    data class Error(
        val message: String? = null,
        val messageRes: Int? = null
    ) : ScreenState<Nothing>

    data class Content<T>(
        val data: T
    ) : ScreenState<T>
}
