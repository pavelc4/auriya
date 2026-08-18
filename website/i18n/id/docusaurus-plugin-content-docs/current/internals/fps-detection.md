# Deteksi FPS

Auriya menggunakan arsitektur deteksi FPS bertingkat untuk mengukur performa visual dan waktu render frame secara akurat.

## Dua Sumber Pengukuran (Sysfs & eBPF) {#two-sources-sysfs-first}

1. **eBPF Probe (Kala Frame Probe)**: Mengaitkan probe (*uprobe*) ke fungsi pipeline grafis Android (`/system/lib64/libgui.so`) untuk menangkap *frame deadline*, waktu render, dan *jank* frame per frame. Ini adalah sumber paling presisi yang menggerakkan Frame-Aware Scheduling (FAS).
2. **Sysfs Node (Fallback)**: Membaca node kernel seperti `/sys/class/drm/` atau counter display driver untuk menghitung FPS rata-rata secara berkala.

## Sumber Sysfs {#sysfs-source}

Jika kernel perangkat tidak mendukung eBPF (misalnya kernel lama di bawah versi 5.8) atau uprobe gagal dimuat, daemon secara otomatis jatuh ke pengukuran berbasis sysfs. Pada mode ini, daemon tetap dapat menampilkan FPS di layar status dan overlay, namun penjadwal FAS otomatis dinonaktifkan demi stabilitas sistem.

## Penanganan Batas Refresh Rate {#refresh-rate-boundaries}

Deteksi FPS memperhitungkan refresh rate aktif layar (misalnya 60Hz, 90Hz, 120Hz, 144Hz) untuk menentukan apakah suatu penurunan frame dikategorikan sebagai *stutter* nyata atau sekadar pembatasan internal game.
