# ZSU `su` Identity and SELinux Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the kernel-selected root identity by default while adding fail-closed explicit identity, SELinux-context, and terminal-wrapper behavior aligned with the audited references.

**Architecture:** Keep the existing `root_shell` entry point and authorization path. Parse all options first, perform shell setup in the current process, conditionally apply requested identity/context changes, and finish with `exec`; use focused source-level regression contracts because Android runtime facilities are unavailable on the host.

**Tech Stack:** Rust 2024, `std::process::Command`, `rustix`, `libc`, `anyhow`, Python 3 `unittest`

## Global Constraints

- Modify only `userspace/ksud/src/su.rs`, `scripts/test_engineering_regressions.py`, and the requested planning/tracking documentation.
- With no explicit identity options, do not call `setgroups`, `setresgid`, or `setresuid`.
- Unknown users, malformed IDs, requested identity failures, and requested SELinux-context failures must prevent target-shell execution.
- Do not alter root authorization, SELinux policy, permissive mode, or the separate `grant_root` helper.
- Do not add `claim_inherited_driver_fd`; ZSU lacks its supporting interface.
- Do not port wholesale features, rotate signing keys, push commits, or create a release in this plan.
- Do not claim physical-device verification from host tests or source contracts.

---

### Task 1: Preserve the Kernel-Selected Identity by Default

**Files:**
- Modify: `scripts/test_engineering_regressions.py:50-56`
- Modify: `userspace/ksud/src/su.rs:5-16,97-101,254-270,305-336`
- Test: `scripts/test_engineering_regressions.py`

**Interfaces:**
- Consumes: Existing `set_identity(uid: u32, gid: u32, groups: &[u32]) -> Result<()>` and `resolve_uid(user: &str) -> Result<u32>`.
- Produces: A local `identity_requested: bool`; direct `set_identity(...) ?` propagation before `Command::exec` only when identity was requested.

- [ ] **Step 1: Replace the obsolete pre-exec contract and add the failing default-identity test**

Replace `test_su_identity_changes_propagate_failures` and add the new test immediately after it:

```python
    def test_su_identity_changes_propagate_failures(self) -> None:
        su = read("userspace/ksud/src/su.rs")
        identity = block(su, "fn set_identity(", "fn resolve_uid(")
        self.assertIn(
            "fn set_identity(uid: u32, gid: u32, groups: &[u32]) -> Result<()>",
            identity,
        )
        self.assertNotIn(".ok();", identity)
        self.assertIn("set_identity(uid, gid, &groups)?;", su)

    def test_su_preserves_kernel_identity_without_explicit_options(self) -> None:
        su = read("userspace/ksud/src/su.rs")
        declaration = (
            "let identity_requested = free_idx < matches.free.len() "
            "|| gid.is_some() || !groups.is_empty();"
        )
        self.assertIn(declaration, su)
        self.assertLess(su.index(declaration), su.index("let gid = gid.unwrap_or(uid);"))
        self.assertIn(
            "if identity_requested {\n        set_identity(uid, gid, &groups)?;\n    }",
            su,
        )
        root = block(su, "pub fn root_shell()", "fn add_path_to_env(")
        self.assertNotIn("command.pre_exec", root)
```

- [ ] **Step 2: Run the focused tests and confirm RED**

Run from the repository root:

```bash
python scripts/test_engineering_regressions.py EngineeringRegressionTests.test_su_identity_changes_propagate_failures EngineeringRegressionTests.test_su_preserves_kernel_identity_without_explicit_options -v
```

Expected: both tests FAIL because the current source converts errors inside `pre_exec`, has no `identity_requested`, and calls `set_identity` unconditionally.

- [ ] **Step 3: Implement conditional identity and pre-exec setup removal**

Change the top-level `anyhow` import to:

```rust
use anyhow::{Context, Ok, Result, anyhow, bail};
```

Remove the function-local line:

```rust
    use anyhow::anyhow;
```

Immediately after the optional `-` login marker handling, add:

```rust
    let identity_requested = free_idx < matches.free.len() || gid.is_some() || !groups.is_empty();
```

Keep the existing UID resolution and `let gid = gid.unwrap_or(uid);` unchanged. Replace the `root_shell` `unsafe { command.pre_exec(...) }` block and final argument/exec statements with:

```rust
    command.args(args).arg0(arg0);
    umask(0o22);
    utils::switch_cgroups();

    // switch to global mount namespace
    if mount_master {
        let _ = utils::switch_mnt_ns(1);
    }

    if use_fd_wrapper {
        wrap_tty(0);
        wrap_tty(1);
        wrap_tty(2);
    }

    if identity_requested {
        set_identity(uid, gid, &groups)?;
    }
    Err(command.exec().into())
```

Do not change the `grant_root` `pre_exec` block.

- [ ] **Step 4: Run the focused tests and confirm GREEN**

Run:

```bash
python scripts/test_engineering_regressions.py EngineeringRegressionTests.test_su_identity_changes_propagate_failures EngineeringRegressionTests.test_su_preserves_kernel_identity_without_explicit_options -v
```

