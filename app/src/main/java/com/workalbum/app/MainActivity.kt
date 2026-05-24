package com.workalbum.app

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.workalbum.app.model.WorkImage
import com.workalbum.app.service.ScreenshotMonitorService
import com.workalbum.app.ui.screens.GalleryScreen
import com.workalbum.app.ui.screens.ImageDetailScreen
import com.workalbum.app.ui.screens.SettingsScreen
import com.workalbum.app.ui.theme.WorkAlbumTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository by lazy {
        (application as WorkAlbumApplication).imageRepository
    }

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startScreenshotMonitor()
        } else {
            Toast.makeText(this, "通知权限被拒绝，将无法收到截图提醒", Toast.LENGTH_LONG).show()
        }
    }

    // 手动从系统相册选图导入
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    repository.saveSharedImage(uri, "manual_import")
                    deleteFromMediaStore(uri)
                    Toast.makeText(this@MainActivity, "✅ 已导入工作相册", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Android 11+ 删除系统相册图片：使用系统确认对话框
     * MediaStore.createDeleteRequest → 弹出"允许xxx删除这张图片吗？"
     */
    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // 用户操作完成（无论是确认还是拒绝）
        android.util.Log.d("WorkAlbum", "删除请求结果: ${result.resultCode}")
    }

    // 待删除的 URI（跨 launcher 保存）
    private var pendingDeleteUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WorkAlbumTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "gallery") {
                    composable("gallery") {
                        GalleryScreen(
                            repository = repository,
                            onImageClick = { image -> navController.navigate("detail/${image.id}") },
                            onSettingsClick = { navController.navigate("settings") },
                            onImportClick = {
                                pickMediaLauncher.launch(
                                    PickVisualMediaRequest.Builder()
                                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        .build()
                                )
                            }
                        )
                    }
                    composable("detail/{imageId}") { backStackEntry ->
                        val imageId = backStackEntry.arguments?.getString("imageId")?.toLongOrNull()
                        val image = imageId?.let {
                            var result by remember { mutableStateOf<WorkImage?>(null) }
                            LaunchedEffect(imageId) {
                                repository.getAllImages().collect { images ->
                                    result = images.find { img -> img.id == imageId }
                                }
                            }
                            result
                        }
                        if (image != null) {
                            ImageDetailScreen(image = image, repository = repository, onBack = { navController.popBackStack() })
                        }
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }

        requestPermissionsAndStartMonitor()
        handleScreenshotIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleScreenshotIntent(intent)
    }

    /**
     * 处理截图通知点击 → 导入 + 删除系统原图
     */
    private fun handleScreenshotIntent(intent: Intent?) {
        if (intent?.action == ScreenshotMonitorService.ACTION_MOVE_SCREENSHOT) {
            val uriString = intent.getStringExtra(ScreenshotMonitorService.EXTRA_IMAGE_URI)
            if (uriString != null) {
                val uri = Uri.parse(uriString)
                lifecycleScope.launch {
                    try {
                        repository.saveSharedImage(uri, "screenshot")
                        deleteFromMediaStore(uri)
                        Toast.makeText(this@MainActivity, "✅ 截图已移入工作相册", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "移入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 删除系统相册中的图片
     *
     * 策略（按优先级）：
     * 1. contentResolver.delete() — 部分设备支持直接删除
     * 2. MediaStore.createDeleteRequest() — Android 11+ 弹出系统确认窗
     */
    private fun deleteFromMediaStore(uri: Uri) {
        try {
            // 策略1：直接删除（部分设备/ColorOS 可行）
            val deleted = contentResolver.delete(uri, null, null)
            if (deleted > 0) {
                android.util.Log.d("WorkAlbum", "系统相册已直接删除: $uri")
                return  // 成功，无需进一步操作
            }

            // 策略2：Android 11+ 用 MediaStore API 弹出确认窗
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                if (pi != null) {
                    android.util.Log.d("WorkAlbum", "弹出系统删除确认对话框: $uri")
                    pendingDeleteUri = uri
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(pi.intentSender).build()
                    )
                    return
                }
            }

            // 都不行
            android.util.Log.w("WorkAlbum", "无法删除系统原图（可能需要手动清理）")
        } catch (e: SecurityException) {
            android.util.Log.w("WorkAlbum", "无权限删除: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.w("WorkAlbum", "删除异常: ${e.message}")
        }
    }

    private fun requestPermissionsAndStartMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            val mediaGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            if (!notificationGranted || !mediaGranted) {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_IMAGES)
                )
            } else {
                startScreenshotMonitor()
            }
        } else {
            startScreenshotMonitor()
        }
    }

    private fun startScreenshotMonitor() {
        ScreenshotMonitorService.start(this)
    }
}