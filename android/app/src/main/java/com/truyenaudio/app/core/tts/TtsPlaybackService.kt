package com.truyenaudio.app.core.tts

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.truyenaudio.app.MainActivity
import com.truyenaudio.app.TruyenAudioApp
import com.truyenaudio.app.core.constants.AppConstants
import com.truyenaudio.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import java.util.Locale

class TtsPlaybackService : Service() {
    companion object {
        const val PLAY = "com.truyenaudio.app.ACTION_PLAY"
        const val PAUSE = "com.truyenaudio.app.ACTION_PAUSE"
        const val STOP = "com.truyenaudio.app.ACTION_STOP"
        const val NEXT = "com.truyenaudio.app.ACTION_NEXT"
        const val PREV = "com.truyenaudio.app.ACTION_PREV"
        const val PLAN = "plan"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val repository get() = (application as TruyenAudioApp).repository
    private var sessionVersion = 0
    private lateinit var media: MediaSessionCompat
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var tts: TextToSpeech? = null
    private var engine = ""
    private var engineGeneration = 0
    private var ready = false
    private var destroyed = false
    private var foreground = false
    private var wantPlay = false
    private var wake: PowerManager.WakeLock? = null
    private var plan: ListenPlan? = null
    private var chapter: ChapterContentResponse? = null
    private var parts = emptyList<SpeechChunk>()
    private val cursor = QueueCursor()
    private var loadJob: Job? = null
    private var prefetch: Deferred<Result<ChapterContentResponse>>? = null
    private var prefetchNumber = -1
    private var timer: Job? = null
    private var requestGeneration = 0
    private var lastSavedAt = 0L
    private var chapterNumbers = emptyList<Int>()
    private var utteranceStart = 0
    private val snapshot get() = PlaybackStore.mutable.value
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionVersion = repository.sessionVersion.value
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) pause()
            }, main).build()
        media = MediaSessionCompat(this, "TruyenAudio").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resume() }
                override fun onPause() { pause() }
                override fun onStop() { stopReading() }
                override fun onSkipToNext() { skip(1) }
                override fun onSkipToPrevious() { skip(-1) }
            }, main)
            isActive = true
        }
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { repository.sessionVersion.drop(1).collect { stopReading(save = false) } }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                PLAY -> {
                    promote()
                    val newPlan = intent.getStringExtra(PLAN)?.let { Gson().fromJson(it, ListenPlan::class.java) }
                    if (newPlan != null) start(newPlan) else if (plan == null) {
                        repository.store.progress()?.plan?.let { start(it) } ?: fail("Chưa có tiến độ để tiếp tục")
                    } else resume()
                }
                PAUSE -> pause()
                STOP -> stopReading()
                NEXT -> skip(1)
                PREV -> skip(-1)
                Intent.ACTION_MEDIA_BUTTON -> { promote(); androidx.media.session.MediaButtonReceiver.handleIntent(media, intent) }
                else -> stopReading()
            }
        } catch (error: Exception) { fail(error.message ?: "Không thể bắt đầu nghe. Hãy mở ứng dụng và thử lại.") }
        return START_NOT_STICKY
    }

    private fun start(value: ListenPlan) {
        value.validate()
        require(repository.store.cookies.hasActiveSession()) { "Vui lòng đăng nhập lại" }
        invalidate()
        prefetch?.cancel(); prefetch = null
        plan = value
        chapterNumbers = emptyList()
        parts = emptyList(); chapter = null
        wantPlay = true
        timer?.cancel()
        if (value.sleepMinutes > 0) timer = scope.launch {
            delay(value.sleepMinutes * 60_000L)
            pause("Đã hết giờ hẹn")
        }
        publish(PlaybackStatus.LOADING, "Đang tải chương…")
        if (tts == null || engine != value.engine) initEngine(value.engine)
        loadChapter(value.chapter, value.offset)
    }

    private fun initEngine(selected: String) {
        engineGeneration++
        val version = engineGeneration
        ready = false
        tts?.shutdown()
        engine = selected
        val callback = TextToSpeech.OnInitListener { status -> main.post {
            if (!destroyed && version == engineGeneration) {
                if (status != TextToSpeech.SUCCESS) fail("Không khởi tạo được engine. Kiểm tra cài đặt giọng đọc của máy.")
                else {
                    ready = true
                    tts?.setAudioAttributes(attributes)
                    installListener()
                    if (parts.isNotEmpty() && wantPlay) beginSpeaking()
                }
            }
        } }
        tts = if (selected.isBlank()) TextToSpeech(this, callback) else TextToSpeech(this, callback, selected)
    }

    private fun configureVoice(): Boolean {
        val current = plan ?: return false
        val voices = tts?.voices.orEmpty()
        val voice = if (current.voice.isNotBlank()) voices.find { it.name == current.voice } else
            voices.filter { it.locale.language == "vi" }.sortedBy { it.isNetworkConnectionRequired }.firstOrNull()
        if (current.voice.isNotBlank() && voice == null) {
            fail("Giọng đã chọn không còn trên máy. Hãy mở cấu hình nghe và chọn lại."); return false
        }
        if (voice != null) {
            if (voice.isNetworkConnectionRequired && !current.networkVoiceAccepted) {
                fail("Giọng này cần mạng. Hãy xác nhận trong cấu hình nghe trước khi sử dụng."); return false
            }
            if (tts?.setVoice(voice) == TextToSpeech.ERROR) { fail("Không chọn được giọng đọc"); return false }
            plan = current.copy(voice = voice.name)
        } else {
            fail("Chưa có giọng tiếng Việt. Hãy cài hoặc tải giọng trong cài đặt TTS của điện thoại."); return false
        }
        tts?.setSpeechRate(current.speed)
        return true
    }

    private fun loadChapter(number: Int, offset: Int = 0) {
        loadJob?.cancel()
        requestGeneration++
        val request = requestGeneration
        val current = plan ?: return
        val prepared = if (prefetchNumber == number) prefetch else null
        if (prepared == null) prefetch?.cancel()
        prefetch = null
        cursor.invalidate(); tts?.stop(); releaseWake()
        parts = emptyList()
        plan = current.copy(chapter = number, offset = offset)
        publish(PlaybackStatus.LOADING, "Đang tải chương $number…")
        loadJob = scope.launch {
            try {
                if (chapterNumbers.isEmpty()) {
                    chapterNumbers = repository.detail(current.slug).chapters.map { it.number }.sorted()
                    require(current.first in chapterNumbers && current.last in chapterNumbers && number in chapterNumbers) { "Phạm vi chương không còn trong mục lục" }
                }
                val content = prepared?.await()?.getOrNull() ?: repository.chapter(current.slug, number)
                ensureActive()
                if (request != requestGeneration || sessionVersion != repository.sessionVersion.value) return@launch
                require(content.number == number && content.slug == current.slug) { "Máy chủ trả về chương không khớp" }
                chapter = content
                val split = withContext(Dispatchers.Default) { TextChunker.split(content.content, TextToSpeech.getMaxSpeechInputLength()) }
                if (split.isEmpty()) { fail("Chương không có nội dung"); return@launch }
                parts = split
                val index = parts.indexOfLast { it.start <= offset }.coerceAtLeast(0)
                cursor.load(parts.size, index)
                saveProgress()
                if (wantPlay && ready) beginSpeaking()
                else if (!wantPlay) publish(PlaybackStatus.PAUSED, "Đã tạm dừng")
                val next = chapterNumbers.firstOrNull { it > number && it <= current.last }
                if (next != null) {
                    prefetchNumber = next
                    prefetch = scope.async {
                        try { Result.success(repository.chapter(current.slug, next)) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) { Result.failure(error) }
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { if (request == requestGeneration) fail(error.message ?: "Không tải được chương") }
        }
    }

    private fun installListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = dispatch(utteranceId) {
                acquireWake(); publish(PlaybackStatus.PLAYING, "Đang nghe")
            }
            override fun onDone(utteranceId: String?) = dispatch(utteranceId) {
                if (cursor.complete(utteranceId)) {
                    releaseWake()
                    if (cursor.index < parts.size) speakCurrent()
                    else {
                        val current = plan ?: return@dispatch
                        releaseWake()
                        val next = chapterNumbers.firstOrNull { it > current.chapter && it <= current.last }
                        if (next != null) loadChapter(next)
                        else {
                            wantPlay = false
                            plan = current.copy(offset = chapter?.content?.length ?: current.offset)
                            saveProgress(); releaseAudio(); timer?.cancel()
                            publish(PlaybackStatus.COMPLETED, "Đã nghe hết phạm vi đã chọn")
                            demote()
                        }
                    }
                }
            }
            @Deprecated("Required by Android")
            override fun onError(utteranceId: String?) = dispatch(utteranceId) { fail("Engine không đọc được nội dung. Hãy kiểm tra dữ liệu giọng đọc.") }
            override fun onError(utteranceId: String?, errorCode: Int) = dispatch(utteranceId) { fail("Lỗi giọng đọc ($errorCode). Kiểm tra engine hoặc mạng rồi tiếp tục.") }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) = dispatch(utteranceId) {
                val part = parts.getOrNull(cursor.index) ?: return@dispatch
                plan = plan?.copy(offset = (utteranceStart + start).coerceIn(part.start, part.end))
                if (SystemClock.elapsedRealtime() - lastSavedAt > 1500) saveProgress()
            }
        })
    }
    private fun dispatch(id: String?, block: () -> Unit) {
        main.post { if (!destroyed && cursor.accepts(id) && wantPlay) block() }
    }
    private fun beginSpeaking() {
        if (!wantPlay || !ready || parts.isEmpty()) return
        if (!configureVoice()) return
        try { promote() } catch (error: Exception) { fail("Hãy mở ứng dụng để tiếp tục nghe"); return }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pause("Chưa có quyền phát âm thanh. Hãy kết thúc cuộc gọi rồi tiếp tục."); return
        }
        cursor.resume()
        speakCurrent()
    }
    private fun speakCurrent() {
        val part = parts.getOrNull(cursor.index) ?: return
        var offset = plan?.offset?.takeIf { it in part.start until part.end } ?: part.start
        val content = chapter?.content ?: return
        if (offset > part.start && Character.isLowSurrogate(content[offset]) && Character.isHighSurrogate(content[offset - 1])) offset--
        utteranceStart = offset
        plan = plan?.copy(offset = offset)
        saveProgress()
        publish(PlaybackStatus.PLAYING, "Đang nghe")
        if (tts?.speak(content.substring(offset, part.end), TextToSpeech.QUEUE_FLUSH, null, cursor.id()) == TextToSpeech.ERROR) fail("Không thể đưa nội dung vào giọng đọc")
    }
    private fun resume() {
        if (wantPlay) return
        if (plan == null) {
            repository.store.progress()?.plan?.let { start(it) } ?: stopReading()
            return
        }
        wantPlay = true
        if (parts.isEmpty()) plan?.let { loadChapter(it.chapter, it.offset) }
        else {
            if (cursor.index >= parts.size) cursor.load(parts.size, 0)
            beginSpeaking()
        }
    }
    private fun pause(message: String = "Đã tạm dừng; tiếp tục từ đầu đoạn gần nhất") {
        wantPlay = false
        cursor.invalidate(); tts?.stop()
        saveProgress(); releaseAudio()
        publish(PlaybackStatus.PAUSED, message); demote()
    }
    private fun skip(delta: Int) {
        val current = plan ?: return
        val index = chapterNumbers.indexOf(current.chapter)
        val next = chapterNumbers.getOrNull(index + delta) ?: return
        if (next !in current.first..current.last) return
        cursor.invalidate(); tts?.stop()
        loadChapter(next)
    }
    private fun invalidate() {
        wantPlay = false; cursor.invalidate(); requestGeneration++
        loadJob?.cancel(); tts?.stop(); releaseAudio()
    }
    private fun saveProgress() {
        val current = plan ?: return
        if (sessionVersion == repository.sessionVersion.value) {
            repository.saveProgress(sessionVersion, Progress(current, chapter?.title.orEmpty()))
            lastSavedAt = SystemClock.elapsedRealtime()
        }
    }
    private fun stopReading(save: Boolean = true) {
        if (save) saveProgress()
        invalidate(); timer?.cancel(); prefetch?.cancel()
        plan = null; parts = emptyList(); chapter = null
        PlaybackStore.mutable.value = PlaybackSnapshot()
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }
    private fun fail(message: String) {
        saveProgress(); invalidate(); timer?.cancel(); prefetch?.cancel()
        publish(PlaybackStatus.ERROR, message)
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }
    private fun acquireWake() {
        if (wake?.isHeld != true) wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:tts").apply { acquire(10 * 60_000L) }
    }
    private fun releaseWake() { if (wake?.isHeld == true) wake?.release(); wake = null }
    private fun releaseAudio() { releaseWake(); audio.abandonAudioFocusRequest(focus) }
    private fun promote() {
        if (!foreground) {
            ServiceCompat.startForeground(this, AppConstants.NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            foreground = true
        }
    }
    private fun demote() { stopForeground(STOP_FOREGROUND_DETACH); foreground = false }
    private fun control(action: String, code: Int): PendingIntent {
        val intent = Intent(this, TtsPlaybackService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (action == PLAY) PendingIntent.getForegroundService(this, code, intent, flags)
            else PendingIntent.getService(this, code, intent, flags)
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, AppConstants.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(plan?.storyTitle ?: "TruyenAudio")
            .setContentText("${chapter?.title ?: "Đang chuẩn bị"} · ${snapshot.message}")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(wantPlay)
            .setDeleteIntent(control(STOP, 9))
            .addAction(android.R.drawable.ic_media_previous, "Chương trước", control(PREV, 1))
            .addAction(if (wantPlay) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (wantPlay) "Tạm dừng" else "Tiếp tục", control(if (wantPlay) PAUSE else PLAY, 2))
            .addAction(android.R.drawable.ic_media_next, "Chương sau", control(NEXT, 3))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng", control(STOP, 4))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0, 1, 2)).build()
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun publish(status: PlaybackStatus, message: String) {
        PlaybackStore.mutable.value = PlaybackSnapshot(status, plan, chapter?.title.orEmpty(),
            cursor.index.coerceAtMost((parts.size - 1).coerceAtLeast(0)), parts.size, message)
        val state = when (status) {
            PlaybackStatus.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PlaybackStatus.LOADING -> PlaybackStateCompat.STATE_BUFFERING
            PlaybackStatus.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PlaybackStatus.ERROR -> PlaybackStateCompat.STATE_ERROR
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        media.setMetadata(MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_TITLE, chapter?.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, plan?.storyTitle).build())
        media.setPlaybackState(PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f).build())
        if (foreground && NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(AppConstants.NOTIFICATION_ID, notification())
        }
    }
    override fun onDestroy() {
        destroyed = true; engineGeneration++
        saveProgress(); invalidate(); scope.cancel(); main.removeCallbacksAndMessages(null)
        tts?.shutdown(); media.release(); unregisterReceiver(noisy)
        getSystemService(NotificationManager::class.java).cancel(AppConstants.NOTIFICATION_ID)
        if (snapshot.status == PlaybackStatus.PLAYING || snapshot.status == PlaybackStatus.LOADING) {
            PlaybackStore.mutable.value = snapshot.copy(status = PlaybackStatus.PAUSED, message = "Dịch vụ đã dừng")
        }
        super.onDestroy()
    }
}
