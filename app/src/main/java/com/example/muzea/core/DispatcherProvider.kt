package com.example.muzea.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Абстракция над диспетчерами корутин.
 *
 * Репозитории не должны жёстко зависеть от [Dispatchers.IO] — тогда в тестах
 * можно подставить тестовый диспетчер и проверять асинхронный код без реальных
 * потоков.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}
