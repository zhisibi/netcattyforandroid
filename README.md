# Netcatty for Android

> AI-Powered SSH Client for Android — based on [Netcatty](https://github.com/binaricat/Netcatty)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android">
  <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin">
  <img src="https://img.shields.io/badge/Compose-Material3-4285F4?style=for-the-badge&logo=jetpackcompose">
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge">
</p>

## 📱 What is Netcatty for Android?

Netcatty for Android is a mobile SSH client and terminal manager, ported from the desktop [Netcatty](https://github.com/binaricat/Netcatty) project. It brings the power of AI-assisted server management to your fingertips.

### Core Features

- 🔐 **SSH Connection** — Password / Key / Certificate authentication
- 🖥️ **Terminal** — Full xterm-256color terminal with touch-friendly input
- 📁 **SFTP** — Dual-pane file browser with upload/download
- 🔄 **Cloud Sync** — Zero-knowledge encrypted sync with desktop (GitHub Gist / Google Drive / OneDrive / WebDAV / S3)
- 🤖 **AI Assistant** — Natural language server management
- 🔑 **Secure Storage** — Android Keystore + AES-GCM field encryption + Biometric unlock
- 📡 **Port Forwarding** — Local / Remote / Dynamic SSH tunnels
- ⚡ **Snippets** — Quick command templates

## 🏗️ Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin 2.0+ |
| UI | Jetpack Compose + Material 3 |
| SSH | JSch 0.2.16 |
| Terminal | Termux terminal-emulator |
| Database | Room + SQLite |
| DI | Hilt |
| Network | OkHttp + Retrofit |
| Crypto | Android Keystore + JCA (AES-GCM / PBKDF2) |

## 📖 Documentation

| Document | Description |
|----------|-------------|
| [Feasibility Report](docs/Netcatty-Android-Feasibility-Report.md) | 移植可行性分析与方案对比 |
| [Development Guide](docs/Netcatty-Android-Dev-Doc.md) | 开发文档（技术选型、模块指南、数据模型） |
| [Architecture Design](docs/Netcatty-Android-Architecture.md) | 架构设计（分层、核心模块、安全、云同步） |

## 🗓️ Roadmap

| Phase | Content | Timeline |
|-------|---------|----------|
| 0 | Project setup (Hilt + Room + JSch) | Week 1 |
| 1 | Core SSH + Terminal + Vault | Week 2-5 |
| 2 | SFTP + File management | Week 6-8 |
| 3 | Split terminal + Port forwarding + Themes | Week 9-11 |
| 4 | Encryption + Biometric + Cloud sync | Week 12-13 |
| 5 | AI Chat integration | Week 14-15 |
| 6 | Polish + Play Store | Week 16-17 |

## ☁️ Cloud Sync Compatibility

Netcatty for Android uses the **same encrypted sync format** as the desktop version, enabling seamless bidirectional sync:

- Same master password → same derived key → can decrypt each other's files
- Git-style three-way merge handles multi-device edits
- Zero-knowledge: cloud providers only see encrypted ciphertext

## 📄 License

This project is licensed under **GPL-3.0-or-later**, consistent with the upstream [Netcatty](https://github.com/binaricat/Netcatty) project.

---

*Made with 🦞 by zhisibi*
