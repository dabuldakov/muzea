package com.example.muzea.utils

import android.content.Intent
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.example.muzea.ui.MainActivity

/**
 * Мост для перехода из системного FCM-уведомления.
 *
 * Launcher-activity приложения — [com.example.muzea.ui.auth.LoginActivity].
 * Когда пользователь тапает notification+data, которые рисует система, Android
 * запускает именно её и кладёт data в extras. LoginActivity/OtpActivity
 * передают эти extras в MainActivity; иначе переход «в чат/новость» теряется.
 */
object PendingPush {

    private const val TTL_MS = 5 * 60_000L

    private var extras: Bundle? = null
    private var capturedAt = 0L

    fun capture(intent: Intent) {
        val source = intent.extras ?: return
        val isPush = source.containsKey("chatUuid") ||
            source.containsKey("chat_uuid") ||
            source.containsKey("newsId") ||
            source.containsKey(MainActivity.EXTRA_OPEN_CHAT_UUID) ||
            source.containsKey(MainActivity.EXTRA_OPEN_NEWS_ID)
        if (isPush) {
            extras = Bundle(source)
            capturedAt = System.currentTimeMillis()
        }
    }

    fun applyTo(intent: Intent) {
        val pending = extras
        extras = null
        if (pending != null && System.currentTimeMillis() - capturedAt <= TTL_MS) {
            intent.putExtras(pending)
        }
    }

    @VisibleForTesting
    fun clear() {
        extras = null
        capturedAt = 0L
    }
}
