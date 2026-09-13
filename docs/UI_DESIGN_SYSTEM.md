# LinkBeacon UI Design System

Status: accepted foundation for the v0.5.x development line.

This document records the first implementation-ready visual vocabulary for
LinkBeacon. It is deliberately small: the foundation supplies stable
tokens and a few primitives while existing screens migrate incrementally.

## Brand direction

LinkBeacon uses **Deep Network Blue** with a **Dark-first** brand
presentation. The visual intent is:

- Clear
- Reliable
- Focused
- Professional
- Calm
- Modern

The Chinese interpretation is 清晰、可信、克制、专业、现代、柔和。

The system intentionally avoids Hacker Terminal, Cyberpunk, Neon-heavy,
game-like styling, excessive glow, dashboard information overload, and an
engineering-demo appearance.

Dark-first describes the primary brand presentation, README/store screenshots,
and the main concept direction. It does not force users into dark mode.

## Color tokens

The Compose implementation is in `core/designsystem`. The following values are
the current first-version tokens.

### Dark theme

| Token | Value | Role |
| --- | --- | --- |
| Background | `#0E1420` | App background |
| Surface | `#151D2A` | Cards and primary surfaces |
| Surface Variant | `#1C2635` | Elevated/secondary surfaces |
| Surface Container Low | `#111A27` | Low tonal elevation |
| Surface Container | `#151D2A` | Standard component container |
| Surface Container High | `#1C2635` | Elevated component container |
| Surface Container Highest | `#243246` | Highest local emphasis |
| Surface Bright | `#212D3D` | Brightest dark surface |
| Primary | `#4C8DFF` | Brand, primary action, selection |
| On Primary | `#06204A` | High-contrast content on bright primary |
| Primary Container | `#1B3560` | Tonal selection/secondary emphasis |
| Secondary | `#76C8D2` | Restrained cyan accent |
| Primary Text | `#F3F7FC` | Main readable text |
| Secondary Text | `#AAB6C5` | Supporting text |
| Outline | `#2B394C` | Dividers and outlines |

### Light theme

| Token | Value | Role |
| --- | --- | --- |
| Background | `#F7F9FC` | App background |
| Surface | `#FFFFFF` | Cards and primary surfaces |
| Surface Variant | `#E9EEF5` | Elevated/secondary surfaces |
| Surface Container Low | `#FBFCFE` | Low tonal elevation |
| Surface Container | `#FFFFFF` | Standard component container |
| Surface Container High | `#E9EEF5` | Elevated component container |
| Surface Container Highest | `#DFE6EF` | Highest local emphasis |
| Primary | `#2F6FED` | Brand, primary action, selection |
| On Primary | `#FFFFFF` | High-contrast content on primary |
| Primary Container | `#DCE8FF` | Tonal selection/secondary emphasis |
| Secondary | `#2A7380` | Restrained cyan accent |
| Primary Text | `#172033` | Main readable text |
| Secondary Text | `#4F5E70` | Supporting text |
| Outline | `#738197` | Dividers and outlines |
| Outline Variant | `#C4CBD7` | Low-emphasis component outline |

### State colors

State colors are independent from the blue brand color.

| Meaning | Dark foreground | Light foreground |
| --- | --- | --- |
| Success / healthy | `#35C98B` | `#147A54` |
| Notice / warning | `#E7B94C` | `#7A5A00` |
| Confirmed error | `#FF5C68` | `#B3261E` |
| Unknown / secondary | `#AAB6C5` | `#506070` |

Light-theme state foregrounds are darker tonal counterparts. This is a
deliberate readability adjustment: the brighter brand-baseline values remain
appropriate as dark-theme accents and are not used as low-contrast text on a
light surface. Status containers and content colors are paired in the theme
implementation so a state is not communicated by color alone.

The dark surface hierarchy is intentionally blue-black rather than purple-gray:
`#0E1420` (background) → `#151D2A` (surface/container) → `#1C2635`
(elevated container) → `#243246` (highest container). All Material 3 surface,
outline, secondary-container, and tertiary roles are explicitly mapped to these
tokens or their approved blue/cyan tonal counterparts; the default purple-gray
fallback is not used.

Semantic rules:

