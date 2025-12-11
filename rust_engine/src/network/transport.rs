use std::net::{TcpStream, UdpSocket};
use std::io::Write;
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicUsize, Ordering};

/// Trait for audio transport mechanisms.
/// Allows swapping between TCP, UDP, or hybrid implementations.
pub trait AudioTransport: Send + Sync {
    fn send(&self, data: &[u8]) -> Result<(), String>;
    fn close(&self) -> Result<(), String>;
}

// ============================================================================
// TCP Transport (for current Android implementation)
// ============================================================================

pub struct TcpTransport {
    stream: Arc<Mutex<TcpStream>>,
    packet_count: AtomicUsize,
}

impl TcpTransport {
    pub fn new(target: &str) -> Result<Self, String> {
        eprintln!("[tcp_transport] Connecting to {}...", target);
        
        let stream = TcpStream::connect(target)
            .map_err(|e| format!("Failed to connect to {}: {}", target, e))?;
        
        stream
            .set_nodelay(true)
            .map_err(|e| format!("Failed to set TCP_NODELAY: {}", e))?;
        
        eprintln!("[tcp_transport] ✓ Connected");
        
        Ok(Self {
            stream: Arc::new(Mutex::new(stream)),
            packet_count: AtomicUsize::new(0),
        })
    }
}

impl AudioTransport for TcpTransport {
    fn send(&self, data: &[u8]) -> Result<(), String> {
        let count = self.packet_count.fetch_add(1, Ordering::Relaxed);
        
        let mut stream = self.stream.lock()
            .map_err(|e| format!("Failed to lock stream: {}", e))?;
        
        stream.write_all(data)
            .map_err(|e| format!("Write error: {}", e))?;
        
        // Flush immediately to ensure data is sent (critical for streaming!)
        stream.flush()
            .map_err(|e| format!("Flush error: {}", e))?;
        
        // Log more frequently to debug stalling
        if count < 20 || count % 100 == 0 {
            eprintln!("[tcp_transport] Packet #{}: sent {} bytes", count, data.len());
        }
        
        Ok(())
    }
    
    fn close(&self) -> Result<(), String> {
        eprintln!("[tcp_transport] Closing connection");
        // TcpStream will be dropped automatically
        Ok(())
    }
}

// ============================================================================
// UDP Transport (for low-latency streaming)
// ============================================================================

pub struct UdpTransport {
    socket: Arc<UdpSocket>,
    packet_count: AtomicUsize,
}

impl UdpTransport {
    pub fn new(target: &str) -> Result<Self, String> {
        eprintln!("[udp_transport] Setting up UDP to {}...", target);
        
        let socket = UdpSocket::bind("0.0.0.0:0")
            .map_err(|e| format!("Failed to bind UDP socket: {}", e))?;
        
        socket.connect(target)
            .map_err(|e| format!("Failed to connect to {}: {}", target, e))?;
        
        eprintln!("[udp_transport] ✓ UDP socket ready");
        
        Ok(Self {
            socket: Arc::new(socket),
            packet_count: AtomicUsize::new(0),
        })
    }
}

impl AudioTransport for UdpTransport {
    fn send(&self, data: &[u8]) -> Result<(), String> {
        let count = self.packet_count.fetch_add(1, Ordering::Relaxed);
        
        const CHUNK_SIZE: usize = 1400; // MTU-safe
        
        for chunk in data.chunks(CHUNK_SIZE) {
            self.socket.send(chunk)
                .map_err(|e| format!("UDP send error: {}", e))?;
        }
        
        if count == 0 || count % 500 == 0 {
            eprintln!("[udp_transport] ✓ Packet #{}: sent {} bytes", count, data.len());
        }
        
        Ok(())
    }
    
    fn close(&self) -> Result<(), String> {
        eprintln!("[udp_transport] Closing socket");
        Ok(())
    }
}

// ============================================================================
// Authenticated UDP Transport (prepends session token)
// ============================================================================

pub struct AuthenticatedUdpTransport {
    inner: UdpTransport,
    token: u64,
}

impl AuthenticatedUdpTransport {
    pub fn new(target: &str, token: u64) -> Result<Self, String> {
        eprintln!("[auth_udp] Setting up authenticated UDP with token");
        
        let inner = UdpTransport::new(target)?;
        
        Ok(Self { inner, token })
    }
}

impl AudioTransport for AuthenticatedUdpTransport {
    fn send(&self, data: &[u8]) -> Result<(), String> {
        // Prepend 8-byte token (little-endian)
        let mut packet = Vec::with_capacity(8 + data.len());
        packet.extend_from_slice(&self.token.to_le_bytes());
        packet.extend_from_slice(data);
        
        self.inner.send(&packet)
    }
    
    fn close(&self) -> Result<(), String> {
        self.inner.close()
    }
}

// ============================================================================
// Factory function to create transport based on mode
// ============================================================================

use crate::models::{TransportMode, SessionState};

pub fn create_transport(session: &SessionState) -> Result<Box<dyn AudioTransport>, String> {
    let target = format!("{}:{}", session.target_ip, session.target_port);
    
    match session.mode {
        TransportMode::TcpOnly => {
            eprintln!("[transport] Creating TCP-only transport");
            Ok(Box::new(TcpTransport::new(&target)?))
        }
        
        TransportMode::TcpUdp => {
            eprintln!("[transport] Creating TCP+UDP transport (no auth)");
            Ok(Box::new(UdpTransport::new(&target)?))
        }
        
        TransportMode::TcpUdpAuth | TransportMode::TlsUdpAuth => {
            eprintln!("[transport] Creating authenticated UDP transport");
            let token = session.token.ok_or("Session token required for authenticated mode")?;
            Ok(Box::new(AuthenticatedUdpTransport::new(&target, token)?))
        }
    }
}

