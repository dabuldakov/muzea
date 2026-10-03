package com.example.muzea.core

import com.example.muzea.core.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Первый «терминальный» результат потока (Success/Error), без Loading.
 *
 * Репозитории отдают тройку Loading→Success/Error; там, где нужен один
 * результат (use-case, проверка), удобнее дождаться терминального значения.
 */
suspend fun <T> Flow<Resource<T>>.firstTerminal(): Resource<T> =
    try {
        first { it !is Resource.Loading }
    } catch (e: NoSuchElementException) {
        Resource.Error("Empty result")
    }
