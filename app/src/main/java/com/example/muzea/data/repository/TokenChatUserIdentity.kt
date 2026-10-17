package com.example.muzea.data.repository

import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.utils.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [ChatUserIdentity] на основе локального chat-профиля.
 *
 * Единый access-токен принадлежит IdP, поэтому его `sub` — это UUID auth-service,
 * а не chat-пользователя. Chat userUuid кэшируется в [TokenManager] при первом
 * обращении к chat-серверу ([ChatAuthManager.ensureChatProfile]).
 */
@Singleton
class TokenChatUserIdentity @Inject constructor(
    private val tokenManager: TokenManager
) : ChatUserIdentity {

    override val userUuid: String?
        get() = tokenManager.getChatUserUuid()
}
