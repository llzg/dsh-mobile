package com.labteto.dshmobile

import android.app.Application
import com.labteto.dshmobile.connection.KeepAliveWorker
import com.labteto.dshmobile.notify.NotificationObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DshApplication : Application() {

    // Injecting this constructs SessionStore + ConnectionManager and starts
    // their frame collectors; start() then begins notification classification.
    @Inject lateinit var notificationObserver: NotificationObserver

    override fun onCreate() {
        super.onCreate()
        KeepAliveWorker.schedule(this)
        notificationObserver.start()
    }
}
