# Netcatty Android 移植可行性报告

> **项目**: Netcatty → Android APK  
> **分析日期**: 2026-04-08  
> **分析人**: 大龙虾 🦞  
> **源项目**: https://github.com/binaricat/Netcatty

---

## 一、项目概况

### 1.1 Netcatty 是什么

Netcatty 是一个**桌面端 SSH 客户端 + 终端管理器 + SFTP 浏览器**，定位对标 Termius / SecureCRT / PuTTY，核心卖点：

- **Catty Agent**: 内置 AI 助手，可用自然语言管理服务器、执行多主机编排
- **Split Terminal**: 分屏终端 + Tab 管理 + Session 恢复
- **SFTP**: 双面板文件管理 + 内置编辑器 + 拖拽上传
- **Vault**: 主机分组管理（Grid / List / Tree 三种视图）
- **Port Forwarding**: SSH 端口转发隧道
- **Cloud Sync**: GitHub Gist / Google Drive / OneDrive / WebDAV / S3 多端同步
- **AI Integration**: 支持 OpenAI / Anthropic / Google / Ollama / OpenRouter 等多 Provider

### 1.2 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | **Electron** | 40 |
| 前端 | React + TypeScript | React 19, TS 5.9 |
| 构建 | Vite | 7 |
| 终端 | xterm.js | 6 (addon-webgl) |
| 样式 | Tailwind CSS | 4 |
| SSH/SFTP | ssh2, ssh2-sftp-client | ssh2 1.17 |
| PTY | node-pty | 1.1.0 |
| AI | ai SDK (Vercel) | 6.0 |
| 云同步 | @aws-sdk/client-s3, webdav | — |
| 代码编辑 | Monaco Editor | 0.55 |
| 图标 | Lucide React | — |

### 1.3 代码规模

| 目录 | 行数 | 说明 |
|------|------|------|
| `components/` | ~62,500 | React UI 组件（终端、SFTP、AI、设置等） |
| `application/` | ~18,100 | 状态管理、i18n |
| `electron/` | ~23,500 | Electron 主进程 + 20+ IPC Bridge |
| `infrastructure/` | ~12,000 | 服务层（云同步、AI、加密等） |
| `domain/` | ~4,250 | 领域模型定义 |
| `lib/` | ~2,400 | 工具函数 |
| **总计** | **~126,000 行** | TypeScript + CJS |

---

## 二、核心模块依赖分析

### 2.1 不可直接移植的模块（🔴 硬阻断）

| 模块 | 依赖 | 问题 |
|------|------|------|
| **Electron 主进程** | Electron 40, BrowserWindow, ipcMain | Android 无 Electron 运行时 |
| **node-pty** | Native C++ addon, POSIX PTY | Android 无 `/dev/ptmx`，需替代方案 |
| **ssh2 (Node.js)** | Native C++ addon (cpu-features) | 纯 JS 实现可用但有性能折衷 |
| **SerialPort** | Native C++ addon | Android USB-serial 需完全不同 API |
| **Monaco Editor** | 浏览器 DOM + Web Worker | 移动端体验差，体积大（~5MB） |
| **Electron safeStorage** | OS Keystore 集成 | 需替换为 Android Keystore |
| **node:child_process** | PTY spawning | Android 沙箱无 fork/exec |

### 2.2 可复用但需适配的模块（🟡 需改造）

| 模块 | 复用度 | 改造方向 |
|------|--------|----------|
| **React 前端 (components/)** | ~60% | 大量桌面端 UI 需重构为移动端布局 |
| **xterm.js 终端渲染** | ~80% | 触屏适配、软键盘处理、WebGL→Canvas |
| **SFTP 业务逻辑** | ~70% | ssh2→JSch/Kotlin SSH 桥 |
| **AI Chat (Vercel AI SDK)** | ~60% | 流式 HTTP 可直接用，但需去掉 node 依赖 |
| **云同步逻辑** | ~50% | OAuth 流程需改用 Android 原生 |
| **Domain 模型** | ~90% | 纯 TypeScript 类型定义，几乎可直接复用 |
| **i18n** | ~95% | 纯数据，直接复用 |
| **终端主题数据** | ~95% | 纯配色数据，直接复用 |

### 2.3 可直接复用的模块（🟢 低成本）

| 模块 | 说明 |
|------|------|
| Domain 类型定义 | `domain/models.ts` 等，纯 TypeScript 接口 |
| i18n 翻译文件 | `application/i18n/locales/` |
| 终端主题数据 | `infrastructure/config/terminalThemes.ts` |
| SSH 配置序列化 | `domain/sshConfigSerializer.ts` |
| 同步合并逻辑 | `domain/syncMerge.ts` |

---

## 三、移植路线方案对比

### 方案 A: Capacitor 封装（保留 React 前端）

