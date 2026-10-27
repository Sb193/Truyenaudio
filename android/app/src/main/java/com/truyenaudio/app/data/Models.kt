package com.truyenaudio.app.data

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class User(val id: String, val username: String)
data class UserResponse(val user: User?)
data class LoginBody(val username: String, val password: String)
data class Story(
    val id: Int, val slug: String, val title: String, val description: String?,
    @SerializedName("chapter_count") val chapterCount: Int,
    @SerializedName("created_at") val createdAt: JsonElement? = null,
    val uploader: String? = null
)
data class StoriesResponse(val data: List<Story>, val page: Int)
data class ChapterSummary(val number: Int, val title: String)
data class StoryDetail(val story: Story, val chapters: List<ChapterSummary>)
data class ChapterContentResponse(
    val number: Int, val title: String, val content: String, val slug: String,
    @SerializedName("story_title") val storyTitle: String,
    @SerializedName("chapter_count") val chapterCount: Int
)
data class ListenPlan(
    val slug: String = "", val storyTitle: String = "", val first: Int = 1,
    val last: Int = 1, val chapter: Int = 1, val offset: Int = 0,
    val engine: String = "", val voice: String = "", val speed: Float = 1f,
    val sleepMinutes: Int = 0, val networkVoiceAccepted: Boolean = false,
    val source: ContentSource = ContentSource.SERVER
) {
    fun validate() {
        require(slug.isNotBlank() && first >= 1 && last >= first && chapter in first..last) { "Phạm vi chương không hợp lệ" }
        require(speed in 0.5f..2f && sleepMinutes >= 0 && offset >= 0) { "Cấu hình nghe không hợp lệ" }
    }
}
data class Progress(val plan: ListenPlan, val chapterTitle: String = "")
