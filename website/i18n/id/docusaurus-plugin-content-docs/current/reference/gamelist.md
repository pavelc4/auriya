# Referensi `gamelist.toml`

File `gamelist.toml` berisi daftar whitelist aplikasi dan profil penyesuaian khusus (*per-game overrides*).

## Lokasi & Kepemilikan {#location-and-ownership}

- **Path:** `/data/adb/.config/auriya/gamelist.toml`
- **Format:** TOML terstruktur.
- **Penulisan:** Ditulis oleh aplikasi manajer atau dimodifikasi melalui CLI `auriyactl`.

## Contoh Struktur File {#the-shipped-default-file}

```toml
[[games]]
package = "com.miHoYo.GenshinImpact"
mode = "performance"
target_fps = 60
refresh_rate = 60
cpu_governor = "performance"
enable_dnd = true

[[games]]
package = "com.mobile.legends"
mode = "performance"
target_fps = [60, 90, 120]
refresh_rate = 120
cpu_governor = "walt"
enable_dnd = true
```

## Penjelasan Kolom Konfigurasi {#field-by-field-reference}

| Kolom | Tipe Data | Deskripsi | Default |
| --- | --- | --- | --- |
| `package` | String | Nama package aplikasi Android unik (wajib) | - |
| `mode` | String | Profil awal: `performance`, `balance`, `powersave` | `performance` |
| `target_fps` | Angka / Array | Target frame rate untuk FAS (misal `120` atau `[60, 90, 120]`) | Otomatis |
| `refresh_rate` | Angka | Refresh rate layar yang diminta dalam Hz | `0` (tidak berubah) |
| `cpu_governor` | String | Governor CPU yang dipaksa saat game berjalan | Mengikuti profil |
| `enable_dnd` | Boolean | Aktifkan mode Jangan Ganggu (DnD) saat bermain | `false` |
