# ZSU v1.5.3

## Security and reliability

- Reject unknown `su` users instead of silently falling back to UID 0.
- Propagate supplementary-group, GID, and UID change failures before executing a shell.
- Quote module IDs, package names, SEPolicy rules, and profile-template data passed through shell commands.
- Use unique module-install temporary files and clean them up even when installation fails.
- Close module-action and WebUI root shells reliably without creating a shell during early WebUI teardown.
- Remove committed signing configuration and the tracked keystore; ignore local signing material.

## Build and release engineering

- Preserve local Gradle configuration and pass signing values through environment-based project properties.
- Make Git-based version discovery wait for and validate Git subprocess results.
- Run host regression contracts in Manager CI, including the `main` branch.
- Make local builds fail fast and install the correctly named ZSU release APK.
- Correct the navigation smoke test to use ZSU's real application ID.
- Publish and verify `SHA256SUMS` with release assets.

## Verification

The host regression suite covers the security, cleanup, build, CI, and release contracts above. Android and kernel artifacts are built by the GitHub Actions release workflow; refer to the associated workflow run for the build record.
