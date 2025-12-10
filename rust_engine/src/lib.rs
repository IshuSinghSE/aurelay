use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use lazy_static::lazy_static;
use std::net::UdpSocket;
use std::time::{Duration, Instant};
use std::sync::{Arc, Mutex};
use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int};

uniffi::setup_scaffolding!();

// Wrapper around cpal::Stream to allow sending it between threads.
struct SendStream {
    _stream: cpal::Stream,
}
unsafe impl Send for SendStream {}
unsafe impl Sync for SendStream {}

struct AudioState {
    stream: Option<SendStream>,
    is_running: bool,
}

/// Discover receivers on the local network by broadcasting an
/// `AURELAY_DISCOVER` UDP packet and collecting `AURELAY_RESPONSE` replies.
#[uniffi::export]
pub fn discover_receivers(timeout_secs: u64) -> Vec<String> {
    let mut results: Vec<String> = Vec::new();

    // Create socket for broadcasting and receiving
    let socket = match UdpSocket::bind("0.0.0.0:0") {
        Ok(s) => s,
        Err(e) => {
            eprintln!("discover: failed to bind socket: {}", e);
            return results;
        }
    };

    // Allow broadcasts
    let _ = socket.set_broadcast(true);

    // Determine local subnet broadcast (best-effort)
    let mut targets = vec![String::from("255.255.255.255")];
    if let Ok(tmp) = UdpSocket::bind("0.0.0.0:0") {
        if tmp.connect("8.8.8.8:80").is_ok() {
            if let Ok(local) = tmp.local_addr() {
                let ip = local.ip().to_string();
                let parts: Vec<&str> = ip.split('.').collect();
                if parts.len() == 4 {
                    let mut sb = parts.clone();
                    sb[3] = "255";
                    let subnet = format!("{}.{}.{}.255", sb[0], sb[1], sb[2]);
                    if !targets.contains(&subnet) {
                        targets.push(subnet);
                    }
                }
            }
        }
    }

    // Message to broadcast
    let msg = b"AURELAY_DISCOVER";

    // Send to all broadcast targets
    for t in &targets {
        let addr = format!("{}:5002", t);
        let _ = socket.send_to(msg, &addr);
    }

    // Set read timeout for receive loop
    let timeout = Duration::from_secs(timeout_secs);
    let start = Instant::now();
    let mut buf = [0u8; 1500];

    while start.elapsed() < timeout {
        // compute remaining time for this recv
        let remaining = timeout.checked_sub(start.elapsed()).unwrap_or_default();
        let _ = socket.set_read_timeout(Some(Duration::from_millis(500)));

        match socket.recv_from(&mut buf) {
            Ok((n, src)) => {
                if n == 0 { continue }
                let text = String::from_utf8_lossy(&buf[..n]).to_string();
                if text.starts_with("AURELAY_RESPONSE") {
                    let parts: Vec<&str> = text.split(';').collect();
                    if parts.len() >= 3 {
                        let port = parts[1];
                        let name = parts[2];
                        let entry = format!("{}:{};{}", src.ip(), port, name);
                        if !results.contains(&entry) {
                            results.push(entry);
                        }
                    }
                }
            }
            Err(_e) => {
                // timeout or other; continue until overall timeout
                std::thread::sleep(Duration::from_millis(50));
            }
        }
        // small sleep to avoid busy loop
        std::thread::sleep(Duration::from_millis(10));
    }

    results
}

/// Send a connection request to a receiver and wait for ACCEPT/REJECT.
/// Returns Some("accepted") or Some("rejected") or None on timeout.
#[uniffi::export]
pub fn request_connect_and_wait(host: String, timeout_secs: u64, device_name: String) -> Option<String> {
    let target = format!("{}:5002", host);

    // Try to bind to the discovery port so replies land here
    let sock = match UdpSocket::bind("0.0.0.0:5002") {
        Ok(s) => s,
        Err(_) => match UdpSocket::bind("0.0.0.0:0") {
            Ok(s2) => s2,
            Err(e) => {
                eprintln!("connect: failed to bind socket: {}", e);
                return None;
            }
        }
    };

    let _ = sock.set_broadcast(true);

    let msg = format!("AURELAY_CONNECT;{}", device_name);
    if let Err(e) = sock.send_to(msg.as_bytes(), &target) {
        eprintln!("connect: failed to send connect request: {}", e);
        return None;
    }

    let timeout = Duration::from_secs(timeout_secs);
    let start = Instant::now();
    let mut buf = [0u8; 1024];

    while start.elapsed() < timeout {
        let _ = sock.set_read_timeout(Some(Duration::from_millis(500)));
        match sock.recv_from(&mut buf) {
            Ok((n, src)) => {
                if n == 0 { continue }
                let text = String::from_utf8_lossy(&buf[..n]).to_string();
                if text.starts_with("AURELAY_ACCEPT") {
                    println!("Connection ACCEPTED by {}", src.ip());
                    return Some(String::from("accepted"));
                } else if text.starts_with("AURELAY_REJECT") {
                    println!("Connection REJECTED by {}", src.ip());
                    return Some(String::from("rejected"));
                }
            }
            Err(_) => {
                // no packet, keep waiting
                std::thread::sleep(Duration::from_millis(50));
            }
        }
    }

    None
}

