package io.github.hebadenys.fitnesshub.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiCloudRuntime
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiSourceGateway
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiSourceRepository
import javax.inject.Singleton

/** One runtime/session store per installation, shared by all source screens. Gate is not overridden. */
@Module
@InstallIn(SingletonComponent::class)
internal object XiaomiModule {
    @Provides @Singleton fun runtime(@ApplicationContext context: Context, database: HealthDatabase): XiaomiCloudRuntime =
        XiaomiCloudRuntime.create(context, database)

    @Provides @Singleton fun sources(database: HealthDatabase, runtime: XiaomiCloudRuntime): XiaomiSourceGateway =
        XiaomiSourceRepository(database, runtime.client, runtime.archive)
}
