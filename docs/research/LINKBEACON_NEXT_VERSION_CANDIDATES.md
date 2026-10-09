# LinkBeacon Next-version Candidates

日期：2026-10-09（Asia/Shanghai）。

Task 129，依据[v0.9源码审计](LINKBEACON_V09_PRODUCT_AUDIT.md)和[六个竞品的官方资料](LINKBEACON_COMPETITIVE_RESEARCH.md)。

**所有10项均为 Candidate only / Not approved。** 下一版本号及范围未确认；不启动v0.10设计/实现，不改变PRODUCT_PLAN或DECISIONS。

## 1. 决策问题

已发布工具数量足够支撑一次更好的排障体验。最大缺口不是“有没有更多菜单”，而是用户的问题和目标没有进入当前通用诊断，现有Website/TLS的应用层证据缺少短路径复用。可靠保存、确定性测试、Android版本/测量边界是必要质量条件。

研究结论：优先讨论**方案B的小规模目标驱动引导排障**；先复用已有流程，不自动混合所有工具、不重写Analyzer、不对所有失败给确定根因。方案A保留为质量先决门；若保存/取消/测试可靠性出现可复现发布阻断，则维护者应先选择A。以下排序不是正式排期。

## 2. 评分方法

100分主观框架：用户价值30、诊断定位25、技术可行性15、验证可行性10、隐私/平台安全10、开发维护经济性10。后两列**越高表示风险越小、成本越低**，不是风险/成本越大越高分。

分数基于源码复用基础、平台限制和预期任务收益，无用户实验、工时测量或竞品同网benchmark。高/中/低置信度指证据充分程度，不是成功率；差1–3分无实际精确意义。不采用总分阈值自动批准。

| 排序 | ID / 候选 | 价值30 | 定位25 | 技术15 | 验证10 | 安全10 | 经济10 | 总分 | 置信度 / 研究优先 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | C01 目标驱动引导排障 | 28 | 24 | 13 | 8 | 9 | 8 | 90 | 中 / P0讨论 |
| 2 | C03 保存状态与长期History可靠性 | 26 | 20 | 14 | 9 | 10 | 9 | 88 | 高（缺口），中（收益）/ P0质量 |
| 3 | C02 跨工具证据与路径可比较性 | 26 | 24 | 12 | 7 | 9 | 6 | 84 | 中 / P1分阶段 |
| 4 | C04 确定性回归与安全QA门 | 23 | 20 | 14 | 9 | 10 | 8 | 84 | 高（记录），中（修复成本）/ P0门 |
| 5 | C05 Website/TLS本地证据导出 | 25 | 22 | 13 | 8 | 8 | 7 | 83 | 中 / P1 |
| 6 | C07 设备/端口到单服务检查 | 23 | 21 | 14 | 8 | 8 | 8 | 82 | 中 / P1 |
| 7 | C08 Wi-Fi观察的保守解释 | 22 | 19 | 13 | 8 | 9 | 8 | 79 | 中 / P2 |
| 8 | C06 IPv4/IPv6对照诊断 | 26 | 23 | 9 | 6 | 8 | 5 | 77 | 低至中 / P2研究 |
| 9 | C09 Android17兼容/本地网络门审计 | 22 | 20 | 11 | 6 | 8 | 6 | 73 | 中（API），低（实机）/ 风险触发 |
| 10 | C10 受控LAN iPerf吞吐测量 | 22 | 17 | 8 | 5 | 6 | 3 | 61 | 低 / P3技术spike |

Top10即此表；没有把它们全塞进下一版本。下文每项字段和排除边界是研究建议，不是实现规格批准。

## 3. 十项候选卡

### C01 — 目标驱动引导排障

