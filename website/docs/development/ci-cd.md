# CI/CD Workflows

Verified against Auriya commit `10fe7c6b56474a00513fec34ebac1376b30e95e6`. Workflow/action references below point to that revision. Re-verify this page after changing `.github/workflows/`, `.github/actions/`, `Cargo.toml`, Android output names, or module layout.

## Inventory

| File | Trigger |
| --- | --- |
| `.github/workflows/build.yml` | `workflow_dispatch` only. No branch, tag, or path filter. |
| `.github/workflows/release.yml` | Push of any tag matching `v*`, or `workflow_dispatch`. No branch/path filter. |

There is no `pull_request`, branch-push, schedule, release-event, or matrix workflow. `.github/dependabot.yml` is separate weekly dependency-update automation for Cargo `/`, Gradle `/android`, and GitHub Actions `/`; it does not execute either workflow.

## Shared composite actions

### `setup-tools`

Every invocation executes the same sequence ([source](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/.github/actions/setup-tools/action.yml)):

1. `actions/setup-java@v4`: Temurin Java 26.
2. `nttld/setup-ndk@v1`: Android NDK r29, added to `PATH` and exposed through the action's environment.
3. `dtolnay/rust-toolchain@nightly`: nightly Rust, target `aarch64-linux-android`, component `rust-src`.
4. `rustup default nightly-x86_64-unknown-linux-gnu`.
5. `curl -sL <bpf-linker-v0.11.0.tar.zst> | tar --zstd -x -C "${HOME}/.cargo/bin"`.
6. `cargo install cargo-ndk --locked`.
7. `sudo apt-get update`, then `sudo apt-get install -y p7zip-full zstd`.

Failure of any command/action stops the current job. Downloads and package installation access external systems. The `curl` pipeline does not enable `pipefail`, so a failed download can be masked if `tar` exits successfully.

### `package-module`

The composite action runs one Bash block with `set -e` ([source](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/.github/actions/package-module/action.yml)):

1. Creates `build/release/module` and copies `module/*` into it.
2. Copies repository-root `settings.toml` and `gamelist.toml`; failures are explicitly ignored with `|| true`.
3. Attempts to copy `module/system`; failure is ignored.
4. Searches the downloaded artifacts for the manager APK in this order: exact `app-arm64-v8a-release.apk`, any `*-arm64-v8a-*.apk`, then any APK under an `app` path.
5. Searches for exact `service-release.apk`, then any APK under a `service` path.
6. Missing APKs print warnings but do not fail packaging. Found APKs become `libs/companion/auriya-app.apk` and `libs/companion/service.apk`.
7. Requires `target/aarch64-linux-android/release/auriya`; absence exits `1`. `auriyactl` is optional and only produces a warning when absent.
8. Copies binaries into `libs/aarch64/` and runs `sha256sum * > checksums.sha256` there.
9. Reads `VERSION` from the first `version =` line in `Cargo.toml`, `COMMIT_HASH` from `git rev-parse --short HEAD`, and `VERSION_CODE` from `git rev-list --count HEAD`.
10. Rewrites `module.prop` version fields, removes `.placeholder` files, then runs `7z a -tzip -mm=Deflate -mx=9 -mfb=258 -mpass=15` from inside the module staging directory.
11. Exposes `zip_name`, `version`, and `version_code` through `$GITHUB_OUTPUT`.

Output name: `auriya-<Cargo version>-<git commit count>-<short SHA>-<build_type>.zip`. The input is `debug` in `build.yml` and `release` in `release.yml`; both workflows still compile Rust and Android with release build commands.

### `telegram-notify`

The action reads the current commit subject, HTML-escapes `&`, `<`, and `>`, then chooses a Telegram Bot API request from `NOTIFY_TYPE` ([source](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/.github/actions/telegram-notify/action.yml)):

- `start`: `curl -s -X POST .../sendMessage`; parses `message_id` with `grep`/`cut` and writes it only when found.
- `complete`: exits successfully when the ZIP path is empty/missing; otherwise `curl -s -X POST .../sendDocument` with the ZIP and caption.
- `failure`: `curl -s -X POST .../sendMessage` with a failure message.
- When `DELETE_MSG_ID` is non-empty, calls `deleteMessage`; errors are ignored with `|| true`.

**High-risk external side effect:** these calls send messages/documents and delete messages in Telegram. `curl` uses `-s` without `-f`, and the JSON response is not validated, so an HTTP/API rejection can leave the step green. The action also prints the full Telegram JSON response to the Actions log.

## `build.yml`

**Trigger:** manual `workflow_dispatch` only ([source](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/.github/workflows/build.yml)). Workflow permission is `contents: read`.

### Job DAG

```mermaid
flowchart LR
    setup["setup"]
    rust["rust-binary"]
    apk["android-apk"]
    pkg["package"]
    notify["notify"]

    setup --> rust
    setup --> apk
    rust --> pkg
    apk --> pkg
    setup -.-> notify
    pkg --> notify
```

