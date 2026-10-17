package com.example.muzea.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.muzea.ui.MainActivity
import com.example.muzea.ui.auth.LoginActivity
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Промежуточная активность для системного меню «Поделиться» (ACTION_SEND).
 *
 * Она существует ради того, чтобы приложение появилось в шите share. Сама
 * ничего не показывает: вытаскивает текст и сразу передаёт его дальше —
 * на список чатов, если пользователь авторизован, иначе просто открывает
 * приложение (ссылка в этом случае теряется, как и решено по UX).
 */
@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = extractSharedText(intent)
        // Сессия нового образца: есть access+refresh. Иначе (legacy-токен без
        // refresh) ведём на экран входа — старая сессия в новом входе не валидна.
        val loggedIn = !tokenManager.getToken().isNullOrEmpty() &&
            !tokenManager.getAuthRefreshToken().isNullOrEmpty() &&
            !tokenManager.getUsername().isNullOrEmpty()

        val target = if (loggedIn && ConsentManager.isAccepted(this)) {
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_SHARED_TEXT, sharedText)
            }
        } else {
            Intent(this, LoginActivity::class.java)
        }

        startActivity(target)
        finish()
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
