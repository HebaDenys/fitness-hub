package io.github.hebadenys.fitnesshub
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import io.github.hebadenys.fitnesshub.core.sync.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp class FitnessHubApp: Application(),Configuration.Provider{
 @Inject lateinit var workerFactory:HiltWorkerFactory
 override val workManagerConfiguration:Configuration get()=Configuration.Builder().setWorkerFactory(workerFactory).build()
 override fun onCreate(){super.onCreate();WorkManager.getInstance(this).enqueueUniquePeriodicWork("health-sync",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<SyncWorker>(1,TimeUnit.DAYS).build())}
}