/// C-compatible wrapper: `discover_receivers_c(timeout_secs, out_ptr, out_len)`
/// Writes a newline-separated list of `ip:port;name` into `out_ptr` (null-terminated).
/// Returns 0 on success, -1 on error.
#[no_mangle]
pub extern "C" fn discover_receivers_c(timeout_secs: u64, out_ptr: *mut c_char, out_len: usize) -> c_int {
    if out_ptr.is_null() || out_len == 0 {
        return -1;
    }

    let results = discover_receivers(timeout_secs);
    let out_str = results.join("\n");

    match CString::new(out_str) {
        Ok(cstr) => {
            let bytes = cstr.as_bytes_with_nul();
            unsafe {
                let dst = std::slice::from_raw_parts_mut(out_ptr as *mut u8, out_len);
                let copy_len = std::cmp::min(bytes.len(), out_len);
                dst[..copy_len].copy_from_slice(&bytes[..copy_len]);
                if copy_len < out_len {
                    dst[copy_len] = 0;
                } else {
                    // ensure last byte is null
                    dst[out_len - 1] = 0;
                }
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
    if host.is_null() || device_name.is_null() || out_ptr.is_null() || out_len == 0 {
        return -1;
    }

    let host_c = unsafe { CStr::from_ptr(host) };
    let dev_c = unsafe { CStr::from_ptr(device_name) };

    let host_str = match host_c.to_str() {
        Ok(s) => s.to_string(),
        Err(_) => return -1,
    };
    let dev_str = match dev_c.to_str() {
        Ok(s) => s.to_string(),
        Err(_) => return -1,
    };

    let res = request_connect_and_wait(host_str, timeout_secs, dev_str);
    let out_text = match res {
        Some(s) => s,
        None => String::from("timeout"),
    };

    match CString::new(out_text) {
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
lazy_static! {
    static ref AUDIO_STATE: Arc<Mutex<AudioState>> = Arc::new(Mutex::new(AudioState {
        stream: None,
        is_running: false,
    }));
}

#[uniffi::export]
pub fn start_stream(target_ip: String) {
    let mut state = AUDIO_STATE.lock().unwrap();
    if state.is_running {
        println!("Stream already running");
        return;
    }

    println!("Starting stream to {}", target_ip);

    let socket = Arc::new(UdpSocket::bind("0.0.0.0:0").expect("couldn't bind to address"));
    socket.set_broadcast(true).expect("failed to enable broadcast");

    let target_addr = if target_ip.contains(":") {
        target_ip
    } else {
        format!("{}:5000", target_ip)
    };

    if let Err(e) = socket.connect(&target_addr) {
         eprintln!("Failed to connect UDP socket: {}", e);
         return;
    }
    println!("UDP socket connected to {}", target_addr);

    let host = cpal::default_host();
    let device = match find_input_device(&host) {
        Some(d) => d,
        None => {
            eprintln!("No suitable audio input device found!");
            return;
        }
    };
    println!("Using audio device: {}", device.name().unwrap_or("Unknown".to_string()));

    let config = match device.default_input_config() {
        Ok(c) => c,
        Err(e) => {
            eprintln!("Failed to get default input config: {}", e);
            return;
        }
    };

    let err_fn = move |err| {
        eprintln!("an error occurred on stream: {}", err);
    };

    let socket_clone = socket.clone();

    let stream = match config.sample_format() {
        cpal::SampleFormat::F32 => device.build_input_stream(
            &config.into(),
            move |data: &[f32], _: &_| write_audio_data(data, &socket_clone),
            err_fn,
            None,
        ),
        cpal::SampleFormat::I16 => device.build_input_stream(
            &config.into(),
            move |data: &[i16], _: &_| write_audio_data(data, &socket_clone),
            err_fn,
            None,
        ),
        cpal::SampleFormat::U16 => device.build_input_stream(
            &config.into(),
            move |data: &[u16], _: &_| write_audio_data(data, &socket_clone),
            err_fn,
            None,
        ),
        _ => return,
    };

    match stream {
        Ok(s) => {
            s.play().unwrap();
            state.stream = Some(SendStream { _stream: s });
            state.is_running = true;
            println!("Stream started successfully");
        }
        Err(e) => {
            eprintln!("Failed to build input stream: {}", e);
        }
    }
}

#[uniffi::export]
pub fn stop_stream() {
    let mut state = AUDIO_STATE.lock().unwrap();
    if state.is_running {
        println!("Stopping stream");
        state.stream = None;
        state.is_running = false;
    }
}

fn find_input_device(host: &cpal::Host) -> Option<cpal::Device> {
    let devices = host.input_devices().ok()?;
    for device in devices {
        let name = device.name().unwrap_or_default().to_lowercase();
        if name.contains("monitor") || name.contains("analog stereo") {
             return Some(device);
        }
    }
    host.default_input_device()
}

fn write_audio_data<T>(input: &[T], socket: &UdpSocket)
where
    T: cpal::Sample,
{
    let size = std::mem::size_of::<T>();
    let byte_len = input.len() * size;
    let bytes = unsafe {
        std::slice::from_raw_parts(input.as_ptr() as *const u8, byte_len)
    };

    const CHUNK_SIZE: usize = 1400;
    for chunk in bytes.chunks(CHUNK_SIZE) {
        if let Err(_e) = socket.send(chunk) {
            // Ignore errors
        }
    }
}
