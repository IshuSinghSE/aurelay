use cpal::traits::{DeviceTrait, StreamTrait};
use std::sync::Arc;

use crate::logs::push_log;

/// Audio packet callback type: receives raw PCM bytes.
pub type AudioCallback = Arc<dyn Fn(Vec<u8>) + Send + Sync>;

/// Start capturing audio from a device and send to callback.
/// Returns the Stream (must be kept alive or capture stops).
/// Enforces 44100 Hz stereo to match Android receiver expectations.
pub fn start_capture<F>(device: cpal::Device, callback: F) -> Result<cpal::Stream, String>
where
    F: Fn(&[u8]) + Send + 'static,
{
    let device_name = device.name().unwrap_or_else(|_| "Unknown".to_string());
    eprintln!("[capture] Starting capture on: {}", device_name);
    push_log(&format!("[capture] Starting capture on: {}", device_name));
    
    // Try to get a config close to 44100 Hz stereo (Android expectation)
    let mut supported_configs = device
        .supported_input_configs()
        .map_err(|e| format!("Failed to get supported configs: {}", e))?;
    
    // Find best matching config for 44100 Hz stereo
    let target_config = supported_configs
        .find(|config| {
            config.channels() == 2 
            && config.min_sample_rate().0 <= 44100 
            && config.max_sample_rate().0 >= 44100
        })
        .or_else(|| {
            // Fallback: any stereo config
            device.supported_input_configs().ok()?.find(|c| c.channels() == 2)
        })
        .unwrap_or_else(|| {
            // Last resort: use default config (will be converted to stereo later)
            let default_cfg = device.default_input_config().expect("No audio config");
            cpal::SupportedStreamConfigRange::new(
                default_cfg.channels(),
                default_cfg.sample_rate(),
                default_cfg.sample_rate(),
                cpal::SupportedBufferSize::Range { min: 64, max: 96000 },
                default_cfg.sample_format(),
            )
        });
    
    // Build config with 44100 Hz if supported, otherwise use max rate
    let sample_rate = if target_config.min_sample_rate().0 <= 44100 
        && target_config.max_sample_rate().0 >= 44100 {
        cpal::SampleRate(44100)
    } else {
        target_config.max_sample_rate()
    };
    
    let channels = target_config.channels();
    let sample_format = target_config.sample_format();
    
    let config = cpal::StreamConfig {
        channels,
        sample_rate,
        buffer_size: cpal::BufferSize::Fixed(512), // Smaller buffer = more frequent callbacks
    };
    
    eprintln!("[capture] Using config: {} Hz, {} channels, {:?} format (buffer: 512 frames)", 
        config.sample_rate.0, config.channels, sample_format);
    push_log(&format!("[capture] Config: {} Hz, {} ch, buffer=512", config.sample_rate.0, config.channels));
    
    // Warn if not matching Android expectations
    if config.sample_rate.0 != 44100 {
        eprintln!("[capture] ⚠ Warning: Sample rate {} Hz (Android expects 44100 Hz)", config.sample_rate.0);
    }
    if config.channels != 2 {
        eprintln!("[capture] ⚠ Warning: {} channel(s) (Android expects stereo)", config.channels);
    }
    
    let err_fn = |err| {
        eprintln!("[capture] ✗ Stream error: {}", err);
    };
    
    // Track if callback is being called
    use std::sync::atomic::{AtomicUsize, Ordering};
    static CALLBACK_COUNT: AtomicUsize = AtomicUsize::new(0);
    
    let channels_for_callback = channels;
    
    let stream = match sample_format {
        cpal::SampleFormat::F32 => {
            device.build_input_stream(
                &config,
                move |data: &[f32], _: &_| {
                    let count = CALLBACK_COUNT.fetch_add(1, Ordering::Relaxed);
                    // Log more frequently to debug stalling
                    if count < 20 || count % 100 == 0 {
                        eprintln!("[capture] Callback #{}, {} samples", count, data.len());
                    }
                    let bytes = sample_to_bytes_f32(data, channels_for_callback);
                    callback(&bytes);
                },
                err_fn,
                None,
            )
        }
        cpal::SampleFormat::I16 => {
            device.build_input_stream(
                &config,
                move |data: &[i16], _: &_| {
                    let count = CALLBACK_COUNT.fetch_add(1, Ordering::Relaxed);
                    if count < 20 || count % 100 == 0 {
                        eprintln!("[capture] Callback #{}, {} samples", count, data.len());
                    }
                    let bytes = sample_to_bytes_i16(data, channels_for_callback);
                    callback(&bytes);
                },
                err_fn,
                None,
            )
        }
        cpal::SampleFormat::U16 => {
            device.build_input_stream(
                &config,
                move |data: &[u16], _: &_| {
                    let count = CALLBACK_COUNT.fetch_add(1, Ordering::Relaxed);
                    if count < 20 || count % 100 == 0 {
                        eprintln!("[capture] Callback #{}, {} samples", count, data.len());
                    }
                    let bytes = sample_to_bytes_u16(data, channels_for_callback);
                    callback(&bytes);
                },
                err_fn,
                None,
            )
        }
        cpal::SampleFormat::U8 => {
            device.build_input_stream(
                &config,
                move |data: &[u8], _: &_| {
                    let count = CALLBACK_COUNT.fetch_add(1, Ordering::Relaxed);
                    if count < 20 || count % 100 == 0 {
                        eprintln!("[capture] Callback #{}, {} samples", count, data.len());
                        // Log sample data to detect silence vs real audio
                        let avg: u32 = data.iter().map(|&s| s as u32).sum::<u32>() / data.len() as u32;
                        eprintln!("[capture]   Average U8 value: {} (128=silence)", avg);
                    }
                    let bytes = sample_to_bytes_u8(data, channels_for_callback);
                    callback(&bytes);
                },
                err_fn,
                None,
            )
        }
        _ => {
            eprintln!("[capture] ✗ Unsupported format: {:?}", sample_format);
            return Err(format!("Unsupported sample format: {:?}", sample_format));
        }
    };
    
    let stream = stream.map_err(|e| {
        eprintln!("[capture] ✗ Failed to build stream: {}", e);
        format!("Failed to build stream: {}", e)
    })?;
    
    stream.play().map_err(|e| {
        eprintln!("[capture] ✗ Failed to play stream: {}", e);
        format!("Failed to play stream: {}", e)
    })?;
    
    eprintln!("[capture] ✓ Stream started successfully");
    push_log(&format!("[capture] Stream started: {}", device_name));
    Ok(stream)
}

