package com.workalbum.app

import android.Manifest
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository by lazy {
        (application as WorkAlbumApplication).imageRepository
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startScreenshotMonitor()
        else toast("通知权限被拒绝")
    }

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                repository.saveSharedImage(uri, "manual_import")
                deleteFromMediaStore(uri)
                toast("✅ 已导入工作相册")
            } catch (e: Exception) {
                toast("导入失败: ${e.message}")
            }
        }
    }

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        toast(if (result.resultCode == RESULT_OK) "✅ 系统相册已删除" else "用户取消了删除")
    }

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
                        if (image != null)
                            ImageDetailScreen(image = image, repository = repository,
                                onBack = { navController.popBackStack() })
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

    private fun handleScreenshotIntent(intent: Intent?) {
        if (intent?.action == ScreenshotMonitorService.ACTION_MOVE_SCREENSHOT) {
            val uriString = intent.getStringExtra(ScreenshotMonitorService.EXTRA_IMAGE_URI)
            if (uriString != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        repository.saveSharedImage(Uri.parse(uriString), "screenshot")
                        deleteFromMediaStore(Uri.parse(uriString))
                    } catch (e: Exception) {
                        runOnUiThread { toast("移入失败: ${e.message}") }
                    }
                }
            }
        }
    }

    /**
     * 删除系统相册图片
     * 策略1 catch SecurityException 后不中止，继续策略2
     */
    private fun deleteFromMediaStore(uri: Uri) {
        // ---- 策略1：直接删除（大多数 Android 11+ 会抛 SecurityException） ----
        var directDeleted = false
        try {
            val deleted = contentResolver.delete(uri, null, null)
            if (deleted > 0) {
                directDeleted = true
                runOnUiThread { toast("🗑️ 系统相册已清理") }
                return
            }
        } catch (e: SecurityException) {
            // 预期行为：Android 11+ 不允许直接删除其他 APP 的文件
            // 不 return，继续走策略2
        }

        if (directDeleted) return

        // ---- 策略2：MediaStore.createDeleteRequest（弹出系统确认框） ----
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                if (pi != null) {
                    runOnUiThread {
                        deleteRequestLauncher.launch(
                            IntentSenderRequest.Builder(pi.intentSender).build()
                        )
                    }
                    return
                }
                runOnUiThread { toast("❌ createDeleteRequest 返回 null") }
            } else {
                runOnUiThread { toast("⚠️ SDK<30，需要手动删除") }
            }
        } catch (e: Exception) {
            runOnUiThread { toast("💥 ${e.message}") }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissionsAndStartMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_IMAGES))
            } else startScreenshotMonitor()
        } else startScreenshotMonitor()
    }

    private fun startScreenshotMonitor() {
        ScreenshotMonitorService.start(this)
    }
}