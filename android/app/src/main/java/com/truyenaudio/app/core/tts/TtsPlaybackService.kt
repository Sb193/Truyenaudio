package com.truyenaudio.app.core.tts

import android.app.Service
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.MediaSessionCompat
import com.truyenaudio.app.core.constants.AppConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger

class TtsPlaybackService : Service(), TextToSpeech.OnInitListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSessionCompat
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null

    val playbackSnapshot = MutableStateFlow(PlaybackSnapshot())
    private val sessionGeneration = AtomicInteger(1)

    // Chunks & Queue
    private var currentChunks = listOf<String>()
    private var currentChunkPointer = 0
    private var prefetchedChapterContent: ChapterContentResponse? = null

    override fun onCreate() {
        super.onCreate()
        initMediaSession()
        registerNoiseReceiver()
        initTextToSpeech()
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Stale callback guard via sessionGeneration
                val parsed = parseUtteranceId(utteranceId) ?: return
                if (parsed.generation != sessionGeneration.get()) return

                acquireWakeLock()
                playbackSnapshot.value = playbackSnapshot.value.copy(
                    status = PlaybackStatus.PLAYING,
                    currentChunkIndex = parsed.chunkIndex
                )
                checkAndPrefetchNextChapter()
            }

            override fun onDone(utteranceId: String?) {
                val parsed = parseUtteranceId(utteranceId) ?: return
                if (parsed.generation != sessionGeneration.get()) return

                val nextChunk = parsed.chunkIndex + 1
                if (nextChunk < currentChunks.size) {
                    speakChunk(nextChunk)
                } else {
                    handleChapterCompleted()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                releaseWakeLock()
            }
        })
    }

    private fun handleChapterCompleted() {
        val nextChapter = playbackSnapshot.value.currentChapterNumber + 1
        if (playbackSnapshot.value.range.isChapterInRange(nextChapter, playbackSnapshot.value.totalChapters)) {
            loadAndPlayChapter(playbackSnapshot.value.storySlug, nextChapter, 0)
        } else {
            pausePlayback()
        }
    }
}