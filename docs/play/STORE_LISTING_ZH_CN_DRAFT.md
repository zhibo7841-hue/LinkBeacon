# LinkBeacon 简体中文商店资料草案

Status: **Draft / Pending Maintainer Review**

准备及政策查阅日期：**2026-10-11**

产品依据：[v0.9.0 PRODUCT_PLAN](../PRODUCT_PLAN.md)

开发者：**LanYun Studio**

支持联系方式：**[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]**

以下三个字段的代码块是拟用商店正文，备注和版本亮点不计入描述字段。不代表 Google 审核通过或商店已经上线。

## App Title

```text
LinkBeacon
```

## Short Description

```text
网络诊断、Wi-Fi 分析与本地设备工具箱，无广告，无需账号。
```

## Full Description

```text
LinkBeacon 是开源 Android 网络分析与故障排查工具箱。先看清晰易懂的结论，再按需展开专业数据，了解当前网络发生了什么。

网络工具
- 查看当前 IP 地址、网关、网络配置 DNS 与连接信息。
- 使用 Ping 查看可达性、延迟、丢包率与网络质量。
- 查询 A、AAAA、CNAME、MX、TXT DNS 记录，查看可获取的 TTL 和查询耗时。
- 检测单个 TCP 端口，或使用快速端口集合及 1–65535 自定义范围执行 TCP Connect 端口扫描。
- 使用 IPv4 路由追踪查看逐跳探测结果。
- 计算 IPv4 子网。

局域网设备
- 自动或自定义扫描本地私有 IPv4 范围，最多 254 个地址。
- 查看可获取的反向 DNS、mDNS 与 UPnP 设备信息，发现能力取决于设备响应。
- 在设备中心本地保存设备资料、收藏、自定义名称、设备类型与备注。
- 向兼容的本地设备发送 Wake-on-LAN 唤醒数据包。

Wi-Fi 与网站
- 查看可获取的 Wi-Fi 信号、信道和附近接入点观察，受 Android 权限、系统定位服务和扫描限制约束。
- 检查 SSL/TLS 连接与证书信息。
- 分析 HTTP/HTTPS 网站连接阶段、重定向与响应信息。

诊断与记录
- 自动网络诊断根据检测证据提供解释、可能原因和排障建议。
- 本地查看受支持的检测历史与报告，支持报告复制、PDF 导出及分享。

无广告，无需 LinkBeacon 账号。诊断结果与设备资料采用本地优先方式，不会自动上传到开发者服务器。检测会向目标和解析器发送必要请求；你主动导出或分享时，报告才会通过相应操作离开设备。

需要 Android 12 及以上。请仅检测你拥有或已获授权的网络与设备。LinkBeacon 辅助排障，不会自动修复网络，也不保证所有设备或路由节点都会响应。
```

## 字符校验

统计包括字段内空格、标点和单个 LF 换行，不包括 Markdown 代码块及末尾分隔换行。本文拟用正文均为 BMP 字符，因此也与 UTF-16 长度一致。

| 字段 | 字符数 | 上限 | 结果 |
| --- | ---: | ---: | --- |
| App Title | 10 | 30 | 符合 |
| Short Description | 31 | 80 | 符合 |
| Full Description | 810 | 4000 | 符合 |

[Google 官方字段限制](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)，2026-10-11 查阅。修改后或粘贴到 Console 后再次校验。

## Release Highlights — 独立草案

v0.9.0 增加 Wi-Fi Analyzer，提供具备权限边界的连接信息和附近信道观察，同时保留本地网络工具、设备中心、自动诊断与 English / 简体中文支持。这不是已发布 Google Play 版本声明。

## Console 建议与待维护者确认字段

- 类型：App；建议分类：Tools（工具）。
- 两种语言均使用 LinkBeacon 标题；开发者为 LanYun Studio。
- 支持邮箱：`[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]`；隐私政策 URL 尚未确定。
- Play 计划一次性付费完整版，价格和可用地区由维护者后续确认；没有额外应用内购买或订阅实现。
- 可以准确说明开源、无广告、无需账号，但不在商店描述增加免费替代下载或绕过购买的引导。[付款政策](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en)，2026-10-11 查阅。
- 不宣传 WHOIS、iPerf、最佳信道、实时干扰测量、自动修复、SSH/Telnet 终端、云端诊断、UDP/SYN 扫描、Banner 或服务指纹识别。
- 截图须为真实当前界面、本语言且已审阅隐私，参见[资源清单](STORE_ASSET_CHECKLIST.md)。
- 目标年龄、IARC、应用访问、权限和 Data Safety 声明需维护者审阅，参见[合规审计](PLAY_COMPLIANCE_AUDIT.md)。
