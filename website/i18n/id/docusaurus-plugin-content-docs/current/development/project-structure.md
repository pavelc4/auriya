# Struktur Proyek

Halaman ini memberikan gambaran umum tata letak direktori dan organisasi modul di dalam repositori Auriya.

## Pratinjau Struktur Repositori {#bundled-zip-preview}

```text
auriya/
├── android/                 # Proyek aplikasi Android & companion
│   ├── app/                 # Aplikasi manajer berbasis Compose (dev.auriya.app)
│   ├── service/             # Companion service background (dev.auriya.service)
│   └── shared/              # Model data TOML & utilitas bersama Kotlin
├── src/                     # Source code Rust
│   ├── main.rs              # Entry point binary daemon utama
│   ├── ctl.rs               # Entry point binary CLI kontrol (auriyactl)
│   ├── cli/                 # Logika interaksi baris perintah CLI
│   ├── daemon/              # Orkestrasi event loop, socket IPC, & runtime tick
│   └── core/                # Logika inti performa (tweak, config, fas, telemetry)
├── module/                  # Skrip & aset paket modul Magisk/KSU/APatch
│   ├── customize.sh         # Skrip instalasi modul
│   ├── service.sh           # Skrip eksekusi boot Android
│   ├── uninstall.sh         # Skrip pembersihan saat modul dihapus
│   └── module.prop          # Metadata modul
├── settings.toml            # Konfigurasi default global yang dikemas
├── gamelist.toml            # Whitelist aplikasi default yang dikemas
└── website/                 # Dokumentasi website berbasis Docusaurus
```
