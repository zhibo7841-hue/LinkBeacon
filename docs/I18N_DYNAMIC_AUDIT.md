# Dynamic localization audit (Task 083)

Baseline: `def51c6088ffc5d79c474f642bb8d31d3ed9323f`.
This is the pre-implementation audit, not a completion or acceptance claim.

## Snapshot contract

The live path is `RunAutomaticDiagnosticUseCase` → `DiagnosticOrchestrator`
→ evidence → `DefaultDiagnosticAnalyzerV4` → `AutomaticDiagnosticResult`.
History stores schema-3 `AUTOMATIC_DIAGNOSTIC_V4` JSON. The reader restores
the original analysis; it must never invoke an analyzer or a probe. Schema-2
reports have a separate retained reader. Room only stores the existing envelope.

| Content | Current source / identity | Parameters and rendered text | Safe localization / compatibility | Proposed ownership |
|---|---|---|---|---|
| Overall diagnosis | Analyzer, diagnosis status and primary finding code | Chinese title/explanation; code does not identify every branch | Preserve old prose; no branch reconstruction | Optional stable message descriptors selected in the original branch |
| Finding | Analyzer, finding code/severity/evidence references | Chinese title, description and possible causes | Same code can have different descriptions; old prose remains | Descriptor per text field; identity unchanged |
| Explanation | Diagnosis or selected material finding | Chinese, including branch-specific uncertainty | Cannot translate from primary finding alone | Carry selected descriptor, never re-evaluate evidence |
| Recommendation | Recommendation code/priority/applicability | Chinese title/action/reason/verification hint | One code has several reasons; preserve old reason | Descriptor alongside existing fallback fields |
| Check result | Orchestrator, check code/stage/status | Summary prose plus typed target/time/method | Typed labels may localize; preserve original legacy summary | Resource mapping at presentation boundary |
| Evidence | Observation code + sealed value | Boolean/address/latency/TCP/DNS values, raw technical text | Typed labels localize; raw values remain exact | Shared report renderer |
| Retry / verify | Comparator status and finding-code sets | Branch-specific Chinese summary | Do not repeat comparison on locale changes | Capture summary descriptor when comparison occurs |
| Fake-IP / VPN | Context finding codes and observations | Chinese contextual explanation | No new fault interpretation or proxy identification | Localize the captured contextual message |
| Gateway / Internet | Check state and analyzer finding | Timeout is inconclusive; multiple evidence sources | No new analysis in mapper | Preserve existing selected conclusion |
| DNS | Typed DNS outcome/records | Raw record data; prose finding | A success + AAAA no-records semantics unchanged | Outcome labels and captured finding text |
| TCP | Typed outcome/target/latency | Refused can be positive public IP-path evidence | Never replace refused with Internet failure | Typed outcome label plus selected explanation |
| Ping / latency / loss | Probe evidence and tool History fields | Numeric metrics/quality level; no high-latency diagnosis rule exists | Localize metrics, do not invent new findings | Tool History presentation |
| Diagnostic History summary | Stored summary/historySummary and full schema-3 result | Stored Chinese fallback | Render known descriptors; legacy prose retained | Read-only snapshot presentation |
| Tool History summary | HistoryRecordFactory and LAN serializer | Ping/DNS/TCP/LAN metrics; some records prose-only | Only reliable typed fields are renderable; no Traceroute History type exists | Read-only tool-specific presentation, no new history type |
| Text report | DiagnosticReportTextFormatter | Chinese section labels, typed details and snapshot prose | Fixed locale per operation; preserve legacy/raw text | Immutable localization context |
| PDF report | Existing Android PdfDocument renderer | Text formatter output, sans-serif, bounded A4 layout | Reuse layout/fonts; test long English and mixed Chinese | Same fixed localization context as text |
| Copy | ReportScreen callback with formatted snapshot | No persistence/probing required | Capture current locale on action | Shared formatter, no new side effects |
| Share | Existing PDF export lifecycle | Bytes generated from snapshot | Keep request identity/recreation ownership | Shared frozen-locale renderer |

## Non-negotiable compatibility boundaries

- A: complete stable message identity and arguments → current-language rendering.
- B: partially structured → localized typed parts plus exact saved prose.
- C: prose-only → saved prose, including Chinese in an English interface.
- Unknown message code or invalid arguments → saved fallback, not a blank,
  resource ID, inferred diagnosis or network request.
- Optional JSON additions are preferred; no Room migration or bulk rewrite.
- Domain identifiers are strings/enums, never Android resource integers.
- Resource mapping belongs to presentation. Locale is not snapshot identity.
- Keep gateway uncertainty, positive public TCP refusal evidence, asymmetric
  VALIDATED evidence, mobile gateway applicability, and VPN/Fake-IP context.
- No README/release/version changes. Android 12 runtime verification remains
  pending; no system image installation is part of this task.

## Implementation and acceptance tracking

Branch-specific message capture, backward-compatible optional JSON metadata,
shared localized presentation/export and structured History projection have been
implemented. Report resources add 326 English/zh-Hans pairs and History adds 22.
Targeted report/history/common JVM tests pass, including equivalence, fallback,
unknown code, frozen locale and pagination checks. Full gates and PDF/device
acceptance are tracked separately in I18N_SCOPE_AUDIT.md; this audit alone is not
a completion claim.

The maintainer changed the acceptance device to G8142 during Task 083. It reports
Android 13 / API 33. Do not label its results Sony Xperia 1 VII / Android 16.
