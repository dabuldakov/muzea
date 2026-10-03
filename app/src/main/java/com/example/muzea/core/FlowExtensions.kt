package com.example.muzea.core

import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Первый «терминальный» результат потока (Success/Error), без Loading.
 *
 * Репозитории отдают тройку Loading→Success/Error; там, где нужен один
 * результат (use-case, проверка), удобнее дождаться терминального значения.
 */
suspend fun <T> Flow<NetworkResult<T>>.firstTerminal(): NetworkResult<T> =
    try {
        first { it !is NetworkResult.Loading }
    } catch (e: NoSuchElementException) {
        NetworkResult.Error("Empty result")
    }
