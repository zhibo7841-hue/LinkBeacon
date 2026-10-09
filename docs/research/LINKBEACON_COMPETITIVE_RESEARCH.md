# LinkBeacon Competitive Research

研究日期：2026-10-09（Asia/Shanghai）。

Task 129，公开资料研究；未安装竞品、未做同网性能或隐私抓包。

**Candidate only / Not approved**；本文不批准代码复用、依赖引入或下一版本范围。

## 1. 样本与证据方法

选择六个有互补价值的Android样本：Fing、Network Analyzer、PingTools Network Utilities、Ubiquiti WiFiman、PortDroid、VREM WiFiAnalyzer。它们分别代表家庭设备发现、综合专业工具、协议工具集合、硬件生态联动、服务/端口工作流、开源Wi-Fi专用工具。

优先核验官方站点、官方帮助、Google Play开发者说明和官方GitHub；以下“支持”仅代表公开文档可验证，不代表本次真机PASS。价格受地区/套餐变化影响，本轮不报未验证价格。Data safety是开发者自声明，不是独立安全认证。未列出某能力用Unknown / Not Verified，不能把资料没写当作产品没有。

LinkBeacon的对比事实来自[源码审计](LINKBEACON_V09_PRODUCT_AUDIT.md)，不是仅凭README。市场地位、活跃用户、准确率、扫描时间和故障定位成功率均未测量。

## 2. 六个产品的证据卡

### C01 Fing

