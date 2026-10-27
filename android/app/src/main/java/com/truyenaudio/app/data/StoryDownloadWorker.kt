package com.truyenaudio.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.truyenaudio.app.R
import com.truyenaudio.app.TruyenAudioApp
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class StoryDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val TAG = "story-download"
        fun uniqueName(slug: String) = "download:$slug"
        fun enqueue(context: Context, story: Story) {
            val work = OneTimeWorkRequestBuilder<StoryDownloadWorker>()
                .setInputData(workDataOf("slug" to story.slug, "title" to story.title.take(500)))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .addTag(TAG).addTag("slug:${story.slug}").build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(story.slug), ExistingWorkPolicy.KEEP, work)
        }
    }
    private val app get() = applicationContext as TruyenAudioApp
    private fun progress(done: Int, total: Int, message: String = "") = workDataOf(
        "slug" to inputData.getString("slug"), "title" to inputData.getString("title"), "done" to done, "total" to total, "message" to message)
    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0, 0)
    private fun foreground(done: Int, total: Int): ForegroundInfo {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(TAG, "Tải truyện về máy", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, TAG).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(inputData.getString("title") ?: "Đang tải truyện")
            .setContentText(if (total > 0) "Đã lưu $done/$total chương" else "Đang chuẩn bị tải…")
            .setProgress(total, done, total == 0).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng tải", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        val notificationId = 2000 + Math.floorMod(id.hashCode(), 100000)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(notificationId, notification)
    }
    override suspend fun doWork(): Result {
        val slug = inputData.getString("slug") ?: return Result.failure()
        var done = 0
        var total = 0
        try {
            setForeground(foreground(done, total))
            setProgress(progress(done, total))
            val repository = app.repository
            if (!repository.store.cookies.hasActiveSession()) return Result.failure(progress(done, total, "Đăng nhập để tiếp tục tải truyện"))
            val version = repository.sessionVersion.value
            val detail = repository.detail(slug)
            val chapters = detail.chapters.distinctBy { it.number }.sortedBy { it.number }
            require(chapters.isNotEmpty()) { "Truyện chưa có chương để tải" }
            total = chapters.size
            app.localLibrary.putStory(detail.story.copy(chapterCount = total))
            for (chapter in chapters) {
                currentCoroutineContext().ensureActive()
                if (version != repository.sessionVersion.value) return Result.failure(progress(done, total, "Phiên đăng nhập đã thay đổi. Các chương đã tải vẫn được giữ."))
                if (!app.localLibrary.hasChapter(slug, chapter.number)) {
                    val content = repository.chapter(slug, chapter.number)
                    currentCoroutineContext().ensureActive()
                    require(content.slug == slug && content.number == chapter.number) { "Máy chủ trả về chương không khớp" }
                    app.localLibrary.putChapter(content)
                }
                done++
                setProgress(progress(done, total))
                if (done == total || done % 5 == 0) setForeground(foreground(done, total))
            }
            return Result.success(progress(done, total, "Đã tải toàn bộ truyện. Có thể nghe không cần đăng nhập."))
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: ApiError) {
            return if (error.status in listOf(429, 500, 502, 503, 504) && runAttemptCount < 3) Result.retry()
                else Result.failure(progress(done, total, error.message ?: "Không tải được truyện"))
        } catch (error: IOException) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure(progress(done, total, "Mất kết nối. Chọn Tải truyện để tiếp tục các chương còn thiếu."))
        } catch (error: Exception) {
            return Result.failure(progress(done, total, error.message ?: "Không lưu được truyện. Kiểm tra dung lượng máy."))
        }
    }
}
