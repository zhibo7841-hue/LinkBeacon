# Diagnostic presentation terminology

Task 083 uses one set of language-independent enums and message codes. These
labels are presentation only; they never determine evidence or severity.

| Meaning | English | 简体中文 |
| --- | --- | --- |
| Diagnosis NORMAL | Network status looks normal | 网络状态正常 |
| Diagnosis ATTENTION | Findings need attention | 发现需要关注的问题 |
| Diagnosis LIMITED | Some network capabilities may be limited | 部分网络能力受限 |
| Diagnosis UNKNOWN | Overall state unconfirmed | 状态未确定 |
| Severity HEALTHY | Normal | 正常 |
| Severity NOTICE | Notice | 提示 |
| Severity WARNING | Warning | 异常 |
| Severity ERROR | Serious issue | 严重异常 |
| Check PASS | Normal | 正常 |
| Check FAIL | Failed | 异常 |
| Check NOT_APPLICABLE | Not applicable | 不适用 |
| Check SKIPPED | Not performed | 未执行 |
| Evidence section | Analysis evidence | 分析依据 |
| Findings | Findings | 发现 |
| Recommendations | Recommendations | 建议 |

Actual wording is owned by `feature/report` resources; this table describes the
shared vocabulary, not a second rule engine. TCP CONNECTION_REFUSED remains
positive target-side IP-path evidence, not a conclusion of Internet failure.
Gateway timeout remains unconfirmed. VALIDATED=false does not prove no Internet.
VPN and Fake-IP are context, not evidence of malware, DNS poisoning or a fault.
Mobile gateway not applicable remains different from a failed probe.

Protocol tokens, addresses, hostnames and user-authored names remain unchanged.
Unknown message codes use their saved fallback. Legacy prose can remain Chinese
inside an English report; it is neither translated by inference nor re-analyzed.
