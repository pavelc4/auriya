# Protokol IPC

Komunikasi antara aplikasi manajer Android, CLI `auriyactl`, dan daemon Rust dilakukan melalui Unix domain socket lokal di `/dev/socket/auriya.sock`.

## Transport & Format Pesan {#transport}

- **Tipe Socket:** Unix Domain Socket (Streaming `SOCK_STREAM`).
- **Izin Akses:** Dibuat oleh root dengan izin `0660` (atau `0666` untuk klien Android).
- **Format Pesan:** Protokol berbasis teks per baris (diakhiri newline `\n`). Setiap baris perintah menerima respon berbasis teks atau JSON terstruktur.

## Konvensi Respon {#response-conventions}

- Perintah sukses: mengembalikan teks status atau string JSON (misal `OK`, atau payload `GET_STATS`).
- Perintah gagal: mengembalikan format `ERR <deskripsi kesalahan>`.

## Daftar Perintah Utama {#core-commands}

| Perintah | Deskripsi | Respon |
| --- | --- | --- |
| `PING` | Memeriksa ketersediaan daemon | `PONG` |
| `STATUS` | Mengambil ringkasan status daemon | Teks status multi-baris |
| `GET_STATS` | Mengambil data telemetri lengkap (CPU, GPU, Suhu, FPS) | Objek JSON |
| `SET_PROFILE <MODE>` | Mengubah mode profil aktif secara manual | `OK` |
| `RELOAD` | Memuat ulang file konfigurasi `settings.toml` | `OK` |
| `ADD_GAME <PKG> <CONFIG>` | Menambahkan game baru ke `gamelist.toml` | `OK` |
| `REMOVE_GAME <PKG>` | Menghapus game dari whitelist | `OK` |
