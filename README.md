# Cloud Mail for Android

cloud-mail 的原生 Kotlin 安卓客户端（Jetpack Compose + Material3），直接调用后端同一套 REST API，后端零改动。

## 功能

- **登录**：邮箱 + 密码；支持 TOTP 两步验证；登录页右上角可填服务器地址（自部署，首次启动自动弹出）
- **多账号**：抽屉顶部切换账号，添加/退出账号，会话按用户隔离
- **邮件**：收件箱 / 已发送 / 星标 / 草稿箱，下拉刷新、上拉加载更多
- **邮件详情**：HTML 用 WebView 渲染（JS 已禁用）
- **写信**：收件人/抄送/密送、主题、正文、附件
- **PGP**：生成/导入密钥（RSA 3072 或 Ed25519），加密发送，解密查看
- **设置**：主题（跟随系统/浅色/深色）、主题色（图标蓝/Material You 动态取色）、头像、修改密码
- **开屏**：圆形扩散动画

## 构建

### GitHub Actions（推荐）

Push 到 main 分支自动构建，APK 在 Actions → Artifacts 下载：
- `app-debug`：调试包
- `app-release-unsigned`：未签名 release 包

### 本地构建

环境要求：Android Studio（AGP 8.5.2 / Kotlin 2.0.21 / compileSdk 34 / JDK 17）

```bash
# Debug
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk

# Release（需先配置签名）
./gradlew assembleRelease
```

## 版本

当前：v0.1.2-Beta（versionCode 3）

## 相关

- 后端原仓库：[maillab/cloud-mail](https://github.com/maillab/cloud-mail)
- 本 App 适配的 fork：[IV16SL/cloud-mail](https://github.com/IV16SL/cloud-mail)（部分功能如 PGP、头像、capabilities 接口暂时只在 fork 中实现）
- API 文档见后端仓库 `API_ANDROID.md`
