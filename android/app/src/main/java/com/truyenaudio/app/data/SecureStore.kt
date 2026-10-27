package com.truyenaudio.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.truyenaudio.app.core.network.SecureCookieJar

class SecureStore(context: Context) {
    private val gson = Gson()
    val preferences = EncryptedSharedPreferences.create(
        context, "private_session", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    val cookies = SecureCookieJar(preferences)
    @Synchronized fun progress(): Progress? = runCatching {
        preferences.getString("progress", null)?.let { gson.fromJson(it, Progress::class.java) }
    }.getOrNull()
    @Synchronized fun save(progress: Progress) {
        preferences.edit().putString("progress", gson.toJson(progress)).apply()
    }
    @Synchronized fun clear() {
        cookies.clearSession()
        preferences.edit().clear().commit()
    }
}
