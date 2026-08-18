# Companion Service

Companion Service (`dev.auriya.service`) adalah komponen Android khusus tanpa antarmuka grafis yang dijalankan langsung melalui binary `app_process` Android saat proses boot.

## Mengapa Companion Service Dibutuhkan {#why-it-exists}

Daemon Rust Auriya berjalan sebagai proses native murni dengan hak akses root. Meskipun memiliki kontrol penuh atas kernel dan sistem berkas, proses native tidak memiliki akses langsung ke API Framework Android tingkat tinggi seperti:
- Mendeteksi aplikasi yang sedang berada di latar depan (*foreground task stack*).
- Mendeteksi status layar hidup/mati (`DisplayManager` / `PowerManager`).
- Memantau status penghemat daya baterai (`BatteryManager`).
- Mengubah refresh rate layar secara dinamis dan mengatur mode Do Not Disturb (`ZenMode`).

Companion service bertindak sebagai jembatan yang menghubungkan API Android Framework dengan daemon Rust.

## Peluncuran & Penguncian Single-Instance {#launch--single-instance-lock}

Companion dijalankan oleh `module/service.sh` menggunakan perintah:
```bash
app_process -Djava.class.path=/system/etc/auriya/service.apk /system/bin dev.auriya.service.Main &
```

Untuk memastikan hanya ada satu instans companion yang berjalan, proses ini mengunci file `/data/adb/.config/auriya/companion.lock`. Jika instans companion terhenti atau crash, daemon mendeteksi pelepasan kunci tersebut dan secara otomatis mencoba memulai ulang (*rate-limited restart*).

## File Status Sistem {#system-status-file}

Companion menulis status terkini ke file `/data/adb/.config/auriya/system_status` setiap kali terjadi perubahan state Android. Daemon Rust membaca file ini secara berkala pada setiap tick evaluasi.