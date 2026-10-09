# LinkBeacon v0.9.0 Product Audit

日期：2026-10-09（Asia/Shanghai）

任务：Task 129，Research + Audit only。

审计源码基线：`4024d0270d0b69acddfd509f092eeb5511a6cef5`。

研究资料，不是正式路线；所有后续建议均为 **Candidate only / Not approved**。

## 1. Executive Summary

LinkBeacon 已不是只有工具按钮的集合：自动诊断已有结构化观测、证据引用、置信度、保守故障边界和最多三条建议；Website Diagnostics 已贯通 DNS、TCP、TLS、证书、HTTP 和重定向。Device Center 将当前观察与保存资料分离，Wi-Fi Analyzer 对缓存和平台限制的表述较严谨。

最重要的三处产品短板：

1. **诊断入口仍是通用检测，尚未变成用户问题驱动的排障。** `ReportViewModel.runCheck()` 使用无目标的 `DiagnosticIntent()`；目标阶段因此跳过。已有网站诊断不能直接成为通用诊断的同一份证据，Wi-Fi/TLS/HTTP 也未接入自动分析。
2. **协议和路径边界需要更容易被用户理解。** Ping 实际是系统可达性检测，不是专用 ICMP；IPv6 地址/DNS 记录存在不等于 IPv6 公网可用；直连 TLS 与系统代理 HTTP 的结果不能无条件拼成同一路径。当前代码大体保守，但普通用户仍需自己在工具间推理。
3. **证据保存与长期使用体验不完整。** 七类 History 可保存，自动诊断可复制/PDF/分享；并非所有工具均有历史或报告。保存异常被吞掉，History 列表一次组合全部记录；这两个问题有源码依据，但尚无大历史库性能实测，不应夸大为已发生的数据丢失或卡顿。

建议优先研究“小规模引导排障 + 现有工具的目标传递/证据说明”，而不是一次运行所有工具、重写诊断框架或直接新增大量协议。详见[候选报告](LINKBEACON_NEXT_VERSION_CANDIDATES.md)。

## 2. Baseline

