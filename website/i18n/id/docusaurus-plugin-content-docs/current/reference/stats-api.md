# Stats API & Perekam FPS (`GET_STATS`)

Perintah `GET_STATS` menyediakan data telemetri real-time lengkap dari daemon Auriya untuk antarmuka pengguna Android dan overlay in-game.

## Transport {#transport}

Klien mengirim string teks `GET_STATS\n` melalui Unix domain socket `/dev/socket/auriya.sock`. Daemon menjawab dengan objek JSON satu baris berisi seluruh statistik perangkat.

## Struktur Skema JSON {#json-schema-one-group--one-ui-card}

```json
{
  "daemon_running": true,
  "profile_mode": "Performance",
  "active_package": "com.example.game",
  "fps": 119.5,
  "fps_source": "eBPF",
  "cpu": {
    "usage_percent": 34.2,
    "temperatures": [42.0, 44.5],
    "cluster_frequencies": [1800000, 2400000, 3000000]
  },
  "gpu": {
    "usage_percent": 68.0,
    "frequency": 650000000,
    "temperature": 45.0
  },
  "battery": {
    "level": 85,
    "temperature": 35.0,
    "charging": false
  }
}
```

## Penggunaan di Aplikasi {#app-usage}

Aplikasi Android manajer meng-query endpoint ini setiap interval tertentu untuk memperbarui grafik monitor performa, Quick Settings status tile, dan floating overlay.
