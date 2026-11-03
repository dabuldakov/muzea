package com.example.muzea

import android.app.Application
import com.example.muzea.utils.NotificationChannels
import com.example.muzea.utils.PushTokenRegistrar
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MuzeaApplication : Application() {

    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAll(this)
        pushTokenRegistrar.sync()
    }
}
