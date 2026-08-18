# Komponen

Arsitektur Auriya dibagi menjadi beberapa komponen utama yang bekerja secara terkoordinasi di seluruh lingkungan Android dan ruang pengguna (userspace).

## Manajer Android — `android/app/` {#android-manager}

Aplikasi Android berbasis Jetpack Compose yang menyediakan antarmuka pengguna untuk memantau status sistem dan mengubah konfigurasi:
- **UI Compose:** Manajemen konfigurasi profil game, pengaturan daemon global, pengubah governor, dan pemantau telemetri real-time.
- **Root Shell Bridge:** Menulis file konfigurasi ke `/data/adb/.config/auriya/` dan mengirim sinyal IPC ke daemon.
- **Komponen Sistem Tambahan:** Quick Settings Tile, Widget status layar utama, dan overlay monitor performa in-game.

## Companion Service — `android/service/` {#companion-service}

Service latar belakang mandiri tanpa UI yang dijalankan melalui binary `app_process` Android:
- **Sensor Status Sistem:** Memantau aplikasi yang sedang aktif di foreground, status layar menyala/mati, dan status penghemat baterai.
- **Eksekutor Perintah Android:** Mengubah refresh rate layar dan mode Jangan Ganggu (Zen Mode / DnD) atas instruksi daemon.
- **Snapshot Status:** Menulis snapshot status berkala ke `/data/adb/.config/auriya/system_status`.

## Daemon Rust — `src/main.rs`, `src/daemon/` {#rust-daemon}

Inti pengambil keputusan performa yang berjalan dengan hak akses root penuh:
- **Event Loop Tokio:** Memproses event timer, perubahan file konfigurasi, dan sinyal IPC.
- **Penjadwal Profil (Profile Scheduler):** Menentukan profil yang sesuai (`Performance`, `Balance`, `Powersave`) berdasarkan aplikasi foreground.
- **Frame-Aware Scheduling (FAS):** Mengukur frame rate dan waktu render via uprobe eBPF Kala atau fallback sysfs untuk penyesuaian dinamis.
- **Lapisan Tweak Kernel:** Menerapkan modifikasi governor CPU, mode GPU, afinitas core, cache, dan kontrol termal.

## Kontrol CLI — `src/ctl.rs` (`auriyactl`) {#control-cli}

Binary baris perintah ringan untuk mengontrol daemon secara langsung melalui terminal root atau script otomatisasi tanpa perlu membuka antarmuka grafis.
