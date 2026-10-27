package com.truyenaudio.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiContractTest {
    @Test fun loginUsesJsonAndDoesNotExpectAccessToken() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"user":{"id":"u1","username":"reader"}}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(BackendApi::class.java)
            assertEquals("reader", api.login(LoginBody("reader", "test-password")).user?.username)
            val request = server.takeRequest()
            assertEquals("/api/auth/login", request.path)
            assertEquals("POST", request.method)
            assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
            assertNull(request.getHeader("Origin"))
            assertEquals("""{"username":"reader","password":"test-password"}""", request.body.readUtf8())
            server.enqueue(MockResponse().setBody("""{"user":null}"""))
            assertNull(api.me().user)
            assertEquals("/api/auth/me", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            api.logout()
            val logout = server.takeRequest()
            assertEquals("/api/auth/logout", logout.path)
            assertEquals("{}", logout.body.readUtf8())
        } finally { server.shutdown() }
    }
    @Test fun chapterUsesNumberFromContractAndCreatedAtCanBeNumeric() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(BackendApi::class.java)
            server.enqueue(MockResponse().setBody("""{"number":7,"title":"Chương bảy","story_title":"Truyện","slug":"truyen-a","chapter_count":9,"content":"Nội dung"}"""))
            assertEquals(7, api.chapter("truyen-a", 7).number)
            assertEquals("/api/stories/truyen-a/chapters/chuong_7", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"data":[{"id":1,"slug":"a","title":"A","description":"","chapter_count":1,"created_at":123}],"page":2}"""))
            assertTrue(api.stories(2).data.first().createdAt!!.isJsonPrimitive)
            assertEquals("/api/stories?page=2", server.takeRequest().path)
        } finally { server.shutdown() }
    }
}
