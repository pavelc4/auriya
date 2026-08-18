# Instalasi

Auriya dikemas sebagai satu file ZIP modul yang dapat di-flash. ZIP ini berisi **seluruh kebutuhan** — daemon, CLI, kedua APK (aplikasi dan service), serta konfigurasi default — sehingga tidak ada yang diunduh saat boot. Lihat [Siklus Hidup Modul](../architecture/module-lifecycle) untuk detail pengemasan.

:::info Terverifikasi dari Source Code
Perilaku instalasi diverifikasi langsung dari
[`module/customize.sh`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/module/customize.sh).
:::

## Langkah-Langkah {#steps}

1. **Unduh** file `auriya-*.zip` terbaru dari [halaman rilis](https://github.com/pavelc4/auriya/releases). Anda **tidak** perlu mengunduh APK terpisah — aplikasi manajer sudah dikemas di dalam ZIP.
2. **Flash** file ZIP melalui root manager Anda (Magisk / KernelSU / APatch).
3. **Perhatikan output installer.** Skrip `customize.sh` akan mencetak info perangkat dan log setiap langkah.
4. **Reboot** perangkat saat diminta.
5. **Buka Auriya** dari launcher aplikasi dan berikan izin root saat diminta (lihat [Penggunaan Pertama](first-run)).

## Apa yang Sebenarnya Dilakukan Installer {#what-the-installer-actually-does}

Skrip `customize.sh` menjalankan pemeriksaan dan tindakan berikut secara berurutan:

1. **Pemeriksaan Arsitektur** — jika `$ARCH` bukan `arm64`, instalasi dibatalkan. Modul hanya mengemas binary aarch64.
2. **Ekstraksi** file ZIP ke `/data/adb/modules/auriya`.
3. **Pemeriksaan Integritas** — memverifikasi hash SHA-256 binary daemon terhadap `checksums.sha256`; jika tidak cocok instalasi **dibatalkan**.
4. **Instalasi Binary** — menyalin binary daemon ke `/data/adb/modules/auriya/system/bin/auriya` (`0755`), serta `auriyactl` jika ada.
5. **Instalasi Companion** — menyalin `service.apk` ke `system/etc/auriya/service.apk`.
6. **Instalasi Aplikasi Manajer** — menjalankan `pm install -r -g` untuk memasang `auriya-app.apk` (`dev.auriya.app`).
7. **Inisialisasi Konfigurasi Awal** — menyalin `settings.toml` / `gamelist.toml` ke `/data/adb/.config/auriya/` **hanya jika belum ada konfigurasi sebelumnya**, sehingga menginstal ulang tidak akan menimpa setelan lama Anda.
8. **Symlink Root Manager** — membuat symlink binary ke `/data/adb/ksu/bin` (KernelSU) atau `/data/adb/ap/bin` (APatch) agar `auriya` dan `auriyactl` langsung tersedia di `$PATH`.

## Setelah Reboot {#after-reboot}

Modul **tidak** menjalankan daemon melalui aplikasi. Saat perangkat boot, `module/service.sh` menunggu `sys.boot_completed`, menjalankan companion service via `app_process`, menunggu file status sistem siap, lalu mengeksekusi daemon Rust `auriya`.

## Selanjutnya

[Penggunaan Pertama](first-run).
