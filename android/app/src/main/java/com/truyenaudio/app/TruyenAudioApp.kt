package com.truyenaudio.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.truyenaudio.app.core.constants.AppConstants
import com.truyenaudio.app.data.Repository
import com.truyenaudio.app.data.SecureStore

class TruyenAudioApp : Application() {
    lateinit var repository: Repository
        private set
    override fun onCreate() {
        super.onCreate()
        repository = Repository(SecureStore(this))
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(AppConstants.CHANNEL, "Nghe truyện", NotificationManager.IMPORTANCE_LOW))
    }
}
