#[cfg(target_os = "android")]
pub fn get_system_property(key: &str) -> Option<String> {
    use std::ffi::{CStr, CString};
    unsafe extern "C" {
        fn __system_property_get(
            name: *const libc::c_char,
            value: *mut libc::c_char,
        ) -> libc::c_int;
    }
    let c_key = CString::new(key).ok()?;
    let mut buf = [0u8; 92]; // PROP_VALUE_MAX
    let len =
        unsafe { __system_property_get(c_key.as_ptr(), buf.as_mut_ptr() as *mut libc::c_char) };
    if len > 0 {
        let val = unsafe { CStr::from_ptr(buf.as_ptr() as *const libc::c_char) };
        let s = val.to_string_lossy().trim().to_string();
        if !s.is_empty() {
            return Some(s);
        }
    }
    None
}

#[cfg(not(target_os = "android"))]
pub fn get_system_property(_key: &str) -> Option<String> {
    None
}
