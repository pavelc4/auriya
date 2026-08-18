# Probe Frame eBPF (Kala)

Kala adalah implementasi probe berbasis eBPF (*Extended Berkeley Packet Filter*) yang dirancang khusus untuk memantau performa grafis dan waktu render frame pada sistem Android.

## Apa yang Diukur oleh Kala {#what-kala-measures}

Kala memasang *uprobe* (user-space probe) pada pipeline grafis Android (khususnya simbol `Surface` atau `GraphicBuffer` pada `/system/lib64/libgui.so`). Dengan membaca event queue buffer langsung di kernel, Kala dapat mengukur:
- Waktu produksi frame (*frame production time*) dalam mikrodetik.
- Deteksi frame terlambat (*frame drop / jank*) secara real-time.
- Estimasi beban CPU vs GPU untuk setiap frame yang dirender.

## Siklus Hidup Probe {#probe-lifecycle}

1. **Inisialisasi Awal**: Saat daemon menyala, ia memeriksa kapabilitas kernel (`BPF_PROG_TYPE_KPROBE`, ring-buffer support, `libgui.so`).
2. **Attach ke PID**: Ketika game yang ada di whitelist mulai berjalan, probe secara dinamis di-attach ke PID proses game tersebut.
3. **Stream Telemetri**: Kernel mengalirkan event frame melalui eBPF ring-buffer ke thread penerima di daemon Rust.
4. **Detach**: Saat game ditutup atau kehilangan fokus, probe di-detach untuk menghemat sumber daya CPU.

## Integrasi dengan Auriya {#auriya-integration}

Data dari Kala dikonsumsi langsung oleh modul Frame-Aware Scheduling (FAS) Auriya untuk menentukan tindakan penskalaan frekuensi CPU/GPU per-frame secara presisi.
