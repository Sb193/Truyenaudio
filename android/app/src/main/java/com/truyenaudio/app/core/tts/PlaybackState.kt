package com.truyenaudio.app.core.tts

import com.truyenaudio.app.data.ListenPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, COMPLETED, ERROR }
data class PlaybackSnapshot(
    val status: PlaybackStatus = PlaybackStatus.IDLE, val plan: ListenPlan? = null,
    val title: String = "", val chunk: Int = 0, val chunks: Int = 0,
    val message: String = "Chưa phát truyện"
)
object PlaybackStore {
    internal val mutable = MutableStateFlow(PlaybackSnapshot())
    val state = mutable.asStateFlow()
}

/** Only the current generation/index can complete; duplicate or stale callbacks cannot advance. */
class QueueCursor {
    var generation = 0; private set
    var index = 0; private set
    var size = 0; private set
    var active = false; private set
    fun load(size: Int, index: Int = 0) {
        require(size > 0 && index in 0 until size)
        invalidate(); this.size = size; this.index = index
    }
    fun resume() { generation++; active = true }
    fun invalidate() { generation++; active = false }
    fun id() = "$generation:$index"
    fun accepts(id: String?) = active && id == id()
    fun complete(id: String?): Boolean {
        if (!accepts(id)) return false
        index++
        if (index >= size) active = false
        return true
    }
}