- Blue means brand, primary action, selection, or interaction.
- Green means healthy, online, or success.
- Amber means notice or attention.
- Red means a confirmed error or severe abnormal condition.
- Gray means unknown, not executed, cancelled, disabled, or secondary data.

## Typography

The system uses the Android system font and the Material 3 typography scale.
`NetworkToolboxTypography` gives screen and card headings a restrained
semi-bold hierarchy and uses medium weight for action labels.

The default hierarchy is:

- Screen title: `headlineMedium`
- Section title: `titleLarge`
- Card title: `titleMedium`
- Body: `bodyLarge` / `bodyMedium`
- Supporting text: `bodyMedium` / `bodySmall`
- Label: `labelLarge` / `labelMedium`
- Technical data: `NetworkToolboxTextStyles.TechnicalData`

Technical data uses the system monospace family only where it improves reading
of IP addresses, IPv6 addresses, MAC addresses, hostnames, or other exact
values. The whole application is not rendered as a terminal.

## Spacing

The foundation exposes a small 4 dp grid:

- `XS`: 4 dp
- `SM`: 8 dp
- `MD`: 12 dp
- `LG`: 16 dp
- `XL`: 24 dp
- `XXL`: 32 dp

Existing pages are not rewritten in one pass. New or migrated UI should prefer
these tokens over introducing one-off values such as 7, 11, 13, or 19 dp.

## Shapes

Material 3 shapes are configured as:

- Small: 14 dp
- Medium: 16 dp
- Large: 20 dp

Shared component guidance is:

- Card: 18 dp (`NetworkToolboxComponentShapes.Card`)
- Button: 16 dp (`NetworkToolboxComponentShapes.Button`)
- Chip: pill shape (`NetworkToolboxComponentShapes.Chip`)

The result is soft and modern without making every element excessively round.

## Action semantics

The design system exposes three small action primitives:

- `PrimaryActionButton`: filled `primary` container with `onPrimary` content
  for the page's main action.
- `SecondaryActionButton`: outlined, lower-emphasis action using the brand
  primary for content and the shared outline for its border.
- `DestructiveActionButton`: outlined error action for irreversible operations;
  confirmation dialogs may use a low-emphasis error-colored text action.

The current `onPrimary` values are intentional contrast choices. Dark theme
uses `#06204A` over `#4C8DFF`, while light theme uses white over `#2F6FED`.
The action role remains blue in both themes; error red is reserved for
destructive and confirmed-error semantics.

## Icons

Formal UI uses Material Icons / Material Symbols and a small number of
purpose-built brand vectors. Emoji are not functional icons.

The launcher direction is **Brand N + Network**:

- a simple N-shaped network path;
- three nodes;
- no text;
- no complete Wi-Fi symbol;
- no toolbox outline;
- no stacked network motifs.

The vector stays within the adaptive-icon safe area so circular, squircle, and
rounded-rectangle launcher masks do not clip the path or nodes.

## Status visuals

`StatusVisualState` and `NetworkStatusChip` provide a common visual mapping:

| Internal state | User-facing label | Visual intent |
| --- | --- | --- |
| `NORMAL` | 正常 | Green check |
| `NOTICE` | 提示 | Amber information |
| `WARNING` | 异常 | Amber warning |
| `ERROR` | 严重异常 | Red error |
| `UNKNOWN` | 未确定 | Gray help |
| `RUNNING` | 进行中 | Brand blue activity |
| `CANCELLED` | 已停止 | Gray cancellation |
| `NOT_EXECUTED` | 未执行 | Gray not-executed state |

Icons and labels accompany color. A normal state must not be blue merely
because blue is the brand color.

Bottom Navigation follows the same separation: selected icon and label use the
theme's primary blue, the selected indicator uses the primary container, and
unselected content uses secondary text. Success green is never used for
navigation selection.

## Theme architecture

`NetworkToolboxTheme` lives in `core:designsystem` and supports:

- `SYSTEM`: follows the Android system setting;
- `LIGHT`: deterministic light theme for previews or future settings;
- `DARK`: deterministic dark theme for previews or future settings.

The app currently uses the default `SYSTEM` mode and does not add a manual
theme setting in v0.5 foundation work. Dark and light system-bar icon
appearance is synchronized with the resolved theme, while the existing
edge-to-edge and bottom navigation architecture remains unchanged.
The App Shell supplies explicit NavigationBar colors so Material 3's
secondary/teal selection default cannot override the NetworkToolbox primary
selection semantics.

