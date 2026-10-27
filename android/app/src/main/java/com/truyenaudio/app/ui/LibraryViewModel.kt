package com.truyenaudio.app.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.truyenaudio.app.TruyenAudioApp
import com.truyenaudio.app.core.tts.*
import com.truyenaudio.app.data.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DownloadUi(val id: UUID, val slug: String, val title: String, val done: Int, val total: Int,
    val active: Boolean, val waiting: Boolean, val error: String)

data class LibraryState(
    val checking: Boolean = false, val user: User? = null, val busy: Boolean = false,
    val error: String = "", val stories: List<Story> = emptyList(), val page: Int = 0,
    val more: Boolean = false, val detail: StoryDetail? = null,
    val chapter: ChapterContentResponse? = null, val progress: Progress? = null,
    val sessionNetworkError: Boolean = false, val source: ContentSource = ContentSource.LOCAL,
    val downloads: List<DownloadUi> = emptyList(), val localCounts: Map<String, Int> = emptyMap()
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TruyenAudioApp
    private val repository get() = app.repository
    private val local get() = app.localLibrary
    private val work = WorkManager.getInstance(application)
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var operationVersion = 0
    private var observingServer = false
    init {
        // No /auth/me, cookie store, or HTTP client is touched when the local home opens.
        viewModelScope.launch {
            local.changes.collectLatest {
                try {
                    val stories = local.stories()
                    val counts = stories.associate { it.slug to it.chapterCount }
                    val current = state.value
                    val detail = if (current.source == ContentSource.LOCAL && current.detail != null && counts.containsKey(current.detail.story.slug))
                        local.detail(current.detail.story.slug) else current.detail
                    mutable.update { value -> if (value.source == ContentSource.LOCAL)
                        value.copy(stories = stories, localCounts = counts, detail = detail, progress = local.progress())
                        else value.copy(localCounts = counts) }
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { mutable.update { it.copy(error = "Không mở được thư viện trên máy. Kiểm tra dung lượng bộ nhớ.") } }
            }
        }
        viewModelScope.launch {
            work.getWorkInfosByTagFlow(StoryDownloadWorker.TAG).collect { entries ->
                val downloads = entries.map { info ->
                    val data = if (info.state.isFinished) info.outputData else info.progress
                    DownloadUi(info.id, info.tags.firstOrNull { it.startsWith("slug:") }?.removePrefix("slug:").orEmpty(),
                        data.getString("title").orEmpty(), data.getInt("done", 0), data.getInt("total", 0),
                        !info.state.isFinished, info.state == WorkInfo.State.ENQUEUED,
                        if (info.state == WorkInfo.State.FAILED) data.getString("message") ?: "Không tải được truyện" else "")
                }
                mutable.update { it.copy(downloads = downloads) }
            }
        }
    }
    private fun observeServer() {
        if (observingServer) return
        observingServer = true
        viewModelScope.launch { repository.sessionVersion.drop(1).collect {
            work.cancelAllWorkByTag(StoryDownloadWorker.TAG)
            if (PlaybackStore.state.value.plan?.source == ContentSource.SERVER) {
                app.stopService(Intent(app, TtsPlaybackService::class.java))
                PlaybackStore.mutable.value = PlaybackSnapshot()
            }
            if (state.value.source == ContentSource.SERVER) {
                operation?.cancel()
                mutable.update { it.copy(checking = false, busy = false, user = null, detail = null, chapter = null,
                    stories = emptyList(), progress = null, error = "Vui lòng đăng nhập để tải thêm truyện") }
            } else mutable.update { it.copy(user = null) }
        } }
    }
    private fun execute(block: suspend () -> Unit) {
        operationVersion++
        val version = operationVersion
        operation?.cancel()
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = "") }
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.update { it.copy(error = error.message ?: "Không thể thực hiện yêu cầu") } }
            finally { if (version == operationVersion) mutable.update { it.copy(busy = false) } }
        }
    }
    fun showLocal() {
        operation?.cancel()
        if (PlaybackStore.state.value.plan?.source == ContentSource.SERVER) {
            app.stopService(Intent(app, TtsPlaybackService::class.java))
            PlaybackStore.mutable.value = PlaybackSnapshot()
        }
        mutable.update { it.copy(source = ContentSource.LOCAL, checking = false, sessionNetworkError = false, busy = false,
            detail = null, chapter = null, stories = emptyList(), more = false, progress = local.progress(), error = "") }
        refresh()
    }
    fun enterServer() {
        if (state.value.source == ContentSource.SERVER) return
        mutable.update { it.copy(source = ContentSource.SERVER, checking = true, stories = emptyList(), detail = null,
            chapter = null, progress = null, page = 0, more = true, error = "") }
        observeServer()
        checkSession()
    }
    fun checkSession() = execute {
        if (state.value.source == ContentSource.LOCAL) return@execute
        mutable.update { it.copy(checking = true, sessionNetworkError = false) }
        try {
            val user = repository.me()
            mutable.update { it.copy(checking = false, user = user, progress = repository.store.progress()) }
            if (user != null) loadPage(true)
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { mutable.update { it.copy(checking = false, sessionNetworkError = true, error = error.message ?: "Không kiểm tra được phiên") } }
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
    fun refresh() = execute {
        if (state.value.source == ContentSource.LOCAL) mutable.update { it.copy(stories = local.stories(), more = false, progress = local.progress()) }
        else loadPage(true)
    }
    fun more() { if (!state.value.busy && state.value.more && state.value.source == ContentSource.SERVER) execute { loadPage(false) } }
    fun openStory(slug: String) = execute {
        val detail = app.content.source(state.value.source).detail(slug)
        mutable.update { it.copy(detail = detail, chapter = null) }
    }
    fun openChapter(number: Int) = execute {
        val current = state.value
        val detail = current.detail ?: return@execute
        val chapter = app.content.source(current.source).chapter(detail.story.slug, number)
        mutable.update { it.copy(chapter = chapter) }
    }
    fun back() {
        operation?.cancel()
        mutable.update { if (it.chapter != null) it.copy(chapter = null, busy = false, error = "")
            else it.copy(detail = null, busy = false, error = "", progress = if (it.source == ContentSource.LOCAL) local.progress() else repository.store.progress()) }
    }
    fun reloadProgress() { mutable.update { it.copy(progress = if (it.source == ContentSource.LOCAL) local.progress() else repository.store.progress()) } }
    fun download(story: Story) {
        if (state.value.user == null || state.value.source != ContentSource.SERVER) return
        StoryDownloadWorker.enqueue(app, story)
    }
    fun cancelDownload(id: UUID) { work.cancelWorkById(id) }
    fun deleteLocal(slug: String) = execute {
        work.cancelUniqueWork(StoryDownloadWorker.uniqueName(slug))
        if (PlaybackStore.state.value.plan?.let { it.source == ContentSource.LOCAL && it.slug == slug } == true) {
            app.stopService(Intent(app, TtsPlaybackService::class.java)); PlaybackStore.mutable.value = PlaybackSnapshot()
        }
        local.delete(slug)
        mutable.update { it.copy(detail = null, chapter = null, stories = local.stories(), progress = local.progress()) }
    }
    fun logout() {
        work.cancelAllWorkByTag(StoryDownloadWorker.TAG)
        if (PlaybackStore.state.value.plan?.source == ContentSource.SERVER) {
            app.stopService(Intent(app, TtsPlaybackService::class.java)); PlaybackStore.mutable.value = PlaybackSnapshot()
        }
        execute { try { repository.logout() } finally {
            mutable.update { it.copy(checking = false, user = null, stories = emptyList(), detail = null, chapter = null, progress = null) }
        } }
    }
}
