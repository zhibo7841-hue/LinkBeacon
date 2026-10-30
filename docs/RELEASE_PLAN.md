# Release Plan

## Current checkpoint — v0.10.0 (2026-10-22)

- Authoritative scope and release gates: [PRODUCT_PLAN.md](PRODUCT_PLAN.md).
- Stage A: **Development Complete**, reviewed and accepted within the explicit
  [audit boundaries](V0.10_STAGE_A_AUDIT.md); retain the ES editor failure and
  Not Tested environments.
- Stage B: [technical design and task split](V0.10_STAGE_B_DESIGN.md) prepared;
  Runtime implementation awaits separate authorization and technical choices.
- Stage C is not started; Stage D validation and signed exact-artifact/upgrade
  gates remain pending. **Not RC Ready / Not Released.**
- Draft PR #1 remains draft/unmerged. Task 134 permits a local documentation
  commit only, not push, merge, version/signing/Play changes, Tag or Release.
- Released v0.9.1 and its frozen artifacts are unchanged.

The V0.1/V0.2 sections below are historical scope records, not a replacement
for the current product plan.

## V0.1 development scope

V0.1 is the first focused local network analysis workflow. Its scope is frozen to the following areas.

### Included

- Dashboard
- Network Info
- IPv4 Calculator
- Ping
- DNS
- TCP Port Test
- Reports
- History

### Not included

- LAN Scanner
- Wi-Fi Analyzer
- Traceroute
- iPerf
- SSL/TLS
- WHOIS

SSH/Telnet terminal functionality, SFTP, automatic network repair, automatic definitive fault diagnosis, and unrelated utilities are also outside the product boundary.

## Release readiness

Detailed implementation acceptance criteria will be defined when Android development begins. They must remain traceable to the included V0.1 scope, the privacy principles, and the architecture direction.

## V0.2.0

- Status: Released (v0.2.0)
- Goal: Network Diagnostic Enhancement

Implemented areas:

- History persistence fix.
- Ping enhancement.
- DNS enhancement.
- Diagnostic Report upgrade.
- LAN Scanner v1, including bounded custom RFC1918 IPv4 scan ranges.

The v0.2 implementation remains bounded by the documented privacy, local-first, and evidence-based product principles. It does not include mDNS, UPnP, MAC/OUI identification, device details, Wake-on-LAN, or IPv6 LAN discovery.

V0.3 and V1.0 feature commitments are not defined by this document.
