package com.workalbum.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.workalbum.app.MainActivity
import java.io.File

/**
 * 截图监控服务 v3 - 双通道检测（文件系统 + MediaStore）
 *
 * FileObserver：秒级响应（直接监听文件夹变化）
 * ContentObserver：兜底（MediaStore 数据库确认）
 */
class ScreenshotMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var contentObserver: ContentObserver
    private var fileObserver: FileObserver? = null

    // ColorOS 截图默认目录
    private val screenshotDirs = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots",
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Screenshots",
        "/sdcard/Pictures/Screenshots",
        "/sdcard/DCIM/Screenshots"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID_MONITOR, buildMonitorNotification())

        // 通道1：ContentObserver（兜底）
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                uri?.let { checkAndNotify(it) }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver
        )

        // 通道2：FileObserver（快速通道，秒级响应）
        startFileObserver()
    }

    /**
     * 启动 FileObserver，监听截图目录的 CREATE + CLOSE_WRITE 事件
     */
    private fun startFileObserver() {
        val existingDir = screenshotDirs.firstOrNull { File(it).isDirectory }
            ?: screenshotDirs.first() // fallback

        val dir = File(existingDir)
        if (!dir.exists()) dir.mkdirs()

        fileObserver = object : FileObserver(dir, FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return

                val name = path.lowercase()
                // 仅处理截图文件
                if (!name.contains("screenshot") && !name.contains("screen_shot")) return
                if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")) return

                // CLOSE_WRITE 或 MOVED_TO 表示文件写入完成
                if (event == FileObserver.CLOSE_WRITE || event == FileObserver.MOVED_TO) {
                    val file = File(dir, path)
                    if (file.exists() && file.length() > 0) {
                        // 用 FileProvider URI 或直接查 MediaStore
                        handler.post { queryMediaStoreForFile(file) }
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }

    /**
     * 根据文件路径查 MediaStore 获取 content:// URI
     */
    private fun queryMediaStoreForFile(file: File) {
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            MediaStore.Images.Media.DATA + "=?",
            arrayOf(file.absolutePath),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                showNotification(uri)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.workalbum.app.TEST_SCREENSHOT") {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    showNotification(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                }
            }
        }
        return START_STICKY
    }

    /**
     * ContentObserver 回调：检测是否为新截图
     */
    private fun checkAndNotify(uri: Uri) {
        contentResolver.query(uri, arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        ), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1) ?: ""
                val rel = cursor.getString(2) ?: ""
                val dateAdded = cursor.getLong(3)

                val isScreenshot = name.contains("Screenshot", ignoreCase = true) ||
                        rel.contains("creenshot", ignoreCase = true)
                val isRecent = (System.currentTimeMillis() / 1000 - dateAdded) < 10

                if (isScreenshot && isRecent && id > 0) {
                    showNotification(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                }
            }
        }
    }

    private fun showNotification(imageUri: Uri) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_MOVE_SCREENSHOT
            putExtra(EXTRA_IMAGE_URI, imageUri.toString())
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_SCREENSHOT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📸 检测到新截图")
            .setContentText("👇 点击此处移入工作相册")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("点击此通知将截图移入工作相册\n系统相册原图将自动删除"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_SCREENSHOT, notification)
    }

    private fun buildMonitorNotification() =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("工作相册")
            .setContentText("截图监控运行中")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_MONITOR, "截图监控", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_SCREENSHOT, "截图提醒", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onDestroy() {
        fileObserver?.stopWatching()
        contentResolver.unregisterContentObserver(contentObserver)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_MONITOR = "screenshot_monitor"
        private const val CHANNEL_SCREENSHOT = "screenshot_alert"
        const val NOTIFICATION_ID_MONITOR = 1001
        const val NOTIFICATION_ID_SCREENSHOT = 1002
        const val ACTION_MOVE_SCREENSHOT = "com.workalbum.app.MOVE_SCREENSHOT"
        const val EXTRA_IMAGE_URI = "image_uri"

        fun start(context: Context) {
            val intent = Intent(context, ScreenshotMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenshotMonitorService::class.java))
        }
    }
}