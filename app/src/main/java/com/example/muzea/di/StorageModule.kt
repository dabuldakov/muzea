package com.example.muzea.di

import com.example.muzea.data.repository.ChatUserIdentity
import com.example.muzea.data.repository.TokenChatUserIdentity
import com.example.muzea.utils.ChatTokenStore
import com.example.muzea.utils.FcmTokenStore
import com.example.muzea.utils.TokenManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Связывает интерфейсы локального хранилища с единственной реализацией
 * [TokenManager], чтобы токен-хранилище было одним объектом на процесс.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindChatTokenStore(impl: TokenManager): ChatTokenStore

    @Binds
    @Singleton
    abstract fun bindFcmTokenStore(impl: TokenManager): FcmTokenStore

    @Binds
    @Singleton
    abstract fun bindChatUserIdentity(impl: TokenChatUserIdentity): ChatUserIdentity
}
