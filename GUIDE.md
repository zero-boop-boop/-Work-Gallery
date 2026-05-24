# 工作相册 - 开发指南

> 适配 ColorOS 16（OPPO 手机） ✅ MVP 已完成

---

## 环境配置

| 项目 | 路径 | 说明 |
|------|------|------|
| Java | `D:\Android Developers Tool\jbr` | JDK 21 |
| SDK | `D:\Android\Sdk` | API 36 |
| Gradle | `.gradle-local\gradle-8.9` | 绕过 wrapper jar |
| 手机 | OPPO ColorOS 16 | `adb devices` → 21484eb4 |

### 重要

**编译前关闭 Clash Verge！** 否则 AGP 插件下载失败。

---

## 编译和安装（一行命令）

```powershell
$env:ANDROID_HOME="D:\Android\Sdk"; $env:JAVA_HOME="D:\Android Developers Tool\jbr"
cd D:\AI_Coding\工作相册
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.workalbum.app.debug
adb shell am start -n com.workalbum.app.debug/com.workalbum.app.MainActivity
```

---

## ColorOS 踩坑记录

### 问题1：通知 action 按钮失效
- ❌ `addAction` 在 ColorOS 不显示
- ✅ 改用 `setContentIntent` + 点击通知体触发

### 问题2：gradle.properties UTF-8 BOM
- ❌ PowerShell `Out-File -Encoding UTF8` 会加 BOM
- ✅ 用 `[System.Text.UTF8Encoding]::new($false)` 写无 BOM 文件

### 问题3：中文路径
- `gradle.properties` 必须加 `android.overridePathCheck=true`

### 问题4：微信入口
- ❌ 微信长按→「分享」不存在
- ✅ 微信全屏图片→右上角「···」→「用其他应用打开」→ 选工作相册

### 问题5：系统相册删除 ⭐ 关键！
- ❌ `contentResolver.delete()` 在 Android 11+ 抛 SecurityException（不是返回0！）
- ✅ `MediaStore.createDeleteRequest()` 弹出系统确认框
- ⚠️ 策略1的异常必须单独 try-catch，否则策略2永远不会执行

---

## MVP 功能清单

| 功能 | 状态 |
|------|------|
| 截图自动检测（ContentObserver） | ✅ |
| 截图通知→点击通知体导入 | ✅ |
| 导入后删除系统相册原图 | ✅ |
| 手动 ⊕ 按钮从系统相册导入 | ✅ |
| 微信「用其他应用打开」→工作相册 | ✅ |
| 图片列表 + 来源标签 | ✅ |
| 图片详情 + 添加备注 | ✅ |
| 批量删除 | ✅ |
| 设置页面 + 测试通知 | ✅ |

---

## 项目结构

```
工作相册/
├── agents.md              ← AI 编码指导
├── GUIDE.md               ← 本文件
├── TODO.md                ← 待办
├── app/src/main/java/com/workalbum/app/
│   ├── MainActivity.kt            ← 核心：处理截图导入 + 系统删除
│   ├── WorkAlbumApplication.kt
│   ├── model/WorkImage.kt
│   ├── data/{ImageDao,ImageDatabase,ImageRepository}
│   ├── receiver/{ShareReceiverActivity,BootReceiver}
│   ├── service/ScreenshotMonitorService  ← 截图监听
│   └── ui/screens/{Gallery,ImageDetail,Settings}
```

---

## 后续可做的

- [ ] 关闭APP后监控服务被杀（ColorOS 杀后台），引导用户锁定
- [ ] 非 OPPO 手机兼容测试
- [ ] 图片编辑/标注功能
- [ ] 导出/备份
- [ ] Git 初始化