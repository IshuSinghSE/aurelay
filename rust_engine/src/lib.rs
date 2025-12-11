use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int};
use std::sync::{Arc, Mutex};
use cpal::traits::DeviceTrait;

// Modular structure: Keep lib.rs as a thin FFI bridge
mod models;
mod audio;
mod network;
mod discovery;
mod logs;
mod ffmpeg;

pub use models::*;
pub use logs::*;

uniffi::setup_scaffolding!();

// Global engine state (singleton pattern)
use once_cell::sync::Lazy;

struct EngineState {
    is_streaming: bool,
    stream: Option<audio::SendStream>,
    transport: Option<Box<dyn network::AudioTransport>>,
}

impl Default for EngineState {
    fn default() -> Self {
        Self {
            is_streaming: false,
            stream: None,
            transport: None,
        }
    }
}

static ENGINE: Lazy<Mutex<EngineState>> = Lazy::new(|| {
    Mutex::new(EngineState::default())
});

// === Core streaming logic using modular transport system ===

fn start_streaming_impl(target_ip: String, device_name: Option<String>, mode_str: Option<String>) {
    let mut engine = ENGINE.lock().unwrap();
    
    if engine.is_streaming {
        eprintln!("[engine] Already streaming");
        return;
    }
    
    // Determine transport mode
    let mode = mode_str
        .and_then(|s| TransportMode::from_str(&s))
        .unwrap_or(TransportMode::TcpOnly);
    
    eprintln!("[engine] ========== Starting Stream ({:?}) ==========", mode);
    push_log(&format!("[engine] Starting {:?} stream to {}", mode, target_ip));
    
    let target_addr = if target_ip.contains(":") {
        target_ip.clone()
    } else {
        format!("{}:5000", target_ip)
    };
    
    let (ip, port) = if let Some(colon_pos) = target_addr.find(':') {
        let ip = target_addr[..colon_pos].to_string();
        let port = target_addr[colon_pos + 1..].parse::<u16>().unwrap_or(5000);
        (ip, port)
    } else {
        (target_addr.clone(), 5000)
    };
    
    // Step 1: Establish session (may involve handshake)
    eprintln!("[engine] Step 1: Establishing session...");
    let session = if mode.requires_handshake() {
        match network::handshake(ip.clone(), port, mode) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("[engine] ✗ Handshake failed: {}", e);
                push_log(&format!("[engine] Handshake error: {}", e));
                return;
            }
        }
    } else {
        SessionState {
            token: None,
            target_ip: ip,
            target_port: port,
            mode,
        }
    };
    
    eprintln!("[engine] ✓ Session established");
    
    // Step 2: Create transport based on mode
    eprintln!("[engine] Step 2: Creating transport...");
    let transport = match network::create_transport(&session) {
        Ok(t) => t,
        Err(e) => {
            eprintln!("[engine] ✗ Failed to create transport: {}", e);
            push_log(&format!("[engine] Transport error: {}", e));
            return;
        }
    };
    
    eprintln!("[engine] ✓ Transport ready");
    
    // Step 3: Find audio device
    eprintln!("[engine] Step 3: Finding audio device...");
    let device = match device_name {
        Some(ref name) if !name.is_empty() => {
            eprintln!("[engine] Looking for device: {}", name);
            audio::find_device_by_name(name)
                .or_else(|| {
                    eprintln!("[engine] Device '{}' not found, trying default monitor", name);
                    audio::get_default_monitor()
                })
        }
        _ => {
            eprintln!("[engine] Using default monitor");
            audio::get_default_monitor()
        }
    };
    
    let device = match device {
        Some(d) => d,
        None => {
            eprintln!("[engine] ✗ No suitable audio device found");
            push_log("[engine] ERROR: No audio device available");
            return;
        }
    };
    
    let dev_name = device.name().unwrap_or_else(|_| "Unknown".to_string());
    eprintln!("[engine] ✓ Selected device: {}", dev_name);
    push_log(&format!("[engine] Using device: {}", dev_name));
    
    // Step 4: Start capture with transport callback
    eprintln!("[engine] Step 4: Starting audio capture...");
    let transport_arc = Arc::new(Mutex::new(transport));
    let transport_clone = transport_arc.clone();
    
    let capture_result = audio::start_capture(device, move |data| {
        if let Ok(transport) = transport_clone.lock() {
            let _ = transport.send(data);
        }
    });
    
    match capture_result {
        Ok(stream) => {
            eprintln!("[engine] ✓ Capture started successfully");
            engine.stream = Some(audio::SendStream { _stream: stream });
            
            // Extract transport from Arc<Mutex<Box>>
            match Arc::try_unwrap(transport_arc) {
                Ok(mutex) => {
                    engine.transport = Some(mutex.into_inner().unwrap());
                }
                Err(_) => {
                    eprintln!("[engine] Warning: Transport has multiple references");
                }
            }
            
            engine.is_streaming = true;
            push_log("[engine] Stream started successfully");
            eprintln!("[engine] ========================================");
        }
        Err(e) => {
            eprintln!("[engine] ✗ Failed to start capture: {}", e);
            push_log(&format!("[engine] Capture error: {}", e));
        }
    }
}

