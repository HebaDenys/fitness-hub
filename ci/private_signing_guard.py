"""Fail-closed preflight for the inactive, manually approved signing workflow.

This validates documented API fields only. The owner explicitly permits administrator bypass;
that UI setting is not exposed or monitored by this guard.
No environment, variable, secret or branch setting is created or changed.
"""
import json
import os
import re
import urllib.error
import urllib.request
import urllib.parse

REPOSITORY = "HebaDenys/fitness-hub"
ENVIRONMENT = "Dev Env"
OWNER_ID = 99826912
REQUIRED_SECRETS = {"FITNESS_HUB_RELEASE_KEYSTORE_B64", "FITNESS_HUB_RELEASE_STORE_PASSWORD", "FITNESS_HUB_RELEASE_KEY_PASSWORD"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_environment(environment, policies, expected_id):
    require(type(expected_id) is int and expected_id > 0, "Pin the approved environment ID")
    require(type(environment.get("id")) is int and environment.get("id") == expected_id and environment.get("name") == ENVIRONMENT,
            "Environment identity mismatch")
    policy = environment.get("deployment_branch_policy") or {}
    require(policy.get("custom_branch_policies") is True and policy.get("protected_branches") is False,
            "Require selected deployment branches, not protected-branches-only")
    rules = environment.get("protection_rules")
    require(isinstance(rules, list), "Missing protection rules")
    require(all(rule.get("type") in {"required_reviewers", "branch_policy"} for rule in rules),
            "Unexpected protection rule; review configuration")
    reviewers = [rule for rule in rules if rule.get("type") == "required_reviewers"]
    require(len(reviewers) == 1, "Exactly one required-reviewers rule is required")
    rule = reviewers[0]
    require(rule.get("prevent_self_review") is False, "Self-review policy differs from approved solo-owner setup")
    entries = rule.get("reviewers")
    require(isinstance(entries, list) and len(entries) == 1, "Only the approved reviewer is allowed")
    require(entries[0].get("type") == "User" and entries[0].get("reviewer", {}).get("id") == OWNER_ID,
            "Reviewer is not the approved repository owner")
    require(type(policies.get("total_count")) is int and policies["total_count"] == 1,
            "Exactly one deployment branch policy is required")
    branches = policies.get("branch_policies")
    require(isinstance(branches, list) and len(branches) == 1, "Incomplete or extra branch policies")
    require(branches[0].get("name") == "main" and branches[0].get("type") == "branch",
            "Require exactly the main branch; missing type, tags and wildcards are rejected")


def secret_names(metadata):
    entries = metadata.get("secrets")
    require(isinstance(entries, list) and type(metadata.get("total_count")) is int, "Incomplete secret-name metadata")
    require(metadata["total_count"] == len(entries) and len(entries) <= 100, "Secret-name pagination is incomplete")
    names = [entry.get("name") for entry in entries]
    require(all(isinstance(name, str) for name in names) and len(set(names)) == len(names), "Malformed secret-name metadata")
    return set(names)


def validate_secret_scopes(environment_secrets, repository_secrets):
    require(secret_names(environment_secrets) == REQUIRED_SECRETS,
            "The three signing secrets must exist in the dedicated environment")
    require(REQUIRED_SECRETS.isdisjoint(secret_names(repository_secrets)),
            "Repository-level signing-secret copies must be removed; fallback is prohibited")


def validate_context(context, branch, require_activation=True):
    require(context.get("GITHUB_REPOSITORY") == REPOSITORY, "Unexpected repository")
    allowed = {"workflow_dispatch"} if require_activation else {"workflow_dispatch", "push"}
    require(context.get("GITHUB_EVENT_NAME") in allowed, "Unexpected workflow event")
    require(context.get("GITHUB_REF") == "refs/heads/main", "Only refs/heads/main is trusted")
    if require_activation:
        require(context.get("SIGNING_ENABLED") == "true", "Private signing is inactive")
    sha = context.get("GITHUB_SHA", "")
    require(re.fullmatch(r"[0-9a-f]{40}", sha) is not None, "Invalid source SHA")
    require(branch.get("name") == "main" and branch.get("commit", {}).get("sha") == sha,
            "The requested commit is no longer main HEAD")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


def read_json(path):
    token = os.environ["GH_TOKEN"]
    request = urllib.request.Request(
        "https://api.github.com/repos/" + REPOSITORY + "/" + path,
        headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28"},
    )
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=20) as response:
        require(response.status == 200, "Metadata API did not return 200")
        payload = response.read(1_048_577)
        require(len(payload) <= 1_048_576, "Metadata response exceeds limit")
        return json.loads(payload)


def verify_setup(require_activation=True):
    if require_activation:
        require(os.environ.get("SIGNING_ENABLED") == "true", "Private signing is inactive")
    raw_id = os.environ.get("EXPECTED_ENVIRONMENT_ID", "")
    if require_activation or raw_id:
        require(re.fullmatch(r"[1-9][0-9]*", raw_id) is not None, "Approved environment ID is missing")
    branch = read_json("branches/main")
    validate_context(os.environ, branch, require_activation)
    environment = read_json("environments/" + urllib.parse.quote(ENVIRONMENT, safe=""))
    policies = read_json("environments/" + urllib.parse.quote(ENVIRONMENT, safe="") + "/deployment-branch-policies?per_page=100")
    observed_id = environment.get("id")
    validate_environment(environment, policies, int(raw_id) if raw_id else observed_id)
    if not require_activation:
        print("Environment ID:", observed_id)
        if not raw_id:
            print("Pin FITNESS_HUB_RELEASE_ENVIRONMENT_ID before activation.")
        print("Read-only setup check: secret values and environment variables were not accessed.")
    try:
        validate_secret_scopes(
            read_json("environments/" + urllib.parse.quote(ENVIRONMENT, safe="") + "/secrets?per_page=100"),
            read_json("actions/secrets?per_page=100"),
        )
        print("Environment-only secret NAMES verified; no values were retrieved.")
    except urllib.error.HTTPError as error:
        if not require_activation and error.code in (401, 403):
            print("::warning::Secret-name scope could not be verified with this read-only token. Signing must remain disabled pending manual review; no fallback is allowed.")
        else:
            raise
    print("Reviewer, environment identity and exact main-branch rules verified.")
    print("Administrator bypass is owner-approved; no API enforcement is claimed.")


if __name__ == "__main__":
    try:
        verify_setup(require_activation=os.environ.get("SETUP_CHECK_ONLY") != "true")
    except (ValueError, KeyError, TypeError, urllib.error.URLError) as error:
        reason = str(error) if type(error) is ValueError else type(error).__name__
        raise SystemExit("Signing preflight blocked: " + reason) from None
