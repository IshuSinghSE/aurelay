use lazy_static::lazy_static;
use std::sync::{Arc, Mutex};
use std::ffi::CString;
use std::os::raw::{c_char, c_int};

lazy_static! {
    pub static ref NATIVE_LOGS: Arc<Mutex<Vec<String>>> = Arc::new(Mutex::new(Vec::new()));
}

pub fn push_log(msg: &str) {
    let mut logs = NATIVE_LOGS.lock().unwrap();
    logs.push(msg.to_string());
    if logs.len() > 500 {
        let excess = logs.len() - 500;
        logs.drain(0..excess);
    }
}

/// C wrapper to get native logs (newline-separated). Caller supplies buffer.
#[no_mangle]
pub extern "C" fn get_native_logs_c(out_ptr: *mut c_char, out_len: usize) -> c_int {
    if out_ptr.is_null() || out_len == 0 {
        return -1;
    }
    let logs = NATIVE_LOGS.lock().unwrap();
    let joined = logs.join("\n");
    match CString::new(joined) {
        Ok(cstr) => {
            let bytes = cstr.as_bytes_with_nul();
            unsafe {
                let dst = std::slice::from_raw_parts_mut(out_ptr as *mut u8, out_len);
                let copy_len = std::cmp::min(bytes.len(), out_len);
                dst[..copy_len].copy_from_slice(&bytes[..copy_len]);
                if copy_len < out_len {
                    dst[copy_len] = 0;
                } else {
                    dst[out_len - 1] = 0;
                }
            }
            0
        }
        Err(_) => -1,
    }
}
