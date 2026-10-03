package com.example.muzea

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.muzea.utils.PushTokenRegistrar
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MuzeaApplication : Application() {

    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        pushTokenRegistrar.sync()
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
