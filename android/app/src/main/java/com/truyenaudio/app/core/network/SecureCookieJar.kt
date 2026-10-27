package com.truyenaudio.app.core.network

import android.content.SharedPreferences
import com.truyenaudio.app.core.constants.AppConstants
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray

class SecureCookieJar(private val preferences: SharedPreferences) : CookieJar {
    private val origin = AppConstants.BASE_URL.toHttpUrl()
    private val cookies = linkedMapOf<String, Cookie>()
    init {
        runCatching {
            val values = JSONArray(preferences.getString("cookies", "[]"))
            for (i in 0 until values.length()) {
                Cookie.parse(origin, values.getString(i))?.let {
                    if (eligible(it) && it.expiresAt > System.currentTimeMillis()) cookies[key(it)] = it
                }
            }
        }.onFailure { preferences.edit().remove("cookies").apply() }
    }
    private fun key(cookie: Cookie) = "${cookie.name}|${cookie.path}"
    private fun target(url: HttpUrl) = url.isHttps && url.host == AppConstants.BACKEND_HOST
    private fun eligible(cookie: Cookie) = cookie.secure && cookie.domain == AppConstants.BACKEND_HOST
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!target(url)) return
        for (cookie in cookies) {
            if (!eligible(cookie)) continue
            if (cookie.expiresAt <= System.currentTimeMillis()) this.cookies.remove(key(cookie))
            else this.cookies[key(cookie)] = cookie
        }
        persist()
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!target(url)) return emptyList()
        return cookies.values.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
    }
    @Synchronized fun hasActiveSession() = loadForRequest(origin).any { it.name == AppConstants.SESSION_COOKIE_NAME }
    @Synchronized fun clearSession() {
        cookies.clear()
        preferences.edit().remove("cookies").commit()
    }
    private fun persist() {
        val array = JSONArray()
        cookies.values.filter { it.persistent && it.expiresAt > System.currentTimeMillis() }.forEach { array.put(it.toString()) }
        preferences.edit().putString("cookies", array.toString()).commit()
    }
}
