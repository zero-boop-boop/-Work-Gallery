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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.workalbum.app.MainActivity

/**
 * 截图监控服务 - ColorOS 专用版 v2
 *
 * 原理：MediaStore ContentObserver 监听新截图 → 发通知。
 * ColorOS 禁用通知 action 按钮，所以只用 setContentIntent（点击通知体启动 Activity）。
 * 点击通知 → MainActivity 直接处理导入 + 删除系统原图。
 */
class ScreenshotMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var contentObserver: ContentObserver

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID_MONITOR, buildMonitorNotification())

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                uri?.let { checkNewScreenshot(it) }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 测试通知：用最近一张图片模拟截图通知
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

    private fun checkNewScreenshot(uri: Uri) {
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
                        name.contains("ScreenShot", ignoreCase = true) ||
                        rel.contains("creenshot", ignoreCase = true)
                val isRecent = (System.currentTimeMillis() / 1000 - dateAdded) < 5

                if (isScreenshot && isRecent && id > 0) {
                    showNotification(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                }
            }
        }
    }

    /**
     * 发截图通知 - ColorOS: 只用 contentIntent，不用 action 按钮
     */
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
            .setContentText("👇 点击此处移入工作相册（不保留到系统相册）")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("点击此通知即可将截图移入工作相册。\n系统相册中的原图将自动删除。"))
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
            .setContentText("截图监控运行中 · 截图后可点击通知导入")
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