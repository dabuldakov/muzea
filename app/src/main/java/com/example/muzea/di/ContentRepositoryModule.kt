package com.example.muzea.di

import com.example.muzea.data.repository.NewsRepositoryImpl
import com.example.muzea.data.repository.UserRepositoryImpl
import com.example.muzea.data.repository.VideoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Привязка доменных контрактов новостей/видео к реализациям data-слоя. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContentRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNewsRepository(
        impl: NewsRepositoryImpl
    ): com.example.muzea.domain.repository.NewsRepository

    @Binds
    @Singleton
    abstract fun bindVideoRepository(
        impl: VideoRepositoryImpl
    ): com.example.muzea.domain.repository.VideoRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): com.example.muzea.domain.repository.UserRepository
}
