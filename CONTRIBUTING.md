# Contributing to Fitness Hub

Thank you for your interest in contributing to Fitness Hub. This project is built on strict principles of privacy, data ownership, local-first computing, and engineering discipline.

---

## 1. Development Setup

### Prerequisites
- **JDK**: Java Development Kit 17 (Eclipse Temurin 17 recommended).
- **Android SDK**:
  - Compile SDK: `36`
  - Target SDK: `35`
  - Minimum SDK: `28`
  - Android SDK Build-Tools: `35.x` or `36.x`
- **IDE**: Android Studio (Ladybug / Meerkat or newer) or any editor configured with Kotlin and Gradle support.

### Command-Line Verification
Before committing or submitting changes, ensure your local build compiles cleanly, passes all unit tests, and satisfies Android lint checks:

```bash
# Linux / macOS
./gradlew testDebugUnitTest lintDebug assembleDebug

# Windows (PowerShell)
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

---

## 2. Core Engineering Principles

All contributions must strictly respect the rules defined in [AGENTS.md](AGENTS.md):

1. **Local-First & Privacy-First**: No remote backend, authentication server, advertising network, or tracking SDK may be added without explicit approval.
2. **Never Fabricate Health Measurements**:
   - Missing or permission-denied values must be recorded as `null`, never default to `0` or placeholder values.
   - Any derived body composition indices calculated from impedance must be explicitly marked as `ESTIMATE` and include algorithm provenance.
3. **Connector Isolation**: External hardware (e.g., scales, trackers) and external services must reside behind decoupled connector interfaces. Connectors must never write directly to presentation layers.
4. **Idempotency & Data Provenance**: Every sync must be idempotent. Always record the source package or hardware identifier (`dataOrigin`) and preserve original measurement timestamps.
5. **Security & Zero Secrets**: Never commit personal health data exports, API keys, bindkeys, or credentials to version control.
6. **No Health Data in Logs**: System logs must **never** include health values (steps, weight, heart rate, sleep). Log only operation counts, elapsed time, and error types.
7. **Phased Development**: Do not begin implementation of Phase 3 or later phases without explicit project authorization.

---

## 3. Commit Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification to keep the repository history clean and traceable:

- `feat:` A new feature or user-facing enhancement.
- `fix:` A bug fix.
- `docs:` Documentation changes only.
- `refactor:` Code changes that neither fix a bug nor add a feature.
- `test:` Adding missing tests or correcting existing tests.
- `chore:` Toolchain, Gradle configuration, or build workflow updates.

**Example:**
```
feat(healthconnect): add granular permission checks for vitals
fix(sleep): attribute cross-midnight sessions to waking date
docs(s400): document passive BLE protocol and bindkey storage
```

Keep commits atomic, well-described, and self-contained.

---

## 4. Testing Standards

- **JVM Unit Tests**: Implement tests for all business logic, mappers, conflict resolution strategies, and calculation engines using **JUnit 5** and **mockito-kotlin**.
- Avoid tests with no assertions or synthetic logic. Validate realistic edge cases (such as midnight daylight-saving transitions, partial permission denials, and empty dataset handling).

---

## 5. Licensing & Contributor Agreement (CLA) Notice

- The repository's formal license is currently **TBD** (intended model: source-available, free for personal and non-commercial use, with separate commercial licensing).
- Until an explicit license file is committed to the repository, all rights remain reserved by default.
- External code contributions will likely require signing a **Contributor License Agreement (CLA)** to ensure that contributions can be distributed under the project's eventual licensing framework.
