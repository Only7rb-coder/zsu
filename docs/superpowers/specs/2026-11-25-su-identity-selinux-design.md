# ZSU `su` identity and SELinux hardening design

Date: 2026-11-25
Status: Approved design, pending written-spec review

## Purpose

Correct ZSU's root-shell identity handling while preserving the kernel-selected root profile. Add the explicit SELinux-context and terminal-wrapper behavior already present in the audited KernelSU-Next, BakaSU, and FinaSU snapshots without importing unrelated kernel interfaces or changing root authorization.

## Evidence and scope

The compared implementations are:

- KernelSU-Next commit `896f45a78c0806cfd585c4e14e6989f867b143d7`
- BakaSU commit `57b2db6076f439cfd489f14576444e8f0f2f0a25`
- FinaSU commit `a85c03ab6be57fd1a81a6d345eb5aa8275652b42`

ZSU currently calls `set_identity` unconditionally inside `Command::pre_exec`. With no user or group options, this derives UID and GID from the current UID and supplies an empty supplementary-group list. That can overwrite the identity and groups selected by the kernel root profile.

This change is limited to `userspace/ksud/src/su.rs` and focused regression tests. It does not merge other root projects, add feature subsystems, alter SELinux policy, enable permissive mode, or change the authorization path in `cli.rs` and the kernel.

## Behavior

### Identity selection

Compute `identity_requested` after consuming the optional login marker and before replacing an absent GID with its default. It is true when any of the following is present:

- A positional user name or numeric UID
- `-g/--group`
- One or more `-G/--supp-group` values

When `identity_requested` is false, do not call `setgroups`, `setresgid`, or `setresuid`. Preserve the UID, GID, supplementary groups, and SELinux domain already applied by the kernel.

When `identity_requested` is true, retain the existing defaults: an absent UID uses the current UID, an absent GID uses the selected UID, and the first supplementary group becomes the primary GID if `-g` is absent. Apply groups before GID and UID. Unknown users, malformed IDs, and every identity-system-call failure remain fatal.

### SELinux context

Add `-Z CONTEXT` and `--context=CONTEXT` using the existing option parser. Include these options in the positional-argument scanner so a context value cannot be mistaken for a user or executable.

After the existing root authorization and any requested identity change, write the requested context to `/proc/thread-self/attr/current`. A failed write aborts shell execution with context information. With no `-Z`, perform no context write and preserve the active domain. Do not add a fallback domain, modify sepolicy, or suppress errors.

### Shell setup and terminal wrapping

Move `root_shell` setup out of `pre_exec`, matching the three references. Configure arguments, umask, cgroups, optional mount namespace, and optional terminal wrapping before identity/context changes and the final `exec`. The separate `grant_root` helper remains unchanged.

For terminal wrapping, treat `isatty(fd)` returning `EACCES` as a reason to inspect the wrapped descriptor. Check that the wrapped descriptor is a terminal, close it on every non-`dup2` path, preserve the `dup2` error number across close, and keep terminal wrapping best-effort with diagnostic logging. Identity and SELinux failures, unlike terminal-wrapper failures, must abort execution.

Do not port `claim_inherited_driver_fd`: ZSU's current `ksucalls.rs` has no such interface, and adding that kernel/userspace contract is outside this change.

## Error handling

- Option parsing and help/version behavior remain unchanged except for the new context option.
- `resolve_uid` never falls back to UID 0 for an unknown user.
- Requested identity changes run in the parent before `exec`; any error returns from `root_shell` and no target shell is launched.
- Requested SELinux-context changes likewise fail closed.
- Existing ignored mount-namespace errors and best-effort terminal diagnostics retain upstream behavior; this design does not broaden those exceptions.

## Regression tests

Add failing source-level regression contracts before implementation, consistent with `scripts/test_engineering_regressions.py`, for:

1. `identity_requested` is computed before `gid.unwrap_or(uid)` and includes positional user, primary GID, and supplementary groups.
2. `set_identity` is conditional and no unconditional identity reset remains in `pre_exec`.
3. `-Z`, `--context`, the positional scanner exclusions, the proc-thread context write, and fatal error propagation are present.
4. Terminal wrapping handles `EACCES`, validates the wrapped descriptor, and closes it correctly.
5. The existing unknown-user contract still passes. Update the existing identity-error contract from `pre_exec` error conversion to direct fatal propagation before `exec`; the required fail-closed behavior remains unchanged.

Run the complete engineering regression suite, jailbreak-manager configuration validation, whitespace checks, shell syntax checks, and `rustfmt --check` for the modified Rust source. Run Android Rust compilation and Clippy in project CI when a reviewed change is pushed. Host checks cannot prove Android runtime behavior.

## Verification limits and device gate

A successful implementation must not be described as device-proven until tested on supported Android devices. The minimum device matrix covers:

- Default `su` with a non-default kernel root profile, checking UID, GID, supplementary groups, and SELinux domain
- Explicit user, `-g`, and `-G`, including invalid and unknown values
- Valid and invalid `-Z` contexts, confirming failures do not launch a shell
- Interactive and non-interactive shells with terminal wrapping enabled and disabled
- `-M`, login shells, command execution, and `--ksu-no-new-privs`
- ARM64 Android builds and the supported kernel/KMI matrix in CI

Physical-device results, rather than feature presence or host tests alone, determine whether this change is ready to release.

## Non-goals

- GhostLock synchronization; it requires an independent native/profile contract design
- Wholesale FinaSU or BakaSU feature ports
- Signing-key rotation, a new release, or remote repository mutation
- Claims that ZSU is objectively superior to every other root implementation
