package com.example.muzea.data.repository

import android.util.Base64
import com.example.muzea.utils.TokenManager
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [ChatUserIdentity] на основе chat-токена из [TokenManager]. */
@Singleton
class TokenChatUserIdentity @Inject constructor(
    private val tokenManager: TokenManager
) : ChatUserIdentity {

    override val userUuid: String?
        get() {
            val token = tokenManager.getChatToken() ?: return null
            return try {
                val parts = token.split(".")
                if (parts.size < 2) return null
                val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)
                JSONObject(String(decoded, Charsets.UTF_8)).getString("sub")
            } catch (e: Exception) {
                null
            }
        }
}
