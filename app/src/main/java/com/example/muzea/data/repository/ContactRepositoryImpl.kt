package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.Presence
import com.example.muzea.core.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.ContactRepository]. */
@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager
) : com.example.muzea.domain.repository.ContactRepository {

    private companion object {
        /**
         * Размер чанка batch-запроса статусов. Держим заметно ниже серверного
         * лимита (200), чтобы URL со списком UUID не разрастался до проблем
         * у прокси и серверов.
         */
        const val PRESENCE_BATCH_SIZE = 100
    }

    override suspend fun loadContacts(): Flow<Resource<List<Contact>>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            var response = apiService.getContacts()
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.getContacts()
                }
            }

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.map { it.toDomain() }))
            } else {
                emit(Resource.Error("Failed to load contacts: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun addContactByUsername(username: String): Flow<Resource<Contact>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            val userResponse = apiService.getUserByUsername(username)
            if (!userResponse.isSuccessful || userResponse.body() == null) {
                emit(Resource.Error("User not found: $username"))
                return@flow
            }

            val userUuid = userResponse.body()!!.userUuid
            val response = apiService.addContact(AddContactRequest(userUuid, null))

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to add contact: ${response.message()}"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    /**
     * Пакетный статус присутствия по UUID.
     *
     * Бэкенд ограничивает размер пачки, поэтому список режется на чанки и
     * ответы склеиваются: иначе запрос на 300 контактов упал бы с 400.
     * Пустой вход даёт пустой результат без обращения к сети.
     */
    override suspend fun loadPresence(userUuids: List<String>): Map<String, Presence> {
        val wanted = userUuids.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, Presence>()
        for (chunk in wanted.chunked(PRESENCE_BATCH_SIZE)) {
            try {
                var response = apiService.getPresence(chunk)
                if (response.code() == 401) {
                    chatAuthManager.invalidate()
                    if (!chatAuthManager.isAuthenticated()) return result
                    response = apiService.getPresence(chunk)
                }
                if (response.isSuccessful) {
                    response.body()?.forEach { result[it.userUuid] = it.toDomain() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Частичный результат лучше пустого: контакты, чей статус
                // пришёл, покажут реальное состояние, остальные — прошлый.
                Unit
            }
        }
        return result
    }
}
