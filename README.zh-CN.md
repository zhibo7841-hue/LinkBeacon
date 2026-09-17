# LinkBeacon

[English](README.md) | 简体中文

![LinkBeacon 标志](docs/assets/linkbeacon-logo.png)

开源 Android 网络分析与故障排查工具箱。

**LinkBeacon by LY**

## 项目介绍

LinkBeacon 帮助用户了解 Android 设备的网络状态、执行针对性的本地检测，
并收集可用于排障的证据。它是网络分析辅助工具，不是自动修复工具，也
不承诺能够确定性地识别所有网络故障。

NetworkToolbox 已在 v0.5.0 更名为 LinkBeacon。Android application ID
继续使用 `com.networktoolbox`，以保持现有安装的升级连续性。

## 开发状态

- **最新稳定版本：** [v0.5.0](https://github.com/zhibo7841-hue/LinkBeacon/releases/tag/v0.5.0)
- **当前 main 分支：** 包含持续进行的双语本地化改进，这些改进尚未包含
  在已签名的 v0.5.0 Release APK 中。

main 分支可能包含尚未进入最新稳定版本的功能或改进。下方功能说明以
当前 main 分支为准；下载链接始终指向正式稳定版本。

## 截图

<p align="center">
  <img src="docs/screenshots/linkbeacon-overview.png" alt="LinkBeacon 界面概览" width="100%">
</p>

## 功能

- 本地网络信息与 IPv4、IPv6 详情。
- IPv4 子网计算器。
- Ping 网络质量统计与连续检测。
- DNS Lookup：支持 A、AAAA、CNAME、MX、TXT 与 TTL 信息。
- 单目标、单端口 TCP Port Check。
- 提供逐跳探测结果的 IPv4 Traceroute。
- LAN Scanner：支持当前网络或受限的 RFC1918 自定义 IPv4 范围。

## 网络诊断

- 基于证据、包含解释与建议的自动网络诊断。
- 可展开技术详情的诊断报告。
- 保存已完成检测和诊断报告的本地历史记录。
- 用户主动操作时复制报告文本、导出 PDF 或分享 PDF。

LinkBeacon 展示检测事实、可能原因和排障建议。当现有证据无法证明唯一
原因时，不会对路由器、运营商、服务或其他基础设施作出绝对判断。

## 设备中心

- 当前网络设备发现与设备详情。
- 仅在本机保存的设备与收藏。
- 不随 App 语言变化的自定义设备名称。
- 针对当前设备与已保存设备的搜索和筛选。
- 在设备提供信息时，通过 Reverse DNS、mDNS / Bonjour 和 SSDP / UPnP
  证据进行本地设备识别。

## Wake-on-LAN

- 从设备详情手动执行本地 Wake-on-LAN。
- 为当前本地网络中符合条件的已保存设备提供快速唤醒。

发送成功只表示 Magic Packet 已交付发送，不保证目标设备已经开机或恢复可达。

## 语言支持

当前 main 分支支持：

- English。
- 简体中文。
- 跟随系统语言。

繁体中文目前不是独立完成本地化的语言。Android 13 及以上版本支持系统级
单应用语言入口；Android 12 使用 App 内兼容的语言设置入口。

## 隐私

- 无广告。
- 无需账号。
- 本地优先存储。
- 诊断数据保留在设备本机。
- 已保存设备、自定义名称和 Wake-on-LAN 配置保留在本机。
- 网络诊断数据不会上传到云服务。
- App 本地数据默认不参与 Android 系统云备份。

LinkBeacon 只会为了执行用户主动选择的检测而访问网络。这不代表检测结果
会被上传。

## 系统要求

- Android 12 或更高版本（`minSdk 31`）。
- 项目当前使用 Android API 36 编译并作为 target SDK。

## 下载

最新稳定版本为 **v0.5.0**：

- [下载 LinkBeacon-v0.5.0.apk](https://github.com/zhibo7841-hue/LinkBeacon/releases/download/v0.5.0/LinkBeacon-v0.5.0.apk)
- [查看 v0.5.0 GitHub Release](https://github.com/zhibo7841-hue/LinkBeacon/releases/tag/v0.5.0)

对应 SHA-256 校验文件与 Release 附件一同提供。v0.5.0 APK 尚未包含当前
main 分支中的全部双语本地化改进。

## 构建与开发

环境要求：

- 安装 Android SDK API 36 的 Android Studio。
- JDK 17。

克隆并构建：

```bash
git clone https://github.com/zhibo7841-hue/LinkBeacon.git
cd LinkBeacon
./gradlew test
./gradlew assembleDebug
```

Windows 请使用 `gradlew.bat` 代替 `./gradlew`。Debug APK 生成在
`app/build/outputs/apk/debug/app-debug.apk`。Release 签名凭据仅由维护者
本地保存，普通开发构建不需要维护者私钥。

## 项目文档

- [产品规划](docs/PRODUCT_PLAN.md)
- [架构设计](docs/ARCHITECTURE.md)
- [决策记录](docs/DECISIONS.md)
- [发布计划](docs/RELEASE_PLAN.md)
- [v0.5.0 Release Notes](docs/V0.5_RELEASE_NOTES.md)
- [已发布的 v0.4.0 Release Notes](docs/releases/v0.4.0.md)
- [开发流程](docs/DEVELOPMENT_WORKFLOW.md)
- [UI 设计系统](docs/UI_DESIGN_SYSTEM.md)
- [开源软件调研](docs/OSS_RESEARCH.md)

`docs/PRODUCT_PLAN.md` 是唯一正式产品范围基线；中文 README 不构成另一套路线。

## 贡献

欢迎使用英文或中文参与贡献。提交 Pull Request 前请阅读
[CONTRIBUTING.md](CONTRIBUTING.md)。

## 安全

请按照 [SECURITY.md](SECURITY.md) 私下报告安全漏洞，不要公开披露尚未修复的
漏洞细节。

## 许可证

LinkBeacon 使用 [Apache License 2.0](LICENSE) 发布。