官方Mobile页面确认移动网络扫描、设备概览、Ping/Traceroute/端口等排障工具及测速；同步/远程数据与Desktop/Agent生态有关。账号不是Mobile基础使用的强制条件，但同步需账号。免费下载与高级订阅并存，具体价格和当前功能权益未核实。学习其“发现设备→理解问题→下一操作”的叙述；不照搬云端故障数据库、持续Agent监控或未经证据的安全风险结论。[Fing Mobile官方页面](https://www.fing.com/app/)

### C02 Network Analyzer（Techet）

Android手册确认网络信息、Wi-Fi信道列表/图/usage视图、LAN发现、UPnP/Bonjour、网络工具和测速；LAN结果可进入相关操作，多数页面能导出。Android手册列出Ping、Traceroute、Port Scan、DNS、WHOIS等专业能力；不能把同页iOS特有字段算Android已支持。学习数据到操作及导出的一致性；图中的信道使用表现不等于实测RF占用。[官方Android手册](https://techet.net/netanalyzer/help-android)

### C03 PingTools Network Utilities

Play说明列出ICMP/TCP/HTTP Ping、UDP/ICMP Traceroute、iPerf、TCP端口扫描、WHOIS、DNS、UPnP/Bonjour、Wi-Fi和WoL。页面标注广告，Data safety声明可能收集App activity/performance。公开更新时间为2022-11-06；不能据此断言Android16不兼容，实际状态未测。学习协议/测量目的的清楚划分，不照搬广告、远程GeoPing或无约束持续监控。[Google Play官方发布说明](https://play.google.com/store/apps/details?id=ua.com.streamsoft.pingtools&hl=en)

### C04 Ubiquiti WiFiman

官方说明确认移动测速、设备发现和Teleport；无线连接指标及Teleport要求UniFi Gateway。Floorplan功能还受LiDAR设备条件限制，不能当作任意Android手机能力。学习把射频、链路和访问层分开解释；不能把拥有网关侧遥测的结果移植成只读Android scan即可获得的吞吐/漫游诊断。官方说可免费下载；具体账号条件/套餐和完整历史导出未核实。[Ubiquiti官方使用说明](https://help.ui.com/hc/en-us/articles/205204150-Using-WiFiman)

### C05 PortDroid

Play说明确认端口扫描、LAN、Ping/Traceroute、mDNS/UPnP、证书、DNS/WHOIS和WoL等；部分多目标/图表/协议模式属于Pro，具体价格未核实。页面更新时间2026-03-08，Data safety声明可能收集/分享activity、performance、IDs。学习“选定主机→检查具体服务”的短路径；不把端口提示或banner当安全漏洞证明，不照搬大规模扫描和用户追踪。[Google Play官方说明](https://play.google.com/store/apps/details?id=com.stealthcopter.portdroid&hl=en_US)

### C06 VREM WiFiAnalyzer（开源）

官方仓库确认附近AP、信道/RSSI图、过滤、导出、OUI、浅深主题，2.4/5/6GHz及宽信道依赖硬件/软件。项目声明不需要Internet权限、不收集个人/设备数据；未独立抓包验证。**当前main的许可证是GPLv3**，不能引用旧分支印象称Apache2.0，也不能未经许可证审查直接复制到LinkBeacon。学习观察密度、可访问文本与平台说明；不照搬“最佳信道”、距离估算或图形所暗示的精度。[官方README及License/Privacy章节](https://github.com/VREMSoftwareDevelopment/WiFiAnalyzer/blob/main/README.md)

## 3. 比较矩阵

“资料支持”与“实际效果”分开：学习价值和操作难度是研究者推断，非用户实验。Fing Desktop/Agent、WiFiman网关侧功能、PortDroid Pro不得默认为Android免费独立App均可用。

### 3.1 用户与检测深度

| 产品 | 用户/定位（研究归纳） | 网络检测深度 | 自动诊断的可验证边界 | Wi-Fi | LAN/设备管理 | 来源 |
| --- | --- | --- | --- | --- | --- | --- |
| LinkBeacon | 普通用户+HomeLab+学习/运维；本地排障 | TCP/DNS/TLS/HTTP及IPv4路径 | 本地规则、证据/反证和有限建议；通用入口无目标 | 真实观察/缓存/Basic Channels | scoped identity、保存资料、Favorite、WoL | [源码审计E01–E20](LINKBEACON_V09_PRODUCT_AUDIT.md#111-可复核源码索引) |
| Fing | 家庭设备可视化 | 基础排障+设备概览 | 官方宣传排障/外部故障信息；规则透明度未知 | 网络概览，专业射频深度未知 | 生态同步/远程管理边界 | [C01官方](https://www.fing.com/app/) |
| Network Analyzer | 综合专业工具 | 协议工具较全 | 问题检测描述，不确认完整证据矩阵 | 图/list/usage展示 | 发现后继续操作 | [C02手册](https://techet.net/netanalyzer/help-android) |
| PingTools | 协议工具集合 | 多Ping协议、iPerf等 | 集合与监控不等于统一故障分析 | Wi-Fi scanner | LAN及发现工具 | [C03 Play](https://play.google.com/store/apps/details?id=ua.com.streamsoft.pingtools&hl=en) |
| WiFiman | Wi-Fi/UniFi生态 | 测速及网关辅助指标 | 有生态遥测前提；不能视为通用Android规则 | 有硬件依赖 | UniFi discovery | [C04官方](https://help.ui.com/hc/en-us/articles/205204150-Using-WiFiman) |
| PortDroid | 主机/服务排障 | TCP/证书/域名工具 | 未验证综合自动故障推理 | Analyzer能力为发布者声明 | LAN及服务工作流 | [C05 Play](https://play.google.com/store/apps/details?id=com.stealthcopter.portdroid&hl=en_US) |
| VREM WiFiAnalyzer | Wi-Fi观察专用 | 无综合应用层链路声明 | 信道观察/评分不是网络故障定位 | 图/过滤/射频事实丰富 | AP不是Saved Device资产模型 | [C06仓库](https://github.com/VREMSoftwareDevelopment/WiFiAnalyzer/blob/main/README.md) |

### 3.2 证据、易用性与信任

| 产品 | History / Report | 操作难度（推断） | 隐私 / 账号 | 开源核验 | 差异化 |
| --- | --- | --- | --- | --- | --- |
| LinkBeacon | 七类History；自动诊断文本/PDF；部分工具无导出 | 简单默认+展开专业；跨工具选择仍需知识 | 无账号、无广告、local-first；普通探测联网 | 本仓库Apache2.0 | 可审计的本地保守诊断+双语设备工作流 |
| Fing | 概览/事件/同步有资料；逐工具导出未核实 | 问题导向较易懂，生态有额外概念 | Mobile账号可选；远程同步需账号 | 未核实可复用开放源码 | 家庭设备与服务生态 |
| Network Analyzer | 手册说明多页面导出；持久报告范围未知 | 工具与细节较专业 | 本次未独立核验账号/完整隐私行为 | 未核实 | 工具深度与结果操作一致 |
| PingTools | Watcher有说明；完整快照/PDF未知 | 专业协议选择较多 | 广告；Play收集自声明；账号要求未知 | 未核实 | 测量类型广 |
| WiFiman | 完整History/PDF未知 | 生态集成减少操作，但需相应环境 | 账号/云流量边界未独立测试 | 未核实 | 网关侧指标，不仅手机scan |
| PortDroid | 持久化/报告全范围未知 | 主机→服务路径明确；Pro边界 | Play收集/分享自声明；账户强制性未知 | 本次未核实可复用源码/license | 服务级工作流 |
| VREM WiFiAnalyzer | AP详情可导出，不等于综合诊断历史 | 专用视图丰富，对普通人需解释 | 项目声明无Internet/个人信息收集 | GPLv3已核实 | 开源Wi-Fi专项观察 |

本表竞品事实依据第2节对应来源；Unknown不能被改写为“竞品不支持”。所有隐私声明未做网络行为验证，不据此给竞品打安全分；公开评分、下载量和营销速度不作为技术质量证据。

## 4. 可以借鉴什么

1. **场景先于工具**：首先确定“整个网络不可用 / 一个网站打不开 / 一个设备端口异常”，再引导少量必要检查。LinkBeacon已有保守Analyzer，不必再复制闭源“智能诊断”规则。
2. **主机到服务的短路径**：保留选定IP/域名、端口、地址族和上下文，明确用户确认后进入下一工具。现Device Detail→Ping/TCP/Port Scan已是基础。
3. **观察与解释分层**：普通用户看结论和下一步，专业用户看响应方法、时间和原始字段；图形不得比数据来源更确定。
4. **可带走的证据**：复用本地文本/PDF安全边界，优先讨论有意义的Website/TLS报告，而非每次AP刷新都存历史。
5. **权限/测量条件前置**：平台限制、设备能力、缓存状态是产品信息，而不是测试失败后才加的免责文字。
6. **小而可信**：覆盖关键任务、真实双语、可重现测试比菜单数量重要。未做用户研究，上述学习方向需维护者确认和任务验证。

## 5. 不应照搬

- 云端设备识别/用户同步、远程Agent和crowdsourced outage依赖：与local-first不同，增加账号和数据上传边界。
- 最佳信道、干扰百分比、AP距离：仅凭Android附近扫描不足以做精确结论。WiFiman的网关指标不证明LinkBeacon拥有相同数据。
- 自动“安全风险/CVE”标签、banner/version识别、大网段/多目标端口扫描：不是当前保守故障诊断的自然必需。
- 广告、追踪、评分弹窗、账号门槛：不符合项目原则；不以商业竞品收入模式作为产品参考。
- 完整SSH/Telnet终端/SFTP：明确out of scope；只保留服务发现、端口连通及基础线索。
- 未审计源码复制：尤其当前VREM GPLv3与LinkBeacon Apache2.0边界，须单独合规审查。参考交互思想不等于获得代码授权。

## 6. LinkBeacon 的差异化机会

当前可被源码证明的优势是：可解释、可保留不确定性的本地诊断；结构化双语快照不重测；用户资料与扫描事实分离；无账号/广告，公开代码；Android最低版本升级与QA证据可追溯。不要宣传“比竞品更准确/更快/更安全”，本轮无同网benchmark或独立认证。

最有价值的下一步差异化（仍未批准）：用户给问题和目标，App展示支持证据、反证、路径局限，再给最多三条下一操作。用已有Website/TLS深度弥补通用诊断目标缺口；不是追赶Fing云识别、WiFiman硬件生态或Wi-Fi图表数量。

| 用户 | LinkBeacon当前价值 | 最重要的候选补强 |
| --- | --- | --- |
| 普通用户 | 无账号、简单结果和建议 | 目标驱动排障，解释“网络正常但此服务异常” |
| HomeLab | 设备资料、端口、TLS/WoL | 设备→服务工具短链，不混淆弱身份 |
| 学习者 | 原始数据+方法与边界 | 支持/反证和不同层次的教学解释 |
| 运维 | 本地报告、时间/上下文 | 可分享Website证据、保存反馈和可复验条件 |

## 7. 开源展示与推广建议（仅建议）

README中英真实截图、Release安装/校验链接和限制说明已经有基础。候选低成本工作：用三条实际排障场景说明首屏价值；维护者核实Topics、Issue模板、私密安全报告通道；制作真实脱敏演示并讲明测量限制；按社区规则在HomeLab/Android开源社区分享可重现案例。

不刷Star/下载/评价，不加广告/统计SDK，不承诺自动修复或完整安全扫描。本轮未修改README、截图、Topics、Issue、Release或任何外部设置。

## 8. 研究限制

- 所有来源在2026-10-09查询；页面可更新。Google Play功能/价格按地区或版本变化，未做购买/登录。
- 未安装竞品，不报告真实耗时、误报漏报、Android16支持率、可访问性或离线行为。
- 官方功能声明是边界参考，不是实现审计；产品清单不能替代用户价值分析。
- `docs/OSS_RESEARCH.md`目前是placeholder。本次核实VREM license不构成库选型批准。
- 本研究不决定v0.10.0或任何下一版本；路线、范围、许可与实施仍需维护者另行授权。
