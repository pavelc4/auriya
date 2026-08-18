# Menghapus Modul (Uninstall)

Hapus modul Auriya melalui daftar modul di root manager Anda (Magisk / KernelSU / APatch) — tandai modul untuk dihapus lalu reboot perangkat. Skrip `uninstall.sh` bawaan modul akan membersihkan seluruh sisa file dan paket.

:::info Terverifikasi dari Source Code
Diverifikasi langsung dari [`module/uninstall.sh`](https://github.com/pavelc4/auriya/blob/10fe7c6b56474a00513fec34ebac1376b30e95e6/module/uninstall.sh).
:::

## Urutan Tindakan yang Dilakukan `uninstall.sh`

1. **Menghentikan Daemon** — Mengirim sinyal `SIGTERM` ke binary `auriya`, menunggu hingga 5 detik, lalu `SIGKILL`.
2. **Menghentikan Companion** — Rangkaian TERM→KILL yang sama untuk proses `AuriyaSysMon`.
3. **Menghentikan Paksa dan Menghapus Aplikasi** — `am force-stop` kemudian `pm uninstall` untuk `dev.auriya.app`, `dev.auriya.app.debug`, dan `dev.auriya.service`. Setiap uninstall dicoba ulang hingga 3 kali dengan batas waktu 15 detik.
4. **Menghapus Data Runtime**:
   - `/dev/socket/auriya.sock`
   - `/data/adb/.config/auriya` — **seluruh konfigurasi**, termasuk `settings.toml` dan `gamelist.toml`
   - `/data/adb/auriya` — seluruh file log
   - Symlink KernelSU/APatch (`/data/adb/ksu/bin/*`, `/data/adb/ap/bin/*`)
5. **Hitung Mundur Keamanan** — Hitung mundur singkat "Do not reboot" agar Android menyelesaikan proses penghapusan paket di latar belakang, lalu menampilkan "Safe to reboot".

Folder modul itu sendiri (`/data/adb/modules/auriya`) akan dihapus otomatis oleh root manager setelah perangkat direboot.

:::warning Konfigurasi Anda Akan Dihapus
Langkah 4 menghapus folder `/data/adb/.config/auriya` sepenuhnya. Jika Anda ingin menyimpan file `settings.toml` / `gamelist.toml`, lakukan backup **sebelum** menghapus modul.
:::

## Jangan Reboot Terlalu Cepat

Perintah `pm uninstall` berjalan secara asinkron. Me-reboot perangkat saat proses hitung mundur masih berjalan dapat menyebabkan paket aplikasi tersisa sebagian. Tunggu sampai muncul pesan **"Auriya uninstall complete. Safe to reboot."** sebelum melakukan reboot.
