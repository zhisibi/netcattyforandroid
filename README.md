# Netcatty Android

Netcatty Android 是桌面端 Netcatty 的移动端版本，旨在为开发者、运维工程师和 DevOps 人员提供一个在移动设备上管理远程服务器的强大工具。

## ✨ 主要功能

*   **SSH 终端：** 支持密码、密钥认证，提供可靠的 SSH 连接（使用 WakeLock 保活）。
*   **SFTP 文件管理：** 浏览远程文件，支持上传、下载、新建文件夹、重命名、删除。
*   **Vault 主机管理：** 安全存储和管理 SSH 主机信息，支持分组、标签、置顶。
*   **生物识别解锁：** 可选指纹/面容解锁，提升安全性。
*   **端侧输入优化：** 改进的终端输入体验，可见输入栏，支持特殊键和键盘切换。
*   **Snippet 快捷命令：** 在终端中通过 `⚡` 按钮快速执行预设命令。
*   **端口转发：** 支持 Local, Remote, Dynamic (SOCKS) 端口转发规则管理。
*   **全局设置：** 可定制暗色模式、终端字体大小。
*   **AI 辅助：** 集成 AI 聊天功能（开发中）。

## 🚀 项目状态

Netcatty Android 正在积极开发中，已实现核心功能，并不断迭代优化。

### 近期更新亮点：

*   **SSH 连接稳定性：** 采用 WakeLock 机制代替原有的前台服务，解决了 Android 14+ 的兼容性问题，确保后台连接不中断。
*   **安全增强：** 集成了生物识别解锁功能，并完善了 Vault 的锁定/解锁流程。
*   **终端输入体验：** 引入可见输入栏，彻底解决了键盘输入不畅的问题，并支持快捷命令（Snippets）。
*   **端口转发 UI：** 实现了端口转发规则的管理界面。
*   **全局设置：** 支持暗色模式切换和终端字体大小自定义。

### 待办事项：

*   完善 SFTP 传输进度显示与通知。
*   实现端口转发的后端逻辑（实际启动/停止）。
*   集成 AI Chat 功能。
*   增加自动化测试覆盖。

## 🛠️ 构建与运行

### 构建环境

*   **Android Studio**
*   **JDK**: 21+
*   **Android SDK**: API 34+ (platforms-34, build-tools-34.0.0)
*   **Gradle**: 8.11+
*   **Min SDK**: 26 (Android 8.0)
*   **Target SDK**: 36 (Android 14+)
*   **Kotlin**: 2.0+
*   **Compose**: Material 3

### 构建步骤

1.  **克隆仓库：**
    ```bash
    git clone https://github.com/zhisibi/netcattyforandroid.git
    cd netcattyforandroid
    ```
2.  **配置 Gradle 镜像：**
    请确保在 `settings.gradle.kts` 或 `build.gradle.kts` 文件中配置了阿里云 Maven 镜像，以解决 Google Maven 访问问题：
    ```kotlin
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    ```
3.  **同步项目并构建：**
    ```bash
    ./gradlew assembleDebug
    ```
4.  **安装 APK：**
    ```bash
    adb install app/build/outputs/apk/debug/app-debug.apk
    ```

---

## 📜 许可证

Netcatty Android 项目遵循 **GPL-3.0-or-later** 许可证。

---

## 💖 贡献

欢迎提交 Issue 和 Pull Request！
