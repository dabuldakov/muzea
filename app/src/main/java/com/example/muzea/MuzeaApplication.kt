package com.example.muzea

import android.app.Application

class MuzeaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}