#[derive(Debug, Clone)]
pub struct PidController {
    kp: f32,
    ki: f32,
    kd: f32,
    prev_error: f32,
    integral: f32,
}

impl PidController {
    pub fn new(kp: f32, ki: f32, kd: f32) -> Self {
        Self {
            kp,
            ki,
            kd,
            prev_error: 0.0,
            integral: 0.0,
        }
    }

    pub fn reset(&mut self) {
        self.prev_error = 0.0;
        self.integral = 0.0;
    }

    pub fn next(&mut self, target: f32, actual: f32) -> f32 {
        let error = target - actual;

        self.integral += error;
        self.integral = self.integral.clamp(-100.0, 100.0);

        let p = self.kp * error;
        let i = self.ki * self.integral;
        let d = self.kd * (error - self.prev_error);

        self.prev_error = error;

        p + i + d
    }
}
