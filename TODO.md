# TODO - 工作相册

## ✅ 已完成（MVP）
- [x] 截图自动检测 + 通知（ContentObserver → setContentIntent）
- [x] 点击通知体导入截图（ColorOS addAction 兼容方案）
- [x] 导入后删除系统相册原图（MediaStore.createDeleteRequest）
- [x] 手动 ⊕ 按钮从系统相册导入
- [x] 微信「用其他应用打开」→ 工作相册（ACTION_VIEW）
- [x] 删除无用代码 ScreenshotActionReceiver.kt
- [x] 删除无用 ACTION_IGNORE 逻辑

## 🔜 短期
- [ ] 关闭APP后监控服务被杀 → 引导用户锁定后台（ColorOS）
- [ ] 非 OPPO 手机兼容性验证
- [ ] Git 初始化

## 🔮 中期
- [ ] 图片编辑/标注
- [ ] 多格式导出（ZIP/PDF）
- [ ] 云备份（WebDAV/Google Drive）
- [ ] 图片搜索（OCR 文字识别）
- [ ] 标签/分类管理