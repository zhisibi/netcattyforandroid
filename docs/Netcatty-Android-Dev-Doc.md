# Netcatty Android 开发文档

> **项目名称**: Netcatty Android (暂定名: Netcatty Mobile)  
> **仓库**: 待建  
> **基于**: [Netcatty](https://github.com/binaricat/Netcatty) (GPL-3.0)  
> **技术栈**: Kotlin 2.0+ / Jetpack Compose / Material 3 / JSch / Room  
> **文档版本**: v1.0 — 2026-04-08

---

## 目录

1. [项目概述](#1-项目概述)
2. [架构设计](#2-架构设计)
3. [技术选型详述](#3-技术选型详述)
4. [项目结构](#4-项目结构)
5. [核心模块开发指南](#5-核心模块开发指南)
6. [数据模型](#6-数据模型)
7. [UI/UX 设计规范](#7-uiux-设计规范)
8. [构建环境](#8-构建环境)
9. [开发路线图](#9-开发路线图)
10. [测试策略](#10-测试策略)
11. [已知约束与注意事项](#11-已知约束与注意事项)
12. [许可证](#12-许可证)

---

## 1. 项目概述

### 1.1 产品定位

Netcatty Android 是桌面端 Netcatty 的移动端版本，面向需要在移动设备上管理远程服务器的开发者、运维工程师和 DevOps 人员。

**核心价值主张**：
- 移动端 SSH 终端，触屏友好
- SFTP 文件管理，随时随地上传/下载
- AI 辅助服务器运维（自然语言执行命令）
- 云端多设备同步配置

### 1.2 功能范围

| 功能 | 优先级 | Phase |
|------|--------|-------|
| SSH 连接 + 终端 | P0 | 1 |
| 主机管理 (Vault) | P0 | 1 |
| 密码/密钥认证 | P0 | 1 |
| SFTP 文件浏览 | P0 | 2 |
| 文件上传/下载 | P0 | 2 |
| 分屏终端 | P1 | 3 |
| 端口转发 | P1 | 3 |
| 快捷命令 (Snippet) | P1 | 3 |
| 自定义主题 | P1 | 3 |
| 加密存储 + 生物识别 | P1 | 4 |
| 云同步 | P2 | 4 |
| AI Chat | P2 | 5 |
| 串口连接 | P3 | 6 |

### 1.3 非目标 (Out of Scope)

- 本地终端 (Android 无 PTY)
- Mosh 协议 (Phase 6 考虑)
- Monaco 代码编辑器 (使用轻量替代)
- Electron 特有功能 (系统托盘、全局快捷键等)

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────┐
│                 Android App                  │
├─────────────────────────────────────────────┤
│  UI Layer (Compose + Material 3)            │
│  ├── screens/                                │
│  │   ├── vault/     (主机管理)               │
│  │   ├── terminal/  (终端)                   │
│  │   ├── sftp/      (文件管理)               │
│  │   ├── settings/  (设置)                   │
│  │   └── ai/        (AI Chat)                │
│  └── components/                             │
├─────────────────────────────────────────────┤
│  ViewModel Layer (StateFlow + ViewModel)    │
├─────────────────────────────────────────────┤
│  Domain Layer (Use Cases + Models)          │
│  ├── model/         (数据模型)               │
│  ├── usecase/       (业务逻辑)               │
│  └── repository/    (仓库接口)               │
├─────────────────────────────────────────────┤
│  Data Layer (Room + JSch + Retrofit)        │
│  ├── local/         (Room DAO + Entity)      │
│  ├── remote/        (JSch SSH + SFTP)        │
│  ├── ai/            (Retrofit AI API)        │
│  └── sync/          (云同步适配器)            │
├─────────────────────────────────────────────┤
│  Platform Layer (Android SDK)               │
│  ├── Keystore / BiometricPrompt             │
│  ├── Foreground Service (SSH/SFTP 长连接)    │
│  └── Notification (连接状态/传输进度)         │
└─────────────────────────────────────────────┘
```

### 2.2 数据流

```
UI (Compose) ──event──▶ ViewModel ──usecase──▶ Repository
    ▲                                         │
    │                                         ▼
    └─────── StateFlow ◀──── Data Source (Room/JSch/Network)
```

### 2.3 SSH 连接生命周期

```
用户点击连接
    │
    ▼
HostRepository.get(id) ──▶ 获取主机配置
    │
    ▼
SshSessionManager.connect(host) ──▶ JSch Session
    │                                       │
    ▼                                       ▼
TerminalScreen         ◀──bridge──▶   SshChannelShell
    │                                       │
    │ (TerminalInput → write)                │ (read → TerminalOutput)
    ▼                                       ▼
Compose TerminalView              JSch Channel InputStream
```

---

## 3. 技术选型详述

### 3.1 SSH 库: JSch

**选择 JSch 而非 ssh2-javascript 或 Kotlin-ssh2 的理由**：

| 对比项 | JSch 0.2.x | Kotlin-ssh2 | Apache MINA SSHD |
|--------|-----------|-------------|-----------------|
| 纯 Java/Kotlin | ✅ | ✅ | ✅ |
| Android 兼容 | ✅ 成熟 | 🟡 较新 | 🟡 较重 |
| SFTP 支持 | ✅ | ✅ | ✅ |
| 密钥格式支持 | ✅ (OpenSSH/PuTTY/PKCS8) | 🟡 | ✅ |
| 社区活跃度 | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| 体积 | ~300KB | ~200KB | ~2MB+ |
| sbssh 项目验证 | ✅ | ❌ | ❌ |

**依赖**:
```kotlin
implementation("com.github.mwiede:jsch:0.2.16")
```

### 3.2 终端渲染: Termux terminal-emulator

Termux 的 `terminal-emulator` 库是 Android 上最成熟的终端渲染方案：

- 处理 ESC 序列解析、字符渲染、滚动、选区
- 支持自定义配色方案
- 支持多种字体
- Compose 可通过 `AndroidView` 嵌入

**依赖**:
```kotlin
implementation("com.termux:terminal-emulator:v0.118.0")
```

**Compose 嵌入方式**:
```kotlin
@Composable
fun TerminalView(
    terminalSession: TerminalSession,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            TerminalView(context).apply {
                attachSession(terminalSession)
            }
        },
        modifier = modifier
    )
}
```

### 3.3 数据存储: Room + 字段级 AES-GCM 加密

参考 sbssh 项目的加密方案：

- **Room** 作为本地数据库（SQLite）
- **AES-GCM** 字段级加密敏感字段（密码、私钥）
- **PBKDF2** 从用户密码派生会话密钥
- **Android Keystore** 存储 PBKDF2 salt + 密钥哈希
- **SessionKeyHolder** 内存中持有派生密钥

```kotlin
@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey val id: String,
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    // 加密字段 — 存储为 Base64 编码的 AES-GCM 密文
    val passwordEncrypted: String? = null,
    val identityFileEncrypted: String? = null,
    val group: String? = null,
    val tags: String = "[]",  // JSON array
    // ...
)
```

### 3.4 依赖注入: Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SshModule {
    @Provides
    @Singleton
    fun provideSshSessionManager(
        fieldCryptoManager: FieldCryptoManager
    ): SshSessionManager = SshSessionManager(fieldCryptoManager)
}
```

---

## 4. 项目结构

```
netcatty-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/netcatty/mobile/
│   │   │   ├── NetcattyApp.kt                    # Application
│   │   │   ├── MainActivity.kt                    # 单 Activity
│   │   │   ├── di/                                # Hilt Modules
│   │   │   │   ├── AppModule.kt
│   │   │   │   ├── SshModule.kt
│   │   │   │   └── DataModule.kt
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── dao/
│   │   │   │   │   │   ├── HostDao.kt
│   │   │   │   │   │   ├── KeyDao.kt
│   │   │   │   │   │   ├── SnippetDao.kt
│   │   │   │   │   │   └── PortForwardingDao.kt
│   │   │   │   │   └── entity/
│   │   │   │   │       ├── HostEntity.kt
│   │   │   │   │       ├── SshKeyEntity.kt
│   │   │   │   │       ├── SnippetEntity.kt
│   │   │   │   │       └── PortForwardingRuleEntity.kt
│   │   │   │   ├── remote/
│   │   │   │   │   ├── ssh/
│   │   │   │   │   │   ├── SshSessionManager.kt
│   │   │   │   │   │   ├── SshConnection.kt
│   │   │   │   │   │   ├── JschConfig.kt
│   │   │   │   │   │   └── SshAuthHelper.kt
│   │   │   │   │   ├── sftp/
│   │   │   │   │   │   ├── SftpClient.kt
│   │   │   │   │   │   ├── SftpTransferManager.kt
│   │   │   │   │   │   └── SftpFileEntry.kt
│   │   │   │   │   └── portforward/
│   │   │   │   │       └── PortForwardingManager.kt
│   │   │   │   ├── ai/
│   │   │   │   │   ├── AiApiClient.kt
│   │   │   │   │   ├── AiProviderConfig.kt
│   │   │   │   │   └── AiChatService.kt
│   │   │   │   ├── sync/
│   │   │   │   │   ├── SyncManager.kt
│   │   │   │   │   ├── adapters/
│   │   │   │   │   │   ├── GithubGistAdapter.kt
│   │   │   │   │   │   ├── WebdavAdapter.kt
│   │   │   │   │   │   └── S3Adapter.kt
│   │   │   │   │   └── SyncMergeStrategy.kt
│   │   │   │   └── crypto/
│   │   │   │       ├── FieldCryptoManager.kt
│   │   │   │       ├── SessionKeyHolder.kt
│   │   │   │       └── BiometricHelper.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── Host.kt
│   │   │   │   │   ├── SshKey.kt
│   │   │   │   │   ├── Snippet.kt
│   │   │   │   │   ├── HostGroup.kt
│   │   │   │   │   ├── PortForwardingRule.kt
│   │   │   │   │   ├── TerminalTheme.kt
│   │   │   │   │   ├── TerminalSession.kt
│   │   │   │   │   ├── SftpConnection.kt
│   │   │   │   │   ├── TransferTask.kt
│   │   │   │   │   └── AiProvider.kt
│   │   │   │   ├── repository/
│   │   │   │   │   ├── HostRepository.kt
│   │   │   │   │   ├── KeyRepository.kt
│   │   │   │   │   ├── SnippetRepository.kt
│   │   │   │   │   └── SettingsRepository.kt
│   │   │   │   └── usecase/
│   │   │   │       ├── ConnectSshUseCase.kt
│   │   │   │       ├── StartSftpUseCase.kt
│   │   │   │       ├── TransferFileUseCase.kt
│   │   │   │       ├── StartPortForwardUseCase.kt
│   │   │   │       └── AiChatUseCase.kt
│   │   │   ├── ui/
│   │   │   │   ├── navigation/
│   │   │   │   │   └── NavGraph.kt
│   │   │   │   ├── screens/
│   │   │   │   │   ├── vault/
│   │   │   │   │   │   ├── VaultScreen.kt
│   │   │   │   │   │   ├── VaultViewModel.kt
│   │   │   │   │   │   ├── HostCard.kt
│   │   │   │   │   │   ├── GroupTreeView.kt
│   │   │   │   │   │   └── HostDetailPanel.kt
│   │   │   │   │   ├── terminal/
│   │   │   │   │   │   ├── TerminalScreen.kt
│   │   │   │   │   │   ├── TerminalViewModel.kt
│   │   │   │   │   │   ├── SplitTerminalLayout.kt
│   │   │   │   │   │   ├── TabBar.kt
│   │   │   │   │   │   └── TerminalToolbar.kt
│   │   │   │   │   ├── sftp/
│   │   │   │   │   │   ├── SftpScreen.kt
│   │   │   │   │   │   ├── SftpViewModel.kt
│   │   │   │   │   │   ├── DualPaneLayout.kt
│   │   │   │   │   │   ├── FileList.kt
│   │   │   │   │   │   └── TransferQueue.kt
│   │   │   │   │   ├── settings/
│   │   │   │   │   │   ├── SettingsScreen.kt
│   │   │   │   │   │   ├── ThemeSettings.kt
│   │   │   │   │   │   └── AiProviderSettings.kt
│   │   │   │   │   └── ai/
│   │   │   │   │       ├── AiChatScreen.kt
│   │   │   │   │       └── AiChatViewModel.kt
│   │   │   │   ├── components/
│   │   │   │   │   ├── NetcattyTerminalView.kt
│   │   │   │   │   ├── PasswordDialog.kt
│   │   │   │   │   ├── KeyPickerDialog.kt
│   │   │   │   │   ├── SearchBar.kt
│   │   │   │   │   └── ConfirmDialog.kt
│   │   │   │   └── theme/
│   │   │   │       ├── Theme.kt
│   │   │   │       ├── Color.kt
│   │   │   │       └── TerminalThemes.kt
│   │   │   └── service/
│   │   │       ├── SshConnectionService.kt       # 前台服务保活
│   │   │       └── SftpTransferService.kt         # 文件传输服务
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   └── strings.xml                    # 英文
│   │   │   ├── values-zh/
│   │   │   │   └── strings.xml                    # 中文
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 5. 核心模块开发指南

### 5.1 SSH 连接管理

#### SshSessionManager

```kotlin
@Singleton
class SshSessionManager @Inject constructor(
    private val fieldCryptoManager: FieldCryptoManager
) {
    private val sessions = ConcurrentHashMap<String, SshConnection>()
    private val jsch = JSch()

    suspend fun connect(host: Host): Result<TerminalSession> = withContext(Dispatchers.IO) {
        try {
            val session = jsch.getSession(host.username, host.hostname, host.port)

            // 配置认证
            when (host.authMethod) {
                AuthMethod.PASSWORD -> {
                    val password = host.passwordEncrypted?.let {
                        fieldCryptoManager.decrypt(it)
                    }
                    session.setPassword(password)
                }
                AuthMethod.KEY -> {
                    host.identityFileEncrypted?.let { encrypted ->
                        val keyContent = fieldCryptoManager.decrypt(encrypted)
                        jsch.addIdentity(host.id, keyContent.toByteArray(), null, null)
                    }
                }
                AuthMethod.CERTIFICATE -> { /* TODO */ }
            }

            // 严格主机密钥检查（可配置）
            session.setConfig("StrictHostKeyChecking", "ask")

            // 连接超时
            session.connect(30000)

            // 创建 shell channel
            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color", 80, 24, 800, 600)
            channel.connect()

            val connection = SshConnection(
                id = UUID.randomUUID().toString(),
                hostId = host.id,
                session = session,
                channel = channel,
                inputStream = channel.getInputStream(),
                outputStream = channel.getOutputStream()
            )

            sessions[connection.id] = connection

            Result.success(
                TerminalSession(
                    id = connection.id,
                    hostId = host.id,
                    hostLabel = host.label,
                    username = host.username,
                    hostname = host.hostname,
                    status = TerminalSession.Status.CONNECTED
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeToSession(sessionId: String, data: String) {
        sessions[sessionId]?.outputStream?.apply {
            write(data.toByteArray())
            flush()
        }
    }

    fun disconnect(sessionId: String) {
        sessions.remove(sessionId)?.apply {
            channel.disconnect()
            session.disconnect()
        }
    }

    fun getSession(sessionId: String): SshConnection? = sessions[sessionId]
}
```

#### 前台服务保活

```kotlin
class SshConnectionService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Netcatty")
            .setContentText("SSH 连接保持中")
            .setSmallIcon(R.drawable.ic_terminal)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "ssh_connection"
    }
}
```

### 5.2 终端渲染

#### TerminalSession 适配器 (桥接 JSch 和 Termux terminal-emulator)

```kotlin
class NetcattyTerminalSession(
    private val sshConnection: SshConnection,
    private val terminalOutput: TerminalOutput
) : TerminalSession.SessionChangedCallback {

    private val termSession = TerminalSession(
        /* 命令行 — 不需要，因为 shell 由 JSch channel 提供 */
        "/system/bin/sh",
        cwd = "/",
        env = null,
        termTranscript = terminalOutput,
        sessionChangedCallback = this
    )

    init {
        // JSch InputStream → Termux TerminalSession
        thread(name = "ssh-read-${sshConnection.id}") {
            val buffer = ByteArray(8192)
            val input = sshConnection.inputStream
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    val data = String(buffer, 0, read, Charsets.UTF_8)
                    termSession.write(data)
                }
            } catch (_: IOException) { }
        }
    }

    fun write(data: String) {
        sshConnection.outputStream.apply {
            write(data.toByteArray(Charsets.UTF_8))
            flush()
        }
    }

    // TerminalSession.SessionChangedCallback 实现
    override fun onTextChanged(changedSession: TerminalSession) { }
    override fun onTitleChanged(changedSession: TerminalSession) { }
    override fun onSessionFinished(finishedSession: TerminalSession) { }
    override fun onClipboardText(session: TerminalSession?, text: String?) { }
    override fun onBell(session: TerminalSession?) { }
    override fun onColorsChanged(session: TerminalSession?) { }
}
```

### 5.3 SFTP 文件管理

```kotlin
class SftpClient(
    private val sshSession: Session
) {
    private var channel: ChannelSftp? = null

    fun connect(): ChannelSftp {
        val ch = sshSession.openChannel("sftp") as ChannelSftp
        ch.connect()
        channel = ch
        return ch
    }

    fun listDirectory(path: String): List<SftpFileEntry> {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        val entries = mutableListOf<SftpFileEntry>()
        ch.ls(path).forEach { item ->
            val entry = item as ChannelSftp.LsEntry
            if (entry.filename in listOf(".", "..")) return@forEach
            entries.add(
                SftpFileEntry(
                    name = entry.filename,
                    type = when {
                        entry.attrs.isDir -> SftpFileEntry.Type.DIRECTORY
                        entry.attrs.isLink -> SftpFileEntry.Type.SYMLINK
                        else -> SftpFileEntry.Type.FILE
                    },
                    size = entry.attrs.size,
                    lastModified = entry.attrs.mTime * 1000L,
                    permissions = entry.attrs.permissionsString
                )
            )
        }
        return entries.sortedWith(compareBy({ it.type != SftpFileEntry.Type.DIRECTORY }, { it.name }))
    }

    fun download(remotePath: String, localPath: String, monitor: SftpProgressMonitor? = null) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.get(remotePath, localPath, monitor)
    }

    fun upload(localPath: String, remotePath: String, monitor: SftpProgressMonitor? = null) {
        val ch = channel ?: throw IllegalStateException("SFTP not connected")
        ch.put(localPath, remotePath, monitor)
    }

    fun disconnect() {
        channel?.disconnect()
        channel = null
    }
}
```

### 5.4 端口转发

```kotlin
class PortForwardingManager @Inject constructor(
    private val sshSessionManager: SshSessionManager
) {
    data class Tunnel(
        val id: String,
        val ruleId: String,
        val type: PortForwardingType,
        val localPort: Int,
        val remoteHost: String?,
        val remotePort: Int?,
        val status: Status
    ) {
        enum class Status { INACTIVE, CONNECTING, ACTIVE, ERROR }
    }

    private val tunnels = ConcurrentHashMap<String, Tunnel>()

    fun startLocalForward(sessionId: String, localPort: Int, remoteHost: String, remotePort: Int): Result<Tunnel> {
        return try {
            val connection = sshSessionManager.getSession(sessionId)
                ?: return Result.failure(Exception("Session not found"))
            val portForwarding = connection.session.setPortForwardingL(localPort, remoteHost, remotePort)
            val tunnel = Tunnel(
                id = UUID.randomUUID().toString(),
                ruleId = "",
                type = PortForwardingType.LOCAL,
                localPort = localPort,
                remoteHost = remoteHost,
                remotePort = remotePort,
                status = Tunnel.Status.ACTIVE
            )
            tunnels[tunnel.id] = tunnel
            Result.success(tunnel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopForwarding(sessionId: String, localPort: Int) {
        val connection = sshSessionManager.getSession(sessionId) ?: return
        connection.session.delPortForwardingL(localPort)
    }
}
```

### 5.5 AI Chat 服务

```kotlin
class AiChatService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun streamChat(
        provider: AiProvider,
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit
    ): Flow<String> = flow {
        val request = Request.Builder()
            .url(provider.apiEndpoint)
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(createChatRequestBody(provider.model, messages).toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        val reader = response.body?.byteStream()?.bufferedReader() ?: return@flow

        reader.use { r ->
            var line: String?
            while (r.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                val content = parseSseContent(data) ?: continue
                emit(content)
                onChunk(content)
            }
        }
    }.flowOn(Dispatchers.IO)
}
```

---

## 6. 数据模型

### 6.1 Host (对应 Netcatty 的 Host)

```kotlin
enum class AuthMethod { PASSWORD, KEY, CERTIFICATE }
enum class HostProtocol { SSH, TELNET, LOCAL, SERIAL }
enum class DeviceType { GENERAL, NETWORK }

data class Host(
    val id: String,
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    val passwordEncrypted: String? = null,
    val identityFileId: String? = null,
    val group: String? = null,
    val tags: List<String> = emptyList(),
    val os: String = "linux",
    val deviceType: DeviceType = DeviceType.GENERAL,
    val protocol: HostProtocol = HostProtocol.SSH,
    val agentForwarding: Boolean = false,
    val startupCommand: String? = null,
    val proxyConfig: ProxyConfig? = null,
    val hostChain: List<String> = emptyList(),  // Jump host IDs
    val envVars: List<EnvVar> = emptyList(),
    val charset: String = "UTF-8",
    val themeId: String? = null,
    val fontFamily: String? = null,
    val fontSize: Int? = null,
    val distro: String? = null,
    val keepaliveInterval: Int = 0,
    val legacyAlgorithms: Boolean = false,
    val pinned: Boolean = false,
    val lastConnectedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sftpBookmarks: List<SftpBookmark> = emptyList(),
    val keywordHighlightRules: List<KeywordHighlightRule> = emptyList()
)
```

### 6.2 SshKey (对应 Netcatty 的 SSHKey)

```kotlin
enum class KeyType { RSA, ECDSA, ED25519 }
enum class KeyCategory { KEY, CERTIFICATE, IDENTITY }

data class SshKey(
    val id: String,
    val label: String,
    val type: KeyType,
    val keySize: Int? = null,
    val privateKeyEncrypted: String,  // AES-GCM 加密
    val publicKey: String? = null,
    val certificate: String? = null,
    val passphraseEncrypted: String? = null,
    val category: KeyCategory = KeyCategory.KEY,
    val created: Long = System.currentTimeMillis()
)
```

### 6.3 TerminalTheme (对应 Netcatty 的 TerminalTheme)

```kotlin
data class TerminalTheme(
    val id: String,
    val name: String,
    val type: ThemeType,  // DARK / LIGHT
    val isCustom: Boolean = false,
    val colors: TerminalColors
)

data class TerminalColors(
    val background: String,
    val foreground: String,
    val cursor: String,
    val selection: String,
    val black: String, val red: String, val green: String, val yellow: String,
    val blue: String, val magenta: String, val cyan: String, val white: String,
    val brightBlack: String, val brightRed: String, val brightGreen: String, val brightYellow: String,
    val brightBlue: String, val brightMagenta: String, val brightCyan: String, val brightWhite: String
)
```

### 6.4 Snippet (对应 Netcatty 的 Snippet)

```kotlin
data class Snippet(
    val id: String,
    val label: String,
    val command: String,
    val tags: List<String> = emptyList(),
    val targetHostIds: List<String> = emptyList(),
    val shortcutKey: String? = null,
    val noAutoRun: Boolean = false
)
```

---

## 7. UI/UX 设计规范

### 7.1 导航结构

```
Bottom Navigation Bar:
┌──────────┬──────────┬──────────┬──────────┐
│  🗂 Vault │  🖥 Term  │  📁 SFTP │  ⚙ Setup │
└──────────┴──────────┴──────────┴──────────┘

Vault Screen:
┌─────────────────────────────┐
│ 🔍 搜索主机...              │
├─────────────────────────────┤
│ ⭐ 置顶主机                  │
│  ┌──────┐ ┌──────┐ ┌──────┐│
│  │ 🐧   │ │ 🍎   │ │ 🪟   ││
│  │Prod  │ │Dev   │ │Win   ││
│  │Server│ │Mac   │ │DB    ││
│  └──────┘ └──────┘ └──────┘│
├─────────────────────────────┤
│ 📁 生产环境                  │
│  ┌──────┐ ┌──────┐         │
│  │ 🐧   │ │ 🐧   │         │
│  │Web-1 │ │Web-2 │         │
│  └──────┘ └──────┘         │
└─────────────────────────────┘

Terminal Screen:
┌─────────────────────────────┐
│ Tab1 │ Tab2 │ + │ AI 🤖   │ ← 顶部Tab栏
├─────────────────────────────┤
│                             │
│  $ ssh user@host            │
│  Last login: ...            │
│  $ █                        │
│                             │
│                             │
├─────────────────────────────┤
│ ⌨ │ 📋 │ ⚡ │ 📡 │ ...    │ ← 底部工具栏
└─────────────────────────────┘
```

### 7.2 移动端特殊交互

| 交互 | 实现方式 |
|------|----------|
| 终端输入 | 底部浮动输入栏 + 特殊键行 (Tab/Ctrl/Esc/↑↓) |
| 复制粘贴 | 长按选区 → 弹出菜单 |
| 分屏 | 仅横屏支持，左/右各占 50% |
| SFTP 操作 | 长按文件/文件夹 → Context Menu |
| 文件上传 | 右下角 FAB → 选择文件 |
| AI Chat | 终端侧边抽屉，从右侧滑出 |

### 7.3 配色

参考 Netcatty 的暗色/亮色主题，使用 Material 3 动态配色（Dynamic Color），同时支持自定义终端主题（从桌面版移植主题数据）。

---

## 8. 构建环境

### 8.1 开发环境要求

- **JDK**: 21
- **Android SDK**: API 34+ (platforms-34, build-tools-34.0.0)
- **Kotlin**: 2.0.21+
- **Gradle**: 8.11+
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35

### 8.2 阿里云 Maven 镜像

所有 `build.gradle.kts` 必须配置阿里云 Maven 镜像（Google Maven 在国内不可达）：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    google()
    mavenCentral()
}
```

### 8.3 签名

- Debug: 默认 debug keystore
- Release: 需要 `.jks` 签名文件

### 8.4 ProGuard 规则

```proguard
# JSch
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Termux terminal-emulator
-keep class com.termux.terminal.** { *; }
```

---

## 9. 开发路线图

| Phase | 时间 | 里程碑 | 交付物 |
|-------|------|--------|--------|
| 0 | 第 1 周 | 项目搭建 | 空项目 + CI + Hilt + Room + JSch 集成 |
| 1 | 第 2-5 周 | 核心 SSH | 主机管理 + SSH连接 + 终端渲染 + 密码/密钥认证 |
| 2 | 第 6-8 周 | SFTP | 双面板浏览器 + 文件传输 + 内置编辑器 |
| 3 | 第 9-11 周 | 高级终端 | 分屏 + Tab + 端口转发 + Snippet + 主题 |
| 4 | 第 12-13 周 | 安全+同步 | 加密存储 + 生物识别 + 云同步 |
| 5 | 第 14-15 周 | AI | AI Chat + 多 Provider |
| 6 | 第 16-17 周 | 发布准备 | 串口 + Widget + 多语言 + Play Store 上架 |

---

## 10. 测试策略

### 10.1 单元测试

| 模块 | 测试框架 | 覆盖重点 |
|------|----------|----------|
| Domain Model | JUnit 5 | 数据模型转换、同步合并逻辑 |
| Repository | JUnit 5 + MockK | CRUD 操作、加密/解密流程 |
| SSH Session | Robolectric + MockK | 连接/断开/重连逻辑 |
| AI Service | MockWebServer | SSE 流式响应解析 |

### 10.2 集成测试

| 场景 | 方式 |
|------|------|
| SSH 连接真实服务器 | 本地 Docker OpenSSH 容器 |
| SFTP 文件操作 | 同上 |
| 端口转发 | 本地 echo server |
| 云同步 | Mock GitHub Gist API |

### 10.3 UI 测试

- Compose UI 测试 (`createComposeRule`)
- 关键路径 E2E: 添加主机 → 连接 → 执行命令 → 断开

---

## 11. 已知约束与注意事项

### 11.1 Android 沙箱限制

- **无 PTY fork**: Android 应用无法 `fork()` 子进程，不能运行本地 shell — 需完全依赖远程 SSH
- **后台限制**: Android 12+ 对前台服务限制更严，SSH 长连接需 `FOREGROUND_SERVICE_CONNECTED_DEVICE` 权限
- **网络安全性**: Android 9+ 默认禁止明文 HTTP，AI API 需 HTTPS

### 11.2 JSch 注意事项

- JSch 的 `setKnownHosts()` 需要正确处理 host key 验证
- 大文件 SFTP 传输需用 `SftpProgressMonitor` 实现进度回调
- JSch 不原生支持 Ed25519 密钥的 PuTTY PPK 格式，需转换

### 11.3 终端渲染注意

- Termux terminal-emulator 是 View（不是 Compose），需用 `AndroidView` 包裹
- 软键盘弹出时需调整终端区域高度
- 长按选区与 Compose 手势系统可能冲突

### 11.4 i18n

- 从 Netcatty 移植翻译时，注意移动端特有字符串需额外翻译
- 建议使用 Android Studio 的 Translations Editor 管理

---

## 12. 许可证

Netcatty 原项目使用 **GPL-3.0** 许可证。Android 版本作为衍生作品，同样必须使用 **GPL-3.0-or-later** 开源。

---

*文档版本 v1.0 — 2026-04-08 — 大龙虾 🦞*
