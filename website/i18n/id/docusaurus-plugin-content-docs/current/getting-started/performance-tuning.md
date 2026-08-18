# Penyesuaian Performa

Panduan praktis memilih pengaturan dan nilai yang optimal di Auriya. Untuk spesifikasi teknis lengkap setiap kunci (tipe data, default, konsumsi daemon) lihat [Referensi settings.toml](../reference/settings) dan [Referensi gamelist.toml](../reference/gamelist).

:::tip Gunakan Aplikasi Manajer — Tidak Perlu Mengedit File
**Setiap pengaturan di halaman ini dapat dikonfigurasi langsung dari aplikasi manajer Auriya.** Anda **tidak** perlu membuka terminal atau mengedit file teks secara manual. Aplikasi akan menulis konfigurasi untuk Anda dan daemon akan memuat perubahannya secara otomatis.
:::

## Dua Tingkatan Konfigurasi

| Tingkatan | Mengatur | Lokasi di Aplikasi |
| --- | --- | --- |
| **Global** (`settings.toml`) | Default daemon, perilaku FAS, batas termal | Menu Pengaturan / Layar Config |
| **Per-Game** (`gamelist.toml`) | Governor game, target FPS, refresh rate, mode, batas frekuensi | Detail game di layar Games |

Konfigurasi per-game akan menjadi prioritas utama saat game tersebut berada di foreground (layar utama); pengaturan global berlaku di kondisi lainnya.

## FAS: Apa Itu dan Bagaimana Cara Mengaturnya

Frame-Aware Scheduling (FAS) memantau waktu render setiap frame (*frame pacing*) secara real-time dan menyesuaikan frekuensi CPU/GPU naik atau turun untuk mempertahankan target FPS dengan daya seminimal mungkin. FAS hanya aktif untuk **game yang ada di whitelist** dan saat probe frame eBPF tersedia (lihat [Deteksi FPS](../internals/fps-detection) dan [Kala eBPF frame probe](../internals/kala-research)).

### Mengaktifkan FAS

`[fas] enabled = true` (default). Jika kernel perangkat tidak mendukung probe eBPF, daemon otomatis beralih ke deteksi FPS berbasis sysfs dan menonaktifkan FAS — tidak ada konfigurasi rumit yang perlu diatur manual.

### Mode FAS (Pengaturan Margin)

Perilaku FAS diatur melalui **mode**. Setiap mode menentukan nilai `margin` (headroom FPS) dan `thermal_threshold`. Kunci `fas.default_mode` menentukan mode aktif. **Margin lebih kecil = lebih agresif** (mendorong clock lebih cepat agar FPS tetap rapat ke target); **margin lebih besar = lebih hemat** (mentoleransi sedikit penurunan FPS demi menghemat baterai dan menjaga suhu).

| Mode | `margin` | `thermal_threshold` | Karakteristik |
| --- | --- | --- | --- |
| `powersave` | 5.0 | 80 °C | Paling hemat baterai & dingin; mentoleransi fluktuasi FPS |
| `balance` | 2.0 | 90 °C | **Default** — FPS stabil dan suhu wajar |
| `performance` | 1.0 | 95 °C | Mengunci target frame secara ketat |
| `fast` | 0.0 | 95 °C | Zero headroom — mengejar deadline setiap frame secara maksimal |

### Pengaturan yang Disarankan Berdasarkan Kebutuhan

| Kebutuhan Anda | `fas.default_mode` | Catatan |
| --- | --- | --- |
| Penggunaan Harian Seimbang | `balance` | Biarkan semua nilai default. |
| Kelancaran Maksimal (Game Kompetitif) | `performance` | Frame pacing lebih rapat; suhu & baterai sedikit lebih tinggi. |
| Latensi Terendah Mutlak | `fast` | Hanya jika perangkat Anda memiliki sistem pendingin yang baik (batas 95 °C). |
| Sesi Bermain Panjang / Suhu Dingin | `powersave` | Mengizinkan penurunan FPS minor agar perangkat tetap dingin. |

:::note Perubahan FAS Berlaku Setelah Restart
Perubahan pada `[fas]`, `[dynamic_governor]`, dan `[modes.*]` dibaca saat daemon pertama kali menyala. Setelah mengubahnya, restart daemon (aplikasi melakukan ini secara otomatis; dari shell gunakan `auriyactl restart`).
:::

## Penyesuaian Khusus Per-Game

Di layar Games pada aplikasi, setiap game yang ada di whitelist dapat menyesuaikan:

| Kolom | Fungsi | Nilai Umum |
| --- | --- | --- |
| `target_fps` | Target FAS; angka tunggal (`120`) atau bertahap (`[60,90,120]`) | Cap FPS game Anda, misal `120` |
| `cpu_governor` | Governor CPU saat game ini berjalan | `performance` atau `walt` |
| `mode` | Profil statis: `performance` / `balance` / `powersave` | `performance` untuk game berat |
| `refresh_rate` | Refresh rate layar yang diminta | Samakan dengan `target_fps` |
| `enable_dnd` | Mode Jangan Ganggu saat bermain | `true` untuk fokus |

Penggunaan `target_fps` berupa **array** (`[60, 90, 120]`) memungkinkan FAS otomatis menyesuaikan target dengan frame rate yang sedang dirender game — sangat berguna untuk game yang membatasi menu pada 60 FPS dan gameplay pada 120 FPS.

## Mode Profil vs Mode FAS (Jangan Tertukar)

- **Mode Profil** — `performance` / `balance` / `powersave` (3 mode). Mengatur governor CPU, mode GPU, dan tweak kernel dasar.
- **Mode FAS** — `powersave` / `balance` / `performance` / `fast` (4 mode). Preset parameter `margin` + `thermal_threshold` untuk kontroler adaptif FAS.

Mode profil menentukan baseline sistem; mode FAS menentukan seberapa agresif kontroler adaptif mengejar target frame di atas baseline tersebut.

## Lihat Juga

- [Referensi settings.toml](../reference/settings) — seluruh kunci global.
- [Referensi gamelist.toml](../reference/gamelist) — seluruh kolom per-game.
- [Penjadwal Profil](../internals/profile-scheduler) — bagaimana profil diputuskan setiap tick.
