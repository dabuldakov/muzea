package com.example.muzea

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.muzea.utils.PushTokenRegistrar

class MuzeaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        createNotificationChannel()
        PushTokenRegistrar.sync(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.chat_channel_id),
                getString(R.string.chat_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.chat_channel_description) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
