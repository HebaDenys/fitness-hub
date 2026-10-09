# Private-key APK signing preparation

Status: inactive until explicit activation and verified setup. This pipeline does not enable Xiaomi cloud, add INTERNET, change application ID, or access account credentials.

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

Do not keep signing-secret copies at repository scope. GitHub expressions can otherwise fall back to repository secrets. The guard requires all three names at environment scope and none at repository scope; it never retrieves secret values.

## Verification and current limitation
The separate Verify signing setup workflow runs on relevant main pushes or manual dispatch. It has no environment declaration, secret references or signing step. It can discover the environment ID and validate documented environment/reviewer/branch metadata without creating an environment.

Secret-name APIs require permissions that the ordinary GITHUB_TOKEN may not possess. The read-only setup check reports that limitation explicitly; a private signing run fails closed if it cannot verify scope. A green setup job with a scope warning is NOT signing readiness. Do not add an administrator PAT or weaken the guard to make it green. Resolve the verification design before enabling signing.

The owner reported moving the three secrets into Dev Env and deleting repository copies; this is not yet independent API verification. Values are never echoed, downloaded or recorded in this repository.

## Build and isolated signing
The public Android workflow runs Python policy tests, shared debug unit tests, lint/debug build and unsigned non-debuggable release validation without private secrets. Gradle has a read-only token. Public TEST-key publication runs in a separate main-only job.

The private workflow is manual, main-only and default-off. It requires explicit acknowledgement that the resulting APK Actions artifact belongs to this PUBLIC repository. This is not private distribution. Build/test runs without the environment or private secrets. A fresh signing job has no checkout, repository scripts, Gradle or cache; it downloads the exact same-run immutable artifact ID, checks provenance/digest/source SHA and actual APK metadata, rechecks environment policy, then signs using environment-only secrets. Passwords use apksigner env inputs. Temporary JKS/alignment/verification files are removed; only exact APK, checksum and public provenance paths are uploaded. No private key is an artifact.

The signing guard checks current main HEAD, not merely a mutable ref supplied by an operator. Pinned actions and fixed SDK build-tools 36.0.0 reduce drift. Environment metadata checks are not atomic and cannot protect against a malicious repository administrator editing the workflow.

## Installation and remaining gates
A private certificate differs from the public TEST certificate. Android cannot install it as an update over the existing package. Do not uninstall or delete local data to get around this; a separate app identity or explicit backup/migration decision is required. That app-identity choice and Xiaomi network integration are separate increments.

No signed private APK, credential validity, real Xiaomi login, device installation, release UI tests or pixel-level QA is claimed by this preparation.

References:
- https://docs.github.com/en/rest/deployments/environments#get-an-environment
- https://docs.github.com/en/rest/deployments/branch-policies#list-deployment-branch-policies
- https://docs.github.com/en/rest/actions/secrets#list-environment-secrets
- https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments
