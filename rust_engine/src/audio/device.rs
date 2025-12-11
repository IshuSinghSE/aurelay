use cpal::traits::{DeviceTrait, HostTrait};
use crate::models::DeviceInfo;

/// Smart device enumeration that prioritizes monitor/loopback devices.
/// 
/// On Linux: Looks for PipeWire/PulseAudio monitor devices (e.g., "*.monitor")
/// On Windows: Would look for WASAPI loopback devices
/// On macOS: Would look for BlackHole/LoopBack virtual devices

pub fn list_all_devices() -> Vec<DeviceInfo> {
    let host = cpal::default_host();
    let mut devices = Vec::new();
    
    if let Ok(input_devices) = host.input_devices() {
        for (idx, device) in input_devices.enumerate() {
            let name = device.name().unwrap_or_else(|_| format!("Device {}", idx));
            
            // Filter out confusing ALSA hardware devices
            // Keep: pipewire, pulse, default (these are the useful virtual devices)
            // Skip: hw:CARD, plughw:CARD, sysdefault, front: (these are low-level ALSA)
            if name.starts_with("hw:") || 
               name.starts_with("plughw:") ||
               name.starts_with("sysdefault:") ||
               name.starts_with("front:") {
                eprintln!("[device] Skipping low-level ALSA device: {}", name);
                continue;
            }
            
            let is_monitor = is_monitor_device(&name);
            let is_default = false;
            
            devices.push(DeviceInfo {
                name,
                index: idx,
                is_monitor,
                is_default,
            });
        }
    }
    
    devices
}

/// Get devices suitable for system audio capture (monitors/loopback).
pub fn get_monitor_devices() -> Vec<DeviceInfo> {
    list_all_devices()
        .into_iter()
        .filter(|d| d.is_monitor)
        .collect()
}

/// Find a specific device by name (supports fuzzy matching).
/// For PulseAudio monitor names, try to create a pulse device directly.
pub fn find_device_by_name(name: &str) -> Option<cpal::Device> {
    eprintln!("[device] Searching for: {}", name);
    
    // If requesting a PulseAudio monitor, try to open it via pulse: prefix
    if name.contains(".monitor") {
        eprintln!("[device] PulseAudio monitor requested");
        // Try the exact name
        let host = cpal::default_host();
        if let Ok(devices) = host.input_devices() {
            for device in devices {
                if let Ok(dev_name) = device.name() {
                    eprintln!("[device] Checking CPAL device: {}", dev_name);
                    if dev_name == name || dev_name.contains(name) {
                        eprintln!("[device] ✓ Found exact match: {}", dev_name);
                        return Some(device);
                    }
                }
            }
        }
        eprintln!("[device] Monitor device not found in CPAL, using default");
        return get_default_monitor();
    }
    
    let host = cpal::default_host();
    let devices_iter = host.input_devices().ok()?;
    let devices: Vec<cpal::Device> = devices_iter.collect();
    let needle = name.to_lowercase();

    // First: exact substring match (skip ALSA hardware devices)
    for device in &devices {
        let n = device.name().unwrap_or_default();
        let n_lower = n.to_lowercase();
        
        // Skip hw:CARD devices when looking for monitors
        if n.starts_with("hw:") || n.starts_with("plughw:") {
            continue;
        }
        
        if !needle.is_empty() && n_lower.contains(&needle) {
            eprintln!("[device] ✓ Match: {}", n);
            return Some(device.clone());
        }
    }

    // Second: tokenized matching (split on non-alphanumeric)
    let tokens: Vec<String> = name
        .split(|c: char| !c.is_alphanumeric())
        .filter(|s| s.len() > 2)
        .map(|s| s.to_lowercase())
        .collect();

    if !tokens.is_empty() {
        for device in &devices {
            let n = device.name().unwrap_or_default();
            let n_lower = n.to_lowercase();
            
            // Skip hw:CARD devices
            if n.starts_with("hw:") || n.starts_with("plughw:") {
                continue;
            }
            
            for t in &tokens {
                if n_lower.contains(t) {
                    eprintln!("[device] ✓ Token match ({}): {}", t, n);
                    return Some(device.clone());
                }
            }
        }
    }
    
    eprintln!("[device] ⚠ Not found: {}", name);
    None
}

