package com.raviga.downwork

import android.app.Application
import com.raviga.downwork.di.AppContainer
import com.raviga.downwork.push.Notifications

class DownWorkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
    }
}
