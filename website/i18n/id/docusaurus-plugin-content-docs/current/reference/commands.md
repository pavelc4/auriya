# Referensi Perintah (`auriyactl`)

`auriyactl` adalah utilitas baris perintah (*CLI*) untuk mengontrol dan memantau daemon Auriya melalui Unix domain socket.

## Sinopsis {#synopsis}

```console
auriyactl <SUBCOMMAND> [OPTIONS]
```

### Prasyarat {#liveness-precondition}

Daemon Auriya harus sedang berjalan di latar belakang. Jika daemon mati, perintah akan menampilkan pesan kesalahan koneksi socket: `Failed to connect to /dev/socket/auriya.sock`.

## Daftar Subperintah {#subcommands}

### `auriyactl status`
Menampilkan ringkasan status daemon saat ini:
```console
# auriyactl status
Daemon: Running
Profile: Balance
Foreground: com.example.game (PID: 1234)
FPS: 119.8 (eBPF)
Thermal: 42.5 C
```

### `auriyactl set-mode <MODE>`
Mengubah mode profil aktif secara manual.
- Nilai yang valid: `performance`, `balance`, `powersave`.

### `auriyactl reload`
Memerintahkan daemon untuk memuat ulang file konfigurasi `settings.toml` dan `gamelist.toml` dari disk.

### `auriyactl add-game <PACKAGE> [OPTIONS]`
Menambahkan package aplikasi ke dalam whitelist `gamelist.toml`.

### `auriyactl remove-game <PACKAGE>`
Menghapus package dari whitelist game.

### `auriyactl restart`
Memulai ulang proses daemon Rust secara aman.
