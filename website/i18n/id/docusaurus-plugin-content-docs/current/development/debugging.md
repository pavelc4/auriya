# Debugging & Pemecahan Masalah

Panduan mengidentifikasi masalah, memeriksa log, dan mendiagnosis perilaku runtime Auriya.

## Memeriksa File Log {#logs-first}

Semua log runtime penting tersimpan di direktori root perangkat:
- **Log Daemon**: `/data/adb/auriya/daemon.log`
- **Log Companion**: `/data/adb/auriya/companion.log`
- **Logcat Android**: `adb logcat -s Auriya AuriyaSysMon`

Membaca log real-time melalui adb:
```bash
adb shell "tail -f /data/adb/auriya/daemon.log"
```

## Menyesuaikan Tingkat Verbosity Log {#adjust-log-verbosity}

Tingkat verbosity dapat diatur via variabel lingkungan `RUST_LOG`:
```bash
# Debugging penuh
export RUST_LOG=auriya=debug,trace
```

## Menguji Socket IPC Secara Manual {#manual-ipc-testing}

Anda dapat berinteraksi langsung dengan Unix socket menggunakan utilitas `nc` (*netcat*) di shell root:
```bash
adb shell "su -c 'nc -U /dev/socket/auriya.sock'"
PING
# Output: PONG
```
