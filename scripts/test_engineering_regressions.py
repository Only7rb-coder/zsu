#!/usr/bin/env python3
"""Source-level regression contracts that do not require Android or root hardware."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def block(source: str, start: str, end: str) -> str:
    start_index = source.index(start)
    end_index = source.index(end, start_index)
    return source[start_index:end_index]


class EngineeringRegressionTests(unittest.TestCase):
    def test_navigation_smoke_uses_the_real_application_id(self) -> None:
        build = read("manager/app/build.gradle.kts")
        smoke = read("scripts/navigation-smoke.py")
        application_id = re.search(r'applicationId\s*=\s*"([^"]+)"', build).group(1)
        smoke_package = re.search(r'^PACKAGE\s*=\s*"([^"]+)"', smoke, re.MULTILINE).group(1)
        self.assertEqual(application_id, smoke_package)

    def test_webui_interface_is_retained_and_destroyed(self) -> None:
        activity = read(
            "manager/app/src/main/java/com/rifsxd/ksunext/ui/webui/WebUIActivity.kt"
        )
        self.assertIn(
            "webviewInterface = WebViewInterface(this@WebUIActivity, this, moduleDir)",
            activity,
        )
        destroy = block(activity, "override fun onDestroy()", "super.onDestroy()")
        self.assertIn("private val rootShell = lazy", activity)
        self.assertIn("if (::webviewInterface.isInitialized)", destroy)
        self.assertIn("if (rootShell.isInitialized())", destroy)
        self.assertIn("runCatching { rootShell.value.close() }", destroy)
        self.assertNotIn("rootShell?.close()", destroy)

    def test_unknown_su_user_never_falls_back_to_root(self) -> None:
        su = read("userspace/ksud/src/su.rs")
        self.assertNotIn("unwrap_or(0)", su)
        self.assertIn("fn resolve_uid(user: &str) -> Result<u32>", su)
        self.assertIn("Unknown user:", su)

    def test_su_identity_changes_propagate_failures(self) -> None:
        su = read("userspace/ksud/src/su.rs")
        identity = block(su, "fn set_identity(", "fn wrap_tty(")
        self.assertIn("fn set_identity(uid: u32, gid: u32, groups: &[u32]) -> Result<()>", identity)
        self.assertNotIn(".ok();", identity)
        self.assertIn("set_identity(uid, gid, &groups)", su)
        self.assertIn('std::io::Error::other(format!("{error:#}"))', su)

    def test_manager_quotes_shell_arguments(self) -> None:
        cli = read(
            "manager/app/src/main/java/com/rifsxd/ksunext/ui/util/KsuCli.kt"
        )
        self.assertIn("fun String.shellQuote()", cli)
        for expected in (
            "module enable ${id.shellQuote()}",
            "module disable ${id.shellQuote()}",
            "module uninstall ${id.shellQuote()}",
            "module restore ${id.shellQuote()}",
            "module action ${moduleId.shellQuote()}",
            "sepolicy check ${rules.shellQuote()}",
            "profile get-sepolicy ${pkg.shellQuote()}",
            "profile set-sepolicy ${pkg.shellQuote()} ${rules.shellQuote()}",
            "profile get-template ${id.shellQuote()}",
            "profile delete-template ${id.shellQuote()}",
            "am force-stop ${packageName.shellQuote()}",
        ):
            self.assertIn(expected, cli)
        self.assertIn("${id.shellQuote()} ${template.shellQuote()}", cli)

    def test_module_install_uses_unique_files_and_finally_cleanup(self) -> None:
        cli = read(
            "manager/app/src/main/java/com/rifsxd/ksunext/ui/util/KsuCli.kt"
        )
        install = block(cli, "fun flashModule(", "fun runModuleAction(")
        self.assertIn('File.createTempFile("module-", ".zip", ksuApp.cacheDir)', install)
        self.assertIn("requireNotNull(resolver.openInputStream(uri))", install)
        self.assertIn("finally", install)

    def test_signing_secrets_are_not_committed(self) -> None:
        properties = read("manager/gradle.properties")
        self.assertNotRegex(properties, r"(?m)^KEYSTORE_(?:FILE|PASSWORD)=")
        self.assertNotRegex(properties, r"(?m)^KEY_(?:ALIAS|PASSWORD)=")
        self.assertFalse((ROOT / "manager/zsu.keystore").exists())
        ignores = read("manager/.gitignore")
        self.assertIn("zsu.keystore", ignores)
        self.assertIn("keystore.properties", ignores)

    def test_local_build_fails_fast_and_installs_zsu_artifacts(self) -> None:
        build = read("build.sh")
        self.assertIn("set -euo pipefail", build)
        self.assertIn("ZSU_*-release.apk", build)
        self.assertNotIn("KernelSU_Next_v*.apk", build)

    def test_local_manager_setup_preserves_configuration_and_uses_secret_env(self) -> None:
        setup = read("manager/setup.sh")
        self.assertIn("set -euo pipefail", setup)
        self.assertNotIn("> gradle.properties", setup)
        self.assertNotIn(">> gradle.properties", setup)
        self.assertNotIn("echo KEYSTORE_PASSWORD", setup)
        self.assertNotIn("echo KEY_ALIAS", setup)
        self.assertNotIn("echo KEY_PASSWORD", setup)
        for name in ("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
            self.assertIn(f"ORGANIZATION_GRADLE_PROJECT_{name}", setup)
        self.assertIn("test -f key.jks", setup)

    def test_gradle_git_versioning_checks_process_results(self) -> None:
        build = read("manager/build.gradle.kts")
        self.assertIn("private fun runGit", build)
        self.assertIn("redirectErrorStream(true)", build)
        self.assertIn("process.waitFor()", build)
        self.assertIn("process.exitValue()", build)

    def test_main_branch_has_ci_coverage(self) -> None:
        workflow = read(".github/workflows/build-manager-ci.yml")
        self.assertRegex(workflow, r'branches:\s*\[[^\]]*"main"')

    def test_release_publishes_and_verifies_sha256_checksums(self) -> None:
        workflow = read(".github/workflows/release.yml")
        self.assertIn("SHA256SUMS", workflow)
        self.assertIn("sha256sum", workflow)
        self.assertIn("sha256sum -c", workflow)
        self.assertIn("body_path: RELEASE_NOTES.md", workflow)
        self.assertIn("actions/checkout@v5", workflow)


if __name__ == "__main__":
    unittest.main(verbosity=2)
