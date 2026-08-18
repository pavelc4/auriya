# Penggunaan Pertama

Hal-hal yang terjadi saat Anda pertama kali meluncurkan Auriya, dan cara memverifikasi bahwa semuanya berjalan dengan benar.

## Apa yang Ditetapkan pada Penggunaan Pertama {#what-the-first-run-establishes}

1. **Permintaan Izin Root.** Pada peluncuran pertama, aplikasi manajer meminta akses root. Akses ini diperlukan untuk membaca telemetri, berkomunikasi dengan socket daemon, dan mengubah konfigurasi.
2. **Daemon Sudah Berjalan.** Berbeda dari modul pada umumnya, daemon Auriya **bukan** dijalankan oleh aplikasi — melainkan otomatis dimulai saat boot oleh `module/service.sh` (lihat [Instalasi → Setelah Reboot](installation#after-reboot)). Saat Anda membuka aplikasi, daemon dan companion seharusnya sudah aktif di latar belakang.
3. **Preferensi Tampilan/Onboarding** disimpan secara lokal oleh aplikasi untuk peluncuran berikutnya.

## Memverifikasi Bahwa Semuanya Berjalan {#verifying-it-works}

### 1. Dari Aplikasi Manajer (Paling Mudah)

- **Layar Status / Home:** Menampilkan kartu status daemon (`Running`), versi, mode profil aktif (`Performance` / `Balance` / `Powersave`), sensor FPS aktif, dan suhu CPU/GPU.
- Jika status menampilkan *Offline*, daemon gagal berjalan atau socket belum siap.

### 2. Dari Baris Perintah (CLI)

Jalankan perintah berikut di terminal root perangkat (`adb shell su` atau Termux dengan root):

```console
# auriyactl status
Daemon: Running
Profile: Balance
Foreground: None
FPS: 0.0 (sysfs)
```

Jika perintah `auriyactl` tidak ditemukan, binary dapat dipanggil langsung dari `/data/adb/modules/auriya/system/bin/auriyactl status`.

## Memeriksa Log {#inspecting-logs}

Log runtime disimpan di `/data/adb/auriya/`:
- `daemon.log` — log operasi, event loop, dan transisi profil daemon Rust.
- `companion.log` — log deteksi aplikasi latar depan dan sensor sistem Android.

## Selanjutnya

[Konfigurasi](configuration) · [Penyesuaian Performa](performance-tuning).
