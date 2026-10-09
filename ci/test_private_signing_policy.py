import copy
import os
from pathlib import Path
import re
import unittest
from unittest.mock import patch

from private_signing_guard import (
    REQUIRED_SECRETS, validate_context, validate_environment,
    validate_secret_scopes, verify_setup,
)
from package_unsigned_release import apk_metadata

ROOT = Path(__file__).resolve().parents[1]


def environment():
    return {
        "id": 123, "name": "Dev Env",
        "deployment_branch_policy": {"custom_branch_policies": True, "protected_branches": False},
        "protection_rules": [
            {"type": "required_reviewers", "prevent_self_review": False,
             "reviewers": [{"type": "User", "reviewer": {"id": 99826912}}]},
            {"type": "branch_policy"},
        ],
    }


def policies():
    return {"total_count": 1, "branch_policies": [{"name": "main", "type": "branch"}]}


def names(values):
    return {"total_count": len(values), "secrets": [{"name": item} for item in values]}


class SigningGuardTest(unittest.TestCase):
    def test_exact_approved_environment_passes(self):
        validate_environment(environment(), policies(), 123)

    def test_missing_changed_or_recreated_environment_fails(self):
        for field, value in [("id", 124), ("name", "wrong environment"), ("protection_rules", []),
                             ("deployment_branch_policy", None)]:
            item = environment()
            item[field] = value
            with self.assertRaises(ValueError):
                validate_environment(item, policies(), 123)

    def test_implicit_protected_branches_and_wildcards_are_rejected(self):
        item = environment()
        item["deployment_branch_policy"] = {"custom_branch_policies": False, "protected_branches": True}
        with self.assertRaises(ValueError):
            validate_environment(item, policies(), 123)
        for name, kind in [("*", "branch"), ("main", "tag"), ("main", None)]:
            policy = policies()
            policy["branch_policies"][0] = {"name": name, "type": kind}
            with self.assertRaises(ValueError):
                validate_environment(environment(), policy, 123)

    def test_extra_reviewers_policy_drift_and_pagination_fail(self):
        item = environment()
        item["protection_rules"][0]["reviewers"].append({"type": "User", "reviewer": {"id": 1}})
        with self.assertRaises(ValueError):
            validate_environment(item, policies(), 123)
        item = environment()
        item["protection_rules"][0]["prevent_self_review"] = True
        with self.assertRaises(ValueError):
            validate_environment(item, policies(), 123)
        policy = policies()
        policy["total_count"] = 2
        with self.assertRaises(ValueError):
            validate_environment(environment(), policy, 123)

    def test_environment_only_names_are_required_and_repo_fallback_is_forbidden(self):
        validate_secret_scopes(names(REQUIRED_SECRETS), names([]))
        with self.assertRaises(ValueError):
            validate_secret_scopes(names([]), names(REQUIRED_SECRETS))
        with self.assertRaises(ValueError):
            validate_secret_scopes(names(REQUIRED_SECRETS), names([next(iter(REQUIRED_SECRETS))]))

    def test_incomplete_secret_name_metadata_fails(self):
        metadata = names(REQUIRED_SECRETS)
        metadata["total_count"] += 1
        with self.assertRaises(ValueError):
            validate_secret_scopes(metadata, names([]))

    def test_signing_context_is_manual_main_exact_sha_and_explicitly_enabled(self):
        context = {"GITHUB_REPOSITORY": "HebaDenys/fitness-hub", "GITHUB_EVENT_NAME": "workflow_dispatch",
                   "GITHUB_REF": "refs/heads/main", "GITHUB_SHA": "a" * 40, "SIGNING_ENABLED": "true"}
        branch = {"name": "main", "commit": {"sha": "a" * 40}}
        validate_context(context, branch)
        for field, value in [("GITHUB_REPOSITORY", "fork/fitness-hub"), ("GITHUB_EVENT_NAME", "pull_request"),
                             ("GITHUB_REF", "refs/tags/main"), ("SIGNING_ENABLED", "")]:
            changed = dict(context, **{field: value})
            with self.assertRaises(ValueError):
                validate_context(changed, branch)
        with self.assertRaises(ValueError):
            validate_context(context, {"name": "main", "commit": {"sha": "b" * 40}})

    def test_disabled_signing_never_queries_metadata(self):
        with patch.dict(os.environ, {}, clear=True), patch("private_signing_guard.read_json") as read:
            with self.assertRaises(ValueError):
                verify_setup()
            read.assert_not_called()

    def test_real_apk_metadata_must_be_nondebuggable_offline_and_expected_package(self):
        valid = "package: name='io.github.hebadenys.fitnesshub' versionCode='18' versionName='0.3.16'\n"
        self.assertEqual(18, apk_metadata(valid)["version_code"])
        for invalid in [valid + "application-debuggable", valid + "android.permission.INTERNET",
                        valid.replace("io.github.hebadenys.fitnesshub", "different.app"),
                        valid + "android.permission.health.WRITE_WEIGHT"]:
            with self.assertRaises(ValueError):
                apk_metadata(invalid)


