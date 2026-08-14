package com.labteto.dshmobile

import android.app.Application
import com.labteto.dshmobile.connection.KeepAliveWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KeepAliveWorker.schedule(this)
    }
}
