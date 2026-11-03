package com.example.muzea.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.muzea.R

/**
 * Каналы уведомлений.
 *
 * Каналы создаются при старте приложения, а не только при показе уведомления:
 * когда приложение в фоне, уведомление рисует сама система по данным FCM, и
 * если канала ещё нет — push просто пропадёт.
 */
object NotificationChannels {

    fun ensureAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        create(
            manager,
            context.getString(R.string.chat_channel_id),
            context.getString(R.string.chat_channel_name),
            context.getString(R.string.chat_channel_description)
        )
        create(
            manager,
            context.getString(R.string.news_channel_id),
            context.getString(R.string.news_channel_name),
            context.getString(R.string.news_channel_description)
        )
    }

    private fun create(
        manager: NotificationManager,
        id: String,
        name: String,
        description: String
    ) {
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
            .apply { this.description = description }
        manager.createNotificationChannel(channel)
    }
}
