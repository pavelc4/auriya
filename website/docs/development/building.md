# Building

How to build each part of Auriya. The Rust build has a **repo-specific gotcha**
you must know before running any cargo command.

:::info Verified against source
Traced to commit `10fe7c6`:
[`Cargo.toml`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/Cargo.toml),
[`.cargo/config.toml`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/.cargo/config.toml),
`android/*/build.gradle.kts`, `website/package.json`. The authoritative CI recipe
is [CI/CD workflows](ci-cd).
:::

## Cargo is pinned to cross-compile for Android

`.cargo/config.toml` sets:

```toml
[build]
target = "aarch64-linux-android"

[target.aarch64-linux-android]
linker = "/opt/android-ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang"
runner = "bash .cargo/adb-runner.sh"
```

Consequences for a first-time contributor:

- **`cargo build` does not build for your host.** It cross-compiles an
  `aarch64-linux-android` binary and needs an Android NDK at the **hardcoded
  path** `/opt/android-ndk`. Without that NDK, the build fails at the linker.
- **`cargo test` runs on a device.** The `runner` ships each test binary to a
  connected device over adb (`.cargo/adb-runner.sh`) — tests execute on real
  hardware, not your workstation. Several tests (`pid_tracker`, `cmd_writer`)
  assume a real Android/Linux environment.
- CI installs the NDK and uses `cargo ndk` rather than relying on this hardcoded
  path — see below.

To build for your host instead (e.g. to run a unit test locally), override the
target explicitly:

```bash
cargo build --target x86_64-unknown-linux-gnu
cargo test  --target x86_64-unknown-linux-gnu
```

(Device-only tests will not be meaningful on the host.)

## Rust — the CI recipe (reproducible)

The exact command CI uses to produce the shipped binaries
([CI/CD → rust-binary](ci-cd#rust-binary)):

```bash
# Requires: Android NDK, Rust nightly with target aarch64-linux-android,
#           rust-src, and cargo-ndk.
cargo ndk -t aarch64-linux-android --platform 26 -- build --release --bin auriya --bin auriyactl
```

`cargo ndk` supplies the NDK toolchain paths, sidestepping the hardcoded
`/opt/android-ndk` in `.cargo/config.toml`. The release profile is size-optimized
(`Cargo.toml`): `opt-level = "z"`, `lto = "fat"`, `codegen-units = 1`,
`panic = "abort"`, `strip = true`.

:::note Nightly toolchain
CI uses **nightly** Rust with `edition = "2024"` and installs `rust-src`
([CI/CD → setup-tools](ci-cd#setup-tools)). The Kala eBPF dependency is a git
dependency (`Cargo.toml`); its own eBPF object is prebuilt, so you do not need
`bpf-linker` to build Auriya
([Kala eBPF frame probe](../internals/kala-research#auriya-integration)).
:::

## Lints and formatting

The repo enforces strict lints (`Cargo.toml` `[lints]`):

```bash
cargo fmt --all -- --check                                  # formatting
cargo clippy --all-targets --all-features -- -D warnings    # deny-level lints
```

`clippy` groups `all`/`correctness`/`suspicious`/`perf`/`complexity` are set to
**deny**; `style` is `warn`. Rust `unsafe_op_in_unsafe_fn` and `unused_must_use`
are denied.

## Android

Two Gradle modules produce the APKs (`android/app`, `android/service`;
`minSdk = 30` / Android 11, `android/app/build.gradle.kts:54`). Signed release
builds need `android/signing.properties` (template:
`android/signing.properties.example` — never commit real keys):

```bash
cd android
./gradlew build                                    # all modules, debug + release
./gradlew :app:assembleRelease :service:assembleRelease   # what CI builds
./gradlew test                                     # JVM unit tests
```

## Documentation site

The website uses **Bun** (`website/bun.lock`, `website/package.json`):

```bash
cd website
bun install
bun run start     # local dev server with live reload
bun run build     # static production build into website/build
```

## Full pipeline

The complete trigger, job DAG, per-step commands, artifacts, secrets, cache
keys, failure behavior, and external side effects are documented in
[CI/CD workflows](ci-cd).
