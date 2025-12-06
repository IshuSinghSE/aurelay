#!/usr/bin/env python3
"""
Async Audio Relay - Stream desktop audio to Android devices
Supports discovery, connection management, and robust async streaming
"""

import asyncio
import logging
import socket
import ssl
import subprocess
import sys
from typing import Optional, List, Tuple
import platform


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def get_device_name():
    """Get the current device's hostname"""
    return platform.node() or "Desktop"


async def discover_receivers_async(timeout: int = 3, discovery_port: int = 5002) -> List[Tuple[str, int, str]]:
    """
    Async version: Broadcast discovery and collect responses.
    Returns list of (ip, port, name).
    """
    logger.info("Starting device discovery...")
    msg = b"AURYNK_DISCOVER"
    responses: List[Tuple[str, int, str]] = []
    
    loop = asyncio.get_event_loop()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.setblocking(False)
    
    try:
        # Broadcast discovery request
        await loop.sock_sendto(sock, msg, ("255.255.255.255", discovery_port))
        logger.info(f"Discovery broadcast sent on port {discovery_port}")
        
        # Collect responses for timeout seconds
        end_time = asyncio.get_event_loop().time() + timeout
        
        while asyncio.get_event_loop().time() < end_time:
            try:
                remaining = end_time - asyncio.get_event_loop().time()
                if remaining <= 0:
                    break
                    
                data, addr = await asyncio.wait_for(
                    loop.sock_recvfrom(sock, 1024),
                    timeout=min(0.5, remaining)
                )
                
                text = data.decode(errors="ignore").strip()
                if text.startswith("AURYNK_RESPONSE"):
                    parts = text.split(";")
                    if len(parts) >= 3:
                        try:
                            resp_port = int(parts[1])
                        except ValueError:
                            resp_port = 5000
                        name = parts[2] if len(parts) > 2 else "Android Device"
                        ip = addr[0]
                        
                        if (ip, resp_port, name) not in responses:
                            responses.append((ip, resp_port, name))
                            logger.info(f"Discovered: {name} at {ip}:{resp_port}")
                            
            except asyncio.TimeoutError:
                continue
            except Exception as e:
                logger.debug(f"Discovery receive error: {e}")
                continue
                
    except Exception as e:
        logger.error(f"Discovery broadcast failed: {e}")
    finally:
        sock.close()
    
    logger.info(f"Discovery complete. Found {len(responses)} device(s)")
    return responses


async def send_connect_request(host: str, port: int = 5002) -> bool:
    """
    Send connection request to receiver device.
    Returns True if request was sent successfully.
    """
    try:
        device_name = get_device_name()
        msg = f"AURYNK_CONNECT;{device_name}".encode()
        
        loop = asyncio.get_event_loop()
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setblocking(False)
        
        await loop.sock_sendto(sock, msg, (host, port))
        sock.close()
        
        logger.info(f"Connection request sent to {host}:{port}")
        return True
    except Exception as e:
        logger.error(f"Failed to send connection request: {e}")
        return False


async def wait_for_connection_response(timeout: int = 10, listen_port: int = 5002) -> Optional[str]:
    """
    Wait for AURYNK_ACCEPT or AURYNK_REJECT response.
    Returns 'accepted', 'rejected', or None on timeout.
    """
    logger.info(f"Waiting for connection response (timeout: {timeout}s)...")
    
    loop = asyncio.get_event_loop()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", listen_port))
    sock.setblocking(False)
    
    try:
        end_time = loop.time() + timeout
        
        while loop.time() < end_time:
            try:
                remaining = end_time - loop.time()
                if remaining <= 0:
                    break
                    
                data, addr = await asyncio.wait_for(
                    loop.sock_recvfrom(sock, 1024),
                    timeout=min(1.0, remaining)
                )
                
                msg = data.decode(errors="ignore").strip()
                
                if msg.startswith("AURYNK_ACCEPT"):
                    logger.info(f"Connection ACCEPTED by {addr[0]}")
                    return "accepted"
                elif msg.startswith("AURYNK_REJECT"):
                    logger.warning(f"Connection REJECTED by {addr[0]}")
                    return "rejected"
                    
            except asyncio.TimeoutError:
                continue
            except Exception as e:
                logger.debug(f"Response receive error: {e}")
                continue
                
    except Exception as e:
        logger.error(f"Error waiting for response: {e}")
    finally:
        sock.close()
    
    logger.warning("Connection response timeout")
    return None


