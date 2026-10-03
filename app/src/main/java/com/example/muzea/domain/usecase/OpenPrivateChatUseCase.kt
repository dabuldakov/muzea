package com.example.muzea.domain.usecase

import com.example.muzea.core.firstTerminal
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Открывает переписку с контактом: если приватный чат уже существует —
 * возвращает его, иначе создаёт новый. Так повторное нажатие на контакт не
 * плодит дубликаты чатов. Раньше эта логика жила в ContactViewModel.
 */
class OpenPrivateChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {

    suspend operator fun invoke(userUuid: String): NetworkResult<Chat> {
        val existing = try {
            chatRepository.findPrivateChatWith(userUuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (existing != null) return NetworkResult.Success(existing)
        return chatRepository.createPrivateChat(userUuid).firstTerminal()
    }
}