Expected: `Ran 2 tests` and `OK`.

- [ ] **Step 5: Commit the identity fix and its tests**

```bash
git add userspace/ksud/src/su.rs scripts/test_engineering_regressions.py
git commit -m "ksud: preserve default su identity"
```

---

### Task 2: Add Fail-Closed Explicit SELinux Context Selection

**Files:**
- Modify: `scripts/test_engineering_regressions.py`, after `test_su_preserves_kernel_identity_without_explicit_options`
- Modify: `userspace/ksud/src/su.rs`, in `resolve_uid`/`wrap_tty`, option scanning, option registration, option extraction, and final exec sequence
- Test: `scripts/test_engineering_regressions.py`

**Interfaces:**
- Consumes: Parsed `getopts::Matches`, selected identity values, and the existing root-authorized `root_shell` path.
- Produces: `fn set_selinux_context(context: &str) -> Result<()>` and `selinux_context: Option<String>`; optional context application after identity selection and before `exec`.

- [ ] **Step 1: Add the failing SELinux regression contract**

```python
    def test_su_selinux_context_is_explicit_and_fail_closed(self) -> None:
        su = read("userspace/ksud/src/su.rs")
        setter = block(su, "fn set_selinux_context(", "fn wrap_tty(")
        self.assertIn(
            'std::fs::write("/proc/thread-self/attr/current", context)?;',
            setter,
        )
        self.assertIn(
            'opts.optopt("Z", "context", "Specify the SELinux context", "CONTEXT");',
            su,
        )
        self.assertIn('arg[0].starts_with("-Z")', su)
        self.assertIn('arg[0] == "--context="', su)
        self.assertIn('let selinux_context = matches.opt_str("Z");', su)
        root = block(su, "pub fn root_shell()", "fn add_path_to_env(")
        apply = "if let Some(context) = selinux_context.as_deref()"
        self.assertIn(apply, root)
        self.assertIn('with_context(|| format!("setcontext {context}"))?;', root)
        self.assertLess(root.index("if identity_requested"), root.index(apply))
        self.assertLess(root.index(apply), root.index("Err(command.exec().into())"))
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
python scripts/test_engineering_regressions.py EngineeringRegressionTests.test_su_selinux_context_is_explicit_and_fail_closed -v
```

Expected: FAIL with `ValueError: substring not found` because `set_selinux_context` does not exist.

- [ ] **Step 3: Implement context parsing and application**

Insert after `resolve_uid`:

```rust
fn set_selinux_context(context: &str) -> Result<()> {
    std::fs::write("/proc/thread-self/attr/current", context)?;
    Ok(())
}
```

In the `first_non_option` exclusion chain, retain the existing exclusions and add the two context forms so the complete chain is:

```rust
                && !(arg[0].starts_with("-g")
                    || arg[0].starts_with("-G")
                    || arg[0].starts_with("-s")
                    || arg[0].starts_with("-Z")
                    || arg[0] == "--group"
                    || arg[0] == "--supp-group="
                    || arg[0] == "--shell="
                    || arg[0] == "--context=")
```

After the `ksu-no-new-privs` option registration, add:

```rust
    opts.optopt("Z", "context", "Specify the SELinux context", "CONTEXT");
```

After `let ksu_no_new_privs = ...`, add:

```rust
    let selinux_context = matches.opt_str("Z");
```

Immediately after conditional identity application and before `Err(command.exec().into())`, add:

```rust
    if let Some(context) = selinux_context.as_deref() {
        set_selinux_context(context).with_context(|| format!("setcontext {context}"))?;
    }
```

- [ ] **Step 4: Run identity and SELinux tests and confirm GREEN**

```bash
python scripts/test_engineering_regressions.py EngineeringRegressionTests.test_su_identity_changes_propagate_failures EngineeringRegressionTests.test_su_preserves_kernel_identity_without_explicit_options EngineeringRegressionTests.test_su_selinux_context_is_explicit_and_fail_closed -v
```

Expected: `Ran 3 tests` and `OK`.

- [ ] **Step 5: Commit SELinux support and its test**

```bash
git add userspace/ksud/src/su.rs scripts/test_engineering_regressions.py
git commit -m "ksud: support explicit su SELinux context"
```

---

### Task 3: Harden Wrapped Terminal Handling

**Files:**
- Modify: `scripts/test_engineering_regressions.py`, after the SELinux test
- Modify: `userspace/ksud/src/su.rs:11,81-99`
- Test: `scripts/test_engineering_regressions.py`

**Interfaces:**
- Consumes: Existing `get_wrapped_fd(fd: RawFd) -> std::io::Result<RawFd>` and `libc` descriptor functions.
- Produces: Best-effort `wrap_tty(fd: c_int)` that handles original-descriptor `EACCES`, validates the wrapped descriptor, and closes it on every path.

- [ ] **Step 1: Add the failing terminal-wrapper regression contract**

