# Alur Kerja CI/CD

Auriya menggunakan GitHub Actions untuk pengujian otomatis, linting kode ketat, dan rilis build biner otomatis.

## Inventaris Alur Kerja {#inventory}

1. **Lint & Test (`ci.yml`)**:
   - `cargo clippy --all-targets --all-features -- -D warnings` untuk analisis statis kode Rust.
   - `cargo test` untuk unit & integration test.
   - `./gradlew test lint` untuk pengujian unit Kotlin/Android.
2. **Release Build (`release.yml`)**:
   - Memicu kompilasi silang (*cross-compilation*) binary `auriya` dan `auriyactl` aarch64.
   - Membangun APK rilis tertandatangani (*signed release APKs*).
   - Mengemas file ZIP modul lengkap dan mengunggahnya ke GitHub Releases.

## Menjalankan Validasi Lokal {#local-validation}

Sebelum membuat Pull Request atau commit, pastikan perintah berikut lolos tanpa error:
```bash
cargo fmt --all -- --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
cd android && ./gradlew test
```
