package com.example.muzea.di

import android.content.Context
import com.example.muzea.utils.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Хранилище токенов вынесено в отдельный модуль, чтобы тесты экранов могли
 * подменить [TokenManager] через `@UninstallModules` без отключения сети.
 *
 * [StorageModule] связывает этот же экземпляр с интерфейсами чата и FCM.
 */
@Module
@InstallIn(SingletonComponent::class)
object TokenModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager =
        TokenManager(context)
}
