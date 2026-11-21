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

/**
 * HTTP-клиент для presigned PUT в объектное хранилище (s3.muzea.su).
 * Без интерцепторов авторизации: URL уже подписан, лишний Authorization
 * ломает проверку SigV4.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class S3UploadClient

/** Долгоживущий scope для очереди исходящих сообщений. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OutboxScope
