package com.truyenaudio.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class LocalLibrary(context: Context) : ChapterSource {
    private val gson = Gson()
    private val removed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val preferences = context.getSharedPreferences("local_playback", Context.MODE_PRIVATE)
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "downloaded_library.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE stories (slug TEXT PRIMARY KEY, metadata TEXT NOT NULL, saved_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE chapters (slug TEXT NOT NULL REFERENCES stories(slug) ON DELETE CASCADE, number INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, PRIMARY KEY(slug,number))")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { error("Phiên bản thư viện chưa được hỗ trợ") }
    }.apply { setWriteAheadLoggingEnabled(true) }
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()

    suspend fun stories(): List<Story> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT s.metadata,COUNT(c.number) FROM stories s JOIN chapters c ON c.slug=s.slug GROUP BY s.slug ORDER BY s.saved_at DESC", null).use { rows ->
            buildList { while (rows.moveToNext()) add(gson.fromJson(rows.getString(0), Story::class.java).copy(chapterCount = rows.getInt(1))) }
        }
    }
    override suspend fun detail(slug: String): StoryDetail = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val story = db.rawQuery("SELECT metadata FROM stories WHERE slug=?", arrayOf(slug)).use { rows ->
            if (!rows.moveToFirst()) throw IOException("Truyện chưa được lưu trên máy")
            gson.fromJson(rows.getString(0), Story::class.java)
        }
        val chapters = db.rawQuery("SELECT number,title FROM chapters WHERE slug=? ORDER BY number", arrayOf(slug)).use { rows ->
            buildList { while (rows.moveToNext()) add(ChapterSummary(rows.getInt(0), rows.getString(1))) }
        }
        StoryDetail(story.copy(chapterCount = chapters.size), chapters)
    }
    override suspend fun chapter(slug: String, number: Int): ChapterContentResponse = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT body FROM chapters WHERE slug=? AND number=?", arrayOf(slug, number.toString())).use { rows ->
            if (!rows.moveToFirst()) throw IOException("Chương $number chưa có trên máy. Hãy tải chương trong mục Tải thêm truyện.")
            gson.fromJson(rows.getString(0), ChapterContentResponse::class.java)
        }
    }
    suspend fun hasChapter(slug: String, number: Int): Boolean = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT 1 FROM chapters WHERE slug=? AND number=?", arrayOf(slug, number.toString())).use { it.moveToFirst() }
    }
    suspend fun putStory(story: Story) = withContext(Dispatchers.IO) {
        if (story.slug in removed) throw kotlinx.coroutines.CancellationException("Truyện đã được xóa khỏi máy")
        val values = ContentValues().apply { put("slug", story.slug); put("metadata", gson.toJson(story)); put("saved_at", System.currentTimeMillis()) }
        // REPLACE would cascade-delete existing chapters, so update first and insert only when absent.
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            if (db.update("stories", values, "slug=?", arrayOf(story.slug)) == 0) db.insertOrThrow("stories", null, values)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        revision.update { it + 1 }
    }
    suspend fun putChapter(content: ChapterContentResponse) = withContext(Dispatchers.IO) {
        if (content.slug in removed) throw kotlinx.coroutines.CancellationException("Truyện đã được xóa khỏi máy")
        val values = ContentValues().apply {
            put("slug", content.slug); put("number", content.number); put("title", content.title); put("body", gson.toJson(content))
        }
        helper.writableDatabase.insertWithOnConflict("chapters", null, values, SQLiteDatabase.CONFLICT_REPLACE).also {
            if (it == -1L) throw IOException("Không lưu được chương. Kiểm tra dung lượng máy.")
        }
        revision.update { it + 1 }
    }
    suspend fun delete(slug: String) = withContext(Dispatchers.IO) {
        synchronized(this@LocalLibrary) { removed.add(slug) }
        helper.writableDatabase.delete("stories", "slug=?", arrayOf(slug))
        if (progress()?.plan?.slug == slug) preferences.edit().remove("progress").commit()
        revision.update { it + 1 }
    }
    fun progress(): Progress? = runCatching { preferences.getString("progress", null)?.let { gson.fromJson(it, Progress::class.java) } }.getOrNull()
    @Synchronized fun allowDownload(slug: String) { removed.remove(slug) }
    @Synchronized fun saveProgress(progress: Progress) { if (progress.plan.slug in removed) return; preferences.edit().putString("progress", gson.toJson(progress)).apply() }
}
