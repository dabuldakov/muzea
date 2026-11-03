package com.example.muzea.utils

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.muzea.R
import com.example.muzea.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MuzeaMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    override fun onNewToken(token: String) {
        pushTokenRegistrar.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "MESSAGE" -> handleChatMessage(data, message)
            "NEWS" -> handleNews(data, message)
            "BROADCAST" -> handleBroadcast(data, message)
            // Служебные события (печатает, прочитано, звонок) не превращаем
            // в уведомления.
            else -> Unit
        }
    }

    private fun handleChatMessage(data: Map<String, String>, message: RemoteMessage) {
        val chatUuid = firstNonBlank(data["chatUuid"], data["chat_uuid"])
        // Пользователь уже смотрит этот чат — сообщение и так появится в ленте.
        if (chatUuid != null && ActiveChatTracker.chatUuid == chatUuid) return

        val senderName = firstNonBlank(
            data["senderName"], data["sender_name"], data["sender"],
            data["from"], data["nickname"], data["username"]
        )
        val chatTitle = firstNonBlank(
            data["chatTitle"], data["chat_title"], data["chatName"], data["groupName"]
        )
        val body = firstNonBlank(
            data["messageText"], data["body"], data["text"], data["message"],
            message.notification?.body
        ).orEmpty()

        val fallbackTitle = message.notification?.title
            ?: data["title"]
            ?: getString(R.string.chat_channel_name)
        val title = when {
            senderName != null && chatTitle != null -> "$senderName · $chatTitle"
            senderName != null -> senderName
            else -> fallbackTitle
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            // SINGLE_TOP: если MainActivity уже на вершине задачи, она получит
            // onNewIntent, а не будет пересоздана (иначе deep link теряется).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!chatUuid.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_OPEN_CHAT_UUID, chatUuid)
                putExtra(MainActivity.EXTRA_OPEN_CHAT_TITLE, chatTitle ?: senderName)
                data["chatAvatar"]?.let { putExtra(MainActivity.EXTRA_OPEN_CHAT_AVATAR, it) }
                data["messageUuid"]?.let { putExtra(MainActivity.EXTRA_OPEN_MESSAGE_UUID, it) }
            }
        }

        val tag = chatUuid?.let { "chat_$it" } ?: "chat"
        val id = chatUuid?.hashCode() ?: 1
        showNotification(
            channelId = getString(R.string.chat_channel_id),
            title = title,
            body = body,
            tag = tag,
            id = id,
            intent = intent
        )
    }

    private fun handleNews(data: Map<String, String>, message: RemoteMessage) {
        val newsId = firstNonBlank(data["newsId"], data["news_id"])?.toLongOrNull()
        val title = firstNonBlank(data["title"], message.notification?.title)
            ?: getString(R.string.news_channel_name)
        val body = firstNonBlank(
            data["body"], data["text"], data["message"], message.notification?.body
        ).orEmpty()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            newsId?.let { putExtra(MainActivity.EXTRA_OPEN_NEWS_ID, it) }
        }
        val tag = newsId?.let { "news_$it" } ?: "news"
        showNotification(
            channelId = getString(R.string.news_channel_id),
            title = title,
            body = body,
            tag = tag,
            id = newsId?.hashCode() ?: 2,
            intent = intent
        )
    }

    private fun handleBroadcast(data: Map<String, String>, message: RemoteMessage) {
        val title = firstNonBlank(data["title"], message.notification?.title)
            ?: getString(R.string.chat_channel_name)
        val body = firstNonBlank(
            data["body"], data["text"], data["message"], message.notification?.body
        ).orEmpty()
        showNotification(
            channelId = getString(R.string.chat_channel_id),
            title = title,
            body = body,
            tag = "broadcast",
            id = 3,
            intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun showNotification(
        channelId: String,
        title: String,
        body: String,
        tag: String,
        id: Int,
        intent: Intent
    ) {
        NotificationChannels.ensureAll(this)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        val pendingIntent = PendingIntent.getActivity(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("muzea")
            .setContentIntent(pendingIntent)
            .build()

        // Один активный нотификейшн на чат/новость: новый заменяет старый.
        manager.notify(tag, id, notification)
    }
}
