package com.openhealthhub.app.di
import android.content.Context
import com.openhealthhub.app.core.database.HealthDatabase
import com.openhealthhub.app.core.healthconnect.HealthConnectManager
import com.openhealthhub.app.core.sync.HealthSyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
 @Provides @Singleton fun database(@ApplicationContext context:Context)=HealthDatabase.create(context)
 @Provides fun dao(db:HealthDatabase)=db.healthDao()
 @Provides @Singleton fun health(@ApplicationContext context:Context)=HealthConnectManager(context)
 @Provides @Singleton fun repository(health:HealthConnectManager,db:HealthDatabase)=HealthSyncRepository(health,db.healthDao())
}