/// Get the best monitor device (prioritizes virtual devices with proper 44.1kHz support).
pub fn get_default_monitor() -> Option<cpal::Device> {
    let host = cpal::default_host();
    
    eprintln!("[device] Scanning for best audio device...");
    
    // Debug: List all available input devices
    if let Ok(devices) = host.input_devices() {
        eprintln!("[device] Available CPAL input devices:");
        for (i, device) in devices.enumerate() {
            if let Ok(name) = device.name() {
                eprintln!("[device]   {}: {}", i, name);
            }
        }
    }
    
    // Priority 1: Any device with ".monitor" in the name (PulseAudio/PipeWire system audio monitors)
    if let Ok(devices) = host.input_devices() {
        for device in devices {
            if let Ok(name) = device.name() {
                if name.contains(".monitor") {
                    eprintln!("[device] ✓ Selected monitor device: {}", name);
                    return Some(device);
                }
            }
        }
    }
    
    // Priority 2: "pipewire" (best for modern Linux - supports 44.1kHz stereo)
    if let Ok(devices) = host.input_devices() {
        for device in devices {
            if let Ok(name) = device.name() {
                if name == "pipewire" || name.contains("pipewire") {
                    eprintln!("[device] ✓ Selected pipewire (optimal for 44.1kHz stereo)");
                    return Some(device);
                }
            }
        }
    }
    
    // Priority 2: "pulse" (fallback for PulseAudio)
    if let Ok(devices) = host.input_devices() {
        for device in devices {
            if let Ok(name) = device.name() {
                if name == "pulse" || name.contains("pulse") {
                    eprintln!("[device] ✓ Selected pulse");
                    return Some(device);
                }
            }
        }
    }
    
    // Priority 3: "default" 
    if let Ok(devices) = host.input_devices() {
        for device in devices {
            if let Ok(name) = device.name() {
                if name == "default" {
                    eprintln!("[device] ✓ Selected default");
                    return Some(device);
                }
            }
        }
    }
    
    // Priority 4: Any monitor device
    if let Ok(devices) = host.input_devices() {
        for device in devices {
            let name = device.name().unwrap_or_default();
            if is_monitor_device(&name) {
                eprintln!("[device] ✓ Selected monitor: {}", name);
                return Some(device);
            }
        }
    }
    
    // Last resort: system default
    eprintln!("[device] ⚠ No optimal device, using system default");
    host.default_input_device()
}

/// Detect if a device name indicates it's a monitor/loopback device.
fn is_monitor_device(name: &str) -> bool {
    let lower = name.to_lowercase();
    
    // Linux (PipeWire/PulseAudio patterns)
    if lower.contains(".monitor") || 
       lower.contains("monitor of") ||
       lower.contains("loopback") {
        return true;
    }
    
    // Additional PipeWire patterns
    if lower.starts_with("alsa_output") && lower.contains("monitor") {
        return true;
    }
    
    // PulseAudio sink monitors
    if lower.contains("sink") && lower.contains("monitor") {
        return true;
    }
    
    // Windows WASAPI loopback
    if lower.contains("stereo mix") || lower.contains("what u hear") {
        return true;
    }
    
    // macOS virtual devices
    if lower.contains("blackhole") || lower.contains("loopback") {
        return true;
    }
    
    false
}

/// Get a device by index (for direct selection from UI list).
pub fn get_device_by_index(index: usize) -> Option<cpal::Device> {
    let host = cpal::default_host();
    host.input_devices()
        .ok()?
        .nth(index)
}