**思路**: 保留 React + xterm.js 前端，用 Capacitor 打包为 Android WebView 应用，SSH 层用 Java/Kotlin 原生实现并通过桥接暴露给 JS。

| 维度 | 评估 |
|------|------|
| 前端复用率 | ~60-70%（UI 需大量移动端适配） |
| 开发周期 | 3-4 个月 |
| 性能 | 中等（WebView 开销 + JS Bridge 延迟） |
| 终端体验 | xterm.js 在 WebView 中可用但触屏体验需调优 |
| SSH 性能 | 原生 JSch/Kotlin SSH → 优秀 |
| 风险 | WebView 性能瓶颈、复杂手势交互困难 |

**优点**:
- 前端代码最大程度复用
- 可共享 Web 端和桌面端的业务逻辑
- 快速出 MVP

**缺点**:
- Capacitor 打包后 `cap sync` 会覆盖 Maven 镜像配置（已知坑）
- WebView 性能瓶颈（尤其 WebGL 渲染终端）
- 原生 Bridge 需要大量手写 Kotlin 代码
- 桌面端 UI 布局在移动端体验差（侧边栏、分屏等）

### 方案 B: 原生 Android (Kotlin + Compose)（推荐 ✅）

**思路**: 用 Kotlin + Jetpack Compose + Material 3 重写整个应用。SSH/SFTP 使用 JSch 或 Kotlin-ssh2，终端使用自定义 Compose 渲染或 Termux 的 terminal-emulator 库。

| 维度 | 评估 |
|------|------|
| 前端复用率 | ~10%（仅数据模型和逻辑可参考） |
| 开发周期 | 4-6 个月 |
| 性能 | 优秀（原生渲染 + 原生 SSH） |
| 终端体验 | 可用 Termux 的 terminal-emulator 或自定义 |
| SSH 性能 | 原生 JSch → 优秀 |
| 风险 | 工作量大，但可控 |

**优点**:
- 最佳 Android 体验和性能
- 原生 Keystore 集成、生物识别
- 完全控制 UI/UX，适配移动端交互范式
- 后续可扩展到 Wear OS / 折叠屏

**缺点**:
- 几乎等于重写，工作量大
- 需要独立维护 Android 代码库

### 方案 C: React Native + 原生 SSH 模块

**思路**: React Native 重写前端，SSH/终端部分用 Native Module 实现。

| 维度 | 评估 |
|------|------|
| 前端复用率 | ~30%（React 组件可参考但需重写为 RN 组件） |
| 开发周期 | 4-5 个月 |
| 性能 | 良好 |
| 终端体验 | 需自定义 Native 终端组件（复杂） |
| 风险 | RN 自定义终端组件是高风险项 |

**优点**:
- 可跨 iOS/Android
- React 开发者友好

**缺点**:
- RN 终端渲染是已知难题，社区无成熟方案
- xterm.js 无法在 RN 中使用
- SSH 原生模块仍需手写

---

## 四、推荐方案：方案 B（原生 Android）

### 4.1 理由

1. **终端体验是核心**：Netcatty 的核心是 SSH 终端，Android 上原生终端渲染远优于 WebView
2. **已有经验**：博士已开发 sbssh（Kotlin + Compose SSH 客户端），技术栈和经验可直接复用
3. **性能保障**：原生 SSH + 原生终端渲染，不担心 WebView 瓶颈
4. **Android 生态整合**：Keystore、生物识别、通知、快捷方式等原生能力
5. **维护成本**：单一语言栈（Kotlin），不需要维护 JS-Kotlin Bridge

### 4.2 可从 Netcatty 移植的内容

| 内容 | 移植方式 |
|------|----------|
| Domain 模型（Host, SSHKey, Snippet, Group 等） | 参照 TypeScript 类型定义，转为 Kotlin data class |
| 终端主题配色 | JSON 数据直接移植 |
| i18n 翻译 | zh-CN/en 字符串移植为 Android strings.xml |
| SSH 配置解析逻辑 | 参照 sshConfigSerializer 逻辑实现 |
| 同步合并算法 | 参照 syncMerge 逻辑 |
| AI Chat 接口定义 | 参照 AI provider 架构设计 Retrofit 接口 |
| 快捷命令 (Snippet) | 数据模型 + UI 设计参考 |
| 端口转发逻辑 | 参照 portForwardingService 设计 |

### 4.3 核心技术选型

| 模块 | 技术 | 说明 |
|------|------|------|
| 语言 | Kotlin 2.0+ | 主力开发语言 |
| UI | Jetpack Compose + Material 3 | 声明式 UI |
| SSH | JSch 0.2.x 或 Kotlin-ssh2 | JSch 成熟稳定 |
| SFTP | JSch SFTP Channel | 与 SSH 同库 |
| 终端渲染 | Termux terminal-emulator (Java) | 开源、成熟、可嵌入 |
| 数据存储 | Room + AES-GCM 字段加密 | 参考 sbssh 方案 |
| 状态管理 | ViewModel + StateFlow | 标准 Android 架构 |
| 依赖注入 | Hilt | 标准 DI |
| 网络请求 | OkHttp + Retrofit | AI API 调用 |
| 加密 | Android Keystore + PBKDF2 | 密钥安全存储 |
| 生物识别 | BiometricPrompt | 指纹/面部解锁 |

