# WeChatRedCoverFix

微信红包封面隐藏 LSPosed 模块（libxposed API 102，无 UI）。

## 作用
微信自定义红包封面在聊天消息里会渲染出封面图（控件 id: c7q=0x7f090fb2、c7r=0x7f090fb3、尾巴 ccf=0x7f091085），盖在 Monet 等美化样式之上。
本模块拦截 `View.setVisibility`，命中这三个控件的调用一律强制 GONE，让美化样式完整显示。

适配：微信 8.0.72（versionCode 3084/3085），Android 8.0+（API 26+）。

## 构建
GitHub Actions 自动构建（push 到 main 或手动触发 workflow_dispatch），产物在 Actions 的 Artifact 里。

本地构建：
```
./gradlew assembleRelease
```

## 安装
1. 在 LSPosed 管理器中启用本模块（作用域：微信 com.tencent.mm，已通过 scope.list 声明）
2. 重启微信（无需重启设备）

## 注意
- 资源 id 随微信版本变化，升级微信后需同步更新 `WeChatRedCoverFix.java` 中的三个 id。
- 与 RRO 美化模块（MonetWeChat 等）可同时使用，互不冲突。
