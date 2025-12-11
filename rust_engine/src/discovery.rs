use std::net::UdpSocket;
use std::time::{Duration, Instant};
use std::thread;

use crate::logs::push_log;

/// Discover receivers on the local network by broadcasting an
/// `AURELAY_DISCOVER` UDP packet and collecting `AURELAY_RESPONSE` replies.
pub fn discover_receivers_impl(timeout_secs: u64) -> Vec<String> {
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
                            push_log(&format!("Discovered: {}", entry));
                            results.push(entry);
                        }
                    }
                }
            }
            Err(_e) => {
                // timeout or other; continue until overall timeout
                thread::sleep(Duration::from_millis(50));
            }
        }
        thread::sleep(Duration::from_millis(10));
    }

    results
}

/// Send a connection request to a receiver and wait for ACCEPT/REJECT.
/// Returns Some("accepted") or Some("rejected") or None on timeout.
pub fn request_connect_and_wait_impl(host: String, timeout_secs: u64, device_name: String) -> Option<String> {
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
                    push_log(&format!("Connection ACCEPTED by {}", src.ip()));
                    return Some(String::from("accepted"));
                } else if text.starts_with("AURELAY_REJECT") {
                    println!("Connection REJECTED by {}", src.ip());
                    push_log(&format!("Connection REJECTED by {}", src.ip()));
                    return Some(String::from("rejected"));
                }
            }
            Err(_) => {
                std::thread::sleep(Duration::from_millis(50));
            }
        }
    }

    None
}