## Shared primitives

The foundation currently provides only stable primitives:

- `NetworkCard`: shared card surface, shape, padding, and content spacing;
- `NetworkStatusChip`: icon + label + semantic state color;
- `PrimaryActionButton`, `SecondaryActionButton`, and
  `DestructiveActionButton`: small semantic action wrappers;
- App-shell Drawer and secondary information screens: compact navigation and
  outlined information groups for History, Privacy & Data, and About;
- `networkToolboxNavigationItemColors`: primary-blue selection and
  secondary-text unselected navigation mapping;
- `NetworkToolboxColors`, `NetworkToolboxSpacing`, component shapes, and
  technical text style tokens.

Existing feature-local components such as tool cards and section headers remain
in place until their individual migration tasks. No generic UI framework is
being introduced.

## Home pattern

The Home destination is the compact entry point for current network context and
the most-used checks. It does not repeat the App Icon, App Name, or a large
brand header after the user has entered LinkBeacon. Its vertical order is
stable:

1. Network summary hero.
2. Automatic diagnostic primary action within the hero card.
3. Quick Tools, limited to four existing tools.
4. Recent Diagnosis.

The network summary hero is the Home Hero pattern. It uses the shared
`NetworkCard`, a network-type icon with real Wi-Fi signal-strength mapping, a
real SSID only when the existing permission boundary makes it available, and a
conservative `已连接` / `未连接` / `状态未知` status chip. A chevron with an
accessible content description opens the existing detail area. The summary has
exactly four compact metrics in a fixed order: IPv4 address, subnet mask,
default gateway, and DNS. IPv6 and numeric Wi-Fi signal details remain behind
the chevron; IPv6 is not represented as a public-connectivity claim. The Home
DNS summary uses one preferred address only, while the complete configured list
remains in details. Cellular uses a non-misleading
`不适用` gateway value and no Wi-Fi signal field; Ethernet uses the Ethernet
identity and no Wi-Fi signal field. The metrics fall back to a readable two
column layout for phone widths or larger font scales, rather than forcing tiny
four-column text.

Automatic diagnosis is the single prominent action on Home and uses
`PrimaryActionButton` inside the network hero. It keeps the existing navigation
callback and does not start a second data source or a new background check.
The action is presented without an additional privacy helper block in the
hero; complete privacy explanation remains in the appropriate diagnostics and
settings surfaces. Recent Diagnosis is a compact real-data preview; an empty
history uses `暂无诊断记录` rather than placeholder content.

## Tools pattern

Tools is the complete entry point for currently implemented tools. It uses the
following product-facing categories and preserves the existing callback
routes:

- `连通与路径`: Ping, TCP 端口检测, Traceroute;
- `解析与服务`: DNS 查询;
- `网络与地址`: IPv4 子网计算, 局域网扫描;
- `诊断`: 网络诊断.

History is an app-level secondary destination and is not rendered as a Tools
card. A `性能测试` section is rendered only after an implemented performance
tool exists; future concepts are not shown as filler.

Each category is rendered as a compact two-column grid. The grid is part of the
scrollable destination and respects the App Shell's safe-drawing and bottom
navigation insets. Future concepts such as Wake-on-LAN are not shown until
they are implemented. Home and Tools use the same tool definitions and
navigation callbacks so an entry cannot silently diverge between destinations.
Tool entry cards are fully clickable surfaces and do not require a trailing
chevron; the card's label, touch target, and interaction feedback provide the
action semantics.

## Quick tool pattern

Quick Tools is a curated subset of at most four existing tools. It is rendered
as a two-column grid with token-based vertical gaps so the Home destination
does not spend most of its first screen on repeated whitespace. A tool card is
clickable across its full surface and exposes one semantic action to
accessibility services. It does not use a trailing arrow for the same action;
the card already supplies the label and interaction feedback.

Home `QuickToolCard` is a compact outlined surface: it uses the shared card
shape, theme surface, outline-variant border, 40 dp icon container, title,
short explanation, and balanced end padding without a trailing affordance. Its
content is limited to the real four quick-tool definitions and has no demo
values or filler content.
The complete Tools destination may continue to use `ToolCard`; both variants
receive real callbacks and label data from the shared catalog.

