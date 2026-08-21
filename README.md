# WeChatRedCoverFix

微信自定义红包封面隐藏模块（**LSPosed / libxposed API 102**，无 UI，无需重启设备）。

## 解决的问题

微信的**自定义红包封面**在聊天消息里会渲染出封面图（控件：`c7q`=0x7f090fb2 封面图、`c7r`=0x7f090fb3 纹理层、`ccf`=0x7f091085 气泡尾巴），**盖在 Monet 等美化样式之上**，导致美化效果被遮挡。

本模块在微信进程内拦截：
1. **`View.setVisibility`** —— 封面控件永远被强制 `GONE`（用 `Invoker.Type.ORIGIN` 绕过 hook 链设置，再短路原调用，无论微信想设成什么都无效）
2. **`ImageView.setImageBitmap / setImageResource / setImageDrawable`** —— 封面图片加载直接短路，连图都不加载（省内存）

## 适配

- 已在微信 **8.0.72 play**（versionCode 3084/3085）测试通过
- Android 8.0+（API 26+）
- LSPosed 框架（libxposed API 101+，如 JingMatrix LSPosed 等）

> ⚠️ 控件资源 ID 随微信版本变化，升级微信后需同步更新 `WeChatRedCoverFix.java` 里的 `WATCH_IDS`。

## 安装

1. 在 [Releases](https://github.com/Lbybbs/WeChatRedCoverFix/releases) 下载 APK
2. LSPosed 管理器 → 模块 → 启用「微信红包封面隐藏」
3. 重启微信（**无需重启设备**）
4. 作用域已通过 `META-INF/xposed/scope.list` 锁定为 `com.tencent.mm`（staticScope）

## 兼容性

- 与 RRO/Magisk 美化模块（如 MonetWeChat 系列）**可同时使用**，互不冲突
- 收红包、看红包详情等界面不受影响，仅隐藏聊天消息里的封面图层

## 构建

GitHub Actions 自动构建（push 到 main 或手动触发），产物在 Actions Artifact。
本地构建：`./gradlew assembleRelease`（需 JDK 21 + Android SDK 37）

## 诊断

模块日志 tag：`RedCoverDiag`，可用 `adb logcat -s RedCoverDiag:*` 查看拦截情况。