class SigningWorkflowPolicyTest(unittest.TestCase):
    def setUp(self):
        self.private = (ROOT / ".github/workflows/private-signing.yml").read_text()
        self.verify = (ROOT / ".github/workflows/verify-signing-setup.yml").read_text()
        self.public = (ROOT / ".github/workflows/android.yml").read_text()

    def test_private_key_refs_exist_only_in_isolated_sign_job(self):
        before, sign = self.private.split("\n  sign:", 1)
        self.assertNotIn("secrets.", before)
        self.assertNotIn("actions/checkout", sign)
        self.assertNotIn("setup-gradle", sign)
        self.assertNotIn("./gradlew", sign)
        self.assertNotIn("run: python3 ci/", sign)
        self.assertEqual(REQUIRED_SECRETS, set(re.findall(r"secrets\.(FITNESS_HUB_RELEASE_[A-Z_]+)", sign)))
        self.assertEqual(1, self.private.count("environment: Dev Env"))
        self.assertIn("needs: [preflight, build]", sign)
        self.assertIn("artifact-ids: ${{ needs.build.outputs.artifact_id }}", sign)
        self.assertIn("cancel-in-progress: false", self.private)

    def test_actions_are_pinned_to_full_immutable_commits(self):
        for workflow in (self.private, self.verify, self.public):
            actions = re.findall(r"uses:\s+([^\s]+)", workflow)
            self.assertTrue(actions)
            for action in actions:
                self.assertRegex(action, r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}$")

    def test_inline_signing_guard_matches_tested_source(self):
        body = self.private.split("# BEGIN_REVIEWED_ENVIRONMENT_GUARD\n", 1)[1].split("          # END_REVIEWED_ENVIRONMENT_GUARD", 1)[0]
        extracted = "\n".join(line[10:] for line in body.rstrip().splitlines())
        source = (ROOT / "ci/private_signing_guard.py").read_text().rstrip()
        self.assertEqual(source, extracted)

    def test_read_only_setup_has_no_environment_no_secrets_no_key_use(self):
        self.assertNotRegex(self.verify, r"(?m)^\s+environment:")
        self.assertNotIn("secrets.", self.verify)
        self.assertNotIn("apksigner", self.verify)
        self.assertIn("SETUP_CHECK_ONLY: 'true'", self.verify)
        self.assertNotIn("contents: write", self.verify)

    def test_public_gradle_build_has_no_repository_write_token(self):
        build, publish = self.public.split("\n  publish:", 1)
        self.assertNotIn("contents: write", build)
        self.assertNotIn("secrets.", build)
        self.assertIn("contents: write", publish)
        self.assertIn("github.ref == 'refs/heads/main'", publish)
        self.assertNotIn("./gradlew", publish)

    def test_signing_files_are_explicit_and_cleanup_never_uploads_key_directory(self):
        sign = self.private.split("\n  sign:", 1)[1]
        self.assertIn("umask 077", sign)
        self.assertIn("--ks-pass env:FITNESS_HUB_RELEASE_STORE_PASSWORD", sign)
        self.assertIn("--key-pass env:FITNESS_HUB_RELEASE_KEY_PASSWORD", sign)
        self.assertIn("trap 'rm -f", sign)
        self.assertIn("FITNESS_HUB_RELEASE_CERT_SHA256", sign)
        self.assertIn("signed-output/FitnessHub-release.apk.sha256", sign)
        self.assertNotRegex(sign, r"path:\s*\|?\s*\$\{\{ runner.temp \}\}\s*$")
        self.assertNotIn("release create", sign)


if __name__ == "__main__":
    unittest.main()
