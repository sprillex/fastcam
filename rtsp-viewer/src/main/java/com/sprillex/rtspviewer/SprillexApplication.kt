package com.sprillex.rtspviewer

import android.app.Application

class SprillexApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCrashHandler.install(this)
    }
}
