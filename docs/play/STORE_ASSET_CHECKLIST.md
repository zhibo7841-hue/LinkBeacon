# LinkBeacon Google Play Store Asset Checklist

Status: **Draft / Pending Maintainer Review**

Inspection / official-policy access date: **2026-10-11**

Scope: actual existing assets for LinkBeacon v0.9.0

Developer: LanYun Studio

Support: **[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]**

This is a read-only inventory and gap list. No screenshot was generated, edited, cropped, resized, re-encoded or uploaded. Repository images and social preview remain unchanged.

## 1. Current official requirements

From [Google Play preview-asset guidance](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en), checked 2026-10-11:

- Store icon: 512 × 512, 32-bit PNG with alpha, at most 1024 KB.
- Feature graphic: 1024 × 500, JPEG or 24-bit PNG without alpha.
- Screenshots: at least two across device types; up to eight per device type. JPEG or 24-bit PNG without alpha; dimensions 320–3840 pixels, with the longer dimension at most twice the shorter.
- Separate promotional eligibility guidance recommends at least four app screenshots with 1080-pixel minimum and portrait 9:16 or landscape 16:9. This is not the basic two-screenshot submission requirement.

Use real relevant UI. Do not imply unavailable features, rankings, prices or certification. Recheck Console requirements before submission, especially if choosing additional device types.

## 2. Actual measured files

| Existing asset | Dimensions / format | Evidence / suitability |
| --- | --- | --- |
| [English README overview](../screenshots/readme-overview-en.png) | 1285 × 906; 24-bit RGB PNG; 279,137 bytes | Three real UI panes (Home / Tools / Devices), English. General dimension/format checks fit, but one composite is not a complete localized phone set. |
| [Chinese README overview](../screenshots/readme-overview-zh.png) | 1292 × 902; 24-bit RGB PNG; 257,162 bytes | Same broad three-pane structure, Chinese. Not a replacement for a dedicated complete phone set. |
| [Logo](../assets/linkbeacon-logo.png) | 160 × 160; 32-bit ARGB PNG; 4,552 bytes | Not a 512 × 512 Play icon. |
| [Social preview](../assets/linkbeacon-social-preview.png) | 1280 × 640; 32-bit ARGB PNG; 50,506 bytes | Wrong feature-graphic dimensions/alpha. Keep this GitHub asset unchanged. |
| Launcher density resources | 48, 72, 96, 144 and 192 px square PNGs; adaptive XML/vector resources | Valid app assets are not automatically a Play-sized store icon; no 512 px store icon found. |

`docs/screenshots/.gitkeep` and both README overview PNGs are tracked. No additional existing phone set or 1024 × 500 graphic was found in the reviewed repository assets. Files were decoded/measured and both overviews visually inspected; no assumption based solely on filenames.

## 3. Truthfulness and privacy

The overviews show implemented Home, Tools and Devices UI, including Wi-Fi Analyzer and existing network/web tools. Their visual content is consistent with the v0.9 product feature set. Exact capture-device/build/artifact provenance was not established: **Needs verification**, not “exact signed v0.9 screenshot certified.”

The composites show real local network values/device labels. Review SSID, addresses and hostnames before public Play use; approve retained examples or prepare privacy-reviewed replacements in a separate authorized task. Do not silently edit images in this task or substitute an older screenshot as a new one.

Each language currently has one composite. Two language variants should not be treated as a complete consistent two-image localized phone presentation. Their composite aspect ratios also do not meet the recommended 9:16/16:9 promotional layout.

## 4. Required gap list

| Item | Status | Next manual action |
| --- | --- | --- |
| 512 × 512 store icon | Missing | Prepare a proper brand-derived Play icon separately; validate format/size. Do not upscale a tiny asset and claim a completed deliverable here. |
| 1024 × 500 feature graphic | Missing | Prepare actual approved branding in required format separately. No fake app capabilities or payment-bypass messaging. |
| English phone screenshots | Incomplete | Capture at least two true current standalone phone screens with provenance/privacy review; preferably build a four-screen promotional set. |
| Simplified Chinese phone screenshots | Incomplete | Prepare matching localized real captures, not English copies with misleading claims. |
| v0.9 screenshot provenance | Pending | Record capture build/version/device and maintainer approval. |
| Asset upload | Not performed | Upload only after review in a separately authorized Play Console task. |

No screenshot changes affect the existing release artifact or its file hash. No Tag, Release, runtime/resource, PRODUCT_PLAN, DECISIONS or version change is authorized by this checklist.