Recent Diagnosis is a compact real-history card. It keeps the report title,
one-line summary, relative time, and a status icon derived from the stored
diagnostic result; an empty history shows only `暂无诊断记录` and does not
invent a sample report. The card remains a route to local History.

## Network summary pattern

The summary/detail boundary is intentional:

- Summary: network identity icon and name, `已连接` / `未连接` /
  `状态未知`, IPv4 address, subnet mask, default gateway (or an explicit
  cellular `不适用` value), and a concise DNS value.
- Details: network type, interface, prefix and subnet mask when available,
  every IPv6 address, IPv4 gateway, each configured DNS server on its own
  line, numeric Wi-Fi signal when present, Private DNS values when present, VPN
  state, and system validation.

IPv6 is labelled `未配置`, `仅链路本地`, `已配置`, or `未知`. `已配置` is
not a claim that public IPv6 connectivity works. Wi-Fi and Ethernet prefer the
IPv4 default gateway for the ordinary gateway summary; cellular does not show
an internal next-hop as a user-facing gateway. Home DNS shows one preferred
address directly, with no additional `+N` count; the full configured list is in
details. SSID
is shown only when already available and meaningful; the Home migration adds no
location or nearby-device permission and never displays `<unknown ssid>`.

These values come from the shared `NetworkContext` provided by the existing
network repository. Home does not parse Android `LinkProperties` or routes on
its own. Missing or restricted data is represented as an explicit empty,
unknown, or not-applicable state. The UI never invents latency, Wi-Fi speed,
SSID, device counts, or health conclusions.

## Tool accent policy

Tool accents are a small, stable vocabulary mapped to existing theme tokens:

- `PRIMARY`: primary blue for the main tool family and brand emphasis;
- `CYAN`: restrained secondary cyan for network-information utilities;
- `AMBER`: attention-oriented amber for diagnostics or port-oriented tools.

Accents are decorative grouping cues, not result states. Result status must use
the semantic `StatusVisualState` mapping. Cards must not introduce per-tool
rainbow colors, hard-coded feature hex values, gradients, neon/glow effects,
or heavy shadows. The same accent mapping is valid in both light and dark
themes, with contrast supplied by the theme's paired foreground/container
tokens.

Concept art and mockups are direction only. Production Home and Tools render
current repository data and real navigation callbacks; example values such as
sample IP addresses, SSIDs, speeds, latency, or device counts must never be
used as UI data.

## App shell and migration

The App Shell contains exactly three top-level destinations:

- 首页 / Home
- 工具 / Tools
- 设备 / Devices

Tool entry routes retain their caller in the app shell. A tool opened from Home
returns to Home, while a tool opened from Tools returns to Tools; the Android
system back action and each tool's visible back action use this same source-aware
transition. Bottom navigation remains a direct top-level switch and does not
create an additional back-stack layer.

The shared Drawer is available from Home, Tools, and Devices and contains only
the app-level destinations History, Privacy & Data, and About. Those screens
keep the caller's top-level destination for Back navigation. The current
product does not expose a Settings screen because it has no confirmed user
configuration; a future Settings route requires a separate product decision.
Secondary pages do not expose the top-level Drawer action. Devices hosts the
existing LAN Scanner state through the LAN Device Center surface; it is a real
entry point, not a placeholder or a second scanner state. The approved Phase
2A Device Detail and Favorites foundation is described below; Wake-on-LAN
remains a separate later implementation stage.

Migration follows a staged path rather than a Big Bang rewrite:

1. Design foundation and launcher icon.
2. App Shell.
3. Home visual migration (implemented for the current v0.5.x line).
4. Tools visual migration (implemented for the current v0.5.x line).
5. Automatic Diagnostics, Report, and History.
6. Existing tool pages.
7. LAN Device Center.
8. Wake-on-LAN.

Each stage must preserve business semantics and pass unit tests, lint, build,
and Sony Android 16 smoke checks where applicable.

## Diagnostics, Report, and History patterns

The v0.5 visual migration applies a two-tier surface rule to these existing
destinations. Filled or tonal surfaces are reserved for a hero, an overall
status, a primary diagnosis, an important attention state, or the single
primary action. Tool/action entries and ordinary grouped information use the
shared outlined surface. This keeps emphasis meaningful without introducing a
new visual language.

### Automatic Diagnostics pattern

