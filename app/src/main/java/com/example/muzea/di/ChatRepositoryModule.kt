package com.example.muzea.di

import com.example.muzea.data.repository.AvatarRepositoryImpl
import com.example.muzea.data.repository.ChatRepositoryImpl
import com.example.muzea.data.repository.ChatSessionRepositoryImpl
import com.example.muzea.data.repository.ContactRepositoryImpl
import com.example.muzea.data.repository.MessageRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Привязка доменных контрактов чата к реализациям data-слоя. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: ChatRepositoryImpl
    ): com.example.muzea.domain.repository.ChatRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        impl: MessageRepositoryImpl
    ): com.example.muzea.domain.repository.MessageRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(
        impl: ContactRepositoryImpl
    ): com.example.muzea.domain.repository.ContactRepository

    @Binds
    @Singleton
    abstract fun bindAvatarRepository(
        impl: AvatarRepositoryImpl
    ): com.example.muzea.domain.repository.AvatarRepository

    @Binds
    @Singleton
    abstract fun bindChatSessionRepository(
        impl: ChatSessionRepositoryImpl
    ): com.example.muzea.domain.repository.ChatSessionRepository
}
