# Contributing

Thank you for your interest in LinkBeacon. Contributions in English or Chinese
are welcome.

Before contributing, review the project documentation and make sure the proposed
change respects the product boundaries, privacy principles, and current
architecture.

## Contribution flow

1. Fork the repository.
2. Create a focused branch for the change.
3. Implement and test the change.
4. Commit with a clear message.
5. Open a Pull Request describing the purpose and verification results.

## Contribution requirements

- Preserve the existing Clean Architecture boundaries and code quality.
- Add or update tests for changed behavior.
- Do not commit secrets, API keys, accounts, private paths, signing files, or
  real user data.
- Do not introduce network-data uploads, account requirements, advertising, or
  behavior that conflicts with the local-first principle.
- Do not turn LinkBeacon into an automatic repair tool, a system that claims
  definitive diagnosis of every network fault, or an SSH/Telnet client.
- Keep changes within the confirmed product scope. Propose new scope for review
  before implementation.
- Preserve technical accuracy: report what a probe actually measures and do not
  present one protocol's result as another protocol's result.

## Before opening a pull request

Run the relevant tests and at minimum verify:

```text
./gradlew test
./gradlew assembleDebug
```

On Windows, use `gradlew.bat`. A Pull Request should explain:

- What changed and why.
- Which tests or manual checks were performed.
- Whether product scope, privacy behavior, permissions, persistence, or network
  behavior are affected.

Do not include maintainer release-signing credentials. Ordinary development and
CI builds use the repository's standard debug workflow.
