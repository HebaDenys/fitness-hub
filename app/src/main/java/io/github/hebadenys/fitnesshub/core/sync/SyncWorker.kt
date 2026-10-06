package io.github.hebadenys.fitnesshub.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodic background entry point: one repository sync per run. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: HealthSyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = repository.sync().fold(
        onSuccess = { Result.success() },
        onFailure = { Result.failure() }
    )
}
