# Contributing

Guidelines for changing Auriya. These mirror the repository's `AGENTS.md`
(Repository Guidelines) — that file is the canonical short form; this page adds
the doc-site cross-links.

## Before you start

- Read [Project structure](project-structure) and [Components](../architecture/components)
  so a change lands in the right module. Keep platform-specific behavior in its
  existing module; don't duplicate config or shell-handling helpers.
- For daemon changes, trace the real flow first — [Profile scheduler](../internals/profile-scheduler),
  [IPC protocol](../internals/ipc-protocol), and the relevant internals page.

## Validate before submitting

Run the checks that match what you touched:

```bash
# Rust  (note: cargo cross-compiles by default here — see Building)
cargo fmt --all -- --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test

# Android
cd android && ./gradlew test
```

The clippy configuration is **deny-level** for correctness/perf/complexity groups
([Building → lints](building#lints-and-formatting)). CI runs the full build; see
[CI/CD workflows](ci-cd). Mind the [cargo cross-compile gotcha](building#cargo-is-pinned-to-cross-compile-for-android)
when running tests locally.

## Coding style

- **Rust**: `rustfmt` defaults, four-space indent. `snake_case` functions/modules,
  `CamelCase` types, `SCREAMING_SNAKE_CASE` constants.
- **Kotlin**: four-space indent, `PascalCase` types/composables, `camelCase`
  members.

## Testing

Put focused Rust tests beside the code they exercise (or under `tests/` for
integration behavior), named after the behavior they verify — e.g.
`parses_invalid_game_profile`. Android changes should include Gradle tests when
the behavior is testable off-device. Note some Rust tests are **device tests**
(they run over adb via the configured `runner`) — see
[Building](building#cargo-is-pinned-to-cross-compile-for-android).

## Commits and pull requests

- Use imperative, concise **Conventional Commit** subjects with a scope when
  useful: `fix(daemon): …`, `feat(app): …`, `chore(deps): …`.
- PRs should explain the behavior change, list the validation commands you ran,
  link the issue when applicable, and include screenshots or device details for
  UI/Android-runtime changes.
- Keep generated artifacts and unrelated formatting churn out of the diff.
- **If you change config schema, IPC, CLI, or system nodes, update the matching
  wiki page** — the reference and internals pages are source-traced and cite line
  numbers; each carries a "Likely to drift first" note listing what to re-verify.

## Security and trust boundaries

Treat root permissions, `/proc` and `/sys` writes, the Unix socket, `mount`
operations ([vendor lock](../internals/system-tweaks#vendor-lock--stopping-vendor-services-from-fighting-back)),
and shell commands as **trust boundaries**. Validate inputs and preserve
least-privilege behavior. Never commit signing keys, device-specific paths, or
real credentials — use `android/signing.properties.example` as the template.