The idle state is concise: a short explanation, the local-only privacy note,
and one primary `开始诊断` action in an outlined information surface. The
running state uses one overall running hero, compact real check rows, a real
stage progress indicator, and a secondary `停止诊断` action. It does not
render six large parameter cards. The completed state follows this order:
status hero, diagnosis conclusion, findings, recommendations, stage checks,
optional technical details, and export actions.

`NORMAL` is shown with the green normal visual, `ATTENTION`/`NOTICE` with an
amber notice visual, `ERROR` with the red error visual, and `UNKNOWN` with the
neutral visual. These visuals are applied to the relevant hero, chip, or row;
the entire page is never tinted. A normal report with no material findings
does not repeat a second “network normal” finding. NOTICE findings use an
outlined, low-emphasis treatment; warning and error findings use the shared
semantic status visual without turning every section into an error banner.
The UI shows at most the current analyzer recommendation limit and never adds
recommendations of its own. VPN, Fake-IP context, a gateway timeout with
successful Internet evidence, and similar conservative notices remain
informational rather than being promoted to a fault.

Technical details are the lowest-priority expandable group. They use readable
Chinese labels and key/value rows, preserve long addresses without forced
single-line alignment, and never expose raw enum names or machine codes in
ordinary content. Retry/Verify remains a compact comparison section and does
not become a second hero; the existing retry and verification semantics are
unchanged.

### Report pattern

Live reports and restored History reports use the same
`DiagnosticReportPresentation` adapter and the same visual hierarchy. The
header carries the report title, time, and available network type. A compact
overall status hero is followed by the diagnosis conclusion, findings,
recommendations, stage checks, expandable technical details, and export
actions. Copy, save-PDF, and share-PDF remain available through the existing
renderer and FileProvider flow; export is an outlined or tonal secondary
action, not a competing primary action. Restoring a report displays the saved
snapshot and never reruns analysis.

### History pattern

History uses compact outlined surfaces with a semantic status chip derived from
the stored structured result, followed by the record type, title/target,
summary, available network context, date, and an explicit report affordance
when a report snapshot is restorable. The status is never inferred from a
free-form summary string. Delete and clear remain lower-emphasis actions, and
clear-history confirmation semantics are unchanged. Empty, loading, and error
states use the same compact outlined treatment and the shared local-history
wording. A report card opens the exact saved snapshot through the existing
source-aware navigation path; it does not re-run or re-analyze the report.

#### History List Pattern

- Each item is a compact outlined surface rather than a filled heavy card.
- The card itself is the primary open action for a restorable report and uses a
  trailing chevron as its affordance.
- Destructive actions remain secondary and are kept out of the open-action
  hierarchy.
- A type title is shown once; target, diagnosis summary, network context, and
  metrics occupy the supporting lines only when they are available.
- The stable reading order is status, title, time, summary, and compact
  metadata. Spacing uses the shared tokens and lets long text wrap naturally.

### Outlined surface policy and hero boundary

Use `OutlinedNetworkCard` for ordinary sections, check groups, findings,
recommendation groups, technical details, history records, idle/error
information, and running tool state. Use a filled or tonal `NetworkCard` only
where the surface itself establishes hierarchy: a completed overall-status
hero or another explicitly primary diagnosis surface. Do not place a Card
inside another Card. A `NetworkStatusChip` may appear inside a hero or an
outlined group as the compact semantic status indicator.

This boundary applies in both light and dark themes. Spacing uses the shared
`NetworkToolboxSpacing` tokens, typography uses Material 3 plus the shared
technical-data style for addresses and other raw values, and status icons use
the shared semantic mapping. Components must remain readable at large font
scales and on narrow phones; long addresses wrap within their value area
instead of forcing horizontal overflow. Existing navigation, system back,
history snapshot, export, and business rules are outside this visual layer.

## Scope boundary

This foundation does not modify Ping, DNS, TCP, Traceroute, LAN Scanner
discovery, Automatic Diagnostics, History, Report, Retry/Verify, or their data
semantics. The App Shell change only hosts the existing LAN Scanner under the
Devices top-level destination; Phase 2A separately adds the limited Device
Detail and Favorites presentation/persistence described in the LAN Device
Center pattern. Wake-on-LAN remains outside this phase.

## Core Tool Screen Pattern