`rust-binary` and `android-apk` run in parallel after `setup`. `package` requires both. `notify` declares `needs: [setup, package]` and `if: !cancelled()`, so it is allowed to start after a dependency failure/skipped result unless the run was cancelled.

| Job | Runner | Needs | Actual purpose |
| --- | --- | --- | --- |
| `setup` | `ubuntu-latest` | none | optional start notification, writes Git credentials, verifies the shared tool setup can complete |
| `rust-binary` | `ubuntu-latest` | `setup` | cross-compiles and strips two arm64 Android Rust binaries |
| `android-apk` | `ubuntu-latest` | `setup` | checks out private signing material and builds two signed release APKs |
| `package` | `ubuntu-latest` | `rust-binary`, `android-apk` | downloads both artifact sets and builds the flashable ZIP |
| `notify` | `ubuntu-latest` | `setup`, `package` | sends success ZIP or failure notification when a start message exists |

### Steps per job

#### `setup`

1. `actions/checkout@v7` with full history (`fetch-depth: 0`). This full history is local to `setup`; jobs run on separate runners and do not inherit its checkout or credential file.
2. If `BOT_TOKEN` is non-empty, invoke `telegram-notify` with `type: start`; its output becomes `notify_message_id`.
3. Execute `git config --global credential.helper store`, then write `https://pavelc4:${GH_PAT}@github.com` to `~/.git-credentials`.
4. Execute all `setup-tools` steps listed above.

The credential file contains `GH_PAT` in plaintext for the lifetime of the hosted runner. This is a high-risk credential side effect outside the repository checkout.

#### `rust-binary`

1. Checkout source with the action default (the workflow does not set `fetch-depth`, so the runner receives a shallow checkout).
2. Execute `setup-tools`.
3. Restore/save `~/.cargo/registry` and `~/.cargo/git` with exact key `cargo-${hashFiles('Cargo.lock')}` and fallback prefix `cargo-`.
4. Set `TARGET=aarch64-linux-android`.
5. Replace `/opt/android-ndk` in `.cargo/config.toml` with `${ANDROID_NDK_HOME}`.
6. Run `cargo ndk -t aarch64-linux-android --platform 26 -- build --release --bin auriya --bin auriyactl`.
7. Strip both binaries with NDK `llvm-strip` when that file exists; otherwise use host `strip`.
8. Generate separate `.sha256` files with `sha256sum`.
9. Upload artifact `rust-binary` from `target/aarch64-linux-android/release/auriya*`.

#### `android-apk`

1. Checkout Auriya and execute `setup-tools`.
2. Checkout private repository `pavelc4/keystores` into `keystores-private` using `KEYSTORES_SSH_KEY` as the checkout token.
3. Generate `android/signing.properties` containing `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` from secrets.
4. From `android/`, run `chmod +x gradlew` and `./gradlew :app:assembleRelease :service:assembleRelease`.
5. Upload artifact `android-apks` from both modules' `build/outputs/apk/release/*.apk` paths.

#### `package`

1. Checkout source with the action default (shallow checkout).
2. Download `rust-binary` into `target/aarch64-linux-android/release/`.
3. Download `android-apks` into the workspace root.
4. Run `package-module` with `build_type: debug`.
5. Upload `build/release/*.zip` as artifact `auriya-aarch64` with artifact compression disabled (`compression-level: 0`) because the file is already ZIP-compressed.

#### `notify`

1. Checkout source.
2. Only when `package.result == 'success'`, download `auriya-aarch64` into `build/release/`.
3. Only when package succeeded and `notify_message_id` is non-empty, invoke `telegram-notify` with `type: complete`, attach the ZIP, and request deletion of the start message.
4. Only when package did not succeed and `notify_message_id` is non-empty, invoke it with `type: failure` and request deletion of the start message.

If the initial Telegram call returned no parsed message ID, neither final notification step runs.

### Artifacts produced

| Name | Source path | Destination |
| --- | --- | --- |
| `rust-binary` | `target/aarch64-linux-android/release/auriya*` | GitHub Actions artifact store; consumed by `package` |
| `android-apks` | Android app/service release APK output directories | GitHub Actions artifact store; consumed by `package` |
| `auriya-aarch64` | `build/release/*.zip` | GitHub Actions artifact store; downloaded by `notify` and optionally uploaded to Telegram |

### Secrets and environment