- **状态/问题**：Candidate only / Not approved。用户“某网站打不开”与当前无目标通用诊断不匹配；需确认症状/目标，选择已有最小流程。
- **用户/价值**：普通用户优先，兼顾学习者和运维；减少DNS/TLS/Website选择成本，不声称自动找到根因。价值28/30、定位24/25来自真实入口缺口，不是竞品数量。
- **当前基础**：`ReportViewModel.runCheck()`使用`DiagnosticIntent()`；core已有Target DNS/TCP，Website已有应用层完整链、typed findings/建议。审计E15/E16。
- **边界**：符合诊断辅助定位；用户主动目标，不任意扩大扫描；通用网络检查与目标服务结论分开，不自动修复。
- **复杂度/Android**：中；主要入口/路由/结果上下文，非新增协议。URL验证、VPN/代理路径、网络切换、取消和Activity recreation必须保留。
- **权限/隐私**：原则上复用现权限，无定位前提；实际请求到用户目标，URL持久化继续redact，提示正常探测流量。
- **依赖**：复用现Website/Diagnostic contracts、导航与HistoryRecorder；最小阶段不依赖完整C02、不加数据库框架。
- **验证/维护**：中；Fake覆盖“全网异常/仅目标异常/HTTP响应/代理矛盾”，真机受控目标，EN/zh与取消/零额外探测。避免重复Analyzer保持成本中低。
- **建议优先级/评分理由**：P0讨论，90分，中置信度。UI缺口有直接证据；用户是否喜欢选择症状未做实验，先用最小website场景验证，不承诺全场景统一。

### C02 — 跨工具证据、反证与路径可比较性

- **状态/问题**：Candidate only / Not approved。用户在Ping、TCP、TLS、HTTP间自行推理，不同方法/网络路径容易被混为一种success。
- **用户/价值**：运维、学习者、HomeLab和进阶普通用户；展示Observed Fact / hypothesis / confidence / supporting与contradicting evidence / next step。
- **基础**：common诊断模型已有这些多数语义，ConservativeDiagnosticAnalyzer已用反证；Website另有immutable evidence。审计E03/E05/E15/E16。
- **边界**：薄adapter而非新“通用诊断引擎”；同target/port/family/network/time/path才讨论复用。历史旧证据不自动重新解释、不跨网络作强结论。
- **复杂度/Android**：中高；需要provenance与可比较性契约，处理VPN/direct/proxy差异；Android事实指纹并不总能证明同一Network实例。
- **权限/隐私**：不自动增探测，不持久化新敏感字段至必要性明确前；专业证据可包含IP/域名，分享需预览。
- **依赖**：现typed outcomes/common contracts；可在C01完成后按TLS/HTTP一条路径增量接入，不依赖全工具迁移。
- **验证/维护**：中高；Fake冲突矩阵与不可比较case重要，历史schema兼容和双语需fixtures。广泛接入成本高，限定一条流程成本可控。
- **优先/评分理由**：P1分阶段，84分，中置信度。符合核心定位，但共享transport≠共享判定，扣验证/经济性；不得借统一名义抹平LAN OPEN-only规则。

### C03 — 保存反馈与长期History可靠性

- **状态/问题**：Candidate only / Not approved。`RoomHistoryRecorder`非取消异常不反馈，用户无法识别未保存；History全量Column/observe存在规模风险。
- **用户/价值**：运维和普通用户；检测事实不因存储失败变成失败，但保存状态应可解释。大库性能问题尚未实测，不预设必须分页迁移。
- **基础**：统一Repository、七类History、Room v5、versioned快照和只读恢复。审计E11/E18。
- **边界**：先做存储失败契约/可见反馈，后独立做列表虚拟化与规模测试；不自动清空数据、不为每个工具新增独立库，不扩所有历史类型。
- **复杂度/Android**：低至中；磁盘空间、进程重建、失败重试幂等和DAO查询需研究；不因显示改动先改Room Schema。
- **权限/隐私**：无新权限/云同步；用户清空仍确认。测试用隔离数据库，不能把故障注入用户正式库。
- **依赖**：HistoryRecorder错误契约与现UI状态；大库阶段视测量选择Lazy列表/按ID查询，独立验收。
- **验证/维护**：可用Fake写失败、成功一条、重复事件不多存；1k/10k隔离fixture验证内存/帧与恢复。边界有限，维护成本低。
- **优先/评分理由**：P0质量，88分。源码缺口高置信，实际发生频率和性能收益中置信；不把可能风险包装成已证明事故。

