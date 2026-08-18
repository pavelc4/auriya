# Referensi `settings.toml`

File `settings.toml` mendefinisikan setelan global untuk daemon Auriya dan parameter kontroler Frame-Aware Scheduling (FAS).

## Lokasi & Kepemilikan {#location-and-ownership}

- **Path:** `/data/adb/.config/auriya/settings.toml`
- **Format:** TOML.

## Contoh Struktur Default {#the-shipped-default-file}

```toml
[daemon]
check_interval_ms = 5000
default_mode = "balance"

[cpu]
default_governor = "schedutil"

[fas]
enabled = true
default_mode = "balance"

[modes.powersave]
margin = 5.0
thermal_threshold = 80

[modes.balance]
margin = 2.0
thermal_threshold = 90

[modes.performance]
margin = 1.0
thermal_threshold = 95

[modes.fast]
margin = 0.0
thermal_threshold = 95
```

## Referensi Kunci Per Kunci {#key-by-key-reference}

### Blok `[daemon]`
- `check_interval_ms` (integer, default `5000`): Interval tick evaluasi normal dalam milidetik.
- `default_mode` (string, default `"balance"`): Mode profil saat tidak ada game yang berjalan.

### Blok `[cpu]`
- `default_governor` (string, default `"schedutil"`): Governor CPU default sistem.

### Blok `[fas]`
- `enabled` (boolean, default `true`): Mengaktifkan atau menonaktifkan Frame-Aware Scheduling.
- `default_mode` (string, default `"balance"`): Preset mode FAS yang aktif (`powersave`, `balance`, `performance`, `fast`).

### Blok `[modes.*]` {#modes}
Setiap sub-blok menentukan:
- `margin` (float): Headroom target FPS. Angka lebih kecil berarti kontroler lebih agresif mendorong clock CPU/GPU.
- `thermal_threshold` (integer): Batas suhu Celsius sebelum penskalaan termal membatasi frekuensi maksimum.

## Perilaku Muat Ulang (Reload Behavior) {#reload-behavior}

- Kunci `daemon.default_mode` dan `cpu.default_governor` berlaku secara langsung saat file diedit.
- Kunci `[fas]` dan `[modes.*]` dimuat saat startup dan membutuhkan restart daemon (`auriyactl restart`) untuk menerapkan perubahan.
