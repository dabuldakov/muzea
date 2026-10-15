package com.example.muzea.utils

import android.Manifest
import android.app.NotificationChannel
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
        // Имя отправителя важнее общего «Новое сообщение». Для групп добавляем
        // название чата, чтобы было понятно, куда пришло сообщение.
        val title = when {
            senderName != null && chatTitle != null -> "$senderName · $chatTitle"
            senderName != null -> senderName
            else -> fallbackTitle
        }

        showNotification(title, body)
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun showNotification(title: String, body: String) {
        val channelId = getString(R.string.chat_channel_id)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(channelId) == null
        ) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.chat_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.chat_channel_description) }
            manager.createNotificationChannel(channel)
        }

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
