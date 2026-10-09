package com.raviga.app

import android.app.Application
import com.raviga.app.di.AppContainer
import com.raviga.app.push.Notifications

class RavigaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
    }
}
