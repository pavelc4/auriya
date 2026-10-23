#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FpsSource {
    Ebpf,
    Sysfs,
}

#[derive(Debug, Clone)]
pub struct FpsReading {
    pub fps: f64,
    pub source: FpsSource,
}
