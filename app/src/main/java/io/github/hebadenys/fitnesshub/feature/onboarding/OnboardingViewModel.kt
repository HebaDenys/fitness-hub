package io.github.hebadenys.fitnesshub.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("fitness_hub_onboarding", Context.MODE_PRIVATE)

    fun isComplete(): Boolean = preferences.getBoolean(KEY_COMPLETE, false)

    fun complete() {
        preferences.edit().putBoolean(KEY_COMPLETE, true).apply()
    }

    private companion object {
        const val KEY_COMPLETE = "completed_v1"
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val store: OnboardingStore) : ViewModel() {
    private val mutableCompleted = MutableStateFlow(store.isComplete())
    val completed: StateFlow<Boolean> = mutableCompleted.asStateFlow()

    fun complete() {
        store.complete()
        mutableCompleted.value = true
    }
}