fn stop_streaming_impl() {
    let mut engine = ENGINE.lock().unwrap();
    
    if engine.is_streaming {
        push_log("[engine] Stopping stream");
        
        // Close transport
        if let Some(ref transport) = engine.transport {
            let _ = transport.close();
        }
        
        engine.stream = None;
        engine.transport = None;
        engine.is_streaming = false;
    }
}

// === FFI C Wrappers (thin bridge to implementation) ===

/// C-compatible wrapper to start streaming audio to target IP.
/// Uses TCP-only mode by default (compatible with current Android).
#[no_mangle]
pub extern "C" fn start_stream_c(host: *const c_char) -> c_int {
    if host.is_null() { return -1; }
    let c = unsafe { CStr::from_ptr(host) };
    let host_str = match c.to_str() { Ok(s) => s.to_string(), Err(_) => return -1 };
    start_streaming_impl(host_str, None, Some("tcp_only".to_string()));
    0
}

/// C-compatible wrapper to stop streaming audio.
#[no_mangle]
pub extern "C" fn stop_stream_c() -> c_int {
    stop_streaming_impl();
    0
}

/// C-compatible wrapper to start streaming with optional device name.
#[no_mangle]
pub extern "C" fn start_stream_with_device_c(host: *const c_char, device: *const c_char) -> c_int {
    if host.is_null() { return -1; }
    let host_c = unsafe { CStr::from_ptr(host) };
    let host_str = match host_c.to_str() { Ok(s) => s.to_string(), Err(_) => return -1 };
    let device_opt = if device.is_null() { None } else { let d = unsafe { CStr::from_ptr(device) }; match d.to_str() { Ok(s) => Some(s.to_string()), Err(_) => None } };
    start_streaming_impl(host_str, device_opt, Some("tcp_only".to_string()));
    0
}

/// Start stream with transport mode selection.
/// mode: "tcp_only", "tcp_udp", "tcp_udp_auth", "tls_udp_auth"
#[no_mangle]
pub extern "C" fn start_stream_with_mode_c(
    host: *const c_char,
    device: *const c_char,
    mode: *const c_char
) -> c_int {
    if host.is_null() { return -1; }
    let host_c = unsafe { CStr::from_ptr(host) };
    let host_str = match host_c.to_str() { Ok(s) => s.to_string(), Err(_) => return -1 };
    
    let device_opt = if device.is_null() {
        None
    } else {
        let d = unsafe { CStr::from_ptr(device) };
        match d.to_str() { Ok(s) => Some(s.to_string()), Err(_) => None }
    };
    
    let mode_opt = if mode.is_null() {
        Some("tcp_only".to_string())
    } else {
        let m = unsafe { CStr::from_ptr(mode) };
        match m.to_str() { Ok(s) => Some(s.to_string()), Err(_) => Some("tcp_only".to_string()) }
    };
    
    start_streaming_impl(host_str, device_opt, mode_opt);
    0
}

