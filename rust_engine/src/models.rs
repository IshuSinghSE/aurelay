/// Shared data models used across modules

#[derive(Debug, Clone)]
pub struct DeviceInfo {
    pub name: String,
    pub index: usize,
    pub is_monitor: bool,
    pub is_default: bool,
}

/// Transport mode for audio streaming.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TransportMode {
    /// Pure TCP streaming (current Android implementation)
    TcpOnly,
    /// TCP handshake + UDP streaming (no auth)
    TcpUdp,
    /// TCP handshake + authenticated UDP with session token
    TcpUdpAuth,
    /// TCP + TLS handshake + authenticated UDP
    TlsUdpAuth,
}

impl TransportMode {
    pub fn from_str(s: &str) -> Option<Self> {
        match s.to_lowercase().as_str() {
            "tcp" | "tcp_only" => Some(Self::TcpOnly),
            "tcp_udp" => Some(Self::TcpUdp),
            "tcp_udp_auth" => Some(Self::TcpUdpAuth),
            "tls_udp_auth" => Some(Self::TlsUdpAuth),
            _ => None,
        }
    }
    
    pub fn requires_handshake(&self) -> bool {
        matches!(self, Self::TcpUdpAuth | Self::TlsUdpAuth)
    }
    
    pub fn uses_udp(&self) -> bool {
        matches!(self, Self::TcpUdp | Self::TcpUdpAuth | Self::TlsUdpAuth)
    }
}

/// Session state after handshake.
#[derive(Debug, Clone)]
pub struct SessionState {
    pub token: Option<u64>,
    pub target_ip: String,
    pub target_port: u16,
    pub mode: TransportMode,
}

#[derive(Debug, Clone)]
pub struct StreamConfig {
    pub target_ip: String,
    pub port: u16,
    pub device_name: Option<String>,
    pub mode: TransportMode,
}
