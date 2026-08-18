# Penjadwal Profil (Profile Scheduler)

Penjadwal Profil (*Profile Scheduler*) adalah mesin inti dalam daemon Rust yang bertanggung jawab memutuskan kapan dan bagaimana profil performa diterapkan pada sistem.

## Urutan Pengambilan Keputusan {#decision-order}

Setiap kali tick dipicu (oleh timer, perubahan status companion, atau event IPC), penjadwal menjalankan evaluasi kondisi dalam urutan hierarki ketat:

1. **Layar Mati / Penghemat Baterai**: Jika layar mati (`is_screen_on = false`) atau Android Battery Saver aktif, sistem seketika dipaksa ke mode `Powersave`.
2. **Override Manual via IPC**: Jika pengguna menetapkan profil secara manual melalui aplikasi atau CLI.
3. **Aplikasi Game di Foreground**: Jika aplikasi yang sedang aktif terdaftar di whitelist `gamelist.toml` dan memiliki PID valid, sistem beralih ke profil game tersebut (default: `Performance`).
4. **Kondisi Normal / Aplikasi Standar**: Menerapkan mode default sistem (`daemon.default_mode`, default: `Balance`).

## Mekanisme Idempotensi (Idempotence Guard) {#the-idempotence-guard}

Untuk mencegah beban I/O berlebih dan penulisan berulang ke sysfs yang tidak perlu, penjadwal memiliki *idempotence guard*:
- Jika profil target **sama dengan profil yang sedang aktif**, daemon melewati eksekusi penulisan sysfs statis.
- Penulisan hanya dieksekusi jika terjadi perubahan profil nyata atau penyesuaian dinamis dari pengontrol FAS.