async def stream_audio_async(host: str, port: int, device: Optional[str] = None, 
                             buffer_size: int = 4096, use_tls: bool = False,
                             certfile: Optional[str] = None, keyfile: Optional[str] = None,
                             cafile: Optional[str] = None, verify: bool = True):
    """
    Async audio streaming with ffmpeg.
    """
    if device is None:
        device = "default"
    
    ffmpeg_cmd = [
        "ffmpeg",
        "-f", "pulse",
        "-i", device,
        "-f", "s16le",
        "-acodec", "pcm_s16le",
        "-ar", "44100",
        "-ac", "2",
        "-loglevel", "error",
        "-"
    ]
    
    logger.info(f"Starting audio stream to {host}:{port} (TLS: {use_tls})")
    
    proc = None
    sock = None
    
    try:
        # Start ffmpeg process
        proc = await asyncio.create_subprocess_exec(
            *ffmpeg_cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )
        
        # Create socket connection
        loop = asyncio.get_event_loop()
        raw_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        raw_sock.setblocking(False)
        
        await loop.sock_connect(raw_sock, (host, port))
        raw_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        
        if use_tls:
            context = create_ssl_context(cafile, certfile, keyfile, verify)
            # Wrap in SSL (blocking operation, run in executor)
            sock = await loop.run_in_executor(
                None, 
                lambda: context.wrap_socket(raw_sock, server_hostname=host)
            )
            sock.setblocking(False)
        else:
            sock = raw_sock
        
        logger.info("Audio streaming started. Press Ctrl+C to stop.")
        
        silence = b"\x00" * buffer_size
        bytes_sent = 0
        
        # Stream audio data
        while True:
            try:
                data = await proc.stdout.read(buffer_size)
                
                if not data:
                    # Send silence if no data
                    await loop.sock_sendall(sock, silence)
                    await asyncio.sleep(0.01)
                    continue
                
                await loop.sock_sendall(sock, data)
                bytes_sent += len(data)
                
                # Log progress every 10MB
                if bytes_sent % (10 * 1024 * 1024) < buffer_size:
                    logger.info(f"Streamed {bytes_sent / (1024 * 1024):.1f} MB")
                    
            except Exception as e:
                logger.error(f"Streaming error: {e}")
                break
                
    except KeyboardInterrupt:
        logger.info("Stopping stream (user interrupt)...")
        # Send silence for smooth stop
        if sock:
            try:
                silence = b"\x00" * int(44100 * 2 * 2 * 0.1)
                await loop.sock_sendall(sock, silence)
            except:
                pass
                
    except Exception as e:
        logger.error(f"Stream error: {e}")
        
    finally:
        # Cleanup
        if proc:
            try:
                proc.terminate()
                await asyncio.wait_for(proc.wait(), timeout=2.0)
            except:
                try:
                    proc.kill()
                except:
                    pass
        
        if sock:
            try:
                sock.close()
            except:
                pass
        
        logger.info(f"Stream ended. Total: {bytes_sent / (1024 * 1024):.1f} MB")


def create_ssl_context(
    cafile: Optional[str] = None,
    certfile: Optional[str] = None,
    keyfile: Optional[str] = None,
    verify: bool = True,
):
    """
    Create and return a configured SSLContext for use by AudioSender.
    """
    context = ssl.create_default_context(
        ssl.Purpose.SERVER_AUTH if cafile else ssl.Purpose.CLIENT_AUTH
    )
    
    if hasattr(ssl, "TLSVersion") and hasattr(context, "minimum_version"):
        try:
            if hasattr(ssl.TLSVersion, "TLSv1_3"):
                context.minimum_version = ssl.TLSVersion.TLSv1_3
            else:
                context.minimum_version = ssl.TLSVersion.TLSv1_2
        except Exception:
            context.options |= ssl.OP_NO_TLSv1 | ssl.OP_NO_TLSv1_1
    else:
        context.options |= ssl.OP_NO_TLSv1 | ssl.OP_NO_TLSv1_1

    if certfile and keyfile:
        context.load_cert_chain(certfile=certfile, keyfile=keyfile)

    if verify:
        if cafile:
            context.load_verify_locations(cafile=cafile)
        context.verify_mode = ssl.CERT_REQUIRED
        context.check_hostname = True
    else:
        if cafile:
            context.load_verify_locations(cafile=cafile)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        
    return context


