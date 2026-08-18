# Konfigurasi

:::tip Atur Semua Pengaturan Langsung dari Aplikasi Manajer
**Anda tidak perlu mengedit file secara manual.** Aplikasi manajer Auriya dirancang untuk mengubah setiap pengaturan — baik perilaku global maupun per-game. Buka aplikasi, sesuaikan pengaturan yang Anda inginkan, dan aplikasi akan menulis konfigurasinya secara otomatis untuk Anda. Penjelasan file di bawah ini hanya untuk pemahaman teknis dan pengguna mahir. Untuk nilai yang disarankan, lihat [Penyesuaian Performa](performance-tuning).
:::

Di balik layar, Auriya membaca dua file TOML di bawah `/data/adb/.config/auriya/`
(aplikasi yang menulis file ini secara otomatis):

| File | Cakupan | Referensi Lengkap |
| --- | --- | --- |
| `settings.toml` | Default daemon dan scheduler **Global** | [Referensi settings.toml](../reference/settings) |
| `gamelist.toml` | Whitelist dan konfigurasi khusus **Per-Aplikasi** | [Referensi gamelist.toml](../reference/gamelist) |

## Bagaimana Perubahan Konfigurasi Diterapkan

- **Melalui Aplikasi Manajer (Direkomendasikan)** — aplikasi menulis kedua file, dan untuk daftar game, daemon juga memperbarui file saat menerima perintah dari aplikasi. Ini adalah jalur utama yang paling aman.
- **Melalui CLI** — `auriyactl` memutasi daftar game melalui IPC (`add-game`, `remove-game`, dan `UPDATE_GAME`) serta dapat memicu muat ulang setelan dengan `auriyactl reload`. Lihat [Referensi Perintah](../reference/commands).
- **Manual (Fallback Pengguna Mahir)** — Anda dapat mengedit file secara langsung menggunakan text editor root, lalu menjalankan `auriyactl reload` (atau menunggu file watcher mendeteksinya).

## Dua Hal yang Perlu Diketahui

1. **Beberapa kunci berlaku instan, sebagian membutuhkan restart.** Kunci `cpu.default_governor`, `daemon.default_mode`, dan `daemon.check_interval_ms` langsung dibaca ulang saat file diedit; blok FAS (`[fas]`, `[dynamic_governor]`, `[modes.*]`) dimuat saat inisialisasi awal dan membutuhkan restart daemon untuk memuat ulang parameter baru.
2. **`fas.default_mode` memilih profil `[modes.*]` yang aktif.** Hanya mode yang dipilih yang menentukan batas margin/termal FAS; blok mode lain tetap tidak aktif sampai dipilih.

## Nilai Tidak Valid

Kunci yang tidak dikenal akan diabaikan secara aman (*silently ignored*), dan beberapa nilai yang tidak valid akan jatuh ke nilai default aman (misal mode game yang tidak dikenal akan dialihkan ke Performance). Namun, file TOML dengan sintaks rusak (malformed syntax) akan membatalkan start daemon demi keamanan.

## Selanjutnya

[Penyesuaian Performa](performance-tuning) ·
[Referensi settings.toml](../reference/settings) ·
[Referensi gamelist.toml](../reference/gamelist).
