package io.github.hebadenys.fitnesshub.di
import android.content.Context
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.core.sync.HealthSyncRepository
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
