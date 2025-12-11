use std::process::{Command, Stdio};
use std::io::{Read, Write};
use std::net::TcpStream;
use std::sync::{Arc, Mutex};

lazy_static::lazy_static! {
    // Control channel sender stored here; sending a message requests the ffmpeg thread to stop.
    pub static ref FFMPEG_CONTROL: Arc<Mutex<Option<std::sync::mpsc::Sender<()>>>> = Arc::new(Mutex::new(None));
}

use crate::logs::push_log;

/// Start streaming by spawning `ffmpeg` to capture from PulseAudio/PipeWire and
/// pipe raw PCM to a TCP connection to `host:port`.
pub fn start_ffmpeg_stream(host: String, port: u16, device_name: Option<String>) {
    // If a process is already running, ignore
    {
        let guard = FFMPEG_CONTROL.lock().unwrap();
        if guard.is_some() {
            eprintln!("ffmpeg stream already running");
            return;
        }
    }

    // Build ffmpeg command
    let device = device_name.unwrap_or_else(|| String::from("default"));
    let args = vec![
        "-f".to_string(),
        "pulse".to_string(),
        "-i".to_string(),
        device.clone(),
        "-f".to_string(),
        "s16le".to_string(),
        "-acodec".to_string(),
        "pcm_s16le".to_string(),
        "-ar".to_string(),
        "44100".to_string(),
        "-ac".to_string(),
        "2".to_string(),
        "-".to_string(),
    ];

    let mut cmd = Command::new("ffmpeg");
    for a in &args { cmd.arg(a); }
    cmd.stdout(Stdio::piped()).stderr(Stdio::piped());

    match cmd.spawn() {
        Ok(mut child) => {
            // create control channel to signal stop
            let (tx, rx) = std::sync::mpsc::channel::<()>();

            // store sender so stop function can request shutdown
            {
                let mut guard = FFMPEG_CONTROL.lock().unwrap();
                *guard = Some(tx.clone());
            }

            std::thread::spawn(move || {
                // Connect TCP
                let addr = format!("{}:{}", host, port);
                match TcpStream::connect(&addr) {
                    Ok(mut stream) => {
                        if let Some(mut out) = child.stdout.take() {
                            let mut buf = [0u8; 8192];
                            loop {
                                // check for stop signal
                                if rx.try_recv().is_ok() {
                                    let _ = child.kill();
                                    break;
                                }

                                match out.read(&mut buf) {
                                    Ok(0) => break,
                                    Ok(n) => {
                                        if let Err(e) = stream.write_all(&buf[..n]) {
                                            eprintln!("stream write error: {}", e);
                                            break;
                                        }
                                    }
                                    Err(e) => {
                                        eprintln!("ffmpeg read error: {}", e);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    Err(e) => {
                        eprintln!("Failed to connect to {}: {}", addr, e);
                    }
                }

                // drain stderr to logs
                if let Some(mut err) = child.stderr.take() {
                    let mut buf = [0u8; 1024];
                    while let Ok(n) = err.read(&mut buf) {
                        if n == 0 { break; }
                        let s = String::from_utf8_lossy(&buf[..n]);
                        eprintln!("ffmpeg: {}", s.trim_end());
                    }
                }

                // wait for child to exit
                let _ = child.wait();

                // clear global control sender
                let mut guard = FFMPEG_CONTROL.lock().unwrap();
                *guard = None;
                println!("ffmpeg stream ended");
            });
        }
        Err(e) => {
            eprintln!("Failed to spawn ffmpeg: {}", e);
        }
    }
}

/// Stop any running ffmpeg-based stream.
pub fn stop_ffmpeg_stream() {
    let mut guard = FFMPEG_CONTROL.lock().unwrap();
    if let Some(tx) = guard.take() {
        // signal the thread to stop; the thread will kill the child
        let _ = tx.send(());
        println!("Requested ffmpeg stop");
    }
}
