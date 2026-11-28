use auriya::core::scaling::PlatformCapabilities;
use auriya::core::telemetry::gpu::GpuCollector;
use auriya::platform::cpu::topology::classify_from_freqs;
use auriya::platform::gpu::{
    GpuVendor, detect_vendor_in, set_xclipse_balanced_in, set_xclipse_performance_in,
};
use auriya::platform::info::hardware::{detect_gpu_and_driver_in, infer_xclipse_model};
use auriya::platform::procfs::detect_foreground_task_from_cpuset_paths;
use auriya::platform::thermal::zones::read_sysfs_zones_from;
use auriya::platform::vendor::{SocType, classify_soc_from_props};
use std::fs;
use tempfile::TempDir;

#[test]
fn exynos_2200_dts_cpu_cluster_speeds_classify_correctly() {
    // From LineageOS s5e9925.dts exynos-acme domain@0..2:
    // cluster0 (0-3): 400_000..1_824_000 kHz
    // cluster1 (4-6): 576_000..2_515_000 kHz
    // cluster2 (7):   576_000..2_803_000 kHz
    let s5e9925_max_freqs = [
        (0, 1_824_000),
        (1, 1_824_000),
        (2, 1_824_000),
        (3, 1_824_000),
        (4, 2_515_000),
        (5, 2_515_000),
        (6, 2_515_000),
        (7, 2_803_000),
    ];
    let (little, big, prime) = classify_from_freqs(&s5e9925_max_freqs);
    assert_eq!(little, 0b0000_1111); // cores 0..3
    assert_eq!(big, 0b0111_0000); // cores 4..6
    assert_eq!(prime, 0b1000_0000); // core 7
}

#[test]
fn exynos_soc_classification_handles_s5e_and_exynos_names() {
    assert_eq!(
        classify_soc_from_props(Some("s5e9925"), Some("s5e9925")),
        SocType::Exynos
    );
    assert_eq!(
        classify_soc_from_props(Some("erd9945"), None),
        SocType::Exynos
    );
    assert_eq!(
        classify_soc_from_props(None, Some("samsungexynos2200")),
        SocType::Exynos
    );
    assert_eq!(
        classify_soc_from_props(Some("sm8650"), Some("qcom")),
        SocType::Snapdragon
    );
}

#[test]
fn xclipse_sgpu_capabilities_vendor_and_kernel_gpu_nodes() {
    let tmp = TempDir::new().unwrap();
    let root = tmp.path();
    let kgsl = root.join("kgsl-3d0");
    let gpufreq = root.join("gpufreq");
    let platform = root.join("platform");
    let devfreq = root.join("devfreq");
    let mali0 = root.join("mali0");
    let kernel_gpu = root.join("kernel_gpu");

    // Exact paths and frequencies from s5e9925.dts & LineageOS powerhint.json
    let sgpu_df = platform.join("16e00000.sgpu/devfreq/16e00000.sgpu");
    fs::create_dir_all(&sgpu_df).unwrap();
    fs::create_dir_all(&devfreq).unwrap();
    fs::create_dir_all(&kernel_gpu).unwrap();
    fs::write(sgpu_df.join("min_freq"), "303000\n").unwrap();
    fs::write(sgpu_df.join("max_freq"), "1306000\n").unwrap();
    fs::write(
        sgpu_df.join("available_frequencies"),
        "1306000 1095000 999000 903000 807000 605000 500000 404000 303000\n",
    )
    .unwrap();
    fs::write(kernel_gpu.join("gpu_min_clock"), "303000\n").unwrap();
    fs::write(kernel_gpu.join("gpu_max_clock"), "1306000\n").unwrap();
    fs::write(kernel_gpu.join("gpu_ifpo_runtime_control"), "1\n").unwrap();

    assert!(PlatformCapabilities::probe_gpu_in(
        &kgsl, &gpufreq, &platform, &devfreq
    ));
    assert_eq!(
        detect_vendor_in(&kgsl, &platform, &devfreq, &mali0),
        GpuVendor::Xclipse
    );

    set_xclipse_performance_in(&devfreq, &platform, &kernel_gpu);
    assert_eq!(
        fs::read_to_string(sgpu_df.join("min_freq")).unwrap().trim(),
        "1306000"
    );
    assert_eq!(
        fs::read_to_string(sgpu_df.join("max_freq")).unwrap().trim(),
        "1306000"
    );
    assert_eq!(
        fs::read_to_string(kernel_gpu.join("gpu_min_clock"))
            .unwrap()
            .trim(),
        "1306000"
    );
    assert_eq!(
        fs::read_to_string(kernel_gpu.join("gpu_ifpo_runtime_control"))
            .unwrap()
            .trim(),
        "0"
    );

    set_xclipse_balanced_in(&devfreq, &platform, &kernel_gpu);
    assert_eq!(
        fs::read_to_string(sgpu_df.join("min_freq")).unwrap().trim(),
        "303000"
    );
    assert_eq!(
        fs::read_to_string(sgpu_df.join("max_freq")).unwrap().trim(),
        "1306000"
    );
    assert_eq!(
        fs::read_to_string(kernel_gpu.join("gpu_min_clock"))
            .unwrap()
            .trim(),
        "303000"
    );
    assert_eq!(
        fs::read_to_string(kernel_gpu.join("gpu_ifpo_runtime_control"))
            .unwrap()
            .trim(),
        "1"
    );
}

