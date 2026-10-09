# Private-key APK signing preparation

Status: inactive until explicit activation and verified setup. Release 0.3.17 enables the owner-approved Xiaomi adapter only behind the actual private-certificate/non-debuggable gate. The public debug build remains offline. No account credentials are accessed by CI.

## Agreed environment
The owner selected the existing environment **Dev Env**. Required reviewer: only HebaDenys (user ID 99826912); self-review allowed; selected deployment policy: exactly branch main, no tags or wildcard. Administrator bypass is explicitly allowed by the owner. Normal reviewer approval is therefore not an absolute barrier to an administrator. The API guard does not claim to verify or disable bypass.

The environment must already exist. Pin its observed numeric ID in repository variable FITNESS_HUB_RELEASE_ENVIRONMENT_ID before activation; recreation or identity mismatch fails closed. Repository variable FITNESS_HUB_PRIVATE_SIGNING_ENABLED must stay unset or false until setup is verified and a signing run is approved.

Environment secrets (values entered by the owner only):
- FITNESS_HUB_RELEASE_KEYSTORE_B64
- FITNESS_HUB_RELEASE_STORE_PASSWORD
- FITNESS_HUB_RELEASE_KEY_PASSWORD

Environment variables:
- FITNESS_HUB_RELEASE_KEY_ALIAS (owner-created JKS alias)
- FITNESS_HUB_RELEASE_CERT_SHA256 (public SHA-256 fingerprint, case/colon/space normalized)

Do not keep signing-secret copies at repository scope. GitHub expressions can otherwise fall back to repository secrets. When the name APIs are accessible, the guard verifies all three names at environment scope and none at repository scope. When the token receives 401/403, the owner attests that configuration. GitHub secret expressions can fall back; this workflow cannot programmatically prove absence of that fallback without metadata permission. It never retrieves secret values for verification.

## Verification and current limitation
The separate Verify signing setup workflow runs on relevant main pushes or manual dispatch. It has no environment declaration, secret references or signing step. It can discover the environment ID and validate documented environment/reviewer/branch metadata without creating an environment.

Secret-name APIs require permissions that the ordinary GITHUB_TOKEN may not possess. The read-only setup check reports that limitation explicitly; the owner must explicitly attest environment-only scope at each dispatch when metadata access is denied. This exception covers only secret-name metadata 401/403, not invalid environment/reviewer/ref/SHA, missing key, bad certificate or artifact mismatch. The attestation is bound to actor HebaDenys, the exact source SHA and numeric environment ID, actor ID 99826912, owner triggering actor and first run attempt (reruns require a fresh dispatch), and is labelled owner-attested, never API-verified. A green setup job with a scope warning is not proof of secret scope. Do not add an administrator PAT.

The owner reported moving the three secrets into Dev Env and deleting repository copies. CI setup 37865632700 independently verified environment ID 23832844662, reviewer and main-only policy; it could not verify secret-name scope. Each manual signing dispatch must reaffirm the owner verification. Values are never echoed, downloaded or recorded in this repository.

## Build and isolated signing
The public Android workflow runs Python policy tests, shared debug unit tests, lint/debug build and unsigned non-debuggable release validation without private secrets. Gradle has a read-only token. Public TEST-key publication runs in a separate main-only job.

The private workflow is manual, main-only and default-off. It requires explicit acknowledgement that the resulting APK Actions artifact belongs to this PUBLIC repository. This is not private distribution. Build/test runs without the environment or private secrets. A fresh signing job has no checkout, repository scripts, Gradle or cache; it downloads the exact same-run immutable artifact ID, checks provenance/digest/source SHA and actual APK metadata, rechecks environment policy, then signs using environment-only secrets. Passwords use apksigner env inputs. Temporary JKS/alignment/verification files are removed; only exact APK, checksum and public provenance paths are uploaded. No private key is an artifact.

The signing guard checks current main HEAD, not merely a mutable ref supplied by an operator. Pinned actions and fixed SDK build-tools 36.0.0 reduce drift. Environment metadata checks are not atomic and cannot protect against a malicious repository administrator editing the workflow.

## Installation and remaining gates
A private certificate differs from the public TEST certificate. Android cannot install it as an update over the existing package. Do not uninstall or delete local data to get around this; a separate app identity or explicit backup/migration decision is required. The owner chose the same application ID and accepted loss of disposable test data. They perform removal themselves if Android reports a signature conflict.

No signed private APK, credential validity, real Xiaomi login, device installation, release UI tests or pixel-level QA is claimed by this preparation.

References:
- https://docs.github.com/en/rest/deployments/environments#get-an-environment
- https://docs.github.com/en/rest/deployments/branch-policies#list-deployment-branch-policies
- https://docs.github.com/en/rest/actions/secrets#list-environment-secrets
- https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments

## Manual run
Set repository variables FITNESS_HUB_RELEASE_ENVIRONMENT_ID=23832844662 and FITNESS_HUB_PRIVATE_SIGNING_ENABLED=true only when the owner is ready. Open Actions → Private-key APK signing → Run workflow on main. Supply the exact reviewed main SHA and environment ID, confirm the public APK artifact and the environment-only secret scope. GitHub then requests the owner review for Dev Env. Self-approval and administrator bypass are owner-approved settings. A dispatch/review is not evidence that the resulting signature or Xiaomi login succeeded; check terminal CI and artifact certificate.