```python
    def test_su_terminal_wrapper_handles_selinux_eacces_and_closes_fd(self) -> None:
        su = read("userspace/ksud/src/su.rs")
        wrapper = block(su, "fn wrap_tty(", "#[allow(clippy::similar_names)]")
        self.assertIn(
            "io::Error::last_os_error().raw_os_error() != Some(libc::EACCES)",
            wrapper,
        )
        self.assertIn("if unsafe { libc::isatty(new_fd) != 1 }", wrapper)
        self.assertIn("let dup_result = unsafe { libc::dup2(new_fd, fd) };", wrapper)
        self.assertIn("let dup_errno = unsafe { *libc::__errno() };", wrapper)
        self.assertIn("unsafe { libc::close(new_fd) };", wrapper)
        self.assertLess(wrapper.index("let dup_errno"), wrapper.index("if dup_result == -1"))
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
python scripts/test_engineering_regressions.py EngineeringRegressionTests.test_su_terminal_wrapper_handles_selinux_eacces_and_closes_fd -v
```

Expected: FAIL because the current wrapper has no `EACCES` or wrapped-descriptor `isatty` handling.

- [ ] **Step 3: Implement the terminal-wrapper fix**

Change the standard-library import to:

```rust
use std::{cmp::Ordering, env, io};
```

Replace the complete `wrap_tty` function with:

```rust
fn wrap_tty(fd: c_int) {
    let inner_fn = move || -> Result<()> {
        if unsafe { libc::isatty(fd) != 1 }
            && io::Error::last_os_error().raw_os_error() != Some(libc::EACCES)
        {
            return Ok(());
        }

        // The root profile is already active here, so its SELinux domain may
        // return EACCES while querying the original terminal. In that case,
        // check the wrapped fd instead, since that descriptor is intended to
        // bypass this restriction.
        let new_fd = get_wrapped_fd(fd).context("get_wrapped_fd")?;
        if unsafe { libc::isatty(new_fd) != 1 } {
            unsafe { libc::close(new_fd) };
            return Ok(());
        }
        let dup_result = unsafe { libc::dup2(new_fd, fd) };
        let dup_errno = unsafe { *libc::__errno() };
        unsafe { libc::close(new_fd) };
        if dup_result == -1 {
            bail!("dup {new_fd} -> {fd} errno: {dup_errno}");
        }
        Ok(())
    };

    if let Err(e) = inner_fn() {
        error!("wrap tty {fd}: {e:?}");
    }
}
```

- [ ] **Step 4: Run all three new behavior tests and confirm GREEN**

```bash
python scripts/test_engineering_regressions.py EngineeringRegressionTests.test_su_identity_changes_propagate_failures EngineeringRegressionTests.test_su_preserves_kernel_identity_without_explicit_options EngineeringRegressionTests.test_su_selinux_context_is_explicit_and_fail_closed EngineeringRegressionTests.test_su_terminal_wrapper_handles_selinux_eacces_and_closes_fd -v
```

Expected: `Ran 4 tests` and `OK`.

- [ ] **Step 5: Commit terminal handling and its test**

```bash
git add userspace/ksud/src/su.rs scripts/test_engineering_regressions.py
git commit -m "ksud: harden su terminal wrapping"
```

---

### Task 4: Run the Complete Host Verification Gate

**Files:**
- Verify: `userspace/ksud/src/su.rs`
- Verify: `scripts/test_engineering_regressions.py`
- Verify: `.github/workflows/build-manager-ci.yml`
- Verify: all tracked shell scripts

**Interfaces:**
- Consumes: All changes from Tasks 1–3.
- Produces: Recorded host-check results and a clean reviewed diff; no claim of Android or physical-device validation.

- [ ] **Step 1: Run the full engineering regression suite**

```bash
python scripts/test_engineering_regressions.py
```

Expected: `Ran 15 tests` and `OK`.

- [ ] **Step 2: Run manager configuration and repository static checks**

```bash
node scripts/validate-jailbreak-manager-config.mjs
git diff --check
git ls-files '*.sh' -z | xargs -0 -n1 bash -n
```

Expected: configuration validation exits 0; whitespace and shell syntax commands produce no errors.

- [ ] **Step 3: Check Rust formatting**

Run from `userspace/ksud`:

```bash
rustfmt --edition 2024 --check src/su.rs
```

Expected: no diff and exit 0. If formatting differs, run `rustfmt --edition 2024 src/su.rs`, rerun Tasks 1–3 focused tests, and amend only the relevant task commit with the formatting-only change.

- [ ] **Step 4: Review the complete local change**

```bash
git diff a92cac34e198b9083aa0ed604361295cbf097054..HEAD --check
git diff a92cac34e198b9083aa0ed604361295cbf097054..HEAD --stat
git status --short
```

Expected: no whitespace errors; only the design, plan, `su.rs`, and engineering regression test files are listed; status is clean.

- [ ] **Step 5: Record verification limits**

Report that Android `cargo ndk` check/Clippy, Android/kernel CI, and physical-device tests were not run locally. Do not push or release. Carry forward the device matrix in `docs/superpowers/specs/2026-11-25-su-identity-selinux-design.md` as the release gate.