async def async_main():
    """Main async entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description="Stream desktop audio to Android device (async version)."
    )
    parser.add_argument("--discover", action="store_true", 
                       help="Discover receiver devices on local network")
    parser.add_argument("--host", help="Destination host IP")
    parser.add_argument("--port", type=int, default=5000, 
                       help="Destination port (default: 5000)")
    parser.add_argument("--device", default="default",
                       help="PulseAudio/PipeWire source device")
    parser.add_argument("--tls", action="store_true", 
                       help="Enable TLS encryption")
    parser.add_argument("--certfile", help="TLS certificate file")
    parser.add_argument("--keyfile", help="TLS private key file")
    parser.add_argument("--cafile", help="CA certificate file")
    parser.add_argument("--no-verify", action="store_true",
                       help="Disable TLS verification (insecure)")
    parser.add_argument("--buffer-size", type=int, default=4096,
                       help="Stream buffer size (default: 4096)")
    parser.add_argument("--discovery-port", type=int, default=5002,
                       help="Discovery port (default: 5002)")
    
    args = parser.parse_args()
    
    host = args.host
    port = args.port
    
    # Discovery mode
    if args.discover or not host:
        devices = await discover_receivers_async(timeout=3, discovery_port=args.discovery_port)
        
        if not devices:
            logger.error("No receivers found. Make sure receiver app is running.")
            return
        
        print("\n═══ Discovered Receivers ═══")
        for i, (ip, p, name) in enumerate(devices):
            print(f"  [{i}] {name}")
            print(f"      IP: {ip}:{p}")
        print("═" * 30)
        
        try:
            choice = input(f"\nSelect device [0-{len(devices)-1}] (default: 0): ").strip()
            idx = int(choice) if choice else 0
            host, port, device_name = devices[idx]
            logger.info(f"Selected: {device_name} ({host}:{port})")
        except (ValueError, IndexError):
            host, port, device_name = devices[0]
            logger.info(f"Using first device: {device_name} ({host}:{port})")
    
    if not host:
        logger.error("No host specified. Use --host or --discover")
        return
    
    # Send connection request and wait for acceptance
    logger.info(f"Requesting connection to {host}...")
    if not await send_connect_request(host, args.discovery_port):
        logger.error("Failed to send connection request")
        return
    
    response = await wait_for_connection_response(timeout=10, listen_port=args.discovery_port)
    
    if response == "accepted":
        logger.info("✓ Connection accepted! Starting audio stream...")
        await stream_audio_async(
            host, port, args.device, 
            buffer_size=args.buffer_size,
            use_tls=args.tls,
            certfile=args.certfile,
            keyfile=args.keyfile,
            cafile=args.cafile,
            verify=not args.no_verify
        )
    elif response == "rejected":
        logger.error("✗ Connection rejected by receiver")
    else:
        logger.error("✗ Connection timeout. Receiver may not be running or not responding.")


def main():
    """Sync wrapper for async main"""
    try:
        asyncio.run(async_main())
    except KeyboardInterrupt:
        logger.info("Interrupted by user")
    except Exception as e:
        logger.error(f"Fatal error: {e}", exc_info=True)


# Legacy sync functions kept for backward compatibility
def discover_receivers(timeout: int = 3, discovery_port: int = 5002) -> List[Tuple[str, int, str]]:
    """Sync wrapper for discovery"""
    return asyncio.run(discover_receivers_async(timeout, discovery_port))

def stream_desktop_audio(host, port, device=None):
    """Legacy sync audio streaming (kept for backward compatibility)"""
    if device is None:
        device = "default"

    ffmpeg_cmd = [
        "ffmpeg",
        "-f", "pulse",
        "-i", device,
        "-f", "s16le",
        "-acodec", "pcm_s16le",
        "-ar", "44100",
        "-ac", "2",
        "-loglevel", "error",
        "-"
    ]

    buffer_size = 1024
    sock = None
    proc = None
    
    try:
        sock = socket.create_connection((host, int(port)))
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        proc = subprocess.Popen(ffmpeg_cmd, stdout=subprocess.PIPE, bufsize=0)
        print(f"Streaming desktop audio to {host}:{port} in real-time ...")
        silence = b"\x00" * buffer_size
        
        while True:
            data = proc.stdout.read(buffer_size)
            if not data:
                sock.sendall(silence)
                continue
            sock.sendall(data)
            
    except KeyboardInterrupt:
        print("\nStopped by user.")
    except Exception as e:
        print(f"\nError: {e}")
    finally:
        if proc:
            try:
                proc.terminate()
                proc.wait(timeout=2)
            except Exception:
                pass
        if sock:
            try:
                sock.close()
            except Exception:
                pass
        print("Done streaming.")


def stream_desktop_audio_tls(host, port, device=None, certfile=None, keyfile=None, cafile=None, verify=True):
    """Legacy sync TLS streaming (kept for backward compatibility)"""
    if device is None:
        device = "default"
        
    ffmpeg_cmd = [
        "ffmpeg",
        "-f", "pulse",
        "-i", device,
        "-f", "s16le",
        "-acodec", "pcm_s16le",
        "-ar", "44100",
        "-ac", "2",
        "-loglevel", "error",
        "-"
    ]
    
    buffer_size = 1024
    sock = None
    proc = None
    
    try:
        context = create_ssl_context(cafile=cafile, certfile=certfile, keyfile=keyfile, verify=verify)
        raw_sock = socket.create_connection((host, int(port)))
        sock = context.wrap_socket(raw_sock, server_hostname=host)
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        proc = subprocess.Popen(ffmpeg_cmd, stdout=subprocess.PIPE, bufsize=0)
        print(f"Streaming desktop audio to {host}:{port} over TLS in real-time ...")
        silence = b"\x00" * buffer_size
        
        while True:
            data = proc.stdout.read(buffer_size)
            if not data:
                sock.sendall(silence)
                continue
            sock.sendall(data)
            
    except KeyboardInterrupt:
        print("\nStopped by user.")
    except Exception as e:
        print(f"\nError: {e}")
    finally:
        if proc:
            try:
                proc.terminate()
                proc.wait(timeout=2)
            except Exception:
                pass
        if sock:
            try:
                sock.close()
            except Exception:
                pass
        print("Done streaming.")


if __name__ == "__main__":
    main()