The core network tools use one predictable Compose reading order:

1. **Tool header** — a source-aware back action, the tool icon, and the tool
   name. Tool headers do not show a subtitle; tool explanations remain in the
   content area where they are useful. Core screens do not repeat the
   LinkBeacon brand header.
2. **Input / target** — one `OutlinedNetworkCard` groups the target fields and
   any optional parameters. Each input uses the Material 3
   `OutlinedTextField`; validation remains inline and user-readable.
3. **Primary action** — one shared `PrimaryActionButton` starts the existing
   operation. Stop or retry actions use the appropriate secondary or
   destructive style and do not change the operation semantics.
4. **Running state** — the shared `ToolRunningSection` uses an outlined (or,
   if a future screen needs it, very light tonal) surface to show real
   progress or live values supplied by the existing ViewModel. It never uses
   fake timers, simulated progress, or a full-screen loading treatment.
5. **Result summary** — the first result surface shows the localized status,
   the target, and the most important conclusion. Tool status is not promoted
   into a claim about the entire network.
6. **Key metrics** — related values use the compact `ToolMetricGrid` instead
   of one large card per metric. Details use shared key/value rows and the
   technical-data style for addresses, hostnames, and other raw values.
7. **Detailed result** — secondary fields, per-record/per-hop rows, and
   technical information stay in the same clear hierarchy and may be
   collapsed where the existing tool already supports it.

Ordinary input, result, detail, and list sections use outlined surfaces. Filled
or tonal surfaces are reserved for the primary action context, running state,
or an explicitly primary overall result. Cards are not nested inside cards.
The shared spacing tokens (`XS`, `SM`, `MD`, `LG`, `XL`, `XXL`) and Material 3
typography are used instead of screen-specific spacing values.

The common status vocabulary is localized for ordinary users: `NORMAL` is
shown as 正常, `NOTICE` as 提示, `WARNING` as 异常, `ERROR` as 严重异常,
`UNKNOWN` as 未确定, and `CANCELLED` as 已停止. Raw enum names, exception
strings, and machine codes are not displayed as ordinary result labels. The
technical meaning of each tool result remains owned by its existing engine,
UseCase, and presentation mapping.

Ping, DNS 查询, TCP 端口检测, Traceroute, and IPv4 子网计算 keep
their current parameters, detection/calculation rules, limits, history
behavior, and source-aware navigation. This pattern changes only their
Compose presentation; it does not add a network protocol, alter a timeout,
or redefine a result.

## Running State Pattern

All core tools with a visible in-flight operation use the shared
`ToolRunningSection`. Its default surface is `OutlinedNetworkCard` with the
existing Deep Network Blue theme surfaces and outline variant. The process
accent is `StatusVisualState.RUNNING` / primary blue; it is never rendered as
healthy green, attention amber, or error red. Progress indicators consume the
real progress supplied by the current ViewModel and use primary blue on a
low-contrast track. Existing compact metrics remain visible without turning
each metric into a filled card. Ping and Traceroute keep their outlined
destructive stop action; DNS and TCP retain their existing short loading state
without inventing a stop control. A running surface is intentionally weaker
than a completed result hero.

## Home Network Summary Preference

The Home Hero DNS value is a presentation-only compact summary. It selects the
first valid IPv4 configured DNS address, then falls back to the first valid
IPv6 address when no IPv4 address exists. It always shows only that one
preferred address and never appends a `+N` count. An empty or unrecognized list
uses the existing unavailable wording. The full configured DNS list remains
unchanged in Network Detail, and Automatic Diagnostics and DNS Lookup continue
to consume the complete `NetworkContext`/DNS result rather than this Home-only
preference.

## Drawer and information patterns

The shared Drawer is compact and contains only `检测历史`, `隐私与数据`, and
`关于`, below the real app name and dynamic `BuildConfig.VERSION_NAME`. It does
not repeat top-level navigation or expose a placeholder Settings entry.
Drawer rows remain plain, compact navigation rows without new Cards, groups, or
section redesign.

