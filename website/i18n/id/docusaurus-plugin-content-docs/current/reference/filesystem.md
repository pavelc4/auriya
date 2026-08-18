# Referensi Sistem Berkas

Halaman ini mendokumentasikan seluruh path file, direktori, dan socket yang digunakan oleh modul Auriya pada perangkat yang terinstal.

## Path Runtime Sistem {#runtime-paths-on-an-installed-running-device}

### Direktori Konfigurasi — `/data/adb/.config/auriya/` {#configuration-and-runtime-state--dataadbconfigauriya}

| File | Deskripsi | Pemilik Penulisan |
| --- | --- | --- |
| `settings.toml` | File konfigurasi setelan global | Aplikasi Manajer / Pengguna |
| `gamelist.toml` | File konfigurasi whitelist & profil per-game | Aplikasi Manajer / Daemon |
| `system_status` | Snapshot status sistem Android terkini | Companion Service |
| `companion.lock` | Kunci proses single-instance companion | Companion Service |

### Direktori Modul Magisk/KSU — `/data/adb/modules/auriya/` {#module-installation-directory}

| Path | Deskripsi |
| --- | --- |
| `system/bin/auriya` | Binary daemon Rust utama |
| `system/bin/auriyactl` | Binary CLI kontrol |
| `system/etc/auriya/service.apk` | APK Companion Service |
| `service.sh` | Skrip eksekusi saat boot perangkat |
| `uninstall.sh` | Skrip pembersihan saat modul dihapus |

### Direktori Log — `/data/adb/auriya/` {#log-directory}

- `daemon.log`: Log runtime daemon Rust.
- `companion.log`: Log runtime companion service.

### Socket IPC — `/dev/socket/auriya.sock` {#ipc-socket}

Unix domain socket yang digunakan untuk seluruh komunikasi antar-proses.
