package com.truyenaudio.app.core.network

import android.content.SharedPreferences
import com.truyenaudio.app.core.constants.AppConstants
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class SecureCookieJarTest {
    private val origin = AppConstants.BASE_URL.toHttpUrl()
    private fun session(value: String = "secret") = Cookie.parse(origin, "session=$value; Path=/; Max-Age=604800; HttpOnly; Secure; SameSite=Lax")!!

    @Test fun persistsAndRestoresSession() {
        val prefs = MemoryPreferences()
        SecureCookieJar(prefs).saveFromResponse(origin, listOf(session()))
        val restored = SecureCookieJar(prefs)
        assertTrue(restored.hasActiveSession())
        assertEquals("secret", restored.loadForRequest(origin).single().value)
    }
    @Test fun neverSendsToHttpSubdomainsOrUnrelatedHosts() {
        val jar = SecureCookieJar(MemoryPreferences())
        jar.saveFromResponse(origin, listOf(session()))
        assertTrue(jar.loadForRequest("http://${AppConstants.BACKEND_HOST}/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://sub.${AppConstants.BACKEND_HOST}/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://example.com/".toHttpUrl()).isEmpty())
    }
    @Test fun rejectsInsecureParentDomainAndForeignResponses() {
        val jar = SecureCookieJar(MemoryPreferences())
        val insecure = Cookie.Builder().name("session").value("x").hostOnlyDomain(AppConstants.BACKEND_HOST).build()
        val parent = Cookie.Builder().name("session").value("x").domain("workers.dev").secure().build()
        jar.saveFromResponse(origin, listOf(insecure, parent))
        jar.saveFromResponse("https://example.com/".toHttpUrl(), listOf(session()))
        assertFalse(jar.hasActiveSession())
    }
    @Test fun logoutBlocksLateResponsesUntilNextLogin() {
        val prefs = MemoryPreferences()
        val jar = SecureCookieJar(prefs)
        jar.saveFromResponse(origin, listOf(session()))
        jar.clearSession()
        jar.saveFromResponse(origin, listOf(session("late-response")))
        assertFalse(jar.hasActiveSession())
        assertFalse(SecureCookieJar(prefs).hasActiveSession())
        jar.beginLogin()
        jar.saveFromResponse(origin, listOf(session("new-session")))
        assertEquals("new-session", jar.loadForRequest(origin).single().value)
    }
    @Test fun serverCanDeleteSessionCookie() {
        val jar = SecureCookieJar(MemoryPreferences())
        jar.saveFromResponse(origin, listOf(session()))
        jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=; Max-Age=0; Path=/; HttpOnly; Secure")!!))
        assertFalse(jar.hasActiveSession())
    }
}

private class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = apply { values[key!!] = value }
        override fun putStringSet(key: String?, value: MutableSet<String>?) = apply { values[key!!] = value }
        override fun putInt(key: String?, value: Int) = apply { values[key!!] = value }
        override fun putLong(key: String?, value: Long) = apply { values[key!!] = value }
        override fun putFloat(key: String?, value: Float) = apply { values[key!!] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { values[key!!] = value }
        override fun remove(key: String?) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
}
