# Persyaratan Sistem

Hal-hal yang dibutuhkan perangkat sebelum menginstal Auriya. Setiap persyaratan dicocokkan langsung dari source code.

## Perangkat Keras dan Sistem Operasi

| Persyaratan | Rincian | Sumber Source Code |
| --- | --- | --- |
| Root Manager | Magisk, KernelSU, atau APatch | `module/customize.sh` mendeteksi KernelSU/APatch dan membuat symlink binary yang sesuai |
| Arsitektur CPU | **Hanya `arm64-v8a` (aarch64)** | `customize.sh` membatalkan proses pada `$ARCH` lain; modul hanya mengemas binary aarch64 |
| Versi Android | **Android 11 atau lebih baru** (`minSdk = 30`) | `android/app/build.gradle.kts`, `android/service/build.gradle.kts` |
| Izin Root | Aplikasi manajer dan daemon membutuhkan root untuk membaca/menulis `/proc`, `/sys`, dan socket daemon | lihat [System tweaks](../internals/system-tweaks), [Protokol IPC](../internals/ipc-protocol) |

## Fitur Kernel (untuk Frame-Aware Scheduling)

Daemon dasar dapat berjalan di perangkat mana pun yang didukung, namun **FAS bersifat opsional dan tergantung kapabilitas kernel**:

- FAS menggunakan uprobe eBPF (Kala) yang membutuhkan kernel dengan dukungan **uprobe + ring-buffer (kernel 5.8+, teruji di 5.10)**, izin root atau `CAP_SYS_ADMIN` + `CAP_BPF`, dan library `/system/lib64/libgui.so`
  ([Kala eBPF frame probe](../internals/kala-research)).
- Jika kapabilitas tersebut tidak ada, daemon **tetap berjalan dengan FPS berbasis sysfs dan FAS dinonaktifkan** — daemon tidak akan gagal berjalan
  ([Deteksi FPS](../internals/fps-detection)).

Dengan demikian, perangkat dengan kernel lama tetap dapat menikmati profil statis dan tweak Auriya; hanya lapisan adaptif FAS yang dinonaktifkan.

## Batasan Keamanan & Hak Akses

Auriya berjalan dengan hak akses root, menulis ke node kernel, membuka Unix socket, melakukan mount-bind untuk vendor lock, dan menginstal paket. Tweak `/proc` dan `/sys` bersifat *best-effort* (dilewati otomatis jika path tidak ada di perangkat Anda), namun tetap merupakan operasi berhak istimewa root.

## Selanjutnya

[Instalasi](installation).
