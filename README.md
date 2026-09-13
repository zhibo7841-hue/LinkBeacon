# LinkBeacon

![LinkBeacon logo](docs/assets/linkbeacon-logo.png)

Open-source Android network analysis and troubleshooting toolbox.

**LinkBeacon by LY**

LinkBeacon 是一个开源 Android 网络分析与故障诊断工具箱，帮助用户了解网络状态、执行针对性的本地检测，并获得排障参考。它不是自动修复工具，也不承诺自动准确诊断所有网络故障。

## Current version

- Application version: `0.5.0` (release candidate preparation; not published yet)
- Minimum Android version: Android 12 (API 31)
- Target Android SDK: API 36
- Android application ID: `com.networktoolbox` (kept for upgrade continuity)

## Core principles

- Open source
- Privacy first
- No ads
- No account required
- Local first
- Network diagnostic data is not uploaded, and app-local data does not participate in system cloud backup by default

## Features

当前 v0.5 代码已实现：

- ✅ Home：网络状态摘要、快速工具与最近诊断
- ✅ Tools：Ping、DNS、TCP、Traceroute、IPv4 Subnet Calculator、LAN Scanner
- ✅ Devices：当前网络下的 LAN Device Center
- ✅ Device Center：设备详情、本地 Saved Devices、Favorites、Custom Names
- ✅ Device Search / Filters
- ✅ Wake-on-LAN：设备详情手动唤醒与已保存设备 Quick Wake
- ✅ Automatic Diagnostics 与 Diagnostic Report
- ✅ 本地 History、报告文本复制、PDF 保存与分享
- ✅ Ping（网络质量、连续检测与详细统计）
- ✅ DNS Lookup（A、AAAA、CNAME、MX、TXT 与 TTL）
- ✅ TCP Port Check
- ✅ LAN Scanner（当前网络与 RFC1918 自定义 IPv4 范围，单次最多 254 个地址）
- ✅ IPv4 Traceroute
- ✅ LAN Device Identification（Reverse DNS、mDNS/Bonjour、SSDP/UPnP）

v0.5 功能范围已冻结。本 README 不把尚未实现的 Stable Identity V2、First Seen / Last Seen、Multi-network Manager 或 Wi-Fi Analyzer 宣传为现有能力。

## Screenshots

<p align="center">
  <img src="docs/screenshots/linkbeacon-overview.png" alt="LinkBeacon v0.5 overview" width="100%">
</p>

## Installation

Requires Android 12 or later.

The signed `0.5.0` APK is ready for the stable GitHub Release; no v0.5.0 GitHub Release has been published yet. The latest public APK remains the published `v0.4.0` release on [GitHub Releases](https://github.com/zhibo7841-hue/LinkBeacon/releases).

## Privacy

- All network test results are stored locally.
- No account required.
- No network data upload.
- App-local data does not participate in Android system cloud backup by default.

LinkBeacon 不要求账号，不上传网络诊断数据；应用本地数据默认不参与系统云备份。应用访问网络是为了执行用户主动选择的检测，不代表会上传检测数据。

## Documentation

- [Product plan](docs/PRODUCT_PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Decision log](docs/DECISIONS.md)
- [Release plan](docs/RELEASE_PLAN.md)
- [v0.5.0 release notes draft](docs/V0.5_RELEASE_NOTES.md)
- [Published v0.4.0 release notes](docs/releases/v0.4.0.md)
- [Development workflow](docs/DEVELOPMENT_WORKFLOW.md)
- [UI design system](docs/UI_DESIGN_SYSTEM.md)
- [OSS research](docs/OSS_RESEARCH.md)

## Contributing

请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

## Security

安全问题请按照 [SECURITY.md](SECURITY.md) 中的说明私下报告，不要公开发布未修复的漏洞细节。

## License

LinkBeacon 使用 [Apache License 2.0](LICENSE) 发布。
