# Changelog

## Unreleased

### Maintenance

- Updated GitHub Actions dependencies to remove deprecated Node.js 20 runtime warnings.

## 0.5.0

### Branding

- NetworkToolbox renamed to LinkBeacon for the official v0.5 product brand.
- Added the LinkBeacon by LY brand signature and retained the approved launcher icon.

### UI / Navigation

- Added the Home / Tools / Devices shell and shared Drawer navigation.
- Refined Home, Tools, Drawer, Back, and scroll-state presentation.

### Device Center

- Added saved device profiles, Favorites, Custom Names, and Device Details.
- Added Device Center Search / Filters and Ping / TCP integration.

### Wake-on-LAN

- Added local manual Wake-on-LAN from Device Detail.
- Added saved-profile Quick Wake with explicit packet-sent semantics.

### Diagnostics

- Added automatic diagnostics and complete local diagnostic reports.
- Improved History reopening, PDF saving, and PDF sharing.

### Fixes

- Preserved local profiles and history across the v0.5 branding transition.

## [0.4.0] - 2026-09-06

### Added

- Automatic Diagnostics Phase 2 with progressive network, IP, gateway, public
  connectivity, and DNS evidence collection.
- Conservative evidence-driven diagnostic rules with VPN and Fake-IP context,
  mobile gateway `NOT_APPLICABLE` handling, and public-probe conflict handling.
- Retry / Verify for repeating a diagnostic and comparing the resulting evidence.
- Diagnostic Report Phase 1 with complete in-app reports and schema 3 local
  history snapshots.

### Improved

- Reopen diagnostic reports from local History without rerunning probes.
- Copy report text, save PDF, and share PDF on explicit user action.
- Local-only report generation with Chinese, English, and technical-token-safe
  PDF wrapping.
- Report privacy notice and related diagnostic presentation.

## [0.3.0] - 2026-09-01

### Added

- IPv4 Traceroute with per-hop results and three-probe latency details.
- Start, stop, partial-path, cancellation, and network-change states for
  Traceroute.
- LAN device identification enrichment using Reverse DNS, mDNS / Bonjour, and
  SSDP / UPnP information when devices provide it.
- Source-labelled manufacturer, model, hostname, and service information for
  supported devices.

### Improved

- LAN Scanner device presentation and identity aggregation.
- Traceroute timeout, partial-response, Fake-IP, cancellation, and hop-list
  explanations.
- Android 16 mDNS lifecycle handling and callback-executor stability.

### Fixed

- Android 16 mDNS late-callback crashes.
- UPnP Description HTTP/XML compatibility and safety handling on Android 16.
- LAN Scanner and Traceroute state and presentation issues.
- Settings/About showing a stale hard-coded application version.

### Current limitations

- Traceroute currently supports IPv4 only.
- Some devices do not expose a name, manufacturer, model, or service metadata.
- Some intermediate routers may not respond to Traceroute probes; this does
  not by itself indicate a network fault.

## [0.2.0] - 2026-08-29

### Added

- LAN Scanner for bounded local IPv4 host discovery.
- Automatic scanning of the current eligible LAN IPv4 range.
- Custom RFC1918 IPv4 start/end scan ranges with a maximum of 254 addresses.
- LAN scan history records.
- TCP Port Check.
- Enhanced automatic network diagnostics.

### Improved

- Ping real-time statistics, continuous checks, and detailed network-quality results.
- DNS advanced record lookup, TTL details, Fake-IP explanations, and result presentation.
- Home network-status summaries, IPv4/IPv6 presentation, Diagnostic Report, local History, and LAN Scanner scan performance.

### Fixed

- LAN Scanner TCP false positives.
- Dual-stack Wi-Fi gateway selection in diagnostics.
- DNS Fake-IP diagnostic handling and DNS result aggregation semantics.
- The inability to modify a LAN scan range after a completed scan.

## 0.1.0

Initial release.

Features:

- Ping
- DNS
- TCP Port
- Subnet Calculator
- Diagnostic Report
- Local History
