好，我来制定详细的开发计划，然后按顺序执行。

Netcatty Android 开发计划

Phase 0: 项目搭建 (当前)

• [ ] 0.1 创建 Android 项目结构
• [ ] 0.2 配置 build.gradle.kts (依赖、Compose、Hilt)
• [ ] 0.3 配置 AndroidManifest.xml
• [ ] 0.4 创建 Application + Hilt 入口
• [ ] 0.5 创建 MainActivity (单 Activity + Compose)
• [ ] 0.6 配置 Navigation 框架
• [ ] 0.7 创建基础 Theme + Material 3

Phase 1: 数据层基础

• [ ] 1.1 Domain 模型定义 (Host/SshKey/Snippet/等)
• [ ] 1.2 Room Database + Entity + DAO
• [ ] 1.3 Repository 接口 + 实现
• [ ] 1.4 FieldCryptoManager (字段级加密)
• [ ] 1.5 SessionKeyHolder + AppKeyStore

Phase 2: SSH 核心引擎

• [ ] 2.1 SshSessionManager (连接/断开/读写)
• [ ] 2.2 SshAuthHandler (密码/密钥/键盘交互式)
• [ ] 2.3 NetcattyTerminalSession (JSch ↔ Termux 桥接)
• [ ] 2.4 SshConnectionService (前台服务)

Phase 3: UI - Vault 主机管理

• [ ] 3.1 VaultScreen (列表/网格/搜索)
• [ ] 3.2 HostDetailSheet (编辑主机)
• [ ] 3.3 KeychainManager (密钥管理)
• [ ] 3.4 分组管理

Phase 4: UI - 终端

• [ ] 4.1 TerminalScreen (AndroidView 嵌入)
• [ ] 4.2 TerminalToolbar + 特殊键行
• [ ] 4.3 Tab 管理与多会话
• [ ] 4.4 终端主题系统

Phase 5: SFTP

• [ ] 5.1 SftpClient 实现
• [ ] 5.2 SftpScreen (双面板)
• [ ] 5.3 文件传输 + 进度
• [ ] 5.4 内置文本编辑器

Phase 6: 高级功能

• [ ] 6.1 端口转发
• [ ] 6.2 Snippet 快捷命令
• [ ] 6.3 分屏终端
• [ ] 6.4 生物识别解锁

Phase 7: 云同步

• [ ] 7.1 EncryptionService (与桌面端兼容)
• [ ] 7.2 MergeEngine (三路合并)
• [ ] 7.3 GitHub Gist 适配器
• [ ] 7.4 WebDAV / S3 适配器
• [ ] 7.5 SyncManager 总协调

Phase 8: AI

• [ ] 8.1 AI Provider 配置
• [ ] 8.2 AI Chat 流式对话
• [ ] 8.3 AI Chat UI

───

现在从 Phase 0 开始执行。先创建 Android 项目骨架：