### C04 — 确定性回归与安全QA门

- **状态/问题**：Candidate only / Not approved。LAN Fake workers间歇失败已被记录；设备锁屏/错误host曾造成UI失败；CI缺lint/instrumentation证据。
- **用户/价值**：间接惠及全部用户，直接惠及维护者/贡献者；提升可复现性，而非多写测试凑数量。
- **基础**：234 XML suites/2068 variant executions、43 distinct instrumentation、隔离QA包、代表性signed RC门。审计E08/E17/E20、Task126记录。
- **边界**：诊断测试失败原因，稳定调度/完成汇聚与selector，CI加已可用门禁及失败工件；不删失败、不隐藏重试、不让QA改正式资料。
- **复杂度/Android**：低至中；设备解锁/权限/宿主生命周期需显式preconditions，fixture与真机不能混称。
- **权限/隐私**：只隔离测试，不自动卸载/清用户数据；真实端点/大扫描需明确许可，不收集生产日志至云。
- **依赖**：现JUnit/Compose/TestRunner/CI，无必然新截图框架或依赖。
- **验证/维护**：验证可行性高；多轮重复+保留首失败、active resource/cancel/network tests，最低版本代表性RC。成本低至中，不承诺彻底消灭所有flaky。
- **优先/评分理由**：P0门，84分。真实失败证据充分；用户价值是可靠性而非新功能，故不把它作为唯一产品差异化。

### C05 — Website / TLS 本地证据导出

- **状态/问题**：Candidate only / Not approved。用户已经得到应用层解释，但不能像网络诊断那样统一复制/PDF分享。
- **用户/价值**：运维求助、HomeLab私有CA/服务问题；带时间、路径、raw事实、解释和建议，不只截长图。
- **基础**：Web schema1 snapshots、read-only history、自动诊断text/PDF/SAF/FileProvider，审计E14/E15/E18/E19。
- **边界**：先Website文本，再独立PDF；不建云报告、不上传，不自动复测/重新分析旧结果，不扩Wi-Fi AP持久化。
- **复杂度/Android**：中；必须区分direct TLS与proxy HTTP，长证书/redirect链、locale capture和recreation正确。
- **权限/隐私**：无广泛storage权限；URL path/IP/headers仍可能敏感，脱敏预览、默认去secret参数、显式分享。
- **依赖**：稳定Web snapshot和导出owner，薄mapper复用，不直接把Web数据塞入旧diagnostic模型。
- **验证/维护**：长文本/PDF分页/Unicode、未知schema、分享权限和零probe副作用；格式版本需维护，成本中。
- **优先/评分理由**：P1，83分，中置信。复用明显，但隐私和格式验收成本不应低估；本轮不承诺已有Web导出。

### C06 — IPv4 / IPv6 对照诊断

- **状态/问题**：Candidate only / Not approved。地址/AAAA存在与实际访问能力不同；现模型无法解释单family服务异常。
- **用户/价值**：HomeLab、运维、学习者及双栈普通用户；分别展示“配置/解析/连接”证据，不给总括IPv6坏了。
- **基础**：Ping protocol、DNS A/AAAA、TCP候选、NetworkContext。DnsLookupResult尚无每type outcome，需设计后才能可靠区分AAAA无记录与失败。
- **边界**：有限同目标family对照，不含IPv6 LAN Scanner/Traceroute/MTR；link-local scope先保守未知，不借此重写Ping底层。
- **复杂度/Android**：高；NAT64/DNS64、VPN、Happy Eyeballs/path选择、scope、系统resolver差异，目标必须受控。
- **权限/隐私**：复用网络权限；双倍流量需bounded，公共目标不作为唯一依据，无新云服务。
- **依赖**：独立DNS per-type/provenance设计、可注入family TCP；C02可帮助，但不要求大一统。
- **验证/维护**：Fake可覆盖矩阵，真正IPv4-only/IPv6-only/双栈/NAT64需多网络实机；可验证性偏低、成本高。
- **优先/评分理由**：P2研究，77分，低至中置信；用户价值高但目前硬件/环境证据不足，不塞入小版本。

### C07 — 设备/开放端口到单服务检查

