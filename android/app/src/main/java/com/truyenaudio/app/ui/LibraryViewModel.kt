package com.truyenaudio.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.truyenaudio.app.TruyenAudioApp
import com.truyenaudio.app.core.tts.TtsPlaybackService
import com.truyenaudio.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LibraryState(
    val checking: Boolean = true, val user: User? = null, val busy: Boolean = false,
    val error: String = "", val stories: List<Story> = emptyList(), val page: Int = 0,
    val more: Boolean = true, val detail: StoryDetail? = null,
    val chapter: ChapterContentResponse? = null, val progress: Progress? = null,
    val sessionNetworkError: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as TruyenAudioApp).repository
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    init {
        viewModelScope.launch { repository.sessionVersion.drop(1).collect {
            operation?.cancel()
            application.stopService(Intent(application, TtsPlaybackService::class.java))
            com.truyenaudio.app.core.tts.PlaybackStore.mutable.value = com.truyenaudio.app.core.tts.PlaybackSnapshot()
            mutable.value = LibraryState(checking = false, error = "Vui lòng đăng nhập để tiếp tục")
        } }
        checkSession()
    }
    private fun execute(block: suspend () -> Unit) {
        operation?.cancel()
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = "") }
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.update { it.copy(error = error.message ?: "Không thể thực hiện yêu cầu") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun checkSession() = execute {
        mutable.update { it.copy(checking = true, sessionNetworkError = false) }
        try {
            val user = repository.me()
            mutable.update { it.copy(checking = false, user = user, progress = repository.store.progress()) }
            if (user != null) loadPage(true)
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            mutable.update { it.copy(checking = false, sessionNetworkError = true, error = error.message ?: "Không kiểm tra được phiên") }
        }
    }
    fun login(username: String, password: String, done: () -> Unit) {
        if (state.value.busy) return
        execute {
            val user = repository.login(username.trim(), password)
            done()
            mutable.update { it.copy(user = user, checking = false, sessionNetworkError = false) }
            loadPage(true)
        }
    }
    private suspend fun loadPage(refresh: Boolean) {
        val page = if (refresh) 1 else state.value.page + 1
        val response = repository.stories(page)
        mutable.update { it.copy(stories = if (refresh) response.data else (it.stories + response.data).distinctBy { story -> story.slug },
            page = page, more = response.data.size >= 24, progress = repository.store.progress()) }
    }
    fun refresh() = execute { loadPage(true) }
    fun more() { if (!state.value.busy && state.value.more) execute { loadPage(false) } }
    fun openStory(slug: String) = execute {
        val detail = repository.detail(slug)
        mutable.update { it.copy(detail = detail, chapter = null) }
    }
    fun openChapter(number: Int) = execute {
        val detail = state.value.detail ?: return@execute
        val chapter = repository.chapter(detail.story.slug, number)
        mutable.update { it.copy(chapter = chapter) }
    }
    fun back() {
        operation?.cancel()
        mutable.update { if (it.chapter != null) it.copy(chapter = null, busy = false, error = "")
            else it.copy(detail = null, busy = false, error = "", progress = repository.store.progress()) }
    }
    fun reloadProgress() { mutable.update { it.copy(progress = repository.store.progress()) } }
    fun logout() {
        getApplication<Application>().stopService(Intent(getApplication(), TtsPlaybackService::class.java))
        execute {
            try { repository.logout() } finally { mutable.value = LibraryState(checking = false) }
        }
    }
}
