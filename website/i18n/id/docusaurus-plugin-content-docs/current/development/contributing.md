# Panduan Kontribusi

Terima kasih atas minat Anda berkontribusi pada pengembangan Auriya! Halaman ini menjelaskan standar kode dan alur kerja pengembangan.

## Sebelum Memulai {#before-you-start}

- Pastikan Anda memahami struktur modul (daemon Rust, companion Android, dan Compose app).
- Pastikan lingkungan pengembangan Rust (`nightly` atau `stable`) dan Android SDK telah terpasang.

## Validasi Sebelum Mengirim Kode {#validate-before-submitting}

Sebelum membuat pull request, jalankan serangkaian pengujian:
```bash
cargo fmt --all
cargo clippy --all-targets --all-features -- -D warnings
cargo test
cd android && ./gradlew test
```

## Konvensi Commit & Kode {#commit-conventions}

- Gunakan format **Conventional Commits**: contoh `feat(daemon): add new sysfs node support` atau `fix(app): correct dark theme color`.
- Gunakan identasi 4-spasi.
- Ikuti konvensi penamaan standar Rust (`snake_case` fungsi, `CamelCase` struct/enum) dan Kotlin (`PascalCase` composables, `camelCase` method).
