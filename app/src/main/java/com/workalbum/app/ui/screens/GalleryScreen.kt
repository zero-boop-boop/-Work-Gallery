package com.workalbum.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.workalbum.app.data.ImageRepository
import com.workalbum.app.model.WorkImage
import java.text.SimpleDateFormat
import java.util.*

/**
 * 图片列表主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    repository: ImageRepository,
    onImageClick: (WorkImage) -> Unit,
    onSettingsClick: () -> Unit = {},
    onImportClick: () -> Unit = {}   // 新增：手动从系统相册导入按钮回调
) {
    val images by repository.getAllImages().collectAsState(initial = emptyList())
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val scope = rememberCoroutineScope()
    val isSelectionMode = selectedIds.isNotEmpty()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 统计信息
    var stats by remember { mutableStateOf(Pair(0, 0L)) }
    LaunchedEffect(images) {
        stats = repository.getStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("已选 ${selectedIds.size} 项")
                    } else {
                        Column {
                            Text("📁 工作相册")
                            Text(
                                "${stats.first} 张图片 · ${repository.formatSize(stats.second)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "取消选择")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "删除")
                        }
                    } else {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Outlined.Settings, "设置")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        // 右下角 FAB：手动导入
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = onImportClick,
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "从系统相册导入")
                }
            }
        }
    ) { padding ->
        if (images.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "还没有工作图片",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击右下角 ⊕ 从系统相册导入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "或在其他APP中选择\"分享\"→\"工作相册\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = rememberLazyListState()
            ) {
                items(images, key = { it.id }) { image ->
                    ImageListItem(
                        image = image,
                        repository = repository,
                        isSelected = image.id in selectedIds,
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (image.id in selectedIds)
                                    selectedIds - image.id
                                else
                                    selectedIds + image.id
                            } else {
                                onImageClick(image)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedIds = setOf(image.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除确认") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 张图片吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        val toDelete = images.filter { it.id in selectedIds }
                        for (img in toDelete) {
                            repository.deleteImage(img)
                        }
                        selectedIds = emptySet()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 单张图片列表项
 */
@Composable
private fun ImageListItem(
    image: WorkImage,
    repository: ImageRepository,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()) }
    val sourceLabel = when (image.source) {
        "wechat" -> "微信"
        "qq" -> "QQ"
        "dingtalk" -> "钉钉"
        "screenshot" -> "截图"
        "manual_import" -> "手动导入"
        "browser" -> "浏览器"
        "email" -> "邮件"
        "camera" -> "相机"
        else -> image.source
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .then(
                Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            ),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(repository.getImageFile(image.fileName))
                    .crossfade(true)
                    .build(),
                contentDescription = "工作图片",
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            // 图片信息
            Column(modifier = Modifier.weight(1f)) {
                // 来源标签
                Surface(
                    color = when (image.source) {
                        "screenshot" -> Color(0xFFFFF3E0)
                        "wechat" -> Color(0xFFE8F5E9)
                        "manual_import" -> Color(0xFFE3F2FD)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        sourceLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(4.dp))

                // 备注或日期
                Text(
                    text = image.note.ifEmpty { dateFormat.format(Date(image.createdAt)) },
                    style = if (image.note.isNotEmpty())
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    else
                        MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (image.note.isNotEmpty())
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.height(2.dp))

                // 尺寸和日期
                Text(
                    text = "${image.width}×${image.height} · ${repository.formatSize(image.fileSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 选择框或箭头
            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * 兼容旧版 Compose 的 combinedClickable
 */
@Composable
fun Modifier.combinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.then(
    Modifier.clickable(onClick = onClick)
)