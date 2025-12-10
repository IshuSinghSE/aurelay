package uniffi.rust_engine

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Library
import java.io.File

// Minimal JNA-backed bindings to the Rust native library.
// These are used as a pragmatic alternative to UniFFI-generated bindings
// when `uniffi-bindgen` is not available in the build environment.

private interface NativeLib : Library {
    fun discover_receivers_c(timeout_secs: Long, out_ptr: Pointer?, out_len: Long): Int
    fun request_connect_and_wait_c(host: String?, timeout_secs: Long, device_name: String?, out_ptr: Pointer?, out_len: Long): Int
}

private object RustNative {
    val INSTANCE: NativeLib? = try {
        // Library name is set by Main.kt via system property if bundled as resource
        val osName = System.getProperty("os.name").lowercase()
        val libName = if (osName.contains("win")) "rust_engine.dll" else "librust_engine.so"

        val libPath = System.getProperty("uniffi.component.rust_engine.libraryOverride")
        if (libPath != null) {
            Native.load(libPath, NativeLib::class.java) as NativeLib
        } else {
            // Try to load by library name from java.library.path
            Native.load(libName, NativeLib::class.java) as NativeLib
        }
    } catch (e: Throwable) {
        System.err.println("Native library not available: ${e.message}")
        null
    }
}

fun startStream(targetIp: String) {
    // The existing start/stop stream functionality is provided by separate UniFFI stubs,
    // so maintain the previous behavior here: print a message.
    println("[rust_native] startStream called with target=$targetIp")
}

fun stopStream() {
    println("[rust_native] stopStream called")
}

/**
 * Discover receivers on the local network (returns list of "ip:port;name").
 * Falls back to empty list if native library isn't available.
 */
fun discoverReceivers(timeoutSecs: Long = 3): List<String> {
    val lib = RustNative.INSTANCE ?: return emptyList()
    val bufLen = 16 * 1024L
    val mem = Memory(bufLen)
    mem.clear()
    val rc = lib.discover_receivers_c(timeoutSecs, mem, bufLen)
    if (rc != 0) return emptyList()
    val s = mem.getString(0)
    if (s.isBlank()) return emptyList()
    return s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Send connect request and wait for response. Returns "accepted", "rejected", or "timeout".
 */
fun requestConnectAndWait(host: String, timeoutSecs: Long = 10, deviceName: String = "Desktop"): String {
    val lib = RustNative.INSTANCE ?: return "timeout"
    val bufLen = 1024L
    val mem = Memory(bufLen)
    mem.clear()
    val rc = lib.request_connect_and_wait_c(host, timeoutSecs, deviceName, mem, bufLen)
    if (rc != 0) return "timeout"
    val s = mem.getString(0)
    return s.ifBlank { "timeout" }
}