Secrets: `BOT_TOKEN`, `CHAT_ID`, `GH_PAT`, `KEYSTORES_SSH_KEY`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`, `KEYSTORE_KEY_PASSWORD`. Environment/context affecting commands: `CARGO_TERM_COLOR`, `ANDROID_NDK_HOME`, `HOME`, `github.workspace`, run/commit/repository URLs.

### Failure behavior

- Failure/cancellation of `setup` prevents both build jobs from starting.
- Failure of either parallel build job prevents `package` under the default success condition.
- Missing daemon binary fails packaging; missing CLI/APKs and missing default TOML copies do not.
- `notify` runs after non-cancellation dependency failure because of `if: !cancelled()`, then selects success/failure behavior from `package.result`.
- Telegram HTTP/API failure may remain green as described above.
- There is no workflow tied to pull requests, so this file alone does not block merges unless repository rules invoke it manually or require an external check not present here.

## `release.yml`

**Trigger:** a pushed tag matching `v*`, or manual `workflow_dispatch`. Permission is `contents: write` ([source](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/.github/workflows/release.yml)).

### Job DAG

```mermaid
flowchart LR
    setup["setup"]
    rust["rust-binary"]
    apk["android-apk"]
    pkg["package"]
    rel["release"]

    setup --> rust
    setup --> apk
    rust --> pkg
    apk --> pkg
    setup -.-> rel
    pkg --> rel
```

The first four jobs execute the same commands and dependencies as `build.yml`, except packaging receives `build_type: release`. The final job is named `release`, needs `setup` and `package`, and uses `if: !cancelled()`.

| Job | Runner | Needs | Actual purpose |
| --- | --- | --- | --- |
| `setup` | `ubuntu-latest` | none | start notification, credential setup, tool installation |
| `rust-binary` | `ubuntu-latest` | `setup` | build/strip/checksum arm64 Rust binaries |
| `android-apk` | `ubuntu-latest` | `setup` | build signed release APKs |
| `package` | `ubuntu-latest` | both build jobs | build release-labelled module ZIP |
| `release` | `ubuntu-latest` | `setup`, `package` | publish GitHub Release asset, push `update.json`, notify Telegram |

### Steps per job

`setup`, `rust-binary`, and `android-apk` execute the same ordered commands documented for `build.yml`. `package` also matches except `package-module` receives `build_type: release` and exports `zip_name`, `version`, and `version_code`.

#### `release`

1. Checkout full history and download `auriya-aarch64` into `build/release/`.
2. Resolve tag: for a tag-triggered run, write `github.ref_name`; for manual dispatch, require a non-empty packaged Cargo version and synthesize `v<VERSION>`.
3. **High-risk publish:** `softprops/action-gh-release@v3` creates/updates that GitHub Release and uploads the exact packaged ZIP using `GITHUB_TOKEN`.
4. Build variables from package outputs. Extract up to 20 non-empty lines from the first version section of `CHANGELOG.md` through `awk`, multiple `sed` filters, `head`, and `jq`; the resulting `CHANGELOG` variable is calculated but never inserted into `update.json`.
5. Overwrite `update.json` with version, numeric commit-count versionCode, release asset URL, and the raw `main/CHANGELOG.md` URL.
6. Configure the bot identity, stage `update.json`, commit it (commit failure is suppressed with `|| echo "No changes"`), then **high-risk publish** with `git push origin HEAD:main`.
7. On normal step success and non-empty initial message ID, send the ZIP to Telegram with a custom release caption and delete the start message.
8. If an earlier step in this job failed and the message ID is non-empty, send the failure message and delete the start message.

### Artifacts produced

The intermediate Actions artifacts are identical to `build.yml`. The final ZIP is additionally uploaded as a GitHub Release asset under the resolved tag. `update.json` is committed and pushed directly to branch `main`. Telegram receives the same ZIP only when the initial notification produced a message ID.

### Secrets and environment

All `build.yml` secrets plus `GITHUB_TOKEN`. `contents: write` is required for the release asset and repository push. Cache paths and key are identical to `build.yml`.

### Failure behavior

- Setup/build/package blocking behavior matches `build.yml`.
- Because `release` uses `if: !cancelled()`, a failed/skipped package can still start the job; artifact download or empty outputs then fail it.
- Tag resolution fails only for manual dispatch with an empty package version.
- Release upload failure blocks `update.json`, success notification, and produces the conditional failure notification when a message ID exists.
- `git commit` failure is suppressed, but `git push` failure is not and makes the job red.
- Telegram API rejection can remain green because the composite action does not validate it.
- A successful run changes external state: GitHub Release assets, branch `main`, `update.json`, and Telegram messages/documents. Review those steps before modifying permissions, tags, artifact names, or notification inputs.

## Reproduction checklist

There are no matrix combinations. The only compiled target/configuration is `aarch64-linux-android`, Android API 26, Rust `--release`, and Gradle `assembleRelease` for `app` plus `service`. The workflow's `package` checkout does not request full history; with the default shallow checkout, `git rev-list --count HEAD` normally produces the shallow history count (typically `1`), not the repository's lifetime commit count. A manual reproduction that needs a full historical commit count must explicitly use `git fetch --unshallow` before running `package-module`, which will change the generated `versionCode` and ZIP filename from the workflow result.