- **状态/问题**：Candidate only / Not approved。已发现IP/开放port后用户需复制到TLS/Website，设备级排障链未闭合。
- **用户/价值**：HomeLab/运维优先；从一个证据进入一次明确检测，少复制少填错。
- **基础**：Device Detail已传IP到Ping/TCP/PortScan，common hints与TLS/Website normalize已有。审计E06/E10/E14/E15。
- **边界**：用户确认协议/主机名/SNI/端口；443只是hint，不自动认为HTTPS。无banner/fingerprinting、无后台遍历所有ports，不据新connect更新Identity/Last Seen。
- **复杂度/Android**：低至中；主要navigation/input payload，保存但未观察设备需提示旧地址，网络改变需重新确认。
- **权限/隐私**：只用户选定服务流量，不新权限；不得自动对未授权公共主机扩扫。
- **依赖**：现目标传递框架和C01可选入口；不依赖新Device repository。
- **验证/维护**：tests检查目标/SNI/range保留、无多余scan/history/update，EN/zh/back/recreation；成本较低。
- **优先/评分理由**：P1，82分，中置信；短链价值明确，但协议和旧身份不确定性扣风险分。

### C08 — Wi-Fi观察的保守质量解释

- **状态/问题**：Candidate only / Not approved。用户看到RSSI/link speed/AP计数，仍不知道这些能解释什么、不能解释什么。
- **用户/价值**：普通用户和学习者；解释当前信号弱可能影响本地链路，而网络故障还需TCP/DNS/应用层证据。
- **基础**：Wi-Fi snapshot/freshness、RSSI系统分级、Channel Overview、现诊断notice语义。审计E13/E16。
- **边界**：先文字与下一动作，不Best Channel、不干扰/airtime百分比、不用AP数量给100分、不把cached RSSI当实时。
- **复杂度/Android**：低至中；权限/缓存/redaction、VPN底层Wi-Fi与active network区别是关键。
- **权限/隐私**：不追加权限、不AP历史、无BSSID云OUI查；用户拒绝位置仍能用其它诊断。
- **依赖**：已有系统signal classifier，研究薄解释层；不先做Full Channel Graph。
- **验证/维护**：固定snapshot/FakePermission tests、实际不同信号环境与术语理解；无法证明真实干扰，所以不测“最佳信道准确率”。
- **优先/评分理由**：P2，79分，中置信；低成本解释有价值，但Wi-Fi刚发布且范围冻结，不因此马上扩大正式Must Have。

### C09 — Android17及本地网络权限兼容审计

