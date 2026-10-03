package com.example.muzea.di

import javax.inject.Qualifier

/** HTTP-клиент основного API (api-muzea). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainHttpClient

/** HTTP-клиент чат-сервера (chat-muzea). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatHttpClient
