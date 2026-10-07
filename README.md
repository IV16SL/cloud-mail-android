# Cloud Mail for Android（MVP）

cloud-mail 的原生 Kotlin 安卓客户端（Jetpack Compose），直接调用后端同一套 REST API，后端零改动。

## 功能（MVP）

- 登录：邮箱 + 密码；支持 TOTP 两步验证（第二步输 6 位码）
- 登录页右上角齿轮 → 填写**服务器地址**（自部署，每人地址不同，不能写死；首次启动自动弹出）
- 收件箱 / 发件箱、账号切换、下拉刷新、上拉加载更多
- 邮件详情（HTML 用 WebView 渲染，JS 已禁用）
- 写信：收件人/抄送/密送（逗号分隔）、主题、正文
- 标记已读、删除邮件
- 设置页：改服务器地址（换地址自动登出）、退出登录
- token 本地持久化，401 自动踢回登录页

二期（未做）：注册、附件、星标、TOTP 绑定管理、Passkey、OAuth 登录、PGP、管理员功能、FCM 推送。

## 构建

### 环境要求

- Android Studio（Hedgehog 或更新，2024+ 的版本都行）
- JDK 17（Android Studio 自带，Gradle 会用它）
- Android SDK：compileSdk 34（首次打开项目时 Studio 会提示下载）

### 步骤

1. 用 Android Studio 打开 `cloud-mail-android` 目录（选 Open，选中这个文件夹）。
2. 等待 Gradle Sync 完成（首次会自动下载依赖，需要联网）。
3. 接上安卓真机（开 USB 调试）或启动一个模拟器。
4. 点顶部工具栏的 **Run ▶**（或 Shift+F10），选择 `app`。

### 命令行构建（可选）

```bash
cd cloud-mail-android
./gradlew assembleDebug        # 需要先配好 ANDROID_HOME / local.properties
```

Windows 上是 `gradlew.bat assembleDebug`。APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

> 注意：项目里没有 gradle wrapper（`gradlew`），因为生成 wrapper 需要本地有 Gradle。
> Android Studio 打开项目时会自动处理；命令行构建的话，在 Studio 里点
> `File → Settings → Build Tools → Gradle` 确认，或用系统 Gradle 7.6+ 执行。

## 使用

1. 首次启动会弹出服务器地址设置，填你的 cloud-mail 部署地址，如 `https://mail.example.com`。
2. 用邮箱 + 密码登录（和网页版同一套账号）。
3. 如果账号开了 TOTP，会进入第二步输验证码。

## 接口文档

后端接口清单见 cloud-mail 仓库根目录的 `API_ANDROID.md`（含鉴权、分页、特殊流程说明）。

## 已知限制

- 邮件 HTML 直接用 WebView 展示（已禁用 JS、不注入原生接口），和网页版的 DOMPurify 清洗不是同一强度，不要点可疑链接。
- 新邮件靠手动刷新，推送要等二期 FCM。
- 没做深色模式、没做附件上传下载。