---

## 五、功能优先级规划

### Phase 1: 核心 SSH 客户端 (4 周)

- [ ] 项目搭建（Compose + Hilt + Room + JSch）
- [ ] 主机管理 CRUD（Vault：Grid / List 视图）
- [ ] SSH 连接 + 终端渲染
- [ ] 密码 / 密钥认证
- [ ] 终端基本操作（输入、复制、粘贴、滚动）
- [ ] 主机分组

### Phase 2: SFTP + 文件管理 (3 周)

- [ ] SFTP 双面板浏览器
- [ ] 文件上传/下载 + 进度条
- [ ] 内置文本编辑器
- [ ] 文件操作（重命名、删除、权限修改）
- [ ] 拖拽交互（Android 长按拖拽）

### Phase 3: 高级终端功能 (3 周)

- [ ] 分屏终端（Compose 布局分割）
- [ ] Tab 管理与 Session 恢复
- [ ] 端口转发（Local / Remote / Dynamic）
- [ ] Snippet 快捷命令
- [ ] 关键字高亮
- [ ] 自定义终端主题

### Phase 4: 安全与云同步 (2 周)

- [ ] Android Keystore + PBKDF2 加密存储
- [ ] 生物识别解锁
- [ ] 云同步（GitHub Gist / WebDAV / S3）
- [ ] 配置导入/导出

### Phase 5: AI 集成 (2 周)

- [ ] AI Chat 侧边栏
- [ ] 多 Provider 支持（OpenAI / Anthropic / Google / Ollama）
- [ ] 自然语言服务器管理
- [ ] AI 辅助诊断

### Phase 6: 锦上添花 (2 周)

- [ ] 串口连接（USB OTG + serial）
- [ ] Telnet / Mosh 协议支持
- [ ] 自动更新
- [ ] Widget 快捷连接
- [ ] 折叠屏 / 大屏适配

---

## 六、关键风险与缓解

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| JSch 在 Android 上兼容性 | 🟡 中 | 已有 sbssh 项目验证 JSch 可用；备选 Kotlin-ssh2 |
| 终端渲染性能 | 🟡 中 | 使用 Termux terminal-emulator 库，已在 Termux 上充分验证 |
| SSH2 协议兼容性 | 🟢 低 | JSch 支持 SSH2 全特性（密钥交换、加密算法、证书认证） |
| SFTP 大文件传输 | 🟡 中 | 实现断点续传 + 分块传输 + 后台 Service |
| AI 流式响应在移动端 | 🟢 低 | OkHttp SSE/流式已成熟 |
| 云同步 OAuth 流程 | 🟡 中 | 使用 Android 原生 Custom Tabs 或 Chrome Custom Tabs |
| GPL-3.0 许可证合规 | 🟢 低 | Android 版同样使用 GPL-3.0 开源即可 |

---

## 七、成本估算

| 项目 | 工时 | 说明 |
|------|------|------|
| 项目搭建 + 架构设计 | 1 周 | Hilt + Room + JSch 集成 |
| Phase 1: 核心 SSH | 4 周 | 终端渲染 + 主机管理 + 认证 |
| Phase 2: SFTP | 3 周 | 双面板 + 传输 + 编辑器 |
| Phase 3: 高级终端 | 3 周 | 分屏 + 端口转发 + 主题 |
| Phase 4: 安全 + 云同步 | 2 周 | 加密 + 生物识别 + 同步 |
| Phase 5: AI | 2 周 | Chat + Provider |
| Phase 6: 锦上添花 | 2 周 | 串口 / Widget 等 |
| **总计** | **~17 周 (4 个月)** | 1 人全职开发 |

---

## 八、结论

**Netcatty 移植为 Android APK 完全可行，推荐使用原生 Android (Kotlin + Compose) 方案。**

核心论据：
1. 项目核心功能（SSH 终端 + SFTP + 主机管理）在 Android 上有成熟的原生实现方案
2. 博士已有 sbssh 项目经验，可大幅缩短学习曲线
3. Termux terminal-emulator 提供了经过验证的 Android 终端渲染方案
4. JSch 是 Android 上最成熟的 SSH 库，社区活跃
5. 126K 行代码中，真正的"不可移植"部分是 Electron 主进程和 node-pty，其余业务逻辑和 UI 设计均可参考复用

**不建议的方案**: Capacitor 封装 — 终端应用对性能和触屏交互要求高，WebView 封装会导致体验严重降级。

---

*报告完成于 2026-04-08*
