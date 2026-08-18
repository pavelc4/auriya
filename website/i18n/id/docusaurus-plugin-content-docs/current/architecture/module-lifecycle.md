# Siklus Hidup Modul

Halaman ini menjelaskan seluruh fase siklus hidup modul Auriya mulai dari pembuatan paket di CI/CD, instalasi oleh root manager, proses boot, hingga penghapusan.

## 1. Pembuatan Paket (CI / Build) {#1-package-ci}

Alur kerja GitHub Actions mengompilasi binary Rust aarch64, membangun APK Android (`auriya-app.apk` dan `service.apk`), mengumpulkan skrip shell (`customize.sh`, `service.sh`, `uninstall.sh`), serta file konfigurasi default ke dalam satu arsip ZIP siap-flash: `auriya-<version>.zip`.

## 2. Pemasangan & Ekstraksi (Root Manager) {#2-extract-root-manager}

Saat di-flash melalui Magisk, KernelSU, atau APatch:
- Skrip `module/customize.sh` memverifikasi arsitektur `arm64-v8a`.
- Memverifikasi checksum SHA-256 binary.
- Memasang binary ke `system/bin/` dan APK companion ke `system/etc/auriya/`.
- Memasang APK aplikasi manajer `dev.auriya.app` via `pm install`.
- Menginisialisasi file konfigurasi awal di `/data/adb/.config/auriya/`.

## 3. Eksekusi Saat Boot Perangkat (Boot Runtime) {#3-boot-runtime}

Saat perangkat menyala:
1. Root manager mengeksekusi `service.sh`.
2. `service.sh` menunggu hingga sistem Android selesai memuat (`sys.boot_completed = 1`).
3. Meluncurkan service companion dengan `app_process` di latar belakang.
4. Menjalankan binary daemon Rust `auriya`.

## 4. Siklus Hidup Operasional (Runtime Loop) {#4-runtime-loop}

Daemon beroperasi terus-menerus di latar belakang memproses event dan mengatur performa berdasarkan aktivitas pengguna.

## 5. Penghapusan Modul (Uninstall) {#5-uninstall}

Saat modul dihapus melalui root manager, skrip `module/uninstall.sh` mematikan seluruh proses daemon dan companion, menghapus aplikasi, membersihkan socket dan konfigurasi, lalu menyisakan sistem dalam keadaan bersih.
