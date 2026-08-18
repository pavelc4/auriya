# Tweak Sistem Kernel

Halaman ini mendokumentasikan modifikasi tingkat rendah yang dilakukan Auriya ke dalam antarmuka kernel Linux (`/proc` dan `/sys`).

## Peta Modul Tweak {#module-map}

- **CPU Tweaks (`src/core/tweaks/cpu.rs`)**: Mengatur CPU scaling governor (`performance`, `schedutil`, `walt`), frekuensi minimum/maksimum, CPU boost, dan menyalakan semua core CPU (*CPU core online*).
- **GPU Tweaks (`src/core/tweaks/gpu.rs`)**: Mengatur mode daya GPU (Adreno / Mali), batas frekuensi GPU, dan governor daya grafis.
- **Scheduler & Kernel (`src/core/tweaks/scheduler.rs`)**: Mengoptimalkan latensi penjadwalan kernel Linux, isolasi task, dan parameter group cgroup.
- **Storage & VM (`src/core/tweaks/storage.rs`)**: Mengatur I/O scheduler, read-ahead buffer, dan nilai swappiness memori virtual.

## Deteksi Jalur & Caching {#path-detection-and-caching}

Karena produsen chipset (Snapdragon, MediaTek, Tensor, Exynos) menggunakan struktur path sysfs yang berbeda-beda, Auriya melakukan deteksi path saat startup:
- Node yang ditemukan dicatat ke dalam cache memori.
- Node yang tidak ada dilewati secara aman (*best-effort*) tanpa memicu crash atau peringatan yang mengganggu.

## Vendor Lock {#vendor-lock--stopping-vendor-services-from-fighting-back}

Layanan background vendor bawaan (seperti Xiaomi Joyose, Samsung Game Booster, OPPO Game Space) sering kali mencoba menimpa nilai frekuensi CPU/GPU kembali ke setelan default.

Untuk mencegahnya, Auriya menerapkan teknik **Vendor Lock**:
- Melakukan *mount-bind* file dummy kosong atau read-only di atas node kontrol vendor terkait selama sesi game berlangsung.
- Melepas mount-bind tersebut secara otomatis saat pengguna keluar dari game.
