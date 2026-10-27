package com.truyenaudio.app.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.truyenaudio.app.core.constants.AppConstants
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class SecureCookieJar(context: Context) : CookieJar {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "truyen_audio_secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val cookieStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    init {
        loadCookiesFromStorage()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!isTargetHost(url)) return
        val host = url.host
        val currentHostCookies = cookieStore.getOrPut(host) { mutableMapOf() }
        var changed = false
        val now = System.currentTimeMillis()

        for (cookie in cookies) {
            if (cookie.expiresAt <= now) {
                if (currentHostCookies.remove(cookie.name) != null) changed = true
            } else {
                currentHostCookies[cookie.name] = cookie
                changed = true
            }
        }
        if (changed) persistHostCookies(host, currentHostCookies.values)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!isTargetHost(url)) return emptyList()
        val host = url.host
        val hostCookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val validCookies = mutableListOf<Cookie>()
        for (cookie in hostCookies.values) {
            if (cookie.expiresAt > now && cookie.matches(url)) {
                validCookies.add(cookie)
            }
        }
        return validCookies
    }

    fun hasActiveSession(): Boolean {
        val hostCookies = cookieStore[AppConstants.BACKEND_HOST] ?: return false
        val sessionCookie = hostCookies[AppConstants.SESSION_COOKIE_NAME] ?: return false
        return sessionCookie.expiresAt > System.currentTimeMillis()
    }

    @Synchronized
    fun clearSession() {
        cookieStore.clear()
        encryptedPrefs.edit().clear().apply()
    }

    private fun isTargetHost(url: HttpUrl): Boolean =
        url.isHttps && url.host.equals(AppConstants.BACKEND_HOST, ignoreCase = true)

    private fun loadCookiesFromStorage() { /* Deserializes from EncryptedSharedPreferences */ }
    private fun persistHostCookies(host: String, cookies: Collection<Cookie>) { /* Encrypted save */ }
}