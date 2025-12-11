use std::net::{UdpSocket, TcpStream};
use std::time::{Duration, Instant};
use std::io::{Read, Write};

use crate::logs::push_log;
use crate::models::{SessionState, TransportMode};

// ============================================================================
// TCP Handshake Protocol (for authenticated modes)
// ============================================================================

const AUTH_REQUEST: &[u8] = b"AUTH_REQUEST";
const AUTH_OK: &[u8] = b"AUTH_OK";
const HANDSHAKE_TIMEOUT_SECS: u64 = 5;

/// Perform TCP handshake to establish authenticated session.
/// Returns SessionState with token (for TcpUdpAuth / TlsUdpAuth modes).
pub fn handshake(
    target_ip: String,
    target_port: u16,
    mode: TransportMode,
) -> Result<SessionState, String> {
    if !mode.requires_handshake() {
        // No handshake needed for TCP-only or simple TCP+UDP
        eprintln!("[session] Mode {:?} - skipping handshake", mode);
        return Ok(SessionState {
            token: None,
            target_ip,
            target_port,
            mode,
        });
    }
    
    eprintln!("[session] Starting handshake with {}:{}...", target_ip, target_port);
    push_log(&format!("[session] Handshake with {}:{}", target_ip, target_port));
    
    let addr = format!("{}:{}", target_ip, target_port);
    let mut stream = TcpStream::connect(&addr)
        .map_err(|e| format!("Handshake connection failed: {}", e))?;
    
    stream.set_read_timeout(Some(Duration::from_secs(HANDSHAKE_TIMEOUT_SECS)))
        .map_err(|e| format!("Failed to set timeout: {}", e))?;
    
    stream.set_write_timeout(Some(Duration::from_secs(HANDSHAKE_TIMEOUT_SECS)))
        .map_err(|e| format!("Failed to set timeout: {}", e))?;
    
    // Step 1: Send AUTH_REQUEST
    eprintln!("[session] → Sending AUTH_REQUEST");
    stream.write_all(AUTH_REQUEST)
        .map_err(|e| format!("Failed to send AUTH_REQUEST: {}", e))?;
    
    stream.flush()
        .map_err(|e| format!("Failed to flush: {}", e))?;
    
    // Step 2: Wait for AUTH_OK + 8-byte token
    let mut response = vec![0u8; AUTH_OK.len() + 8];
    stream.read_exact(&mut response)
        .map_err(|e| format!("Failed to read AUTH_OK: {}", e))?;
    
    // Verify AUTH_OK prefix
    if !response.starts_with(AUTH_OK) {
        return Err(format!("Invalid handshake response: {:?}", &response[..AUTH_OK.len()]));
    }
    
    // Extract token (8 bytes after AUTH_OK)
    let token_bytes: [u8; 8] = response[AUTH_OK.len()..AUTH_OK.len() + 8]
        .try_into()
        .map_err(|_| "Failed to extract token")?;
    
    let token = u64::from_le_bytes(token_bytes);
    
    eprintln!("[session] ✓ Handshake complete, token received");
    push_log("[session] Handshake complete");
    
    Ok(SessionState {
        token: Some(token),
        target_ip,
        target_port,
        mode,
    })
}

// ============================================================================
// Legacy discovery / connection request (keep for compatibility)
// ============================================================================
pub fn request_connection(host: &str, device_name: &str, timeout_secs: u64) -> Option<String> {
    let target = format!("{}:5002", host);
    
    let sock = match UdpSocket::bind("0.0.0.0:5002") {
        Ok(s) => s,
        Err(_) => match UdpSocket::bind("0.0.0.0:0") {
            Ok(s2) => s2,
            Err(e) => {
                eprintln!("[session] Failed to bind socket: {}", e);
                return None;
            }
        }
    };
    
    let _ = sock.set_broadcast(true);
    
    let msg = format!("AURELAY_CONNECT;{}", device_name);
    if let Err(e) = sock.send_to(msg.as_bytes(), &target) {
        eprintln!("[session] Failed to send connect request: {}", e);
        return None;
    }
    
    let timeout = Duration::from_secs(timeout_secs);
    let start = Instant::now();
    let mut buf = [0u8; 1024];
    
    while start.elapsed() < timeout {
        let _ = sock.set_read_timeout(Some(Duration::from_millis(500)));
        match sock.recv_from(&mut buf) {
            Ok((n, src)) => {
                if n == 0 { continue; }
                let text = String::from_utf8_lossy(&buf[..n]).to_string();
                if text.starts_with("AURELAY_ACCEPT") {
                    push_log(&format!("[session] Connection ACCEPTED by {}", src.ip()));
                    return Some(String::from("accepted"));
                } else if text.starts_with("AURELAY_REJECT") {
                    push_log(&format!("[session] Connection REJECTED by {}", src.ip()));
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
