# Deteksi Game

Halaman ini mendokumentasikan bagaimana Auriya mendeteksi peluncuran game, memvalidasi proses aplikasi, dan menerapkan profil yang sesuai.

## Dari Mana Package Foreground Berasal {#where-the-foreground-package-comes-from}

Companion service memantau stack tugas Android (*ActivityTaskManager / UsageStats*) dan mencatat nama package aplikasi yang sedang berada di layar depan ke dalam file `system_status`.

## Whitelist Game (`gamelist.toml`) {#the-whitelist}

Setiap tick evaluasi, daemon mencocokkan nama package foreground terhadap daftar yang terdaftar di `/data/adb/.config/auriya/gamelist.toml`.
- Jika package **terdaftar di whitelist**, daemon melanjutkan ke proses validasi PID.
- Jika package **tidak terdaftar**, daemon mempertahankan profil default sistem (`daemon.default_mode`).

## Validasi PID & Pelacakan Proses {#pid-validation}

Sebelum menerapkan konfigurasi profil game yang agresif, daemon memvalidasi PID proses melalui `/proc/<pid>/cmdline` untuk memastikan:
1. Proses benar-benar milik package game yang dimaksud.
2. Proses masih aktif dan bukan sisa thread yang tertinggal.
3. Menghindari salah target pada proses sistem lain.

Setelah validasi berhasil, daemon membuat pelacak PID (*PID tracker*) yang memantau siklus hidup proses game tersebut hingga ditutup oleh pengguna.