| 项目 | 本次只读核验结果 |
| --- | --- |
| 正式范围唯一基线 | `docs/PRODUCT_PLAN.md`；v0.9.0 Released / Scope Frozen，下一版本未批准 |
| 分支 | 开始审计时 `main == origin/main == 4024d02`，工作区干净 |
| 当前提交 | `docs: update screenshots`；不是新 Runtime 发布 |
| 发布 Tag 对应提交 | `v0.9.0^{commit} = b1247195c59dc9b57fc77efbc5d3aad594b1e6a4` |
| 版本 / 包名 | `0.9.0` / code `10`；`com.networktoolbox`；显示名 LinkBeacon |
| Android | minSdk 31，targetSdk / compileSdk 36；JDK 17 |
| 数据库 | Room version 5，History + 保存设备资料；本轮无迁移 |
| 模块 | `app`、5 个 `core`、11 个 `feature`，共 17 个 Gradle 模块 |
| 设计资产 | `design/launcher_icon.svg`；实际 UI 共用 `core:designsystem` |
| 正式 Release | [LinkBeacon v0.9.0](https://github.com/zhibo7841-hue/LinkBeacon/releases/tag/v0.9.0)，GitHub API 核验非 draft、非 prerelease，发布于 2026-10-09 13:31:54 UTC |
| 正式 APK | `LinkBeacon-v0.9.0.apk`，52,892,723 bytes；GitHub asset digest 与本地 final APK SHA-256 一致 |
| APK SHA-256 | `C90A80F70DCC4879221483ADC10B9191945A5620377342911E236C80FDED5C93` |

完整阅读了 PRODUCT_PLAN、DECISIONS、ARCHITECTURE、UI_DESIGN_SYSTEM、两版 README 和 CHANGELOG；同时参考 Wi-Fi、Web Diagnostics、自动诊断设计、诊断规则验收和 v0.9 Release Readiness/Notes。历史设计中的“当前实现”描述有日期，不能覆盖较新的源码与正式基线。

证据优先级：当前入口/源码 → 当前测试契约 → 有日期的实际验收记录 → 功能设计 → README 宣传。测试存在、历史测试通过、此次执行通过是三件不同的事。

## 3. Feature Inventory

状态：A = Released and usable；B = Implemented but limited；C = Partially implemented；D = Planned / candidate；E = Not applicable / out of scope。A 仅表示正式流程可用，不表示无风险或所有 Android 设备均通过。

| # | 功能 | 状态 | 当前实际能力 / 主要边界 | 源码与测试证据 |
| --- | --- | --- | --- | --- |
| 01 | Home Network Information | A | 当前网络、地址/掩码/网关/DNS、展开详情、SSID 可用时显示 | E01 |
| 02 | IPv4 / IPv6 | B | 真实地址、前缀、IPv6 分类、部分双栈工具；非完整双栈诊断 | E01/E03/E04/E07 |
| 03 | Subnet Calculator | A | 纯 IPv4 CIDR/掩码、网络/广播/可用地址计算 | E02 |
| 04 | Ping / ICMP | B | 有限 Session、统计、停止、质量摘要；系统可达性，非专用 ICMP | E03 |
| 05 | DNS Lookup | B | A/AAAA/CNAME/MX/TXT、真实 TTL、去重；仅系统解析路径 | E04 |
| 06 | TCP Connectivity | A | 单目标单端口、类型化 connect/refused/timeout/route 结果 | E05 |
| 07 | Port Scanner | B | 24 个 Quick ports、1–65535 自定义 TCP Connect、有界并发/取消 | E06 |
| 08 | Traceroute | B | 非 Root IPv4 UDP/NDK 路径，逐跳探测及取消保留结果 | E07 |
| 09 | LAN Scanner | B | 私网 IPv4 自动/自定义范围，最多 254 地址；受控发现 | E08 |
| 10 | Device Identification | B | PTR、mDNS、SSDP/UPnP 名称/类型证据；非完整资产识别 | E09 |
| 11 | Device Center | A | 当前/已保存设备、搜索/筛选、详情、工具入口 | E10 |
| 12 | Saved Devices | A | 本地资料、上次地址/观察、用户管理状态保留 | E10/E11 |
| 13 | Favorites | A | 收藏与资料保留规则；不是每次扫描重新创建 | E11 |
| 14 | Device Profile Editing | A | Custom Name / Type / Notes 统一编辑、草稿和返回确认 | E11/E17 |
| 15 | Wake-on-LAN | A | 显式用户动作，MAC/UDP 配置及当前 LAN 广播 | E12 |
| 16 | Wi-Fi Analyzer | B | 当前连接、附近 AP、搜索/频段、手动刷新、新鲜/缓存 | E13 |
| 17 | Channel Overview | B | 可见 AP 数量及最强观察 RSSI，不是干扰/占用率测量 | E13 |
| 18 | SSL/TLS Check | A | 系统信任、主机名、证书及握手事实和建议 | E14 |
| 19 | Website Diagnostics | A | DNS→TCP→TLS/证书→HTTP、受限重定向、路径提示 | E15 |
| 20 | WHOIS | D | 未找到正式入口、引擎、UseCase 或测试；不是已支持能力 | E00 |
| 21 | iPerf | D | 未找到实现/依赖/入口；吞吐测量是未批准候选 | E00 |
| 22 | Automatic Network Diagnosis | B | 已有规则化完整通用流程；目标 UI / TLS / HTTP / Wi-Fi 联动缺口 | E16 |
| 23 | History | A | 七类本地记录、删除/确认清空、部分完整快照恢复 | E11/E18 |
| 24 | Reports / PDF / Share | C | 自动诊断完整报告可导出；非全工具统一报告 | E18/E19 |
| 25 | Settings / Language | A | English / 简体中文、AppCompat locale；Settings语言入口及独立About/Privacy | E17 |
| 26 | Privacy & Permissions | A | 无账号/广告/分析上传路径；按使用申请 Wi-Fi 位置权限 | E13/E20 |

统计：**A 13、B 10、C 1、D 2、E 0，共 26 个审计项**。24 项至少有实际能力；其中 23 项 A/B 有发布流程，报告统一性为 C。它们并不是 26 个独立菜单：Saved/Favorite/Profile 是同一资料链，Channel Overview 是 Wi-Fi 子功能。Tools catalog 实际是 11 个工具入口。完整 SSH/Telnet 终端及 SFTP 属于额外边界 E，不计入上述库存。

## 4. Functionality Audit

以下每项统一覆盖十个维度：A 完整性；B 正确性；C 错误处理；D 易懂性；E 原因解释；F 排障建议；G Android；H 隐私权限；I History/Report；J 测试/维护。状态和这些维度的字母互不等同。源码/测试索引位于第 11 节；平台与真实验收证据集中在第 8 节。

### 01–03：网络事实与计算

**01 Home [E01]**

- A/B/C：统一 NetworkRepository，缺失字段显示不可用；IPv4 默认网关优先，IPv6 链路本地不等于公网。`partialConnectivity` 当前实际为 null，不可宣称已可靠读取。
- D/E/F：摘要加详情合理，最近诊断复用历史；本卡解释网络事实而非定位故障，可进入网络诊断，不自行给故障建议。
- G/H/I/J：SSID 可能被权限/平台遮蔽，Android 12 有校验当前 IPv4 后的旧 API fallback；卡片不自动保存历史。Mapper/Presentation 和 recreation 有覆盖；相同网络事实的路径识别仍有局限。

**02 IPv4 / IPv6 [E01/E03/E04/E07]**

- A/B/C：IPv4/IPv6 地址和 DNS 支持、Ping 协议选择已实现；Subnet/LAN/Traceroute 不因此变为 IPv6 工具。Link-local scope 在通用 reader 中被截去，不可靠探测该网关时保留未知。
- D/E/F：“已配置”比“公网可用”准确；无 AAAA 不是故障。尚缺同一目标按地址族分别验证、比较结果的普通用户解释链。
- G/H/I/J：OEM、VPN、IPv6-only 环境约束；无额外权限。随相关工具存储，不是独立记录；IPv6 fixtures ≠ IPv6-only 真实端到端验收。

**03 Subnet [E02]**

- A/B/C：纯 Kotlin IPv4 运算和输入校验完整；不做 IPv6 subnet，不因输入失败发起网络探测。
- D/E/F：给网络/广播/范围等确定计算结果；它不是网络故障诊断，没有自动排障建议。
- G/H/I/J：不需联网权限，不保存 History/Report；`SubnetCalculatorTest` 在 core:common，不能因 feature:subnet 无独立 XML 就称无测试。

### 04–08：连通性与路径

**04 Ping [E03]**

- A/B/C：快速 5 次/500 ms，连续首次默认 100 次/1000 ms，有限次数/停止；丢包、min/avg/max、相邻成功样本差值均值 jitter。质量分级为应用启发式，不是运营商 SLA。
- D/E/F：默认质量/平均/丢包，详细信息折叠；`InetAddress.isReachable()` 的失败仅表示本次方法无响应，不能证实离线。专用 ICMP、无限监控、趋势图未实现。
- G/H/I/J：普通探测流量；每 Session 一条 History。取消由 coroutine 管理，但底层阻塞可达性/DNS 不等于可即时关闭 Socket；需实测停止延迟。统计/Fake Session/ViewModel 已覆盖，未在此次重新联网测量。

**05 DNS [E04]**

- A/B/C：`DnsResolver.rawQuery` + 纯解析器；compression/TTL/MX priority/TXT 多段、去重 TTL min；A 成功+AAAA NO_RECORDS 为成功，真正异常混合为 PARTIAL。
- D/E/F：系统解析器、网络配置 DNS/Private DNS 与响应服务器分开；Fake-IP 是提示。单查失败有解释，但不能推出整个互联网失败。
- G/H/I/J：Android active Network 和系统 Private DNS 路径；一次 Lookup 一条 History，取消不伪造完成。模型只有聚合状态，未保留每种查询的独立 outcome；更细双栈证据需后续扩契约，不能从无记录倒推出 TIMEOUT。

**06 TCP [E05]**

- A/B/C：参数校验、真实 Connect、类型化拒绝/超时/无路由等；取消关闭尝试，连接成功不证明应用协议成功。
- D/E/F：保留技术原因与解释；refused 表示对应连接被拒绝，不指认路由器/ISP。与 DNS/应用层问题尚需用户串联。
- G/H/I/J：INTERNET，普通目标流量；一条 TCP History，不是独立 PDF。connector 取消及 errno/outcome 契约有测试；同一结果在 LAN 和诊断的证据用途应分别解释。

**07 Port Scanner [E06]**

- A/B/C：Quick 24 ports / custom 1–65535，default 64/max 64、connect timeout 1000 ms，有界 workers 和 active socket cleanup；只 TCP Connect，无 UDP/SYN/banner。
- D/E/F：Open ports 和常见 service hint；端口号只是服务线索，不是已识别软件/version/CVE。Cancelled/Network Changed 是部分观察，不是完整资产结论。
- G/H/I/J：需授权目标，可能产生较多连接；不写 History、不改资料身份/Last Seen、不接自动诊断。Fake worker/cancel/network tests和历史实机性能记录可参考，未测所有网络设备。

**08 Traceroute [E07]**

- A/B/C：app UID 下 IPv4 UDP error queue / NDK，逐 Hop、三 Probe、PARTIAL/CANCELLED 保留路径；超时跳不能证明路由断开。
- D/E/F：目标/部分响应/无响应层次清楚；没有 PTR/ASN/GeoIP/MTR/IPv6，也不承诺每个路由器都返回 ICMP 错误。
- G/H/I/J：native/OEM/VPN 限制比纯 Kotlin 大，非 Root；不写 History/Report。mapper/validation/engine/ViewModel 有测试，Sony 历史验证不覆盖所有 ROM/native ABI 路径。

### 09–15：设备工作流

**09 LAN Scanner [E08]**

- A/B/C：RFC1918 IPv4 自动/自定义，最多 254 hosts；500 ms reachability、250 ms TCP、并发 32；fallback 80/443/22/445/53/9100，Connect 成功才算 TCP 发现。
- D/E/F：网关/本机来自已知网络事实，其他设备来自探测；没发现≠离线。开始/停止/修改范围/重新扫描流程已存在，不是后台监控。
- G/H/I/J：Wi-Fi/Ethernet、Cellular/VPN 限制；一次 Completed Scan 一条 History，内部统计不扩数据库。核心 Fake 测试与历史真机 8 台/19.1 秒有边界；该数字不是所有 /24 的性能承诺。

**10 Identification [E09]**

- A/B/C：PTR/mDNS/UPnP 增补名称、UDN、部分 vendor/model/type；未实现可靠通用 MAC 获取/OUI，不是完整服务指纹。mDNS HTTP/IPP/SMB 三类有限窗口。
- D/E/F：显示发现依据与未知，不能因名称像某产品就确定型号。PTR/服务名是可变观察而非强身份。
- G/H/I/J：只本地发现/描述请求，UPnP location 校验、大小/深度限制和 XML 外部实体防护；长期 NSD executor + session generation 拒绝晚到结果。一次扫描历史，不另加记录；Executor/MDNS/UPnP/malformed fixtures覆盖，设备支持协议决定实际识别率。

**11 Device Center [E10]**

- A/B/C：当前扫描和保存资料分层，搜索/筛选、详情、Ping/Port Check/Port Scan 目标传递；不是持续在线管理。
- D/E/F：普通用户可识别设备与进入单项检测；未观察设备显示保存事实/时间，不断言离线。目标专属诊断、TLS/Website 引导仍有联动机会。
- G/H/I/J：本地观察/Room；本身不是独立检测历史。search/selection/detail tests及 recreation 覆盖；多职责 ViewModel 增加回归组合，不以文件长度作为重写理由。

**12 Saved Devices [E11]**

- A/B/C：保存用户资料和上次观察，强 MAC/协议身份与 scoped IP 弱匹配明确区分；IP 可复用，不能直接继承稳定身份。
- D/E/F：用途是复用地址、名称、备注/WoL，而非证明当前在线；保存状态不是故障解释，没有独立排障算法。
- G/H/I/J：本地敏感拓扑/身份数据，用户管理；不自动为浏览写 History。repository/matcher/migration/recreation 已覆盖，冲突与 DHCP 换址是重要后续回归场景。

**13 Favorites [E11]**

- A/B/C：Favorite 是资料中的管理标记；取消收藏但仍有 Name/Type/Notes/WoL 时保留必要资料，不应等同删除一切。
- D/E/F：便于反复检测重要设备；“收藏”和“保存资料”语义要持续清楚，不能把未发现收藏设备变红。
- G/H/I/J：本地保存不云同步；无独立 History。`RoomFavoriteDeviceRepositoryTest`/presentation覆盖，未来批量管理尚未批准。

**14 Profile Editing [E11/E17]**

- A/B/C：三字段统一草稿、校验、Save/Discard/Keep Editing；不通过编辑触发扫描、检测或 Last Seen 更新。
- D/E/F：名称/类型/备注帮助用户记忆用途；用户类型不应覆盖原始识别证据，没有自动故障推断。
- G/H/I/J：Notes 可能含私人信息，仍在本机；不写检测历史。Task 126 saved-profile recreation测试检查无额外副作用；大量资料/长文本仍需边界测试，不以 UI 页面存在替代这些证据。

**15 WoL [E12]**

- A/B/C：检查 MAC、端口、物理 LAN/广播范围并发送 magic packet；Sent 仅表示发包，不表示设备已唤醒。
- D/E/F：配置可复用；网络/BIOS/网卡设置可影响结果，不能把无回应诊断为硬件损坏。
- G/H/I/J：网络绑定、VPN/移动限制，用户显式发送，无后台或 History；magic packet/repository隔离/UseCase tests，设备固件与广播支持需真实硬件，不能仅凭 fixture 判 wake PASS。

### 16–21：Wi-Fi、应用层及缺失工具

**16 Wi-Fi Analyzer [E13]**

- A/B/C：Current/Nearby/Channels、SSID/BSSID/RSSI/band/channel/security 可用事实，搜索过滤不启动扫描；手动 request 与 Fresh/Cached/Unknown 分开。
- D/E/F：权限拒绝、approximate-only、Location off、Wi-Fi off、无新结果均有提示。RSSI/link speed 不是测速或链路故障结论；尚无跨层质量解释。
- G/H/I/J：Fine Location + system Location，OEM字段可能redacted；在 VPN 下可读底层 Wi-Fi，但不同于 active-network 路径。AP 只在内存，不进 History/PDF。core/VM/UI/recreation已有验证，6GHz实机观察不足。

**17 Channel Overview [E13]**

- A/B/C：按频段/信道观察 AP 数和最强 RSSI、连接信道标记；不是实时频谱、airtime 或隐藏干扰分析。
- D/E/F：展示“观察到多少”有教学价值；不提供 Best Channel/精准评分。更清楚的文字说明可候选，不能拿 AP 少就建议最优。
- G/H/I/J：与 Wi-Fi 权限/缓存边界相同，无额外保存。domain aggregation/VM/filter/UI tests；没有真实6GHz AP并不证明附近没有6GHz网络。

**18 TLS [E14]**

- A/B/C：TCP、SNI、握手、平台 trust、hostname 与证书信息分离；没有 trust-all。未做全面 cipher 扫描、OCSP/CT、安全评级或服务指纹。
- D/E/F：过期/不受系统信任/名称不符分别解释并给建议；私有 CA 不能直接等同攻击。直连观察不承诺浏览器/代理链一致。
- G/H/I/J：普通连接暴露目标IP/SNI等必要流量，无上报；TLS History可恢复，未统一PDF/Share。trust/evidence/probe/VM/snapshot tests；TLS支持随Androidtruststore/网络设备变化。

**19 Website [E15]**

- A/B/C：URL规范化、DNS候选、TCP、TLS、HTTP GET响应头、最多配置允许的重定向；不下载页面正文、不执行 JS、不仿浏览器Cookie登录。
- D/E/F：403/5xx属于收到应用层响应，不能说断网；代理 HTTP 成功可反驳独立直连失败。既有 findings/recommendations 是高诊断价值基础。
- G/H/I/J：URL query 可随实际请求发给用户目标，但历史显示字段移除 query/fragment/userinfo；选定响应头不代表零敏感信息。一次 chain 一条 History，只读恢复，尚无独立统一报告导出。Fake HTTP/TLS/redirect/cancel/network-change tests可复用。

**20 WHOIS [E00]**

- A/B/C：未实现；注册信息工具不是连接故障检测，没有错误/UI可审计。
- D/E/F：可为域名维护提供背景，不能定位传输故障或证明所有权；仅候选。
- G/H/I/J：未来需研究 RDAP/WHOIS访问、公开信息隐私、限流/变化和license，当前无依赖、History或测试；Unknown/Not Verified，不因竞品有就列Must Have。

**21 iPerf [E00]**

- A/B/C：未实现；现有 RSSI、TCP延迟和Ping不等于吞吐测试。
- D/E/F：有HomeLab价值但需可控服务端；结果受端点CPU/链路/方向影响，不可称“互联网测速”或故障定位证明。
- G/H/I/J：未来流量、取消、NDK/license/热量电量成本需要spike；当前无History/报告/测试，不批准实现。

### 22–26：诊断、证据与应用体验

**22 Automatic Diagnosis [E16]**

- A/B/C：真实六阶段、证据/置信度/结论/建议、取消及网络变化；通用Intent无目标，TARGET跳过。不会遍历所有工具来“自动定位一切”。
- D/E/F：保守正常/提示/异常/未知，区分无网络、IP未确认、解析/路径异常。建议≤3；TLS/HTTP和完整IPv4-v6差异未参与。可改进问题入口而非先改规则。
- G/H/I/J：共享网络事实，正常公共探针和系统DNS流量；一次完整报告一条History schema3，内探针不单存。分析器/编排/快照/VM/报告 tests丰富；阶段边界网络指纹不等于同一Network实例连续绑定。

**23 History [E11/E18]**

- A/B/C：PING/DNS/TCP/REPORT/LAN_SCAN/TLS_CHECK/WEBSITE_DIAGNOSTIC，恢复未知/旧schema不乱推状态；未为PortScan/Traceroute/Wi-Fi/WoL建历史是当前边界。
- D/E/F：中文英文动态映射技术代码，二次确认清空；不重跑检测、不重新分析历史。保存失败缺少反馈，使用户难区分“无记录”与“未保存”。
- G/H/I/J：本地Room，无自动云备份；`Column + records.forEach`及整表observe是扩展风险，不是已实测卡顿。migration/snapshot/read-only tests；未做长周期、大规模存储压力验收。

**24 Reports / PDF / Share [E19]**

- A/B/C：自动诊断保留timestamp、network、raw observation/check、解释/结论/建议，固定快照生成文本/PDF，用户选SAF/分享；不是全工具统一报告。
- D/E/F：摘要优先技术折叠，适合求助；TLS/Website当前可读快照≠已有PDF，未来应复用导出安全边界而非复制renderer。
- G/H/I/J：FileProvider不可exported，显式URI授权；分享离开本机是用户操作，应提示IP/domain/profile等风险。PDF/localization/recreation tests；TalkBack阅读PDF和大型报告真实验证仍有限。

**25 Settings / Language [E17]**

- A/B/C：English/简体中文与fallback，Android12 AppCompat locale persistence；Settings当前是语言设置，未提供用户主题切换。设计系统支持系统/浅色/深色逻辑，不等于已发布主题设置；不是所有系统语言均翻译。
- D/E/F：技术数据不被“翻译”，历史语义按语言重新呈现不重测。术语应保持Network Diagnosis/网络诊断对称；旧设计文档术语是维护提示，不证明当前错误。
- G/H/I/J：仅本地偏好，切换不写检测History；resource parity、locale、navigation/recreation覆盖。TalkBack、字体极大、所有ROM的对比度并未在本任务实测。

**26 Privacy & Permissions [E20]**

- A/B/C：依赖和入口未发现账号、广告/analytics SDK或报告上传服务；不能据静态审计宣称已做独立安全认证。
- D/E/F：Wi-Fi按需解释定位要求，拒绝不应挡住非Wi-Fi工具；普通探测会触达目标，不是“完全不联网”。
- G/H/I/J：权限清单及SSID/BSSID/URL/保存资料详见第9节；已有permission-deny和QA数据隔离证据，但完整渗透/网络抓包/依赖漏洞扫描未执行，隐私结论限于可检查路径。

## 5. Diagnosis Capability Audit

### 5.1 当前真实调用链

`ReportScreen → ReportViewModel → RunAutomaticDiagnosticUseCase → DefaultDiagnosticOrchestrator → ConservativeDiagnosticAnalyzer (DiagnosticAnalyzerV4) → AutomaticDiagnosticResult → HistoryRecorder`。

旧 BasicDiagnosticAnalyzer、DiagnosticPipeline/V2 合约仍在；不把旧设计中的路径当成当前入口，不建议本轮删除。当前顺序：

1. Network State：活动网络、类型、VALIDATED/VPN/Private DNS等真实上下文；明确无网络时跳过依赖步骤。
2. IP Configuration：地址存在性，不测试每个地址族端到端可用性。
3. Gateway：Wi-Fi/Ethernet优先IPv4；3次/100ms间隔/2000ms，Cellular不适用，无scope IPv6链路本地网关未知。
4. Internet：集中配置公网TCP辅助目标；VALIDATED、不同探针和目标证据交叉解释，非单个Ping决定断网。
5. DNS：系统rawQuery A+AAAA，正常无AAAA记录不判失败，Fake-IP为context notice。
6. Target：core已有目标DNS/TCP能力，但当前UI没有目标输入，`DiagnosticIntent().target == null` → SKIPPED。

| 问题 | 当前支持 | 边界 |
| --- | --- | --- |
| 无活动网络 | 支持明确设备网络状态结论 | 不定位AP/SIM硬件根因 |
| IP配置未确认 | 支持地址缺失/未知 | 不读取DHCP租期或完整路由表 |
| 网关无回应 | 支持保守局部提示 | 公网正常反证时不升级全网故障 |
| 公网异常 | 支持有限辅助探针+系统证据 | 全timeout仍不够证明ISP故障 |
| DNS异常 | 支持与公网证据组合 | NXDOMAIN仅代表该查询；每type outcome未保留 |
| 某目标TCP服务 | core支持，普通诊断入口未启用 | 无目标时不可声称已测用户网站 |
| TLS/HTTP异常 | Website/TLS独立工具支持 | 自动诊断未接入 |
| VPN/Private DNS/Fake-IP | notice和解释支持 | 不识别具体代理软件；proxy配置不等于实际每次路径 |
| IPv4/IPv6差异 | 观察和部分协议选择 | 无完整family-specific诊断矩阵 |

### 5.2 证据层次与可信度

| 层次 | 当前实例 | 不允许越界 |
| --- | --- | --- |
| Raw Data | response TTL、TCP outcome、RSSI、HTTP code | 单个值不能直接决定总网络健康 |
| Observation | source/state/value/timestamp | 缓存观察不能当实时测量 |
| Evidence | Check引用observation IDs、网络指纹、支持/反证 | 不同网络/代理路径不能无条件合并 |
| Diagnosis | 保守问题边界、severity/confidence、possible causes | 不能指认ISP/硬件/软件品牌故障 |
| Recommendation | 限量下一步、重新测试/检查配置/比较网络 | 不自动修复，不要求弱化TLS安全 |

已遵守的关键契约：Ping FAIL + TCP成功不判离线；DNS失败+公网positive倾向解析路径；403/503是服务已响应；未扫描到设备不是离线。`ConservativeDiagnosticAnalyzerTest`有 `gatewayTimeoutWithPublicSuccessIsNormalWithContradictedNotice`、`refusedPublicProbeIsPositivePathEvidence`、`aSuccessAndAaaaNoRecordsRemainsDnsHealthy`、`fakeIpIsNoticeOnly` 等明确案例。

特别说明：诊断把TCP refused当作某路径有回应的positive evidence，**不表示端口开放**；LAN发现仍只有CONNECT SUCCESS。这不是应统一为一种success的矛盾。可共享transport outcome，不应共享未经上下文解释的“在线”布尔值。

### 5.3 跨工具可行性（候选，不实现）

已有统一模型包含观察、假设、confidence、evidence IDs、建议；无需另造“通用证据引擎”。建议采用薄的feature adapter：明确target/port/address family/method/network fingerprint/time/path provenance，引用不可变结果；无法证明同一路径则明确不可比较。

当前可复用 NetworkRepository、DnsQueryEngine、TcpConnector、PingSessionEngine、Website analyzer、HistoryRecorder；Device Detail已传IP到Ping/TCP/PortScan。机会是目标进入引导排障、PortScan→用户确认单服务TLS/Website、失败结果→建议的下一工具，而非后台自动再扫描。内部 probes不额外写History，旧快照不重新分析。Wi-Fi snapshot需单独freshness/permission语义，不作为互联网断开的直接证据。

四类用户中，当前最强是HomeLab的设备/端口/服务定位和学习者的结构化事实；普通用户最缺“我打不开这个网站该从哪一步开始”；运维缺的是统一可分享证据和长期历史规模，不是更多菜单。

## 6. UI / UX Audit

| 区域 | 已有优势 | 用户任务风险 / 候选，不是此次Bug修复 |
| --- | --- | --- |
| Home | 当前事实、折叠详情、最近诊断；SSID取不到不伪造 | 当前Hero四指标是IPv4/掩码/网关/首选DNS，不是历史031设计中的DNS数量；长IPv6需可读性验证 |
| Tools | 分组目录11项，技术名+解释 | 用户需自己决定网站异常用DNS还是TLS；考虑场景入口但保留专家工具 |
| Device Center | 当前/保存/收藏区分、密集卡片/搜索 | 地址改变/弱身份需持续清楚；更重要的是下一检测动作，不是加装饰 |
| History | 紧凑摘要、动态双语、删除/确认清空 | 全量Column不适合无限增长；是否保存、哪些可看详情/导出需说明 |
| Reports | 结论/少量建议优先、证据可展开 | 缺面向用户目标的自动诊断；不把“通用正常”说成某网站正常 |
| Wi-Fi | Nearby/Channels、缓存/权限状态、有文本信号 | AP数量是观察，不应暗示空频道最好；6GHz无观察需限制说明 |
| Settings | AppCompat双语、主题共用token | 旧设计文档仍含早期导航/编辑dialog描述；需后续文档维护，不能按旧图恢复UI |

当前实际App shell是Home/Tools/Devices三个顶层入口，History/Settings/Privacy/About为次级Drawer目的地；Settings实际为语言选择。截图已是真实Home/Tools/Devices中英总览，非本轮生成；它们只能证明这些画面，不能证明所有工具、深浅主题、错误态和TalkBack。`Theme.kt`使用同套Material3颜色、shape、排版，并适配system bars；resource parity/recreation和部分semantics测试有证据，全面无障碍审核/极大字体/对比度评估为 **Unknown / Not Verified**。本轮不凭静态截图重新设计卡片。

## 7. Architecture / Quality Audit

- **层次**：feature UI→ViewModel/UseCase→core engine/platform adapter；Hilt app组合根、Room统一Repository。当前未发现为每个工具建立一套数据库或Compose直接实现Socket的入口证据。
- **事实复用**：`AndroidNetworkContextReader`共享Home/Device/WoL网络事实，网关选择集中。Wi-Fi底层连接snapshot、LAN物理网络selector、DNS serverInfo另有adapter，是用途差异，不应盲目删成一个接口。需要防止 active-network、underlying Wi-Fi和bound LAN事实在报告中混称同一路径。
- **错误映射**：TCP connector有typed outcome；LAN Probe Trace仍从错误文本匹配分类，legacy DNS/Ping也保留message。未来优先在adapter边界做薄映射，不从本地化文案推断故障，不一口气统一所有错误enum。
- **历史**：通用HistoryFactory、自动诊断schema3、旧v2、Web schema1并存；兼容读是价值但重复JSON/字段转换是维护风险。应先添加fixtures/version契约，不追求为美观改Room/schema。
- **报告**：自动诊断文本/PDF成熟，Web snapshot另一路；适合复用导出边界，不直接套用旧报告数据类型而丢失HTTP路径和redaction。
- **UI/权限**：core:permission目前没有完整通用协调器，Wi-Fi权限由app/UI lifecycle负责；没有证据证明需立刻引入新权限框架。DesignSystem已抽取卡/指标/状态/按钮，但大型screen/ViewModel多职责使组合测试重要，不能以行数判架构错误。
- **资源生命周期**：PortScan active sockets、DNS CancellationSignal、HTTP call、TLS resources、LAN bounded workers、NSD长期executor/generation均有明确机制；Ping阻塞API取消与所有adapter瞬时网络变化处理不同，不能称“所有工具立即停止”。
- **安全处理**：UPnP拒绝DOCTYPE、外部entity置空、响应384KiB/深度64限制；URL执行/存储分离；仍不等于完整安全审计。manifest允许cleartext是HTTP/UPnP需求，不作为已证明漏洞，也不能因此建议trust-all。
- **文档漂移**：Architecture开头早期“方向设计”与后续正式模块并存；Wi-Fi设计早期无权限/无入口描述已有日期，现实现不同；README最新边界与代码更接近。`docs/OSS_RESEARCH.md`只是placeholder，不作为已完成OSS选型结论。

### 7.1 测试实际库存与风险

本次只读统计 `*/build/test-results/**/TEST-*.xml`：**234 suites、2068 test executions、failure/error/skip均0**，最后写入2026-10-08 23:29:58–23:33:57。包含Debug和Release unit-test variants；按`classname + testcase.name`去重为**1034个case身份**，不报告行覆盖率，也不是Task129新执行。

| 模块 | 每variant测试数 | 主要覆盖 |
| --- | ---: | --- |
| app | 57 | locale/navigation/保存报告/PDF/resource parity |
| core:common | 75 | subnet/identity/WoL/history/diagnostic contracts |
| core:database | 36 | History/Profile/migration |
| core:designsystem | 7 | theme/token/contracts |
| core:network | 185 | network/Ping/DNS/TCP/PortScan/Traceroute/TLS/HTTP/Website/Wi-Fi |
| feature:dashboard | 45 | Home/context/recent diagnosis/catalog |
| feature:dns | 14 | UseCase/VM/Fake-IP |
| feature:history | 39 | load/clear/presentation/localization |
| feature:lanscan | 240 | workers/range/stop/network/identity/enrichment/profile/WoL |
| feature:ping | 12 | usecase/VM |
| feature:port | 37 | TCP/PortScan VM/UseCase/service hints |
| feature:report | 219 | analyzer/orchestration/history/localization/text/PDF/VM |
| feature:traceroute | 19 | UseCase/VM/presentation |
| feature:webdiagnostics | 39 | TLS/Website VM/analysis/history |
| feature:wifi | 10 | observation lifecycle/filter/refresh/permission presentation |

feature:subnet的主要纯逻辑在core；core:permission未有独立suite，不能由此认定现功能错误。没有计算行覆盖率，**coverage percentage Unknown**。

当前`src/androidTest`源码共有19个Kotlin文件、77处`@Test`声明，含更早的web/TLS/port/locale/PDF/migration等测试；**声明数不是全体最近执行PASS数**。`docs/V0.9_RELEASE_READINESS.md`记录Task126：43个distinct instrumentation（MainActivity recreation18、profile edit4、Wi-Fi UI17、Web history2、Wi-Fi safety2），另有原六项3轮复验；不能把重复轮数都算新测试。此前锁屏导致六项missing Compose roots，解锁后复验通过；是真实测试环境/host修复，不是把失败删掉。Signed RC full与QA包instrumentation为不同证据。

LAN已发生一次Release Fake worker测试间歇失败：`DefaultLanDiscoveryEngineTest.custom range keeps local and gateway markers only when in range`。随后isolated1/1、LAN240/240两variants及完整2068通过；第一次失败仍应保留。候选：确定性scheduler/汇聚契约、失败工件保留、多轮复验；不得静默重试到绿，也不据一次Fake测试断言线上误报。

CI当前只有`test`与`assembleDebug`，未执行lint/instrumentation；本地Task126 lint为0 error/65 warning，不是本轮新结果。后续可批准补CI lint与工件，但不为少量UI像素引入截图框架。设备QA必须隔离package/fixture，明确socket/真实目标和用户资料边界。

## 8. Android Compatibility

| 平台 / 情境 | 已有证据 | 当前判断 |
| --- | --- | --- |
| Android12/API31 Sony XQ-AT72 | Task125同一Signed RC1完整双语回归、v0.8原位升级；Task126隔离QA 43项 | Representative Full RC PASS是历史已执行记录，不是此次Device PASS |
| Android13–15 | API分支、fixtures及此前开发兼容记录；当前未重跑逐版本 | 不写成同一v0.9 RC全量PASS；OEM覆盖Unknown |
| Android16/API36 Sony XQ-FS72 | Task123开发兼容记录 | Supplemental development evidence；非第二台exact-artifact Full RC |
| Android17/未来target37 | 当前官方权限资料，尚无本项目真机记录 | Unknown/Not Verified，需要独立批准兼容研究 |
| VPN / Proxy | notice、底层Wi-Fi事实、TLS direct / HTTP system route区分 | 不能根据Fake-IP/地址断言具体应用；不同路径可得不同结果 |
| Network switching | PortScan/LAN/TLS/Website监测及Fake回归；自动诊断阶段指纹复查 | 同一IP等事实不一定是同一Network；快速来回切换可能缺可见差异，属需验证风险 |
| IPv6-only / link-local | 类型解析fixtures、地址scope限制、gateway unknown | 不声称完整IPv6公网/Traceroute/LAN扫描均支持 |
| Background | observer unregister/generation、显式有限Session | 没有后台连续Wi-Fi/扫描监控承诺，不能假设进程被杀后仍继续 |

Wi-Fi现用扫描API需要Fine Location及system Location，扫描被拒绝/缓存由平台控制，刷新按钮不保证新测量；这与源码的freshness契约相符。[Android官方Wi-Fi扫描说明](https://developer.android.com/develop/connectivity/wifi/wifi-scan)

官方区分Android16的本地网络保护测试路径和Android17/target37的`ACCESS_LOCAL_NETWORK`门槛；target≤36的兼容行为不能拿来推断升级target后全部LAN功能无须验证。当前任务不加权限、不升级SDK。[Android官方本地网络权限](https://developer.android.com/privacy-and-security/local-network-permission)

## 9. Privacy Audit

| 数据 / 权限 | 当前必要用途与存储 | 风险 / 建议边界 |
| --- | --- | --- |
| SSID/BSSID/RSSI/AP时间 | Wi-Fi内存观察，Home可显示可用SSID；Wi-Fi不存History/Report | BSSID/SSID环境敏感，不能说非定位数据；旧v2诊断序列化有wifiName字段，不保证历史库从未保留SSID |
| IP/domain/网络上下文 | 检测、History、诊断证据，用户可清空 | 本地拓扑敏感；分享应显式用户确认，候选redaction预览 |
| Profile/Notes/MAC/UDN/WoL | 本机Room保存用户资料和身份观察 | 备注可含私人信息；不自动上传、不把manual WoL MAC当发现证据 |
| URL | 执行请求使用目标URL；持久化去query/fragment/userinfo | URL path及允许的响应头仍可含业务信息；不要宣传“彻底匿名” |
| VPN/Private DNS/代理 | 上下文与专业解释 | 显示配置≠知道每次真实DNS响应服务器或代理软件 |
| INTERNET / NETWORK/WIFI_STATE | 常规检测和网络事实 | 探测服务收到正常网络请求，不上传诊断报告并非零网络流量 |
| FINE + COARSE / CHANGE_WIFI_STATE | Android12一起请求精确/大致选择；scan必须精确，位置开关独立 | 仅在Wi-Fi使用路径申请；approximate-only不解锁附近扫描 |
| CHANGE_WIFI_MULTICAST_STATE | mDNS本地服务发现锁 | session释放lock、晚到callback隔离；不用于后台跟踪 |
| 存储 / 分享 | Room本地，allowBackup=false；PDF SAF/FileProvider用户动作 | exported launcher符合启动需求；Provider non-exported并显式URI授权 |

未发现广告SDK/账号/cloud analysis/analytics上报路径；这是静态代码和依赖审计，不是抓包/渗透/依赖CVE审计。新的OUI、WHOIS、云速度测试、自动上传崩溃日志均需单独隐私/许可证审查，不因竞品具备就引入。

## 10. Highest-priority Gaps

| 优先 | 有证据的缺口 | 影响 / 下一步候选 | 不做的过度推断 |
| --- | --- | --- | --- |
| P0研究 | 用户问题/目标没有接入当前诊断入口 | Guided target +复用现Website结果；保守建议导航 | 不称现诊断无用，不整套重写 |
| P0质量 | 保存异常无反馈、LAN间歇测试、CI门禁不全 | 将检测结果与保存状态分离、可靠回归及CI候选 | 不宣称已发生用户数据丢失 |
| P1 | 方法/网络路径/family上下文缺统一可读解释 | 薄evidence adapter+可比较性规则 | 不把所有success变一个健康分数 |
| P1 | Report覆盖不同、History未虚拟化 | 有边界的Web导出与长历史测试 | 不一口气为所有工具建历史 |
| P2 | 吞吐、注册信息、完整IPv6工具缺失 | iPerf等独立research，按价值/验证排序 | 不因工具数量不足强行开发 |

### 10.1 GitHub 展示与低成本传播

两版README章节/截图语言对应、安装Release链接、Android12要求、隐私、功能边界和Apache2.0均可读；最新screenshots与当前品牌一致。Release Notes明确Wi-Fi平台/无best-channel限制，CONTRIBUTING要求测试和边界，SECURITY要求私下报告。未验证GitHub Private Vulnerability Reporting是否启用；不能让用户以为仅文档存在就有私密通道。

候选推广：首屏用“网站打不开 / 内网设备服务异常 / Wi-Fi观察”三个真实场景解释价值；保持专家工具列表；维护者核实GitHub Topics与Issue模板/私密漏洞通道；用真实已脱敏双语短演示、发布校验方法和限制说明，分享至HomeLab/Android开源社区。不要刷Star/下载、购买评论、加入埋点或把普通TCP探测营销成安全漏洞扫描。本轮只建议，不改README、截图、GitHub设置或Release。

## 11. Evidence and Limitations

### 11.1 可复核源码索引

路径根：`N = core/network/src/main/java/com/networktoolbox/core/network/`；`C = core/common/src/main/java/com/networktoolbox/core/common/`；`F(x) = feature/x/src/main/java/com/networktoolbox/feature/x/`；`A = app/src/main/java/com/networktoolbox/`。测试同模块`src/test/java/`包路径；Android tests按表单独列出。缩写只是节省重复路径，不是新模块。

| 证据 | 实际类/路径与契约 | 测试 / 文档依据 |
| --- | --- | --- |
| E00 | `F(dashboard)/ToolCatalog.kt`、`A/MainActivity.kt`、`settings.gradle.kts`；生产源码未找到WHOIS/iPerf实现 | `ToolCatalogTest`；PRODUCT_PLAN当前发布范围 |
| E01 | `N/data/AndroidNetworkRepository.kt`、`AndroidNetworkContextReader.kt`、`DefaultGatewaySelector.kt`、`N/model/NetworkContext.kt`；`F(dashboard)/HomeScreen.kt`、`presentation/NetworkStatusPresentation.kt` | `NetworkContextMapperTest`、`DefaultGatewaySelectorTest`、`NetworkStatusPresentationTest`、`HomePresentationTest` |
| E02 | `C/ipv4/SubnetCalculator.kt`、`F(subnet)`输入与展示调用 | `core:common/.../ipv4/SubnetCalculatorTest.kt` |
| E03 | `N/data/AndroidPingSessionProbe.kt`、`N/ping/PingStatisticsCalculator.kt`、`PingSessionEngine.kt`；`F(ping)/presentation/PingViewModel.kt`、`domain/ExecutePingSessionUseCase.kt` | `PingStatisticsCalculatorTest`、`PingSessionEngineTest`、`PingViewModelTest`；PING_V2_DESIGN |
| E04 | `N/dns/DefaultDnsQueryEngine.kt`、`DnsResponseParser.kt`、`DnsLookupResult.kt`；`N/data/dns/AndroidDnsResolverTransport.kt`；`F(dns)/domain/LookupDnsV2UseCase.kt` | `DnsResponseParserTest`、`DefaultDnsQueryEngineTest`、`LookupDnsV2UseCaseTest`；DNS_V2_DESIGN |
| E05 | `N/data/AndroidTcpConnector.kt`、`AndroidTcpPortChecker.kt`、`N/tcp`；`F(port)/domain/CheckTcpPortUseCase.kt` | `AndroidTcpConnectorTest`、`AndroidTcpPortCheckerTest`、`TcpViewModelTest` |
| E06 | `N/portscan/DefaultPortScanEngine.kt`、`PortScanModels.kt`；`F(port)/presentation/PortScanViewModel.kt` | `DefaultPortScanEngineTest`、`PortScanModelsTest`、`PortScanViewModelTest`；PORT_SCAN_DESIGN；app `PortScanPerformanceInstrumentedTest` |
| E07 | `N/traceroute/DefaultTracerouteEngine.kt`、native adapter与core/network NDK源码；`F(traceroute)/presentation`和`ui` | `DefaultTracerouteEngineTest`、`NativeTracerouteOutcomeMapperTest`、`TraceroutePresentationTest`；TRACEROUTE_TECHNICAL_BASELINE |
| E08 | `F(lanscan)/domain/LanDiscoveryEngine.kt`、`model/LanScanModels.kt`、`RunLanScanUseCase.kt`、`data/AndroidLanHostProbe.kt` | `DefaultLanDiscoveryEngineTest`、`AndroidLanHostProbeTest`、`LanCustomRangeCalculatorTest`、`RunLanScanUseCaseTest`；LAN_SCANNER_V1_DESIGN |
| E09 | `F(lanscan)/data/AndroidMdnsDiscovery.kt`、`AndroidReverseDnsResolver.kt`、`AndroidUpnpDescriptionFetcher.kt`；`domain/UpnpDescriptionParser.kt`、`model/LanDeviceIdentity.kt` | `AndroidMdnsDiscoveryExecutorTest`、`MdnsDiscoveryTest`、`UpnpDescriptionParserTest`、`LanDeviceIdentityAggregatorTest`；LAN_DEVICE_IDENTIFICATION_TECHNICAL_BASELINE |
| E10 | `F(lanscan)/presentation/LanScannerViewModel.kt`、`ui/DeviceDetailScreen.kt`及DeviceCenter presentation | `DeviceCenterSearchTest`、`DeviceCenterSearchViewModelTest`、`DeviceCenterPresentationTest`、`DeviceDetailPresentationTest`；DEVICE_CENTER_V2_DESIGN |
| E11 | `C/favorites/FavoriteIdentityMatcher.kt`、`DeviceIdentityMatchResult.kt`；`core/database/.../RoomFavoriteDeviceRepository.kt`、`NetworkToolboxDatabase.kt`；`F(lanscan)/ui/DeviceProfileEditScreen.kt` | `FavoriteIdentityMatcherTest`、`RoomFavoriteDeviceRepositoryTest`、`DatabaseMigrationsTest`、`DeviceProfileEditUiStateTest` |
| E12 | `C/wol`、`F(lanscan)/domain/SendWakeOnLanUseCase.kt`、`N/data/AndroidLanNetworkBindingProvider.kt` | `WakeOnLanCoreTest`、`SendWakeOnLanUseCaseTest` |
| E13 | `N/wifi/WifiScanRepository.kt`、`WifiObservations`等domain映射；`N/data/wifi/AndroidWifiAnalyzerPlatform.kt`；`F(wifi)/presentation/WifiAnalyzerViewModel.kt`、`ui/WifiAnalyzerScreen.kt` | `WifiDomainTest`、`DefaultWifiScanRepositoryTest`、`WifiAnalyzerViewModelTest`；app `WifiAnalyzerScreenTest`、core `WifiCoreSafetyInstrumentedTest`；WIFI_ANALYZER_DESIGN |
| E14 | `N/tls/DefaultTlsProbe.kt`、`TlsTrust.kt`、`CertificateEvidenceMapper.kt`；`F(webdiagnostics)/domain/RunTlsCheckUseCase.kt`、`TlsCheckAnalyzer.kt` | `DefaultTlsProbeTest`、`TlsEvidenceAndTrustTest`、`TlsCheckAnalyzerTest`；WEB_DIAGNOSTICS_DESIGN |
| E15 | `N/website/WebsiteDiagnosticUseCase.kt`、`WebsiteDiagnosticAnalyzer.kt`、`WebsiteTargetNormalizer.kt`；`N/http/OkHttpProbe.kt` | `DefaultWebsiteDiagnosticUseCaseTest`、`WebsiteDiagnosticAnalyzerTest`、`WebsiteTargetNormalizerTest`、`OkHttpProbeTest` |
| E16 | `F(report)/presentation/ReportViewModel.kt`、`domain/RunAutomaticDiagnosticUseCase.kt`、`diagnostic/v2/orchestration/DiagnosticOrchestration.kt`、`diagnostic/v4/ConservativeDiagnosticAnalyzer.kt`；`C/diagnostic/` | `DiagnosticOrchestratorTest`、`ConservativeDiagnosticAnalyzerTest`、`ReportViewModelTest`、`DiagnosticContractsTest`；DIAGNOSTIC_RULE_ACCEPTANCE |
| E17 | `A/AppLanguage.kt`、`LanguageSettingsScreen.kt`、`MainActivity.kt`；`core/designsystem/.../Theme.kt`；English/zh-Hans资源 | `AppLanguageTest`、`CoreUiResourceParityTest`、`AppNavigationStateTest`；app `MainActivityRecreationTest`、`DeviceProfileEditScreenTest`；I18N_SCOPE_AUDIT |
| E18 | `C/history/HistoryType.kt`、`HistoryRecordFactory.kt`；`core/database/.../RoomHistoryRecorder.kt`；`F(history)/ui/HistoryScreen.kt`、`presentation/HistoryRecordPresentation.kt`；`A/SavedReportViewModel.kt`、`SavedWebDiagnosticsHistoryViewModel.kt` | `RoomHistoryRepositoryTest`、`HistoryRecordPresentationTest`、`HistoryDynamicLocalizationTest`、`SavedReportViewModelTest`、`SavedWebDiagnosticsHistoryViewModelTest`、`WebDiagnosticsHistorySnapshotTest` |
| E19 | `F(report)/diagnostic/v2/AutomaticDiagnosticHistorySnapshotSerializer.kt`、对应resolver/deserializer；`presentation/DiagnosticReportPdfRenderer.kt`；app PDF export owner | `AutomaticDiagnosticHistorySnapshotTest`、`DiagnosticReportPdfRendererTest`、`LocalizedReportExportTest`、`PdfExportViewModelTest`；app `RealPdfRecreationTest` |
| E20 | `app/src/main/AndroidManifest.xml`、`core/network/src/main/AndroidManifest.xml`、`gradle/libs.versions.toml`、`.github/workflows/android-ci.yml`、release/privacy描述 | v0.9 Readiness Task123/125/126/128；permissions和data safety仅本项目可查范围 |

### 11.2 限制与审计边界

本次没有运行App、联网探测目标、ADB回归、Gradle gates、性能benchmark、抓包、渗透、依赖漏洞扫描或竞品安装。历史通过结果只按记录原范围引用。测试数来源现存XML与Task126记录；不报告未知coverage、不用少量fixture推断全部设备。

源码能证明契约和已实现流程，不能证明所有设备无Bug；未发现某上传路径不是独立隐私认证。竞争资料是官方公开说明，另见[竞品研究](LINKBEACON_COMPETITIVE_RESEARCH.md)。本轮仅新增三份研究文档，PRODUCT_PLAN、DECISIONS、Runtime、版本、Tag、Release和APK不变。
