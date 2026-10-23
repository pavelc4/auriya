use crate::platform::info::SystemReport;

pub fn print_info(report: &SystemReport) {
    println!("Hardware & Platform:");
    println!("  Brand:          {}", report.hw.brand);
    println!("  Codename:       {}", report.hw.codename);
    println!(
        "  Android:        {} (API {})",
        report.hw.android_version, report.hw.android_api
    );
    println!("  SoC Board:      {}", report.hw.soc_board);
    println!("  CPU Topology:   {}", report.hw.cpu_topology);

    if let Some(ref gpu) = report.hw.gpu_model {
        println!("  GPU:            {}", gpu);
    }
    if let Some(ref drv) = report.hw.gpu_driver {
        println!("  GPU Driver:     {}", drv);
    }

    println!("  Vulkan:         {}", report.hw.vulkan_support);
    println!("  Active Render:  {}", report.hw.active_renderer);

    if let Some(ref disp) = report.hw.display_rates {
        println!("  Display:        {}", disp);
    }

    println!("  Root Manager:   {}", report.hw.root_manager);
    println!("  SELinux:        {}", report.hw.selinux);
    println!("  Kernel:         {}", report.hw.kernel);
    println!();

    println!("Kernel eBPF Diagnostics:");
    println!("  BPF Mount:      {}", report.ebpf.bpf_mount);
    println!("  BPF Syscall:    {}", report.ebpf.bpf_syscall);
    println!("  JIT Compiler:   {}", report.ebpf.jit_compiler);
    println!("  Kala Probe:     {}", report.ebpf.kala_probe);
    println!("  Active Engine:  {}", report.ebpf.active_engine);
    println!();

    println!("Daemon Runtime:");
    if let Some(pid) = report.daemon.pid {
        println!("  PID:            {} (Running)", pid);
        if let Some(ref rss) = report.daemon.memory_rss {
            println!("  Memory (RSS):   {}", rss);
        }
        if let Some(ref cpu) = report.daemon.cpu_load {
            println!("  CPU Load:       {}", cpu);
        }
        if let Some(th) = report.daemon.threads {
            println!("  Threads:        {}", th);
        }
    } else {
        println!("  PID:            Not running");
    }
}
