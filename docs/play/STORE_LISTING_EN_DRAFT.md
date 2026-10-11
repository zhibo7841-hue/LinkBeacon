# LinkBeacon English Store Listing Draft

Status: **Draft / Pending Maintainer Review**

Prepared / policy checked: **2026-10-11**

Product basis: [v0.9.0 PRODUCT_PLAN](../PRODUCT_PLAN.md)

Developer: **LanYun Studio**

Support contact: **[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]**

Only the fenced text in the three fields below is proposed listing copy. Notes and Release Highlights are separate. No claim of Google approval or live store availability is made.

## App Title

```text
LinkBeacon
```

## Short Description

```text
Network diagnostics, Wi-Fi analysis and local device tools. No ads or account.
```

## Full Description

```text
Understand your network with LinkBeacon, an open-source Android toolkit for network analysis and troubleshooting. See a clear summary first, then expand the technical details when you need them.

NETWORK TOOLS
- View current IP addresses, gateways, configured DNS and connection information.
- Use Ping to inspect reachability, latency, packet loss and network quality.
- Look up A, AAAA, CNAME, MX and TXT DNS records, with available TTL and query timing.
- Check a TCP port or run a TCP Connect Port Scan using quick ports or a custom range from 1 to 65535.
- Follow an IPv4 route with Traceroute and inspect per-hop probe results.
- Calculate IPv4 subnets.

LOCAL DEVICES
- Discover devices on a local private IPv4 network using automatic or custom ranges of up to 254 addresses.
- View available reverse DNS, mDNS and UPnP device information; discovery depends on device responses.
- Save device profiles, favorites, custom names, types and notes in Device Center.
- Send Wake-on-LAN packets to compatible local devices.

WI-FI AND WEB
- Inspect available Wi-Fi signal, channel and nearby access-point observations. Android permissions, Location services and scan limits apply.
- Check SSL/TLS connections and certificate information.
- Diagnose HTTP/HTTPS website connection stages, redirects and response information.

DIAGNOSIS AND HISTORY
- Run automatic network diagnosis for evidence-based explanations, possible causes and practical next steps.
- Review supported test history and saved reports locally, with report copy, PDF export and sharing.

No ads. No LinkBeacon account required. Diagnostic results and saved profiles are local-first, not automatically uploaded to a developer server. Tests send necessary requests to network targets and resolvers; reports leave the device when you choose to export or share them.

Requires Android 12 or later. Test only networks and devices you own or are authorized to inspect. LinkBeacon helps you troubleshoot; it does not automatically repair your network or guarantee that every device or hop will respond.
```

## Character validation

Counts include spaces, punctuation and single LF line breaks inside the field, exclude Markdown fences and the final separator newline. All proposed text uses BMP characters, so this count also matches UTF-16 length.

| Field | Count | Limit | Result |
| --- | ---: | ---: | --- |
| App Title | 10 | 30 | Within limit |
| Short Description | 78 | 80 | Within limit |
| Full Description | 2065 | 4000 | Within limit |

[Official listing-field limits](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), checked 2026-10-11. Revalidate final Console text if edited.

## Release Highlights — separate draft

v0.9.0 brings Wi-Fi Analyzer with permission-aware connection and nearby-channel observations, alongside local network tools, Device Center, automatic diagnosis and English / Simplified Chinese support. No claim of a new Google Play release is implied.

## Console recommendations / maintainer fields

- Type: App. Category recommendation: Tools.
- Title is LinkBeacon in both languages. Developer: LanYun Studio.
- Support email: `[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]`; policy URL pending.
- Planned Play model: once-paid full app; price and availability selected later by maintainer. No extra in-app purchase or subscription feature is implemented.
- Keep the accurate open-source/no-ads/no-account description. Do not add download-for-free or payment-bypass directions to this listing. [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en), reviewed 2026-10-11.
- Do not add WHOIS, iPerf, Best Channel, real-time interference measurement, automatic repair, SSH/Telnet terminal, cloud diagnosis, UDP/SYN scan, banner grabbing or service fingerprinting claims.
- Screenshots must depict actual current UI, in this language, with privacy reviewed. [Asset checklist](STORE_ASSET_CHECKLIST.md).
- Audience, IARC, access, permissions and Data Safety require maintainer review; see [audit](PLAY_COMPLIANCE_AUDIT.md).