#[test]
fn xclipse_sgpu_telemetry_reads_khz_over_1ghz_and_interface_utilization() {
    let tmp = TempDir::new().unwrap();
    let root = tmp.path();
    let kgsl = root.join("kgsl-3d0");
    let devfreq = root.join("devfreq");
    let platform = root.join("platform");
    let mali_kernel = root.join("mali_gpu_clock");
    let mtk_ged = root.join("mtk_ged_cur_freq");

    let sgpu_iface = platform.join("16e00000.sgpu/devfreq/16e00000.sgpu/interface");
    fs::create_dir_all(&sgpu_iface).unwrap();
    let sgpu_df = sgpu_iface.parent().unwrap();
    fs::write(sgpu_df.join("cur_freq"), "1306000\n").unwrap();
    fs::write(sgpu_iface.join("current_utilization"), "78\n").unwrap();

    let mut collector = GpuCollector::default();
    collector.detect_paths_in(&kgsl, &devfreq, &platform, &mali_kernel, &mtk_ged);
    assert!(collector.has_vendor());

    let snap = collector.snapshot();
    assert_eq!(snap.vendor, Some("sgpu"));
    assert_eq!(snap.cur_freq_mhz, Some(1306));
    assert_eq!(snap.load_pct, Some(78));
}

#[test]
fn xclipse_hardware_info_and_exynos_thermal_zones() {
    let tmp = TempDir::new().unwrap();
    let root = tmp.path();
    let kgsl_model = root.join("kgsl-3d0/gpu_model");
    let platform = root.join("platform");
    let devfreq = root.join("devfreq");
    let mali0 = root.join("mali0");
    fs::create_dir_all(platform.join("16e00000.sgpu")).unwrap();

    let (model, driver) = detect_gpu_and_driver_in(
        &kgsl_model,
        &platform,
        &devfreq,
        &mali0,
        Some("s5e9925"),
        None,
    );
    assert_eq!(model.as_deref(), Some("Samsung Xclipse 920"));
    assert_eq!(driver.as_deref(), Some("Samsung sgpu (devfreq)"));
    assert_eq!(infer_xclipse_model(Some("s5e9945")), "Samsung Xclipse 940");

    let thermal = root.join("thermal");
    for (idx, (ty, temp)) in [
        ("BIG", "58000"),
        ("MID", "51000"),
        ("LITTLE", "44000"),
        ("G3D", "61500"),
    ]
    .iter()
    .enumerate()
    {
        let zone = thermal.join(format!("thermal_zone{idx}"));
        fs::create_dir_all(&zone).unwrap();
        fs::write(zone.join("type"), format!("{ty}\n")).unwrap();
        fs::write(zone.join("temp"), format!("{temp}\n")).unwrap();
    }

    let snap = read_sysfs_zones_from(&thermal);
    assert_eq!(snap.cpu_temp_c, Some(58.0));
    assert_eq!(snap.gpu_temp_c, Some(61.5));
}

#[test]
fn cpuset_fallback_reads_tasks_when_cgroup_procs_missing() {
    let tmp = TempDir::new().unwrap();
    let missing_procs = tmp.path().join("cgroup.procs");
    let tasks = tmp.path().join("tasks");
    fs::write(&tasks, "invalid\n-5\n999999999\n").unwrap();

    let res = detect_foreground_task_from_cpuset_paths(&[
        missing_procs.to_str().unwrap(),
        tasks.to_str().unwrap(),
    ]);
    assert_eq!(res, None);
}
