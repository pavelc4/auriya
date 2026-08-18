# Mengompilasi Proyek (Building)

Halaman ini mendokumentasikan instruksi kompilasi source code Auriya untuk Rust daemon dan aplikasi Android Kotlin.

## Prasyarat Lingkungan Build {#prerequisites}

- **Rust & Cargo**: Rust toolchain (stable), target `aarch64-linux-android`.
- **Android NDK**: NDK r26b atau lebih baru untuk cross-compilation native.
- **JDK**: Java Development Kit 17 atau 21.
- **Android SDK**: `cmdline-tools`, `platforms;android-34`, `build-tools;34.0.0`.

## Mengompilasi Daemon Rust {#rust-the-ci-recipe-reproducible}

Target utama daemon adalah arsitektur Android 64-bit (`aarch64-linux-android`):

```bash
# Tambahkan target arsitektur
rustup target add aarch64-linux-android

# Build rilis teroptimasi ukuran
cargo build --release --target aarch64-linux-android
```

Output binary akan dihasilkan di: `target/aarch64-linux-android/release/auriya` dan `target/aarch64-linux-android/release/auriyactl`.

## Mengompilasi Modul Android {#android-gradle-build}

Masuk ke direktori `android/` dan jalankan Gradle:

```bash
cd android

# Build seluruh varian APK (App & Service)
./gradlew assembleRelease
```

Output APK akan berada di `android/app/build/outputs/apk/release/` dan `android/service/build/outputs/apk/release/`.

## Mengemas Modul ZIP Siap Flash {#package-zip}

Setelah binary Rust dan APK selesai di-build, salin seluruh komponen ke struktur folder `module/` lalu kompres menjadi file `.zip`.
