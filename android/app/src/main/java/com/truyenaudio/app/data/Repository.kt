package com.truyenaudio.app.data

import com.truyenaudio.app.core.constants.AppConstants
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface BackendApi {
    @POST("api/auth/login") suspend fun login(@Body body: LoginBody): UserResponse
    @GET("api/auth/me") suspend fun me(): UserResponse
    @POST("api/auth/logout") suspend fun logout(@Body body: Map<String, String> = emptyMap()): Map<String, Boolean>
    @GET("api/stories") suspend fun stories(@Query("page") page: Int): StoriesResponse
    @GET("api/stories/{slug}") suspend fun detail(@Path("slug") slug: String): StoryDetail
    @GET("api/stories/{slug}/chapters/chuong_{number}") suspend fun chapter(@Path("slug") slug: String, @Path("number") number: Int): ChapterContentResponse
}

class ApiError(val status: Int, message: String) : IOException(message)

class Repository(val store: SecureStore) {
    val sessionVersion = MutableStateFlow(0)
    @Volatile private var epoch = 0
    private val client = OkHttpClient.Builder().cookieJar(store.cookies)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val version = epoch
            val response = chain.proceed(chain.request())
            if (version != epoch) { response.close(); throw IOException("Phiên đã thay đổi") }
            response
        }.build()
    private val api = Retrofit.Builder().baseUrl(AppConstants.BASE_URL).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(BackendApi::class.java)

    @Synchronized fun clearSession() {
        epoch++
        client.dispatcher.cancelAll()
        store.clear()
        sessionVersion.value = epoch
    }
    private suspend fun <T> request(protected: Boolean = true, block: suspend () -> T): T {
        try { return block() } catch (error: HttpException) {
            val message = runCatching { JSONObject(error.response()?.errorBody()?.string().orEmpty()).optString("error") }.getOrDefault("")
            if (error.code() == 401 && protected) clearSession()
            throw ApiError(error.code(), message.ifBlank {
                when (error.code()) {
                    400 -> "Dữ liệu không hợp lệ"
                    401 -> "Phiên đã hết hạn. Vui lòng đăng nhập lại."
                    404 -> "Không tìm thấy truyện hoặc chương"
                    429 -> "Bạn thử quá nhiều lần. Vui lòng chờ rồi đăng nhập lại."
                    else -> "Máy chủ gặp lỗi (${error.code()})"
                }
            })
        } catch (error: IOException) {
            throw IOException("Không kết nối được máy chủ. Kiểm tra mạng và thử lại.", error)
        }
    }
    suspend fun login(username: String, password: String): User = request(false) {
        val user = api.login(LoginBody(username, password)).user ?: throw IOException("Phản hồi đăng nhập không hợp lệ")
        if (!store.cookies.hasActiveSession()) throw IOException("Máy chủ không cấp cookie phiên hợp lệ")
        user
    }
    suspend fun me(): User? = request { api.me().user }.also { if (it == null) clearSession() }
    suspend fun logout() { try { request { api.logout() } } finally { clearSession() } }
    suspend fun stories(page: Int) = request { api.stories(page) }
    suspend fun detail(slug: String) = request { api.detail(slug) }
    suspend fun chapter(slug: String, number: Int) = request { api.chapter(slug, number) }
}