History reuses the existing History screen and its local data behavior. Privacy
& Data is a lightweight secondary screen using typography and dividers for
local-first storage, no upload of diagnostic results/history, and no account
requirement. It does not duplicate History management actions. About is a
lightweight secondary screen using the real launcher icon, app name, open-source
description, and dynamic version without a giant outer Card. Each secondary
page uses a Back arrow and title only: no header icon, subtitle, or Drawer
button. A secondary page opened from the Drawer uses ordinary caller-aware Back
navigation and does not automatically reopen the Drawer. Light and dark themes
reuse the same LinkBeacon surface, outline, typography, and semantic
tokens.

Home renders only the `快速工具` section title; it does not render supporting
section copy such as `常用网络检测`. Each of the four quick-tool cards keeps
its own one-line user-facing explanation.

## Home Hero pattern

The Network Status Hero uses a complete boundary: a subtle tonal surface with a
low-contrast outline. It keeps its existing real network fields, status chip,
detail chevron, and diagnostic action. The Hero chevron remains because it
opens meaningful network details; it is unrelated to Tool Card affordances.

Recent Diagnosis uses a compact outlined card, including its empty state. It
retains the existing reactive history preview and report-navigation affordance
without re-running or re-analyzing a report.

## Tools pattern

Tools shows only the screen title `工具`, followed by category titles and
compact two-column outlined Tool Cards. Category titles do not repeat
supporting text; each card carries one short description. Cards remain full
clickable surfaces with button semantics, ripple feedback, and no trailing
chevron. The product taxonomy is user-task-oriented: Connectivity & Path,
Resolution & Services, Network & Address, optional Performance, and
Diagnostics. History is not a Tool Card.

## Network Diagnostic header pattern

The live Network Diagnostic tool uses the shared Tool Header: Back arrow,
Diagnostic icon, and `网络诊断`. It has no redundant header subtitle or text
back button; explanations remain in the report content. A report restored from
History may use the saved-artifact context title `网络诊断报告` while reusing
the same report content and export behavior. Live completion and reruns keep
the `网络诊断` tool title.

## LAN Device Center pattern

The top-level `设备` destination is a compact current-network device surface,
not a copy of the configurable `局域网扫描` tool. It starts no scan on entry
and shows one concise current-network summary followed by a manual scan action
or the real scan state. The summary includes only the useful current-network
context for this surface: network type/name when reliable, the selected
current IPv4 subnet, local IPv4 address, and a real gateway where applicable.
Cellular and blocked VPN states remain explicit and do not manufacture a
traditional LAN gateway.

Discovered devices use compact outlined cards. The card reads an aggregated
display name from the existing reverse-DNS, mDNS, and UPnP identity pipeline,
falls back to `未知设备` when no real name is available, and always keeps the
IPv4 address visible. Gateway and local-device badges are reserved for those
roles; ordinary discovered devices do not receive a redundant `在线` badge.
Confirmed discovery evidence and latency remain a single supporting line when
available. The list preserves the scanner's gateway, local-device, and numeric
IPv4 ordering, and does not add detail chevrons, search, filters, or device
management actions.

Tools -> 局域网扫描 remains the place for custom IPv4 ranges and its existing
one-shot range-selection workflow. The two surfaces share the same scanner
state and domain behavior but do not duplicate discovery or persistence
logic. Device Center rendering uses the real scanned progress and does not
simulate progress or create new Room data.

### LAN Scanner visual alignment

Tools -> 局域网扫描 keeps its current range-selection and scan behavior while
using the same surface hierarchy as the other core tools:

- Input configuration (`当前网络` and `自定义 IPv4`) uses an outlined Tool
  Input Surface with the existing Material 3 fields and range validation.
- Running state uses the outlined Tool Running Surface and keeps the real
  progress, elapsed time, device count, cancellation, and network-change
  behavior.
- Completed and terminal summaries use a subtle tonal or outlined Result
  Summary Surface. Re-scan remains an outlined secondary action, and the
  `修改扫描范围 >` text action remains available.
- Device results use the shared compact outlined `LanDeviceCard` primitive
  with the order `display name`, IPv4 address, and observed identity/evidence.
  When no real name is available, the display name is `未知设备`; the IP is
  still shown as its own technical value. Gateway and local badges remain
  reserved for those roles, and unknown identity stays neutral.

LAN Scanner and Device Center therefore share the same device-card visual
language without sharing their page roles: custom ranges remain a Tools-only
concern, while Devices remains current-network-only. This alignment does not
change scanner semantics, discovery evidence, identity aggregation, range
calculation, persistence, or the Device Center information architecture.

