# 工作相册 - 开发指南

> 适配 ColorOS 16（OPPO 手机）

---

## 环境配置

| 项目 | 路径 | 说明 |
|------|------|------|
| Java | `D:\Android Developers Tool\jbr` | JDK 21 |
| SDK | `D:\Android\Sdk` | API 36 |
| Gradle | `.gradle-local\gradle-8.9` | 绕过 wrapper jar |

### 重要：代理问题

**关闭 Clash Verge！** Clash 会劫持 `dl.google.com` 导致 AGP 插件下载失败。

```
❌ Clash 开启 → BUILD FAILED (AGP plugin not found)
✅ Clash 关闭 → BUILD SUCCESSFUL
```

---

## 编译和安装

```powershell
# 环境变量
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:JAVA_HOME = "D:\Android Developers Tool\jbr"

# 编译
cd D:\AI_Coding\工作相册
.\gradlew.bat clean assembleDebug

# 安装
adb install -r app\build\outputs\apk\debug\app-debug.apk

# ⚠️ 安装后必须 force-stop + 启动才能加载新代码
adb shell am force-stop com.workalbum.app.debug
adb shell am start -n com.workalbum.app.debug/com.workalbum.app.MainActivity
```

---

## ColorOS 已知问题和解决方案

### 问题1：通知 action 按钮失效

**现象**：ColorOS 禁用 `PendingIntent.getBroadcast()` 的通知按钮。

**解决**：不用 `addAction` 按钮，改用 `setContentIntent` + `PendingIntent.getActivity()`。
用户**点击通知体本身**触发导入，而非点击按钮。

```
❌ addAction + PendingIntent.getBroadcast()    → 按钮点了没反应
✅ setContentIntent + PendingIntent.getActivity() → 点击通知 → 启动 MainActivity 处理
```

### 问题2：gradle.properties UTF-8 BOM

**解决**：用 `[System.IO.File]::WriteAllText(..., [System.Text.UTF8Encoding]::new($false))` 写文件（无 BOM）。

### 问题3：项目路径含中文

`gradle.properties` 需要 `android.overridePathCheck=true`

### 问题4：微信入口

微信长按图片的菜单中没有「分享」选项（微信限制）。
可用入口：
- ✅ 微信全屏看图 → 右上角「···」→ 「用其他应用打开」→ 选择「工作相册」
- ✅ 手动打开工作相册 APP → 右下角 ⊕ 按钮从系统相册导入

### 问题5：系统相册原图删除

Android 11+ 不允许直接删除其他 APP 创建的媒体文件。
使用双重策略：
1. 先尝试 `contentResolver.delete()`（部分设备可行）
2. 失败则调用 `MediaStore.createDeleteRequest()` 弹出系统确认对话框

---

## 项目结构

```
工作相册/
├── agents.md                     # AI 编码指导
├── TODO.md                       # 待办清单
├── GUIDE.md                      # ← 本文件
├── .gitignore
├── build.gradle.kts              # AGP 8.7.3 + Kotlin 2.0.21
├── settings.gradle.kts
├── gradle.properties             # 必须 ASCII 编码
├── gradlew.bat                   # 指向本地 Gradle
├── local.properties              # sdk.dir=D:\\Android\\Sdk
├── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/...
        └── java/com/workalbum/app/
            ├── MainActivity.kt           # 处理截图intent + 手动导入 + 删除系统原图
            ├── WorkAlbumApplication.kt   # 初始化 Repository
            ├── model/WorkImage.kt        # Room Entity
            ├── data/
            │   ├── ImageDatabase.kt
            │   ├── ImageDao.kt
            │   └── ImageRepository.kt    # 图片文件+数据库操作
            ├── receiver/
            │   ├── ShareReceiverActivity.kt  # 分享+用其他应用打开 入口
            │   └── BootReceiver.kt
            ├── service/
            │   └── ScreenshotMonitorService.kt  # 截图监控(ContentObserver → 通知)
            └── ui/
                ├── theme/Theme.kt
                └── screens/
                    ├── GalleryScreen.kt      # 图片列表 + FAB导入
                    ├── ImageDetailScreen.kt  # 详情+备注
                    └── SettingsScreen.kt     # 设置+调试
```

---

## 三大入口对比

| 入口 | 触发方式 | 状态 |
|------|---------|------|
| 截图自动 | 截图→通知→点击通知体 | ✅ 已验证 |
| 手动导入 | APP内 ⊕ FAB | ✅ 新增 |
| 微信「用其他应用打开」 | 微信全屏图→···→用其他应用打开 | ✅ 新增 |
| 微信「分享」 | 微信长按→分享 | ❌ 微信无此选项 |

---

## 当前检查清单

- [x] 截图→通知→点击通知体→导入（已通）
- [ ] 截图→导入后→系统相册原图自动删除（待验证）
- [ ] 微信「用其他应用打开」→工作相册→弹窗保存（待验证）
- [ ] 手动 ⊕ FAB→选图→导入（待验证）
- [ ] 关闭APP后服务被杀→截图通知消失（已知问题，需引导用户锁定后台）
- [ ] Git 初始化