/// Helper: Convert sample slice to raw bytes (PCM_S16LE format).
/// Receiver expects signed 16-bit little-endian PCM at 44100 Hz stereo.
fn sample_to_bytes_f32(samples: &[f32], channels: u16) -> Vec<u8> {
    // Convert F32 [-1.0, 1.0] to I16 [-32768, 32767]
    let samples_i16: Vec<i16> = samples
        .iter()
        .map(|&s| (s.clamp(-1.0, 1.0) * 32767.0) as i16)
        .collect();
    
    // If mono, duplicate to stereo
    let stereo_samples = if channels == 1 {
        samples_i16.iter().flat_map(|&s| [s, s]).collect::<Vec<i16>>()
    } else {
        samples_i16
    };
    
    // Convert to bytes (little-endian)
    let mut bytes = Vec::with_capacity(stereo_samples.len() * 2);
    for sample in stereo_samples {
        bytes.extend_from_slice(&sample.to_le_bytes());
    }
    bytes
}

fn sample_to_bytes_i16(samples: &[i16], channels: u16) -> Vec<u8> {
    // If mono, duplicate to stereo
    let stereo_samples = if channels == 1 {
        samples.iter().flat_map(|&s| [s, s]).collect::<Vec<i16>>()
    } else {
        samples.to_vec()
    };
    
    // Already in correct format, just convert to bytes
    let mut bytes = Vec::with_capacity(stereo_samples.len() * 2);
    for &sample in &stereo_samples {
        bytes.extend_from_slice(&sample.to_le_bytes());
    }
    bytes
}

fn sample_to_bytes_u16(samples: &[u16], channels: u16) -> Vec<u8> {
    // Convert U16 [0, 65535] to I16 [-32768, 32767]
    let samples_i16: Vec<i16> = samples
        .iter()
        .map(|&s| (s as i32 - 32768) as i16)
        .collect();
    
    // If mono, duplicate to stereo
    let stereo_samples = if channels == 1 {
        samples_i16.iter().flat_map(|&s| [s, s]).collect::<Vec<i16>>()
    } else {
        samples_i16
    };
    
    let mut bytes = Vec::with_capacity(stereo_samples.len() * 2);
    for sample in stereo_samples {
        bytes.extend_from_slice(&sample.to_le_bytes());
    }
    bytes
}

fn sample_to_bytes_u8(samples: &[u8], channels: u16) -> Vec<u8> {
    // Convert U8 [0, 255] to I16 [-32768, 32767]
    // Standard conversion: center at 128, then scale to 16-bit range
    let samples_i16: Vec<i16> = samples
        .iter()
        .map(|&s| ((s as i16 - 128) << 8))  // Shift left 8 bits for proper scaling
        .collect();
    
    // Debug: Log first conversion to verify format
    static FIRST_LOG: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(true);
    if FIRST_LOG.swap(false, std::sync::atomic::Ordering::Relaxed) {
        eprintln!("[capture] First U8 samples: {:?}", &samples[..samples.len().min(8)]);
        eprintln!("[capture] Converted to I16: {:?}", &samples_i16[..samples_i16.len().min(8)]);
        eprintln!("[capture] Channels reported: {}, sample count: {}", channels, samples.len());
    }
    
    // If mono, duplicate to stereo
    let stereo_samples = if channels == 1 {
        samples_i16.iter().flat_map(|&s| [s, s]).collect::<Vec<i16>>()
    } else {
        samples_i16
    };
    
    let mut bytes = Vec::with_capacity(stereo_samples.len() * 2);
    for sample in stereo_samples {
        bytes.extend_from_slice(&sample.to_le_bytes());
    }
    bytes
}

/// Wrapper around cpal::Stream to make it Send+Sync.
pub struct SendStream {
    pub _stream: cpal::Stream,
}

unsafe impl Send for SendStream {}
unsafe impl Sync for SendStream {}