- **状态/问题**：Candidate only / Not approved。target37之后LAN权限门可能影响发现/WoL/本地工具；当前API36行为不是升级后的证明。
- **用户/价值**：全部未来升级用户；防止权限拒绝被误诊为设备离线。
- **基础**：min31/target36、typed outcomes、版本分支/代表性RC、local-first权限策略。官方区分Android16测试及Android17/target37门槛。[官方本地网络权限说明](https://developer.android.com/privacy-and-security/local-network-permission)
- **边界**：先只读SDK/API审计+隔离compat试验，不自动升级target、不提前加ACCESS_LOCAL_NETWORK、不改当前版本范围。
- **复杂度/Android**：中；真实Android17设备/拒绝/恢复/VPN/local TCP/NSD/广播需验证，现无项目实机记录。
- **权限/隐私**：明确最小权限，仅目标SDK批准后设计；不把LAN与Wi-Fi扫描位置权限混为一类。
- **依赖**：维护者目标SDK时间、设备资源和官方文档；不依赖功能增加。
- **验证/维护**：难度中高，最低支持和高风险版本对照；兼容层长期成本中，不能只看编译通过。
- **优先/评分理由**：风险触发，73分；API证据中置信、实机低置信。若维护者近期升级target，应提升优先级，不按静态排名拖延。

### C10 — 受控LAN iPerf吞吐测量

- **状态/问题**：Candidate only / Not approved。现无吞吐能力，Ping/RSSI不能回答内网传输慢的实际带宽。
- **用户/价值**：HomeLab和运维；显式连接用户部署的服务器、有限时长/方向，解释端点CPU和链路条件。
- **基础**：网络上下文、取消资源、双语结果组件可复用，但没有iPerf实现或完成的library/license研究。
- **边界**：先技术spike，无服务器公网部署、全球speedtest、长后台监控、自动诊断强结论；不将峰值当运营商带宽证明。
- **复杂度/Android**：高；native/library audit、协议兼容、server版本、热量/CPU/电量、foreground/cancel和network change。
- **权限/隐私**：大量数据流量、电量和目标网络影响；仅授权LAN、显式流量/时长，无云端身份。
- **依赖**：单独OSS许可证/版本/安全审计、受控server与多设备实验；目前不得批准任何新依赖。
- **验证/维护**：高；需要稳定链路/对照客户端/CPU资源/吞吐可重复性，Fake不能证明测量准确；维护成本高。
- **优先/评分理由**：P3 spike，61分、低置信；是真实能力缺口但不是下一Small Stable Release最优投入。

## 4. 三条候选路线（最多三条）

规模为主观相对量级，不是工时承诺。未做实现spike，不能报精确工期。

| 路线 | 用户收益 / 主要工作 | 依赖 / 风险 | 相对规模 / 验收 | 明确不纳入 |
| --- | --- | --- | --- | --- |
| A 既有可靠性与体验 | C03保存反馈、独立长History验证；C04确定性回归/安全QA | 存储错误契约和历史兼容；反馈不能改变检测事实 | 小至中；Fake故障/多轮回归/隔离大库可独立验收 | 不增协议、不新诊断规则、无全工具历史扩张 |
| B 引导诊断与跨工具短链 | 最小C01目标/场景→现流程；后续C07/C02逐步连接证据 | 目标/SNI/路径不同，避免误推因果；必须守住取消/History与用户授权 | 最小中；Fake语义矩阵+一台代表性实机EN/zh；完整跨工具是中高 | 不一次运行全部工具、不AI/自动修复、不Wi-Fi最佳信道，不全量重写Analyzer |
| C 补齐工具缺口 | C10先LAN吞吐研究；C06双栈对照另立项目 | native/协议/license、真实服务端、多网络/Android测试资源 | 中高至大；测量准确性验收更难 | 不同时开发WHOIS、iperf、IPv6整套；无SSH终端、SYN、云测速 |

### 首选：B的最小阶段；A作为质量门，不是绕过质量

理由：通用诊断无目标和现Website链之间存在可由源码证明的价值断层。把已有数据转成可操作排障更符合定位，且不需要新协议/权限/数据库或云分析。

不是先选A作为唯一产品主题：A很必要，但在目前已通过发布门的基础上，仅技术整洁不能解决普通用户不知道下一步的问题；如果可靠性风险复现为阻断，则先A，不为宣传强上B。

不是先选C：iPerf/IPv6有价值，但测量/平台/维护成本更高，无法轻易在小稳定版本内取得可信端到端证据；多工具不会自动提升故障定位成功率。

## 5. Small Stable Release 的独立阶段建议

以下阶段只是候选讨论顺序，不指定版本号；每阶段可以单独批准、验收或取消。

### 最小可交付：B0 — 一个网站打不开

- 只做一个用户问题入口及明确URL/目标选择，调用现Website Diagnostics；保留通用Network Diagnosis，不伪装其已检测用户网站。
- 普通结果说明故障层、未确认项和下一步；不把HTTP403/503当断网。复用既有Analyzer，禁止UI自写第二套规则。
- 如需切换到通用检查，由用户确认再执行；不自动拼两个独立网络/代理路径的报告。
- History沿用现一次Website Session一条，取消不写假完整记录；不重测旧快照；不加入新Schema/权限/依赖。
- 验收：invalid URL、DNS NXDOMAIN/timeout、TCP refused/timeout、TLS trust/name、HTTP403/503、代理与直连矛盾、cancel/network change、English/zh、recreation零额外probe、exact snapshot/History数量。
- 发布门：现全量单测/lint/build、确定性多轮LAN QA、代表性最终Signed Artifact完整回归；发现新可复现质量阻断则先解决获批的A范围。
- 不含：完整症状菜单、Wi-Fi自动诊断、family对照、统一全工具证据/导出、吞吐、新协议。B0本身也尚未批准。

### 后续独立阶段：B1 — 设备服务短链

C07仅一项：用户选定IP/port后显式确认hostname/SNI或http/https，再到现TLS/Website；保留source target provenance，未观察保存设备提示旧地址。验收无自动扩扫、Identity/Last Seen不更新、History只存显式工具Session、Back/recreation正常。

### 后续独立阶段：B2 — 一个受限跨工具证据适配

C02只连接已批准的一条场景：同目标/网络/时间/路径可比较时引用支持和反证，否则说未知。先Fake矛盾矩阵和快照兼容，再考虑C05分享。不得把B2变为所有工具重写或自动运行全目录。

### 与B独立的质量阶段

C03先保存反馈，后长历史列表测量/虚拟化；C04作为可复现验收基础。C06/C09/C10各需单独研究授权，不因列入候选即开始spike。若选A，以上B阶段全部继续等待。

## 6. Not Recommended Now

| 能力 | 暂不建议理由 | 边界 |
| --- | --- | --- |
| 完整SSH/Telnet终端、SFTP | 超正式产品范围、凭据/会话/文件管理维护大 | 永久排除完整客户端；服务发现、port connect、基础hint可按批准范围保留 |
| 云AI诊断、账号、广告/追踪SDK | 隐私/离线/维护与产品原则冲突 | 不作为候选路线 |
| Best Channel / 干扰占用率 / 精确AP距离 | Android scan缺airtime/非Wi-Fi能量/遮挡证据 | 不制造伪精度；观察图也不代表频谱 |
| 背景Ping Monitor/MTR/长期曲线 | 电量、foreground、长期状态/历史另成主题 | 普通有限Ping保持，不混入小版本 |
| 公网全网扫描/UDP/SYN/指纹/CVE | 授权风险、raw socket限制、误判和维护 | 只现TCP Connect及服务hint，不安全审计营销 |
| 云OUI/自动设备品牌识别 | MAC缺失/随机化、厂商≠设备类型；额外上传与数据库license | 现UPnP等真实证据保留，未来离线OUI也需单独价值审查 |
| WHOIS/RDAP立即开发 | 注册背景不等于连通故障，隐私/响应标准/地区可用性需审计 | 未实现不自动变Must Have；可以独立research但本轮不启动 |
| 完整IPv6 LAN/Traceroute | scope/NAT64/native与真实环境验证成本高 | C06仅对照诊断候选，不批准协议扩张 |
| 所有工具都写History/Report | 无价值重复、存储/隐私增长、schema兼容成本 | 先明确具体用户证据需求 |
| 仪表盘动画、重排全UI、花哨Channel Graph | 当前缺口是排障任务，而非装饰 | 只在可证明可读性收益后考虑 |

这些原因分别对应价值不明、范围越界、平台限制、隐私风险、成本过高、证据不足；不因竞品已有而解除边界。

## 7. 待维护者确认

1. 首选A还是B0？是否认可先解决“一个网站打不开”，而不是全症状诊断？
2. B0使用现Website独立结果还是未来需要合并报告？后者需要单独C02设计，不能默许。
3. History保存反馈是否作为阻断质量项？允许的重试/用户提示策略和实际大库规模是什么？
4. 是否优先C07设备服务短链，还是C05导出？不要把两者都默认加进最小版本。
5. 是否近期升级target37？有无真实Android17测试设备及本地网络权限拒绝验证资源？
6. 有无可控IPv6-only/NAT64环境或iperf server？若无，C06/C10继续低置信候选。
7. 报告脱敏默认范围是否包含IP/domain/URL path/Profile notes？确认后再设计预览，不自动删除用户历史数据。
8. 下一版本号、正式Must Have/Nice to Have/排除项是什么？本研究不自动命名v0.10.0。

维护者选择后才进入独立任务，批准设计/Runtime/数据/版本变更分别需要明确范围。本Task停止于研究文档，不启动任何implementation。
