#[cfg(target_os = "android")]
pub fn get_system_property(key: &str) -> Option<String> {
    use std::ffi::CString;
    unsafe extern "C" {
        fn __system_property_get(
            name: *const libc::c_char,
            value: *mut libc::c_char,
        ) -> libc::c_int;
    }
    let c_key = CString::new(key).ok()?;
    let mut buf = [0u8; 93];
    let len =
        unsafe { __system_property_get(c_key.as_ptr(), buf.as_mut_ptr() as *mut libc::c_char) };
    if len > 0 {
        let actual_len = (len as usize).min(92);
        if let Ok(s) = std::str::from_utf8(&buf[..actual_len]) {
            let trimmed = s.trim();
            if !trimmed.is_empty() {
                return Some(trimmed.to_string());
            }
        }
    }
    None
}

#[cfg(not(target_os = "android"))]
pub fn get_system_property(_key: &str) -> Option<String> {
    None
}
