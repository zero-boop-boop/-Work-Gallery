package com.workalbum.app.receiver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.workalbum.app.WorkAlbumApplication
import com.workalbum.app.ui.theme.WorkAlbumTheme
import kotlinx.coroutines.launch

/**
 * 接收外部分享/打开的图片
 *
 * 支持的入口：
 * - ACTION_SEND / ACTION_SEND_MULTIPLE → 微信/相册的「分享」功能
 * - ACTION_VIEW → 微信的「用其他应用打开」功能（intent.data 携带图片URI）
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 解析图片 URI ---
        val imageUris: List<Uri>? = when {
            // 多图分享
            intent.action == Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)

            // 单图分享
            intent.action == Intent.ACTION_SEND ->
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { listOf(it) }

            // ★ 微信「用其他应用打开」走 ACTION_VIEW，URI 在 intent.data
            intent.action == Intent.ACTION_VIEW && intent.type != null && intent.type!!.startsWith("image/") ->
                intent.data?.let { listOf(it) }

            else -> null
        }

        if (imageUris.isNullOrEmpty()) {
            Toast.makeText(this, "未接收到图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // --- 识别来源 APP ---
        val sourceApp = detectSourceApp(intent)
        val sourceLabel = sourceLabel(sourceApp)

        setContent {
            WorkAlbumTheme {
                var showDialog by remember { mutableStateOf(true) }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { /* 禁止点外部关闭 */ },
                        title = { Text("保存到工作相册") },
                        text = {
                            Text(
                                "来源：$sourceLabel\n将保存 ${imageUris.size} 张图片\n不会出现在系统相册中"
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                showDialog = false
                                saveImagesAndFinish(imageUris, sourceApp)
                            }) {
                                Text("保存到工作相册")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                finish()
                            }) {
                                Text("取消")
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * 识别图片来源 APP
     */
    private fun detectSourceApp(intent: Intent): String {
        // 微信的「用其他应用打开」用 referrer 或 calling package
        val referrer = intent.getStringExtra("android.intent.extra.REFERRER_NAME") ?: ""
        if (referrer.contains("tencent.mm")) return "wechat"

        val pkg = intent.`package` ?: ""
        return when {
            pkg.contains("tencent.mm") -> "wechat"
            pkg.contains("tencent.mobileqq") -> "qq"
            pkg.contains("com.alibaba.android.rimet") -> "dingtalk"
            pkg.contains("chrome") || pkg.contains("mozilla") -> "browser"
            else -> "other"
        }
    }

    private fun sourceLabel(source: String) = when (source) {
        "wechat" -> "微信"
        "qq" -> "QQ"
        "dingtalk" -> "钉钉"
        "browser" -> "浏览器"
        else -> source
    }

    /**
     * 批量保存图片到工作相册私有目录
     */
    private fun saveImagesAndFinish(uris: List<Uri>, source: String) {
        val repo = (application as WorkAlbumApplication).imageRepository
        lifecycleScope.launch {
            var ok = 0
            for (uri in uris) {
                try {
                    repo.saveSharedImage(uri, source)
                    ok++
                } catch (_: Exception) {}
            }
            Toast.makeText(this@ShareReceiverActivity, "已保存 $ok 张", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}