/// C-compatible wrapper: `discover_receivers_c(timeout_secs, out_ptr, out_len)`
/// Writes a newline-separated list of `ip:port;name` into `out_ptr` (null-terminated).
/// Returns 0 on success, -1 on error.
#[no_mangle]
pub extern "C" fn discover_receivers_c(timeout_secs: u64, out_ptr: *mut c_char, out_len: usize) -> c_int {
    if out_ptr.is_null() || out_len == 0 { return -1; }
    let results = discovery::discover_receivers_impl(timeout_secs);
    let out_str = results.join("\n");
    match CString::new(out_str) {
        Ok(cstr) => {
            let bytes = cstr.as_bytes_with_nul();
            unsafe {
                let dst = std::slice::from_raw_parts_mut(out_ptr as *mut u8, out_len);
                let copy_len = std::cmp::min(bytes.len(), out_len);
                dst[..copy_len].copy_from_slice(&bytes[..copy_len]);
                if copy_len < out_len { dst[copy_len] = 0; } else { dst[out_len - 1] = 0; }
            }
            0
        }
        Err(_) => -1,
    }
}

/// C-compatible wrapper: `request_connect_and_wait_c(host, timeout_secs, device_name, out_ptr, out_len)`
/// Writes `accepted`/`rejected`/`timeout` into `out_ptr` (null-terminated). Returns 0 on success.
#[no_mangle]
pub extern "C" fn request_connect_and_wait_c(host: *const c_char, timeout_secs: u64, device_name: *const c_char, out_ptr: *mut c_char, out_len: usize) -> c_int {
    if host.is_null() || device_name.is_null() || out_ptr.is_null() || out_len == 0 { return -1; }
    let host_c = unsafe { CStr::from_ptr(host) };
    let dev_c = unsafe { CStr::from_ptr(device_name) };
    let host_str = match host_c.to_str() { Ok(s) => s.to_string(), Err(_) => return -1 };
    let dev_str = match dev_c.to_str() { Ok(s) => s.to_string(), Err(_) => return -1 };
    let res = discovery::request_connect_and_wait_impl(host_str, timeout_secs, dev_str);
    let out_text = match res { Some(s) => s, None => String::from("timeout") };
    match CString::new(out_text) {
        Ok(cstr) => {
            let bytes = cstr.as_bytes_with_nul();
            unsafe {
                let dst = std::slice::from_raw_parts_mut(out_ptr as *mut u8, out_len);
                let copy_len = std::cmp::min(bytes.len(), out_len);
                dst[..copy_len].copy_from_slice(&bytes[..copy_len]);
                if copy_len < out_len { dst[copy_len] = 0; } else { dst[out_len - 1] = 0; }
            }
            0
        }
        Err(_) => -1,
    }
}

/// C-compatible wrapper to start ffmpeg-based stream: delegates to ffmpeg module.
#[no_mangle]
pub extern "C" fn start_ffmpeg_stream_c(host: *const c_char, port: c_int, device: *const c_char) -> c_int {
    if host.is_null() { return -1; }
    let host_c = unsafe { CStr::from_ptr(host) };
    let host_str = match host_c.to_str() { Ok(s) => s.to_string(), Err(_) => return -1 };
    let port_u = if port <= 0 { 5000u16 } else { port as u16 };
    let device_opt = if device.is_null() { None } else { let d = unsafe { CStr::from_ptr(device) }; match d.to_str() { Ok(s) => Some(s.to_string()), Err(_) => None } };
    ffmpeg::start_ffmpeg_stream(host_str, port_u, device_opt);
    0
}

#[no_mangle]
pub extern "C" fn stop_ffmpeg_stream_c() -> c_int { ffmpeg::stop_ffmpeg_stream(); 0 }

/// C wrapper to list CPAL input devices (newline-separated)
/// Now includes [Monitor] prefix for monitor devices.
#[no_mangle]
pub extern "C" fn list_cpal_input_devices_c(out_ptr: *mut c_char, out_len: usize) -> c_int {
    if out_ptr.is_null() || out_len == 0 { return -1; }
    let devices = audio::list_all_devices();
    let names: Vec<String> = devices.iter().map(|d| {
        if d.is_monitor {
            format!("[Monitor] {}", d.name)
        } else {
            d.name.clone()
        }
    }).collect();
    let joined = names.join("\n");
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

