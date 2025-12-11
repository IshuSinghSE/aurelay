package com.devindeed.aurelay.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aurelay.App
import com.aurelay.engine.ffi.RustBridge
import java.io.File

/**
 * Desktop entry point - thin shell that launches the shared App.
 * 
 * Architecture:
 * - Loads native Rust library (librust_engine.so)
 * - Creates RustBridge (the only class that talks to Rust via UniFFI)
 * - Passes engine to shared App() composable
 * 
 * This file should stay minimal - all logic is in the shared module.
 */
fun main() = application {
    // Load the Rust native library
    loadNativeLibrary()
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Aurelay Sender - Desktop"
    ) {
        val audioEngine = remember { RustBridge() }
        
        // Use the shared App from the KMP module
        App(audioEngine = audioEngine)
    }
}

/**
 * Load the Rust native library (librust_engine.so / rust_engine.dll).
 * Looks in resources first, then falls back to system library path.
 */
private fun loadNativeLibrary() {
    try {
        val osName = System.getProperty("os.name").lowercase()
        val libName = if (osName.contains("win")) "rust_engine.dll" else "librust_engine.so"
        
        // Try to load from resources (bundled with app)
        val resourceStream = object {}.javaClass.getResourceAsStream("/$libName")
        if (resourceStream != null) {
            val tempLib = File.createTempFile("rust_engine", if (osName.contains("win")) ".dll" else ".so")
            tempLib.deleteOnExit()
            
            resourceStream.use { input ->
                tempLib.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            System.setProperty("uniffi.component.rust_engine.libraryOverride", tempLib.absolutePath)
            println("[Main] Loaded native library from resources: ${tempLib.absolutePath}")
        } else {
            // Library will be loaded from system path by JNA
            println("[Main] Native library not found in resources, will try system path")
        }
    } catch (e: Exception) {
        System.err.println("[Main] Warning: Could not load native library: ${e.message}")
        System.err.println("[Main] Make sure to build the Rust library first: ./gradlew :desktopApp:cargoBuild")
    }
}