### Device Detail and Favorites pattern

Device Detail is a secondary page reached only from a Device Center card. Its
header uses the shared secondary-page pattern with Back, the device title, and
a compact star action. The star uses the user-facing labels `收藏设备` and
`已收藏`; toggling it is immediate and does not require a danger confirmation.
Tools -> 局域网扫描 cards remain non-clickable so the scanner does not acquire
a second detail-navigation behavior.

The first viewport prioritizes the device identity, current IP when observed,
optional real metadata, and role badges such as `网关` or `本机`. Detail
sections group basic information, observed identity sources, network relation,
and observation status. Unavailable fields are omitted rather than filled with
guessed values, raw hashes, or internal enum names. An unknown device is
labelled `未知设备`.

The Device Center uses one compact list. Favorites observed in the current
scan are marked with a star and sorted before the gateway/local and other
observed groups; in-scope favorites absent from the current scan remain at the
end with the neutral state `本次未发现`. This state never uses an offline
label or an error color. Cards retain the shared compact primary/secondary
layout and the existing role/evidence semantics.

### Device Detail Edit Name Pattern

Device Detail may expose one compact edit-name action in the secondary-page
header. It opens a Material 3 `AlertDialog` titled `设备名称` with the current
custom name prefilled, or an empty field when the device uses its automatic
name. The supporting text explains that the value is stored only on the
device and does not rename the physical device. `取消` and `保存` are the
primary dialog actions; when a custom name exists, `恢复自动名称` clears only
the custom override and leaves the favorite flag unchanged. There is no list
inline editor and no `删除设备名称` action.

Custom names are trimmed, Unicode-compatible, limited to 40 Unicode code
points, and rejected when blank or containing control characters. Validation
is owned by the pure domain resolver; the UI only presents the localized
validation state. A successful save updates the current detail and any
matching device card through the saved-profile flow without requiring a
rescan or navigation reset.

### Device Card Custom Display Name

When a device card has a safely matched saved profile, its display name
priority is: custom name, detected hostname / mDNS / UPnP identity, then the
localized `未知设备` fallback. The IP address and confirmed discovery evidence
remain visible. The custom name is presentation-only and does not replace the
stored detected identity metadata or change favorite/star semantics.

### LAN scan session-state pattern

LAN Scanner and the Device Center use the same session-state language and
compact `LanDeviceCard` primitive:

- **尚未扫描当前网络**: the current network has no active scan session. Show
  the start action and, when available, current-scope saved profiles with
  `尚未进行本次扫描`.
- **正在扫描**: show real scanned/total progress and the current discovered
  count. Do not present saved profiles as online while the session is running.
- **扫描完成**: show the completed session summary and current observations;
  merge matching saved profiles into the same row and retain only one item per
  device.
- **扫描已停止**: retain only the partial observations from the current
  session when the existing flow supports it; do not label absent profiles
  offline.
- **网络已发生变化**: clear the previous session and return to the current
  network's not-scanned state. Explain that a manual scan is required; never
  auto-start a scan or leave stale results visible.

Saved profiles are neutral recognition records, not presence indicators. The
UI must not use `在线` or `离线` for an unscanned or not-found profile. Status
colors and actions follow the existing Material 3 hierarchy: primary start,
outlined secondary rescan, and a neutral notice for network invalidation.
Both entry points keep their existing roles: Device Center is current-network
only, while Tools -> LAN Scanner retains automatic and custom IPv4 range
selection. No new device actions, notes, background work, or discovery protocol
is introduced by this pattern.

### LAN Scanner and Device Center role-separated scan pattern

LAN Scanner answers what the current one-shot scan actually discovered. It
shows only current-session observations, with matching saved-profile custom
names and favorite state applied as enrichment. It never appends an unmatched
saved profile.

Device Center manages saved profiles in the current network scope. Before a
scan it shows saved profiles neutrally; during a scan unmatched profiles say
`等待本次扫描结果`; after completion they are separated as `本次未发现`.
Stopped or failed scans use incomplete-coverage wording instead of a not-found
or offline conclusion.

Both entry points share start, running progress and stop, terminal summary,
failure, and outlined rescan placement. LAN Scanner retains current/custom
range selection, while Device Center remains current-network-only. No device
actions, background work, or new discovery protocol is introduced.
