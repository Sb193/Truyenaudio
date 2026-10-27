package com.truyenaudio.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.truyenaudio.app.core.constants.AppConstants
import com.truyenaudio.app.data.Repository
import com.truyenaudio.app.data.SecureStore
import com.truyenaudio.app.data.LocalLibrary
import com.truyenaudio.app.data.ContentRouter

class TruyenAudioApp : Application() {
    val repository: Repository by lazy { Repository(SecureStore(this)) }
    val localLibrary: LocalLibrary by lazy { LocalLibrary(this) }
    val content: ContentRouter by lazy { ContentRouter(localLibrary) { repository } }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(AppConstants.CHANNEL, "Nghe truyện", NotificationManager.IMPORTANCE_LOW))
    }
}
