# 工作相册 (Work Gallery)

## 项目定位
Android 工作图片隔离管理 APP。将工作图片（截图/微信/钉钉）存入 APP 私有目录，不进系统相册，保持系统相册干净。

## 技术栈
- Kotlin 2.0 + Jetpack Compose + Material 3
- Room 数据库（图片元数据 + 备注）
- Coil 图片加载
- Gradle 8.9（本地 `.gradle-local/gradle-8.9`，不用 wrapper）
- 最低 SDK 26 / 目标 SDK 36
- 包名：`com.workalbum.app`
- 图片目录：`/sdcard/Android/data/com.workalbum.app/files/images/`

## 架构（MVVM + Repository）
```
MainActivity          ← 核心调度：截图导入 + 系统删除 + 手动导入
ScreenshotMonitorService ← 后台截图监控（FileObserver秒级 + ContentObserver兜底）
ShareReceiverActivity ← 外部分享入口（SEND + VIEW）
ImageRepository       ← 数据仓库（文件IO + Room）
  ├── ImageDao       ← Room DAO
  └── ImageDatabase  ← Room DB

UI (Compose):
  GalleryScreen      ← 图片列表 + ⊕ FAB 手动导入
  ImageDetailScreen  ← 图片详情 + 备注编辑
  SettingsScreen     ← 设置 + 测试通知
```

## MVP 已实现功能
- ✅ 截图自动检测 → 通知 → 点击通知体导入
- ✅ 导入后删除系统相册原图（MediaStore.createDeleteRequest）
- ✅ ⊕ FAB 手动从系统相册选图导入
- ✅ 微信「用其他应用打开」→ 工作相册
- ✅ 图片列表 + 来源标签 + 备注
- ✅ 批量删除

## 修改指南（后续迭代往哪改）
| 想做什么 | 改哪个文件 |
|----------|-----------|
| 改 UI 颜色/主题 | `ui/theme/Theme.kt` |
| 改列表样式/布局 | `ui/screens/GalleryScreen.kt` |
| 改详情页 | `ui/screens/ImageDetailScreen.kt` |
| 加新页面 | `ui/screens/` 下新建，在 `MainActivity.kt` 的 NavHost 注册 |
| 加截图检测逻辑 | `service/ScreenshotMonitorService.kt` |
| 加新数据字段（标签/分类等）| `model/WorkImage.kt` + `data/ImageDao.kt` |
| 加图片编辑功能 | 新建 `ui/screens/EditorScreen.kt` |
| 改 APP 名称/图标 | `res/values/strings.xml` + `res/mipmap-*/` |
| 添加新权限 | `AndroidManifest.xml` + `MainActivity.kt` |
| 加外部分享入口 | `receiver/ShareReceiverActivity.kt` |
| 改数据存储路径 | `data/ImageRepository.kt` 的 `imageDir` |

## ColorOS 踩坑（必须遵守）
1. 通知栏 **不能用 addAction 按钮**（ColorOS 禁用）→ 只用 `setContentIntent` + 点击通知体
2. `contentResolver.delete()` 会**抛 SecurityException** 而非返回0 → 必须单独 try-catch 后继续执行 `MediaStore.createDeleteRequest()`
3. 微信长按菜单**没有「分享」** → 用微信全屏图 → 右上角 `···` → 「用其他应用打开」
4. `gradle.properties` **不能用 UTF-8 BOM** → 写文件用 `[System.Text.UTF8Encoding]::new($false)`
5. 项目路径含中文 → `android.overridePathCheck=true`
6. 通知 `PendingIntent` 用 `FLAG_IMMUTABLE`
7. `adb install -r` 后必须 `am force-stop` + `am start` 新代码才生效

## 编译 & 安装
```powershell
$env:ANDROID_HOME="D:\Android\Sdk"; $env:JAVA_HOME="D:\Android Developers Tool\jbr"
cd D:\AI_Coding\工作相册
# ⚠️ 编译前关闭 Clash Verge（否则 AGP 插件下载失败）
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.workalbum.app.debug
adb shell am start -n com.workalbum.app.debug/com.workalbum.app.MainActivity
# 推送 GitHub 需要开 Clash + 37890 端口代理
```

## 环境
- JDK 21: `D:\Android Developers Tool\jbr`
- SDK: `D:\Android\Sdk` (API 36)
- 测试设备: OPPO ColorOS 16 (adb: 21484eb4)
- GitHub: https://github.com/zero-boop-boop/Work-Gallery
- gh CLI: `C:\Program Files\GitHub CLI\gh.exe`