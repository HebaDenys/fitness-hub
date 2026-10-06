package io.github.hebadenys.fitnesshub.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.hebadenys.fitnesshub.core.ai.AiKeyStore
import io.github.hebadenys.fitnesshub.core.ai.AiSettingsStore
import io.github.hebadenys.fitnesshub.core.ai.AiTransport
import io.github.hebadenys.fitnesshub.core.analytics.InsightsRepository
import io.github.hebadenys.fitnesshub.core.backup.BackupService
import io.github.hebadenys.fitnesshub.core.database.HealthDao
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthDataSource
import io.github.hebadenys.fitnesshub.core.nutrition.FoodCatalogConnector
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionDao
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionPreferences
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionRepository
import io.github.hebadenys.fitnesshub.core.nutrition.OpenFoodFactsConnector
import io.github.hebadenys.fitnesshub.core.scale.BindkeyStore
import io.github.hebadenys.fitnesshub.core.scale.S400ScaleConnector
import io.github.hebadenys.fitnesshub.core.scale.ScaleHistoryCsvImporter
import io.github.hebadenys.fitnesshub.core.scale.ScaleDao
import io.github.hebadenys.fitnesshub.core.sync.AppLogger
import io.github.hebadenys.fitnesshub.core.sync.DataStoreSyncTokenStore
import io.github.hebadenys.fitnesshub.core.workout.WorkoutDao
import io.github.hebadenys.fitnesshub.core.workout.WorkoutRepository
import io.github.hebadenys.fitnesshub.core.sync.HealthSyncRepository
import io.github.hebadenys.fitnesshub.core.sync.SyncEnvironment
import io.github.hebadenys.fitnesshub.core.sync.SyncTokenStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context) = HealthDatabase.create(context)
    @Provides fun dao(db: HealthDatabase): HealthDao = db.healthDao()
    @Provides @Singleton fun health(@ApplicationContext context: Context) = HealthConnectManager(context)
    @Provides @Singleton fun dataSource(health: HealthConnectManager): HealthDataSource = health
    @Provides @Singleton fun tokenStore(@ApplicationContext context: Context): SyncTokenStore = DataStoreSyncTokenStore(context)
    @Provides @Singleton fun logger(): AppLogger = AppLogger()
    @Provides @Singleton fun environment(health: HealthConnectManager, tokens: SyncTokenStore, logger: AppLogger) = SyncEnvironment(health, tokens, logger)
    @Provides @Singleton fun repository(environment: SyncEnvironment, dao: HealthDao) = HealthSyncRepository(environment, dao)

    @Provides @Singleton fun nutritionPreferences(@ApplicationContext context: Context) = NutritionPreferences(context)
    @Provides fun nutritionDao(db: HealthDatabase): NutritionDao = db.nutritionDao()
    @Provides @Singleton fun foodCatalog(preferences: NutritionPreferences): FoodCatalogConnector =
        OpenFoodFactsConnector(enabledProvider = { preferences.isCatalogEnabled() })
    @Provides @Singleton fun nutrition(dao: NutritionDao, catalog: FoodCatalogConnector) = NutritionRepository(dao, catalog)

    @Provides fun scaleDao(db: HealthDatabase): ScaleDao = db.scaleDao()
    @Provides @Singleton fun bindkeyStore(@ApplicationContext context: Context) = BindkeyStore(context)
    @Provides @Singleton fun scaleConnector(dao: ScaleDao, bindkeyStore: BindkeyStore) = S400ScaleConnector(dao, bindkeyStore)
    @Provides @Singleton fun scaleHistoryImporter(dao: ScaleDao) = ScaleHistoryCsvImporter(dao)

    @Provides fun workoutDao(db: HealthDatabase): WorkoutDao = db.workoutDao()
    @Provides @Singleton fun workoutRepository(dao: WorkoutDao) = WorkoutRepository(dao)

    @Provides @Singleton fun insights(healthDao: HealthDao, nutritionDao: NutritionDao, workoutDao: WorkoutDao) =
        InsightsRepository(healthDao, nutritionDao, workoutDao)

    @Provides @Singleton fun backupService(db: HealthDatabase) = BackupService(db)

    @Provides @Singleton fun aiSettingsStore(@ApplicationContext context: Context) = AiSettingsStore(context)
    @Provides @Singleton fun aiKeyStore(@ApplicationContext context: Context) = AiKeyStore(context)
    @Provides @Singleton fun aiTransport() = AiTransport()
}
