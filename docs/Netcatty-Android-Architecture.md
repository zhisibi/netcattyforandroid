# Netcatty Android 架构设计文档

> **版本**: v1.0  
> **日期**: 2026-04-08  
> **架构师**: 大龙虾 🦞  
> **基于**: [Netcatty Desktop](https://github.com/binaricat/Netcatty) GPL-3.0

---

## 目录

1. [架构总览](#1-架构总览)
2. [分层架构](#2-分层架构)
3. [模块划分与依赖关系](#3-模块划分与依赖关系)
4. [核心模块详细设计](#4-核心模块详细设计)
5. [数据模型](#5-数据模型)
6. [云同步系统](#6-云同步系统)
7. [安全架构](#7-安全架构)
8. [进程与服务架构](#8-进程与服务架构)
9. [导航与页面架构](#9-导航与页面架构)
10. [构建配置](#10-构建配置)
11. [包结构与模块划分](#11-包结构与模块划分)
12. [错误处理策略](#12-错误处理策略)
13. [与桌面端的互操作](#13-与桌面端的互操作)

---

## 1. 架构总览

### 1.1 设计原则

| 原则 | 说明 |
|------|------|
| **Clean Architecture** | 分层隔离：UI → Domain → Data，依赖方向单向向内 |
| **零知识云同步兼容** | 加密格式与桌面端完全一致，确保双向同步 |
| **安全优先** | 敏感字段 AES-GCM 加密、Android Keystore、生物识别 |
| **离线优先** | 所有数据本地持久化，云同步为增量操作 |
| **Compose-first** | 全 Compose UI，不使用 View 系统（终端渲染除外） |

### 1.2 技术栈确认

```
┌───────────────────────────────────────────────────────┐
│                    技术栈全景                          │
├──────────┬────────────────────────────────────────────┤
│ 语言     │ Kotlin 2.0.21                             │
│ UI       │ Jetpack Compose + Material 3              │
│ DI       │ Hilt                                      │
│ 异步     │ Kotlin Coroutines + Flow                  │
│ 网络     │ OkHttp 4.12 + Retrofit 2.11               │
│ SSH      │ JSch 0.2.16 (com.github.mwiede:jsch)     │
│ 终端     │ Termux terminal-emulator (AndroidView)     │
│ 数据库   │ Room 2.6 + SQLite                          │
│ 加密     │ Android Keystore + JCA (AES-GCM/PBKDF2)  │
│ 序列化   │ Kotlin Serialization + Gson               │
│ 导航     │ Compose Navigation                        │
│ 图片     │ Coil 3                                    │
│ 构建     │ Gradle 8.11 + AGP 8.13 + JDK 21          │
│ Min SDK  │ 26 (Android 8.0)                          │
│ Target   │ 35                                        │
└──────────┴────────────────────────────────────────────┘
```

### 1.3 系统上下文

```
                         ┌─────────────────┐
                         │   GitHub Gist   │
                         │  Google Drive   │
                         │   OneDrive      │──┐
                         │   WebDAV        │  │ HTTPS
                         │   S3            │  │
                         └─────────────────┘  │
                                              ▼
┌──────────┐    SSH     ┌──────────────────────────────┐
│ Remote   │◄──────────│     Netcatty Android          │
│ Server   │    SFTP    │                              │
│ (Linux)  │◄──────────│  ┌────────┐  ┌─────────────┐ │
└──────────┘           │  │ Room   │  │ Android     │ │
                       │  │ (Local │  │ Keystore    │ │
┌──────────┐    AI    │  │ Store) │  │ (Key Guard) │ │
│ OpenAI   │◄─────────│  └────────┘  └─────────────┘ │
│ Anthropic│   HTTP    └──────────────────────────────┘
│ Google   │  SSE
│ Ollama   │
└──────────┘
```

---

## 2. 分层架构

```
┌───────────────────────────────────────────────────────────┐
│                    Presentation Layer                      │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
│  │ Screens │ │ Compose  │ │ Navigation│ │ Theme/      │  │
│  │ (UI)    │ │ Components│ │ (NavGraph)│ │ DesignSys  │  │
│  └────┬────┘ └────┬─────┘ └─────┬────┘ └──────┬───────┘  │
│       │           │             │              │           │
│  ┌────▼───────────▼─────────────▼──────────────▼───────┐  │
│  │                  ViewModels                         │  │
│  │  (StateFlow<UiState> + 事件处理 + UseCase 调用)      │  │
│  └────────────────────┬───────────────────────────────┘  │
├───────────────────────┼───────────────────────────────────┤
│                    Domain Layer                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐    │
│  │ Models   │  │ UseCases │  │ Repository Interfaces │    │
│  │ (Entity) │  │ (业务逻辑)│  │ (抽象数据访问)        │    │
│  └──────────┘  └──────────┘  └──────────────────────┘    │
├───────────────────────────────────────────────────────────┤
│                    Data Layer                              │
│  ┌────────────┐  ┌──────────┐  ┌───────────┐             │
│  │ Repository  │  │ Data     │  │ Remote    │             │
│  │ Impls       │  │ Sources  │  │ Data      │             │
│  │             │  │          │  │ Sources   │             │
│  │ ┌────────┐ │  │ ┌──────┐ │  │ ┌───────┐ │             │
│  │ │HostRepo│ │  │ │Room  │ │  │ │JSch   │ │             │
│  │ │KeyRepo │ │  │ │DAO   │ │  │ │SSH    │ │             │
│  │ │SyncRepo│ │  │ │      │ │  │ │SFTP   │ │             │
│  │ │AiRepo  │ │  │ │      │ │  │ │PortFwd│ │             │
│  │ └────────┘ │  │ └──────┘ │  │ ├───────┤ │             │
│  │             │  │ ┌──────┐ │  │ │OkHttp │ │             │
│  │             │  │ │Enc.  │ │  │ │AI API │ │             │
│  │             │  │ │Prefs │ │  │ │Cloud  │ │             │
│  │             │  │ └──────┘ │  │ │Sync   │ │             │
│  │             │  │          │  │ └───────┘ │             │
│  └────────────┘  └──────────┘  └───────────┘             │
├───────────────────────────────────────────────────────────┤
│                    Platform Layer                          │
│  ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │ Android    │ │ Android  │ │ Foreground│ │ Notifi-  │  │
│  │ Keystore   │ │ Bio-     │ │ Service   │ │ cation   │  │
│  │ Enc.Prefs │ │ metric   │ │ (SSH/SFTP)│ │ Manager  │  │
│  └────────────┘ └──────────┘ └──────────┘ └───────────┘  │
└───────────────────────────────────────────────────────────┘
```

### 2.1 依赖规则

```
Presentation → Domain ← Data
                ↑
            (纯 Kotlin，无 Android 依赖)
```

- **Domain 层**：纯 Kotlin，无 Android SDK 依赖，可独立单元测试
- **Data 层**：实现 Domain 层定义的 Repository 接口
- **Presentation 层**：仅依赖 Domain 层，通过 Hilt 注入 Repository

---

## 3. 模块划分与依赖关系

### 3.1 Gradle 模块结构

```
netcatty-android/
├── app/                          # 主应用模块
│   ├── di/                       # Hilt 模块
│   └── NetcattyApp.kt
├── feature-vault/                # 主机管理
├── feature-terminal/             # 终端
├── feature-sftp/                 # SFTP 文件管理
├── feature-settings/             # 设置
├── feature-aichat/               # AI Chat
├── feature-sync/                 # 云同步
├── core-data/                    # 数据层 (Repository 实现)
├── core-domain/                  # 领域层 (Model + UseCase + Repo 接口)
├── core-ssh/                    # SSH/SFTP 核心引擎
├── core-sync/                    # 云同步引擎 (加密+适配器+合并)
├── core-ui/                     # 共享 UI 组件 + 主题
└── build-src/                   # Gradle 配置约定
```

### 3.2 模块依赖图

```
                    ┌──────────┐
                    │   app    │
                    └──┬───┬───┘
           ┌───────────┤   ├───────────┐
           ▼           ▼   ▼           ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
    │  vault   │ │ terminal │ │  sftp    │ │ settings │
    └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘
         │            │            │            │
         ▼            ▼            ▼            ▼
    ┌──────────────────────────────────────────────────┐
    │                  core-domain                      │
    └────────┬──────────────┬──────────────┬───────────┘
             │              │              │
    ┌────────▼──┐   ┌──────▼──────┐  ┌────▼─────┐
    │ core-data │   │  core-ssh   │  │ core-sync│
    └───────────┘   └─────────────┘  └───────────┘
             │              │              │
             ▼              ▼              ▼
    ┌──────────────────────────────────────────────────┐
    │               core-ui (可选依赖)                    │
    └──────────────────────────────────────────────────┘
```

> **注**：初期可以先用单模块 (`app-only`) 快速启动，等代码量增大后再拆分多模块。下文以单模块描述包结构，但标注了未来拆分边界。

---

## 4. 核心模块详细设计

### 4.1 core-ssh：SSH/SFTP 引擎

#### 4.1.1 SshSessionManager

```kotlin
/**
 * SSH 会话生命周期管理器。
 * 单例，管理所有活跃的 SSH Session。
 *
 * 线程安全：所有 JSch 操作在 Dispatchers.IO 上执行。
 */
@Singleton
class SshSessionManager @Inject constructor(
    private val cryptoManager: FieldCryptoManager
) {
    // 活跃连接：sessionId → SshConnection
    private val connections = ConcurrentHashMap<String, SshConnection>()

    // JSch 实例（全局共享，管理 known_hosts 和 identity）
    private val jsch = JSch()

    /**
     * 建立 SSH 连接并返回 TerminalSession
     *
     * @param host 主机配置
     * @param passwordOverride 临时密码（键盘交互式认证用）
     * @return TerminalSession 或错误
     */
    suspend fun connect(host: Host, passwordOverride: String? = null): Result<TerminalSession>

    /** 向终端写入数据（用户输入） */
    fun write(sessionId: String, data: String)

    /** 调整终端尺寸 */
    fun resize(sessionId: String, cols: Int, rows: Int)

    /** 断开指定会话 */
    fun disconnect(sessionId: String)

    /** 获取活跃连接 */
    fun getConnection(sessionId: String): SshConnection?

    /** 获取所有活跃会话 ID */
    fun getActiveSessionIds(): List<String>

    /** 断开所有会话（应用退出时调用） */
    fun disconnectAll()
}
```

#### 4.1.2 SshConnection

```kotlin
/**
 * 单个 SSH 连接的完整状态。
 * 封装 JSch Session + ChannelShell + IO 流。
 *
 * 生命周期：
 *   connect() → [读写数据] → disconnect()
 *   不可重连，断开后需新建。
 */
data class SshConnection(
    val id: String,               // UUID
    val hostId: String,            // 对应 Host.id
    val hostLabel: String,         // 显示名
    val username: String,
    val hostname: String,
    val port: Int,
    val protocol: HostProtocol,
    val session: Session,          // JSch Session
    val channel: ChannelShell,      // JSch ChannelShell
    val inputStream: InputStream,   // Channel stdout
    val outputStream: OutputStream, // Channel stdin
    val createdAt: Long = System.currentTimeMillis(),
    var status: ConnectionStatus = ConnectionStatus.CONNECTED
) {
    enum class ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTED, ERROR
    }
}
```

#### 4.1.3 认证策略

```kotlin
/**
 * SSH 认证处理器。
 * 支持：密码、密钥、键盘交互式、Agent Forwarding。
 *
 * 对应桌面端 sshAuthHelper.cjs 的逻辑。
 */
class SshAuthHandler(
    private val jsch: JSch,
    private val cryptoManager: FieldCryptoManager
) {
    /**
     * 配置 JSch Session 的认证方式。
     * 按优先级尝试：publickey → password → keyboard-interactive
     */
    suspend fun configureAuth(session: Session, host: Host) {
        when (host.authMethod) {
            AuthMethod.KEY -> configureKeyAuth(session, host)
            AuthMethod.PASSWORD -> configurePasswordAuth(session, host)
            AuthMethod.CERTIFICATE -> configureCertAuth(session, host)
        }
        // 通用设置
        session.setConfig("PreferredAuthentications", buildAuthPreference(host))
    }

    private suspend fun configureKeyAuth(session: Session, host: Host) {
        val key = cryptoManager.decryptToString(host.privateKeyEncrypted)
        val passphrase = host.passphraseEncrypted?.let {
            cryptoManager.decryptToString(it)
        }
        jsch.addIdentity(host.id, key.toByteArray(), null,
            passphrase?.toByteArray())
    }

    private suspend fun configurePasswordAuth(session: Session, host: Host) {
        val password = cryptoManager.decryptToString(host.passwordEncrypted)
        session.setPassword(password)
    }

    /**
     * 键盘交互式认证回调（2FA/MFA）
     * 对应桌面端 keyboardInteractiveHandler.cjs
     */
    fun createKeyboardInteractiveCallback(
        onPrompt: (String, String, String, String?) -> String?
    ): UserInfo {
        return object : UIKeyboardInteractive {
            override fun promptKeyboardInteractive(
                destination: String, name: String,
                instruction: String, prompt: Array<String>,
                echo: BooleanArray
            ): Array<String> {
                val responses = prompt.mapIndexed { i, p ->
                    onPrompt(destination, name, p, if (echo[i]) "echo" else null) ?: ""
                }
                return responses.toTypedArray()
            }
        }
    }
}
```

#### 4.1.4 SftpClient

```kotlin
/**
 * SFTP 客户端。
 * 复用现有 SSH Session 的 SFTP Channel。
 *
 * 对应桌面端 sftpBridge.cjs 的逻辑。
 */
class SftpClient(private val session: Session) {
    private var channel: ChannelSftp? = null

    fun connect(): ChannelSftp
    fun disconnect()

    // 目录操作
    suspend fun listDirectory(path: String, encoding: String? = null): List<SftpFileEntry>
    suspend fun stat(path: String): SftpFileEntry?
    suspend fun mkdir(path: String)
    suspend fun delete(path: String)
    suspend fun rename(oldPath: String, newPath: String)
    suspend fun chmod(path: String, mode: Int)
    suspend fun getHomeDir(): String

    // 文件传输
    suspend fun download(
        remotePath: String, localPath: String,
        monitor: SftpProgressMonitor?
    )
    suspend fun upload(
        localPath: String, remotePath: String,
        monitor: SftpProgressMonitor?
    )

    // 读写
    suspend fun readFile(path: String, encoding: String? = null): String
    suspend fun writeFile(path: String, content: String, encoding: String? = null)
}
```

#### 4.1.5 端口转发

```kotlin
/**
 * SSH 端口转发管理器。
 * 支持 Local (-L)、Remote (-R)、Dynamic (-D/socks) 三种类型。
 *
 * 对应桌面端 portForwardingBridge.cjs + portForwardingService.ts
 */
@Singleton
class PortForwardingManager @Inject constructor(
    private val sshSessionManager: SshSessionManager
) {
    // 活跃隧道：tunnelId → Tunnel
    private val tunnels = ConcurrentHashMap<String, Tunnel>()

    data class Tunnel(
        val id: String,
        val ruleId: String,
        val type: PortForwardingType,
        val localPort: Int,
        val bindAddress: String,
        val remoteHost: String?,
        val remotePort: Int?,
        val hostId: String,
        val sessionId: String,
        val status: TunnelStatus,
        val error: String? = null
    )

    enum class TunnelStatus { INACTIVE, CONNECTING, ACTIVE, ERROR }

    suspend fun startForward(rule: PortForwardingRule): Result<Tunnel>
    suspend fun stopForward(tunnelId: String)
    fun getActiveTunnels(): List<Tunnel>
    fun getTunnel(tunnelId: String): Tunnel?
}
```

---

### 4.2 core-sync：云同步引擎

这是与桌面端**完全兼容**的核心模块，确保双向同步可用。

#### 4.2.1 架构总览

```
┌───────────────────────────────────────────────────────────┐
│                    SyncManager (入口)                       │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────────┐│
│  │SecurityState│ │  SyncState   │ │ ProviderManager      ││
│  │Machine      │ │  Machine     │ │ (多 Provider 协调)    ││
│  └─────────────┘ └──────────────┘ └──────────────────────┘│
├───────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐│
│  │             EncryptionService                          ││
│  │  PBKDF2(600K) + AES-256-GCM                          ││
│  │  ⚠️ 输出格式必须与桌面端 Web Crypto 完全一致          ││
│  └───────────────────────────────────────────────────────┘│
├───────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐│
│  │             MergeEngine                               ││
│  │  三路合并 (base / local / remote)                     ││
│  │  ⚠️ 合并逻辑必须与桌面端 mergeSyncPayloads 一致       ││
│  └───────────────────────────────────────────────────────┘│
├───────────────────────────────────────────────────────────┤
│  CloudAdapter (统一接口)                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────┐ ┌────┐ │
│  │ GitHub   │ │ Google   │ │ OneDrive │ │WebDAV│ │ S3 │ │
│  │ Gist     │ │ Drive    │ │          │ │      │ │    │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────┘ └────┘ │
└───────────────────────────────────────────────────────────┘
```

#### 4.2.2 SecurityStateMachine

```kotlin
/**
 * 安全状态机。与桌面端 SecurityState 完全对应。
 *
 * NO_KEY → LOCKED → UNLOCKED
 *   ↑                    │
 *   └── resetMasterKey() ─┘ (lock → 销毁密钥)
 */
enum class SecurityState { NO_KEY, LOCKED, UNLOCKED }

@Singleton
class SecurityStateMachine @Inject constructor(
    private val keyStore: SyncKeyStore    // Android Keystore 封装
) {
    private val _state = MutableStateFlow(SecurityState.NO_KEY)
    val state: StateFlow<SecurityState> = _state.asStateFlow()

    private var masterKeyConfig: MasterKeyConfig? = null
    private var unlockedKey: ByteArray? = null  // 派生后的 AES 密钥（仅内存）

    suspend fun setupMasterKey(password: String)
    suspend fun unlock(password: String): Boolean
    fun lock()
    suspend fun changeMasterKey(oldPassword: String, newPassword: String): Boolean
    suspend fun verifyPassword(password: String): Boolean

    /** 获取解锁后的密钥（仅 UNLOCKED 状态可用） */
    fun getDerivedKey(): ByteArray?
}
```

#### 4.2.3 EncryptionService

```kotlin
/**
 * 零知识加密服务。
 *
 * ⚠️ 关键兼容性约束：
 * - PBKDF2 迭代次数：600,000（与桌面端 SYNC_CONSTANTS.PBKDF2_ITERATIONS 一致）
 * - 盐长度：32 字节
 * - IV 长度：12 字节
 * - 加密算法：AES-256-GCM (tag 128-bit)
 * - KDF hash：SHA-256
 * - 输出格式：SyncedFile JSON（meta + Base64 payload）
 *
 * 这样桌面端加密的文件，Android 端能解密；反之亦然。
 */
object EncryptionService {

    private const val AES_KEY_LENGTH = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val SALT_LENGTH = 32
    private const val PBKDF2_ITERATIONS = 600_000

    /**
     * 从密码派生 AES-256 密钥
     * 对应桌面端 deriveKey()
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(
            password.toCharArray(), salt,
            PBKDF2_ITERATIONS, AES_KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * 创建主密钥配置
     * 对应桌面端 createMasterKeyConfig()
     */
    fun createMasterKeyConfig(password: String): MasterKeyConfig {
        val salt = generateRandomBytes(SALT_LENGTH)
        val key = deriveKey(password, salt)
        val verificationHash = createVerificationHash(key)
        return MasterKeyConfig(
            verificationHash = Base64.encodeToString(verificationHash, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            kdf = "PBKDF2",
            kdfIterations = PBKDF2_ITERATIONS,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * 加密 SyncPayload → SyncedFile
     * 对应桌面端 encryptPayload()
     *
     * 输出格式必须与桌面端完全一致，才能双向同步。
     */
    fun encryptPayload(
        payload: SyncPayload,
        password: String,
        deviceId: String,
        deviceName: String,
        appVersion: String,
        existingVersion: Int = 0
    ): SyncedFile {
        val salt = generateRandomBytes(SALT_LENGTH)
        val iv = generateRandomBytes(GCM_IV_LENGTH)
        val key = deriveKey(password, salt)

        val plaintext = Json.encodeToString(payload)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return SyncedFile(
            meta = SyncFileMeta(
                version = existingVersion + 1,
                updatedAt = System.currentTimeMillis(),
                deviceId = deviceId,
                deviceName = deviceName,
                appVersion = appVersion,
                iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                algorithm = "AES-256-GCM",
                kdf = "PBKDF2",
                kdfIterations = PBKDF2_ITERATIONS
            ),
            payload = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    /**
     * 解密 SyncedFile → SyncPayload
     * 对应桌面端 decryptPayload()
     */
    fun decryptPayload(syncedFile: SyncedFile, password: String): SyncPayload {
        val salt = Base64.decode(syncedFile.meta.salt, Base64.NO_WRAP)
        val iv = Base64.decode(syncedFile.meta.iv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(syncedFile.payload, Base64.NO_WRAP)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return Json.decodeFromString(String(plaintext, Charsets.UTF_8))
    }

    private fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun createVerificationHash(key: SecretKey): ByteArray {
        val keyBytes = key.encoded
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(keyBytes)
    }
}
```

#### 4.2.4 MergeEngine

```kotlin
/**
 * 三路合并引擎。
 *
 * ⚠️ 合并逻辑必须与桌面端 mergeSyncPayloads() 一致。
 * 可直接参考 syncMerge.ts 的实现，逐函数移植。
 *
 * 算法：
 *   对每个实体（按 id 标识）：
 *     - 仅 local 有  → 保留 (local addition)
 *     - 仅 remote 有 → 保留 (remote addition)
 *     - base 有、local 删了 → 删除（除非 remote 改了）
 *     - base 有、remote 删了 → 删除（除非 local 改了）
 *     - 仅 local 改了 → 保留 local
 *     - 仅 remote 改了 → 保留 remote
 *     - 两边都改了 → 保留 local（记录冲突）
 */
object MergeEngine {

    data class MergeResult(
        val payload: SyncPayload,
        val hadConflicts: Boolean,
        val summary: MergeSummary
    )

    data class MergeSummary(
        val added: AddedCount,
        val deleted: DeletedCount,
        val modified: ModifiedCount
    )

    data class AddedCount(val local: Int, val remote: Int)
    data class DeletedCount(val local: Int, val remote: Int)
    data class ModifiedCount(val local: Int, val remote: Int, val conflicts: Int)

    fun merge(
        base: SyncPayload?,
        local: SyncPayload,
        remote: SyncPayload
    ): MergeResult {
        // 实现与 syncMerge.ts 逐行对应
        val b = base ?: emptyPayload()
        // ... 合并各实体类型
    }

    /**
     * 泛型实体数组合并。
     * 对应桌面端 mergeEntityArrays<T>()
     */
    private fun <T : HasId> mergeEntityArrays(
        base: List<T>, local: List<T>, remote: List<T>
    ): EntityMergeResult<T> { /* ... */ }

    /**
     * 字符串数组合并（customGroups 等）。
     * 对应桌面端 mergeStringArrays()
     */
    private fun mergeStringArrays(
        base: List<String>, local: List<String>, remote: List<String>
    ): List<String> { /* ... */ }

    /**
     * 设置合并（递归三路合并）。
     * 对应桌面端 mergeSettings()
     */
    private fun mergeSettings(
        base: Map<String, Any?>?, local: Map<String, Any?>?, remote: Map<String, Any?>?
    ): Map<String, Any?>? { /* ... */ }

    private fun fingerprint(value: Any?): String {
        return Json.encodeToString(value)
    }
}

interface HasId { val id: String }
```

#### 4.2.5 CloudAdapter 接口

```kotlin
/**
 * 云存储适配器统一接口。
 * 与桌面端 CloudAdapter 接口对齐。
 */
interface CloudAdapter {
    val isAuthenticated: Boolean
    val accountInfo: ProviderAccount?
    val resourceId: String?

    suspend fun initializeSync(): String?
    suspend fun download(): SyncedFile?
    suspend fun upload(file: SyncedFile)
    suspend fun delete()
    fun signOut()
}

data class ProviderAccount(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null
)
```

#### 4.2.6 GitHub Gist 适配器

```kotlin
/**
 * GitHub Gist 同步适配器。
 * 使用 Device Flow 认证（无需 client secret）。
 *
 * 对应桌面端 GitHubAdapter.ts
 */
class GitHubGistAdapter(
    private val httpClient: OkHttpClient
) : CloudAdapter {

    // Device Flow
    suspend fun startDeviceFlow(clientId: String): DeviceFlowState
    suspend fun pollDeviceFlow(
        deviceCode: String, clientId: String,
        interval: Long, expiresAt: Long
    ): OAuthTokens

    // Gist 操作
    override suspend fun initializeSync(): String?
    override suspend fun download(): SyncedFile?
    override suspend fun upload(file: SyncedFile)
    override suspend fun delete()
    override fun signOut()
}
```

#### 4.2.7 SyncManager (总协调器)

```kotlin
/**
 * 云同步总协调器。
 * 对应桌面端 CloudSyncManager 类。
 *
 * 管理安全状态机 + 同步状态机 + 多 Provider 协调。
 */
@Singleton
class SyncManager @Inject constructor(
    private val securityStateMachine: SecurityStateMachine,
    private val encryptionService: EncryptionService,
    private val mergeEngine: MergeEngine,
    private val adapterFactory: CloudAdapterFactory,
    private val syncKeyStore: SyncKeyStore,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val providers = ConcurrentHashMap<CloudProvider, ProviderConnection>()
    private val adapters = ConcurrentHashMap<CloudProvider, CloudAdapter>()

    // 同步历史
    private val _syncHistory = MutableStateFlow<List<SyncHistoryEntry>>(emptyList())
    val syncHistory: StateFlow<List<SyncHistoryEntry>> = _syncHistory.asStateFlow()

    /**
     * 上传同步：本地数据 → 加密 → 上传到所有已连接的 Provider
     *
     * 对应桌面端 syncAllProviders()
     */
    suspend fun syncToAll(payload: SyncPayload): Map<CloudProvider, SyncResult>

    /**
     * 从指定 Provider 下载
     * 对应桌面端 downloadFromProvider()
     */
    suspend fun downloadFrom(provider: CloudProvider): SyncPayload?

    /**
     * 连接 Provider
     */
    suspend fun connectProvider(provider: CloudProvider, authData: Any)

    /**
     * 断开 Provider
     */
    suspend fun disconnectProvider(provider: CloudProvider)

    /**
     * 自动同步配置
     */
    fun setAutoSync(enabled: Boolean, intervalMinutes: Int)
}
```

---

### 4.3 core-domain：领域模型

#### 4.3.1 核心实体

```kotlin
// ─── Host ───
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
    val passwordEncrypted: String? = null,        // AES-GCM 加密
    val identityFileId: String? = null,
    val identityFileEncrypted: String? = null,    // AES-GCM 加密
    val passphraseEncrypted: String? = null,      // AES-GCM 加密
    val group: String? = null,
    val tags: List<String> = emptyList(),
    val os: String = "linux",
    val deviceType: DeviceType = DeviceType.GENERAL,
    val protocol: HostProtocol = HostProtocol.SSH,
    val agentForwarding: Boolean = false,
    val startupCommand: String? = null,
    val proxyConfig: ProxyConfig? = null,
    val hostChain: List<String> = emptyList(),     // Jump host IDs
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

// ─── SSH Key ───
enum class KeyType { RSA, ECDSA, ED25519 }
enum class KeyCategory { KEY, CERTIFICATE, IDENTITY }

data class SshKey(
    val id: String,
    val label: String,
    val type: KeyType,
    val keySize: Int? = null,
    val privateKeyEncrypted: String,    // AES-GCM 加密
    val publicKey: String? = null,
    val certificate: String? = null,
    val passphraseEncrypted: String? = null,
    val category: KeyCategory = KeyCategory.KEY,
    val created: Long = System.currentTimeMillis()
)

// ─── Snippet ───
data class Snippet(
    val id: String,
    val label: String,
    val command: String,
    val tags: List<String> = emptyList(),
    val targetHostIds: List<String> = emptyList(),
    val shortcutKey: String? = null,
    val noAutoRun: Boolean = false
)

// ─── Port Forwarding ───
enum class PortForwardingType { LOCAL, REMOTE, DYNAMIC }

data class PortForwardingRule(
    val id: String,
    val label: String,
    val type: PortForwardingType,
    val localPort: Int,
    val bindAddress: String = "127.0.0.1",
    val remoteHost: String? = null,
    val remotePort: Int? = null,
    val hostId: String? = null,
    val autoStart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Terminal Theme ───
enum class ThemeType { DARK, LIGHT }

data class TerminalTheme(
    val id: String,
    val name: String,
    val type: ThemeType,
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
    val brightBlack: String, val brightRed: String, val brightGreen: String,
    val brightYellow: String, val brightBlue: String, val brightMagenta: String,
    val brightCyan: String, val brightWhite: String
)

// ─── Sync Payload（与桌面端完全一致） ───
data class SyncPayload(
    val hosts: List<Host> = emptyList(),
    val keys: List<SshKey> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val customGroups: List<String> = emptyList(),
    val snippetPackages: List<String> = emptyList(),
    val groupConfigs: List<GroupConfig> = emptyList(),
    val portForwardingRules: List<PortForwardingRule> = emptyList(),
    val knownHosts: List<KnownHost> = emptyList(),
    val settings: SyncSettings? = null,
    val syncedAt: Long = System.currentTimeMillis()
)

// ─── SyncedFile（与桌面端完全一致） ───
data class SyncedFile(
    val meta: SyncFileMeta,
    val payload: String     // Base64 密文
)

data class SyncFileMeta(
    val version: Int,
    val updatedAt: Long,
    val deviceId: String,
    val deviceName: String?,
    val appVersion: String,
    val iv: String,            // Base64
    val salt: String,           // Base64
    val algorithm: String = "AES-256-GCM",
    val kdf: String = "PBKDF2",
    val kdfIterations: Int? = null
)

// ─── MasterKeyConfig（与桌面端完全一致） ───
data class MasterKeyConfig(
    val verificationHash: String,  // Base64
    val salt: String,               // Base64
    val kdf: String = "PBKDF2",
    val kdfIterations: Int? = null,
    val createdAt: Long
)
```

---

### 4.4 安全架构

#### 4.4.1 分层加密策略

```
┌───────────────────────────────────────────────────┐
│                 用户密码                            │
│                   │                                │
│                   ▼                                │
│            PBKDF2 (600K iter)                     │
│                   │                                │
│                   ▼                                │
│            SessionKey (AES-256)  ◄── 内存持有      │
│                   │                                │
│     ┌─────────────┼─────────────┐                 │
│     ▼             ▼             ▼                  │
│  Host.password  SSHKey.pk   SyncPayload           │
│  (字段级加密)   (字段级加密)  (整体加密)            │
│                                                   │
│  ──── Android Keystore ────                       │
│  存储：PBKDF2 salt + verificationHash             │
│  不存储：密码本身、派生密钥                         │
└───────────────────────────────────────────────────┘
```

#### 4.4.2 FieldCryptoManager

```kotlin
/**
 * 字段级 AES-GCM 加密管理器。
 * 与 sbssh 项目的 FieldCryptoManager 设计一致。
 *
 * 用于加密 Host.password、SSHKey.privateKey 等敏感字段。
 * 密钥由 SessionKeyHolder 在内存中持有。
 */
@Singleton
class FieldCryptoManager @Inject constructor(
    private val sessionKeyHolder: SessionKeyHolder
) {
    fun encrypt(plaintext: String): String {
        val key = sessionKeyHolder.getKey()
            ?: throw SecurityException("Session key not available")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // 格式：Base64(iv + ciphertext)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encrypted: String): String {
        val key = sessionKeyHolder.getKey()
            ?: throw SecurityException("Session key not available")
        val combined = Base64.decode(encrypted, Base64.NO_WRAP)
        val iv = combined.sliceArray(0 until 12)
        val ciphertext = combined.sliceArray(12 until combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun isKeyAvailable(): Boolean = sessionKeyHolder.getKey() != null
}
```

#### 4.4.3 SessionKeyHolder

```kotlin
/**
 * 内存中的会话密钥持有者。
 * 密钥由用户密码通过 PBKDF2 派生，应用退出后清除。
 *
 * 对应桌面端 SessionKeyHolder 的角色（桌面端用 Electron safeStorage，
 * Android 用 Android Keystore 存储 salt + hash，密码不持久化）。
 */
@Singleton
class SessionKeyHolder @Inject constructor(
    private val keyStore: AppKeyStore
) {
    private var derivedKey: SecretKey? = null

    suspend fun deriveAndStore(password: String): Boolean {
        val salt = keyStore.getOrCreateSalt()
        val spec = PBEKeySpec(
            password.toCharArray(), salt,
            600_000, 256
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        derivedKey = SecretKeySpec(keyBytes, "AES")
        return verifyKey()
    }

    fun getKey(): SecretKey? = derivedKey

    fun clear() {
        derivedKey = null
    }

    private suspend fun verifyKey(): Boolean {
        val storedHash = keyStore.getVerificationHash() ?: return false
        val currentHash = MessageDigest.getInstance("SHA-256")
            .digest(derivedKey!!.encoded)
        return Base64.encodeToString(currentHash, Base64.NO_WRAP) == storedHash
    }
}
```

#### 4.4.4 生物识别集成

```kotlin
/**
 * 生物识别解锁。
 * 用 BiometricPrompt 验证身份后，从 EncryptedSharedPreferences 读取密码，
 * 派生 SessionKey。
 */
class BiometricAuthHelper(
    private val activity: FragmentActivity,
    private val encryptedPrefs: SharedPreferences,
    private val sessionKeyHolder: SessionKeyHolder
) {
    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // 从 EncryptedSharedPreferences 读取密码
                    val password = encryptedPrefs.getString("master_pwd", null)
                    if (password != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            sessionKeyHolder.deriveAndStore(password)
                            withContext(Dispatchers.Main) { onSuccess() }
                        }
                    } else {
                        onError("No stored password")
                    }
                }
                override fun onAuthenticationFailed() { onError("Auth failed") }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Netcatty")
            .setSubtitle("Authenticate to access your vault")
            .setNegativeButtonText("Use password")
            .build()
        prompt.authenticate(info)
    }
}
```

---

### 4.5 终端渲染架构

```
┌───────────────────────────────────────────────────┐
│              Compose TerminalScreen                 │
│  ┌─────────────────────────────────────────────┐  │
│  │  AndroidView { TerminalView (Termux) }       │  │
│  │   ↕ TerminalSession                          │  │
│  │   ↕ TerminalOutput (buffer + scrollback)     │  │
│  └─────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────┐  │
│  │  BottomBar (输入栏 + 特殊键)                 │  │
│  │  ┌────────────────────────────────────────┐ │  │
│  │  │ [ESC][Tab][Ctrl][↑][↓]  输入框...  [↵]│ │  │
│  │  └────────────────────────────────────────┘ │  │
│  └─────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────┘
```

#### 4.5.1 NetcattyTerminalSession

```kotlin
/**
 * 桥接 JSch SSH Channel 和 Termux TerminalSession。
 *
 * 数据流：
 *   JSch InputStream → TerminalSession.write() → TerminalView 渲染
 *   TerminalView 输入 → SshConnection.write() → JSch OutputStream
 */
class NetcattyTerminalSession(
    private val connection: SshConnection,
    private val terminalOutput: TerminalOutput
) : TerminalSession.SessionChangedCallback {

    private val terminalSession: TerminalSession

    init {
        terminalSession = TerminalSession(
            "/system/bin/sh",  // 占位，实际 shell 由 SSH channel 提供
            "/",
            arrayOf("TERM=xterm-256color", "COLORTERM=truecolor"),
            terminalOutput,
            this
        )

        // SSH → Terminal: 读取 JSch InputStream，写入 TerminalSession
        thread(name = "ssh-read-${connection.id}", isDaemon = true) {
            val buffer = ByteArray(8192)
            try {
                while (true) {
                    val read = connection.inputStream.read(buffer)
                    if (read == -1) break
                    val data = String(buffer, 0, read, Charsets.UTF_8)
                    terminalSession.write(data)
                }
            } catch (_: IOException) { }
        }
    }

    /** 用户输入 → SSH Channel */
    fun write(data: String) {
        connection.outputStream.apply {
            write(data.toByteArray(Charsets.UTF_8))
            flush()
        }
    }

    fun resize(cols: Int, rows: Int) {
        connection.channel.setPtySize(cols, rows, cols * 8, rows * 16)
    }

    // TerminalSession.SessionChangedCallback
    override fun onTextChanged(s: TerminalSession) {}
    override fun onTitleChanged(s: TerminalSession) {}
    override fun onSessionFinished(s: TerminalSession) {}
    override fun onClipboardText(s: TerminalSession?, text: String?) {}
    override fun onBell(s: TerminalSession?) {}
    override fun onColorsChanged(s: TerminalSession?) {}
}
```

---

## 5. 数据模型

### 5.1 Room Entity 映射

```kotlin
// ─── HostEntity ───
@Entity(tableName = "hosts", indices = [
    Index(value = ["group"]),
    Index(value = ["lastConnectedAt"])
])
data class HostEntity(
    @PrimaryKey val id: String,
    val label: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authMethod: String = "PASSWORD",
    val passwordEncrypted: String? = null,
    val identityFileId: String? = null,
    val identityFileEncrypted: String? = null,
    val passphraseEncrypted: String? = null,
    val group: String? = null,
    val tags: String = "[]",           // JSON array
    val os: String = "linux",
    val deviceType: String = "GENERAL",
    val protocol: String = "SSH",
    val agentForwarding: Boolean = false,
    val startupCommand: String? = null,
    val proxyConfig: String? = null,   // JSON
    val hostChain: String = "[]",      // JSON array of host IDs
    val envVars: String = "[]",        // JSON
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
    val sftpBookmarks: String = "[]",  // JSON
    val keywordHighlightRules: String = "[]" // JSON
)

// ─── SshKeyEntity ───
@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey val id: String,
    val label: String,
    val type: String = "ED25519",
    val keySize: Int? = null,
    val privateKeyEncrypted: String,
    val publicKey: String? = null,
    val certificate: String? = null,
    val passphraseEncrypted: String? = null,
    val category: String = "KEY",
    val created: Long = System.currentTimeMillis()
)

// ─── SnippetEntity ───
@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey val id: String,
    val label: String,
    val command: String,
    val tags: String = "[]",
    val targetHostIds: String = "[]",
    val shortcutKey: String? = null,
    val noAutoRun: Boolean = false
)

// ─── PortForwardingRuleEntity ───
@Entity(tableName = "port_forwarding_rules")
data class PortForwardingRuleEntity(
    @PrimaryKey val id: String,
    val label: String,
    val type: String = "LOCAL",
    val localPort: Int,
    val bindAddress: String = "127.0.0.1",
    val remoteHost: String? = null,
    val remotePort: Int? = null,
    val hostId: String? = null,
    val autoStart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── SyncProviderEntity ───
@Entity(tableName = "sync_providers")
data class SyncProviderEntity(
    @PrimaryKey val provider: String,   // github / google / onedrive / webdav / s3
    val status: String = "disconnected",
    val accountJson: String? = null,   // JSON
    val tokensJson: String? = null,    // 加密存储
    val configJson: String? = null,    // 加密存储
    val resourceId: String? = null,
    val lastSync: Long? = null,
    val lastSyncVersion: Int? = null
)
```

### 5.2 DAO

```kotlin
@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY pinned DESC, lastConnectedAt DESC")
    fun getAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getById(id: String): HostEntity?

    @Query("SELECT * FROM hosts WHERE `group` = :group")
    fun getByGroup(group: String): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE label LIKE '%' || :query || '%' OR hostname LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<HostEntity>>

    @Upsert
    suspend fun upsert(host: HostEntity)

    @Delete
    suspend fun delete(host: HostEntity)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE hosts SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestamp: Long)
}
```

---

## 6. 云同步系统

### 6.1 同步流程图

```
用户点击"同步"
    │
    ▼
┌─ SecurityState == UNLOCKED? ──No──▶ 提示输入主密码
    │ Yes
    ▼
构建 SyncPayload (从 Room 读取所有数据)
    │
    ▼
对每个已连接的 Provider 并行执行：
    │
    ├──▶ 下载远端 SyncedFile
    │       │
    │       ▼
    │   远端版本 > 本地版本？
    │       │
    │    No │ ──▶ 加密 payload → 上传 → 保存 merge base
    │    Yes│
    │       ▼
    │   尝试三路合并 (base / local / remote)
    │       │
    │    成功 │ ──▶ 加密合并结果 → 上传 → 保存新 merge base
    │    失败 │
    │       ▼
    │   显示冲突 UI (USE_LOCAL / USE_REMOTE)
    │
    ▼
更新本地版本号、同步历史
```

### 6.2 与桌面端同步兼容性检查清单

| 检查项 | 要求 | 验证方式 |
|--------|------|----------|
| PBKDF2 参数 | SHA-256, 600K 迭代, 256-bit key | 解密桌面端加密文件 |
| AES-GCM 参数 | 12-byte IV, 128-bit tag | 同上 |
| SyncedFile JSON 格式 | `{ meta: {...}, payload: "base64..." }` | 双向加密解密测试 |
| SyncFileMeta 字段 | version/updatedAt/deviceId/iv/salt/... | JSON schema 对比 |
| SyncPayload 字段 | hosts/keys/snippets/groups/... | 双向序列化对比 |
| 三路合并逻辑 | 与 syncMerge.ts 一致 | 同样的 base+local+remote 输入 → 同样输出 |
| GitHub Gist 文件名 | `netcatty-vault.json` | Gist API 交互 |
| 云端文件不落地 | 加密后才上传，云端仅存密文 | 抓包验证 |

---

## 7. 安全架构

### 7.1 安全层级

```
┌────────────────────────────────────────────────────────┐
│  Layer 1: 应用锁                                       │
│  - 启动时需要密码/生物识别解锁                          │
│  - BiometricPrompt + EncryptedSharedPreferences         │
├────────────────────────────────────────────────────────┤
│  Layer 2: 字段加密                                     │
│  - Host.password, SSHKey.privateKey 等用 AES-GCM 加密  │
│  - 密钥由 PBKDF2 从主密码派生，仅内存持有               │
│  - Room 存储的是密文 Base64                            │
├────────────────────────────────────────────────────────┤
│  Layer 3: Android Keystore                             │
│  - 存储 PBKDF2 salt + verificationHash                │
│  - 不存储密码本身                                      │
│  - 硬件安全模块保护 (TEE/StrongBox)                    │
├────────────────────────────────────────────────────────┤
│  Layer 4: 云同步加密                                   │
│  - AES-256-GCM 端到端加密                             │
│  - 云端仅存储密文，零知识                              │
│  - PBKDF2 600K 迭代抗暴力破解                          │
└────────────────────────────────────────────────────────┘
```

---

## 8. 进程与服务架构

### 8.1 前台服务

```kotlin
/**
 * SSH 连接前台服务。
 * 保持 SSH 连接在后台不被系统杀死。
 *
 * 通知栏显示：
 *   "Netcatty — 3 个活跃连接"
 *   点击 → 回到终端界面
 */
class SshConnectionService : Service() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SshConnectionService = this@SshConnectionService
    }

    override fun onBind(intent: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotification(): Notification {
        // 创建通知渠道 (Android 8+)
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Netcatty")
            .setContentText("SSH 连接保持中")
            .setSmallIcon(R.drawable.ic_terminal_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
```

### 8.2 SFTP 传输服务

```kotlin
/**
 * SFTP 文件传输前台服务。
 * 支持后台传输、进度通知、取消操作。
 */
class SftpTransferService : Service() {
    // 传输任务队列
    private val transferQueue = ConcurrentLinkedQueue<TransferTask>()
    private var currentTransfer: TransferTask? = null

    fun enqueueUpload(task: TransferTask)
    fun enqueueDownload(task: TransferTask)
    fun cancelTransfer(taskId: String)

    // 更新通知进度
    private fun updateProgressNotification(task: TransferTask, progress: Int) {
        val notification = NotificationCompat.Builder(this, TRANSFER_CHANNEL_ID)
            .setContentTitle("正在传输: ${task.fileName}")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(this)
            .notify(TRANSFER_NOTIFICATION_ID, notification)
    }
}
```

---

## 9. 导航与页面架构

### 9.1 导航图

```kotlin
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "vault") {

        composable("vault") {
            VaultScreen(
                onHostClick = { hostId ->
                    navController.navigate("terminal/$hostId")
                },
                onHostLongClick = { hostId ->
                    // 编辑/删除对话框
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            "terminal/{hostId}",
            arguments = listOf(navArgument("hostId") { type = NavType.StringType })
        ) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getString("hostId") ?: return@composable
            TerminalScreen(
                hostId = hostId,
                onOpenSftp = { sessionId ->
                    navController.navigate("sftp/$sessionId")
                },
                onOpenAi = {
                    navController.navigate("aichat")
                }
            )
        }

        composable(
            "sftp/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            SftpScreen(sessionId = sessionId)
        }

        composable("settings") {
            SettingsScreen()
        }

        composable("aichat") {
            AiChatScreen()
        }
    }
}
```

### 9.2 页面层次

```
MainActivity (单 Activity)
├── BottomBar: Vault | Terminal | SFTP | Settings
│
├── VaultScreen
│   ├── SearchBar
│   ├── PinnedHostsRow
│   ├── GroupSection (可折叠)
│   │   └── HostCard (Grid/List)
│   └── FAB: 添加主机
│
├── TerminalScreen
│   ├── TopTabRow (多 Tab)
│   ├── TerminalView (AndroidView)
│   ├── BottomInputBar
│   │   ├── SpecialKeysRow (ESC/Tab/Ctrl/↑↓)
│   │   └── TextInput + Send
│   └── SideDrawer: AI Chat / Snippets
│
├── SftpScreen
│   ├── DualPane (远程 + 本地)
│   ├── BreadcrumbPath
│   ├── FileList
│   ├── TransferQueue (底部抽屉)
│   └── FAB: 上传文件
│
└── SettingsScreen
    ├── 应用锁 (密码/生物识别)
    ├── 终端主题
    ├── 字体/字号
    ├── 云同步 (Provider 管理)
    ├── AI Provider
    └── 关于
```

---

## 10. 构建配置

### 10.1 build.gradle.kts (app)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.netcatty.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.netcatty.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    kapt("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // SSH
    implementation("com.github.mwiede:jsch:0.2.16")

    // Termux terminal-emulator
    implementation("com.termux:terminal-emulator:0.118.0")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // Coil (图片加载)
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
```

### 10.2 ProGuard 规则

```proguard
# JSch
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Termux terminal-emulator
-keep class com.termux.terminal.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.netcatty.mobile.**$$serializer { *; }
-keepclassmembers class com.netcatty.mobile.** { *** Companion; }
-keepclasseswithmembers class com.netcatty.mobile.** { kotlinx.serialization.KSerializer serializer(...); }
```

---

## 11. 包结构与模块划分

```
com.netcatty.mobile/
│
├── NetcattyApp.kt                          # Application (Hilt 入口)
├── MainActivity.kt                          # 单 Activity
├── di/
│   ├── AppModule.kt                         # 全局 DI
│   ├── SshModule.kt                         # SSH 引擎 DI
│   ├── SyncModule.kt                        # 云同步 DI
│   └── DataModule.kt                        # Room + Repository DI
│
├── core/
│   ├── ssh/
│   │   ├── SshSessionManager.kt
│   │   ├── SshConnection.kt
│   │   ├── SshAuthHandler.kt
│   │   └── SftpClient.kt
│   ├── terminal/
│   │   ├── NetcattyTerminalSession.kt
│   │   └── TerminalThemeManager.kt
│   ├── portforward/
│   │   └── PortForwardingManager.kt
│   ├── sync/
│   │   ├── SyncManager.kt
│   │   ├── SecurityStateMachine.kt
│   │   ├── EncryptionService.kt
│   │   ├── MergeEngine.kt
│   │   ├── adapters/
│   │   │   ├── CloudAdapter.kt             # 接口
│   │   │   ├── GitHubGistAdapter.kt
│   │   │   ├── GoogleDriveAdapter.kt
│   │   │   ├── OneDriveAdapter.kt
│   │   │   ├── WebDavAdapter.kt
│   │   │   └── S3Adapter.kt
│   │   └── models/
│   │       ├── SyncPayload.kt
│   │       ├── SyncedFile.kt
│   │       └── MasterKeyConfig.kt
│   ├── crypto/
│   │   ├── FieldCryptoManager.kt
│   │   ├── SessionKeyHolder.kt
│   │   ├── AppKeyStore.kt
│   │   └── BiometricAuthHelper.kt
│   └── ai/
│       ├── AiChatService.kt
│       ├── AiProviderConfig.kt
│       └── SseParser.kt
│
├── domain/
│   ├── model/
│   │   ├── Host.kt
│   │   ├── SshKey.kt
│   │   ├── Snippet.kt
│   │   ├── PortForwardingRule.kt
│   │   ├── TerminalTheme.kt
│   │   ├── HostGroup.kt
│   │   └── KnownHost.kt
│   ├── repository/
│   │   ├── HostRepository.kt               # 接口
│   │   ├── KeyRepository.kt                 # 接口
│   │   ├── SnippetRepository.kt             # 接口
│   │   ├── SettingsRepository.kt            # 接口
│   │   └── SyncRepository.kt               # 接口
│   └── usecase/
│       ├── ConnectSshUseCase.kt
│       ├── StartSftpUseCase.kt
│       ├── TransferFileUseCase.kt
│       ├── StartPortForwardUseCase.kt
│       ├── SyncToCloudUseCase.kt
│       ├── DownloadFromCloudUseCase.kt
│       └── AiChatUseCase.kt
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   │   ├── HostDao.kt
│   │   │   ├── KeyDao.kt
│   │   │   ├── SnippetDao.kt
│   │   │   ├── PortForwardingRuleDao.kt
│   │   │   └── SyncProviderDao.kt
│   │   └── entity/
│   │       ├── HostEntity.kt
│   │       ├── SshKeyEntity.kt
│   │       ├── SnippetEntity.kt
│   │       ├── PortForwardingRuleEntity.kt
│   │       └── SyncProviderEntity.kt
│   ├── repository/
│   │   ├── HostRepositoryImpl.kt
│   │   ├── KeyRepositoryImpl.kt
│   │   ├── SnippetRepositoryImpl.kt
│   │   ├── SettingsRepositoryImpl.kt
│   │   └── SyncRepositoryImpl.kt
│   └── mapper/
│       └── EntityMapper.kt                  # Entity ↔ Domain Model 转换
│
├── ui/
│   ├── navigation/
│   │   └── NavGraph.kt
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── screens/
│   │   ├── vault/
│   │   │   ├── VaultScreen.kt
│   │   │   ├── VaultViewModel.kt
│   │   │   ├── VaultUiState.kt
│   │   │   ├── HostCard.kt
│   │   │   ├── GroupSection.kt
│   │   │   └── HostDetailSheet.kt
│   │   ├── terminal/
│   │   │   ├── TerminalScreen.kt
│   │   │   ├── TerminalViewModel.kt
│   │   │   ├── TerminalUiState.kt
│   │   │   ├── TabRow.kt
│   │   │   ├── InputBar.kt
│   │   │   └── SpecialKeysRow.kt
│   │   ├── sftp/
│   │   │   ├── SftpScreen.kt
│   │   │   ├── SftpViewModel.kt
│   │   │   ├── SftpUiState.kt
│   │   │   ├── DualPaneLayout.kt
│   │   │   ├── FileList.kt
│   │   │   ├── BreadcrumbPath.kt
│   │   │   └── TransferQueue.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   ├── AppLockSettings.kt
│   │   │   ├── TerminalThemeSettings.kt
│   │   │   ├── CloudSyncSettings.kt
│   │   │   └── AiProviderSettings.kt
│   │   └── aichat/
│   │       ├── AiChatScreen.kt
│   │       └── AiChatViewModel.kt
│   └── components/
│       ├── NetcattyTerminalView.kt
│       ├── PasswordDialog.kt
│       ├── PassphraseDialog.kt
│       ├── KeyboardInteractiveDialog.kt
│       ├── ConfirmDialog.kt
│       ├── SearchBar.kt
│       └── LoadingIndicator.kt
│
└── service/
    ├── SshConnectionService.kt
    └── SftpTransferService.kt
```

---

## 12. 错误处理策略

### 12.1 分层错误处理

```kotlin
/**
 * 全局错误类型
 */
sealed class NetcattyError {
    // SSH 错误
    data class SshConnectionError(val hostId: String, val message: String) : NetcattyError()
    data class SshAuthError(val hostId: String, val method: String) : NetcattyError()
    data class SshDisconnectedError(val sessionId: String) : NetcattyError()

    // SFTP 错误
    data class SftpError(val sessionId: String, val message: String) : NetcattyError()
    data class TransferError(val transferId: String, val message: String) : NetcattyError()

    // 同步错误
    data class SyncError(val provider: String, val message: String) : NetcattyError()
    data class SyncConflictError(val provider: String, val localVersion: Int, val remoteVersion: Int) : NetcattyError()
    data class SyncDecryptionError(val message: String) : NetcattyError()  // 密码不一致

    // 加密错误
    data class CryptoError(val message: String) : NetcattyError()

    // 网络错误
    data class NetworkError(val message: String, val code: Int? = null) : NetcattyError()
}

/**
 * 统一结果类型
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: NetcattyError) : Result<Nothing>()
}
```

### 12.2 ViewModel 错误处理

```kotlin
// 在 ViewModel 中统一处理
private fun handle_error(error: NetcattyError) {
    when (error) {
        is NetcattyError.SshAuthError -> {
            _uiState.update { it.copy(
                showAuthDialog = true,
                authHostId = error.hostId,
                authMessage = "Authentication failed: ${error.method}"
            )}
        }
        is NetcattyError.SyncConflictError -> {
            _uiState.update { it.copy(
                showConflictDialog = true,
                conflictInfo = ConflictInfo(
                    provider = error.provider,
                    localVersion = error.localVersion,
                    remoteVersion = error.remoteVersion
                )
            )}
        }
        is NetcattyError.SyncDecryptionError -> {
            _uiState.update { it.copy(
                snackbarMessage = "无法解密云端数据，请确认主密码与桌面端一致"
            )}
        }
        else -> {
            _uiState.update { it.copy(
                snackbarMessage = error.toString()
            )}
        }
    }
}
```

---

## 13. 与桌面端的互操作

### 13.1 数据格式兼容性

| 数据 | 格式 | 兼容方式 |
|------|------|----------|
| Host | SyncPayload JSON | 字段名 + 结构完全对齐 |
| SSH Key | OpenSSH / PuTTY PPK / PKCS8 | JSch 原生支持 |
| 终端主题 | JSON 配色数组 | 直接移植 |
| 云同步密文 | SyncedFile JSON | 加密参数完全一致 |
| i18n | strings.xml | 从 en.ts / zh-CN.ts 移植 |

### 13.2 不兼容项处理

| 桌面端功能 | Android 处理 | 原因 |
|-----------|-------------|------|
| 本地终端 | 不支持 | Android 无 PTY |
| Mosh 协议 | Phase 6 考虑 | 需额外 native 库 |
| 串口连接 | Phase 6 | 需 USB-serial |
| Monaco 编辑器 | 轻量编辑器替代 | 体积和性能 |
| 系统托盘 | 不适用 | Android 无此概念 |
| 全局快捷键 | 不适用 | Android 无此概念 |
| 分屏 (Split Panes) | 横屏支持 | 竖屏屏幕太小 |
| 多窗口 | 不支持 | Android 单窗口 |
| 拖拽上传 | 长按选择文件 | 触屏交互不同 |

### 13.3 同步冲突提示

当 Android 端和桌面端同时修改了同一主机时，三路合并会自动解决大部分情况。仅当两边都修改了同一个字段时，弹出冲突提示：

```
┌────────────────────────────────┐
│   ⚠️ 同步冲突                  │
│                                │
│   主机 "Production Server"     │
│   本地版本: #42 (Android)      │
│   远端版本: #43 (MacBook)      │
│                                │
│   ┌──────────┐ ┌──────────┐   │
│   │ 使用本地 │ │ 使用远端 │   │
│   └──────────┘ └──────────┘   │
└────────────────────────────────┘
```

---

*架构设计 v1.0 — 2026-04-08 — 大龙虾 🦞*
