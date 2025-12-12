package com.aurelay.engine

import java.io.File

/**
 * Helper object for loading the Rust native library on Desktop.
 * Call `NativeLibraryLoader.load()` before creating DesktopAudioEngine.
 */
object NativeLibraryLoader {
    
    private var loaded = false
    
    /**
     * Load the native library (librust_engine.so / rust_engine.dll).
     * This should be called once before using DesktopAudioEngine.
     * 
     * @return true if library was loaded successfully, false otherwise
     */
    fun load(): Boolean {
        if (loaded) return true
        
        try {
            val osName = System.getProperty("os.name").lowercase()
            val libName = if (osName.contains("win")) "rust_engine.dll" else "librust_engine.so"
            
            // Try to load from resources (bundled with app)
            val resourceStream = this::class.java.getResourceAsStream("/$libName")
            if (resourceStream != null) {
                val tempLib = File.createTempFile("rust_engine", if (osName.contains("win")) ".dll" else ".so")
                tempLib.deleteOnExit()
                
                resourceStream.use { input ->
                    tempLib.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                System.setProperty("uniffi.component.rust_engine.libraryOverride", tempLib.absolutePath)
                println("[NativeLibraryLoader] Loaded from resources: ${tempLib.absolutePath}")
                loaded = true
                return true
            } else {
                // Try to load from system library path
                println("[NativeLibraryLoader] Not found in resources, trying system path...")
                
                // The actual loading will happen in rust_engine.kt via JNA
                // Just verify the library exists somewhere
                try {
                    System.loadLibrary("rust_engine")
                    println("[NativeLibraryLoader] Loaded from system library path")
                    loaded = true
                    return true
                } catch (e: UnsatisfiedLinkError) {
                    System.err.println("[NativeLibraryLoader] Library not found in system path")
                    System.err.println("[NativeLibraryLoader] Build it first: ./gradlew :desktopApp:cargoBuild")
                    return false
                }
            }
        } catch (e: Exception) {
            System.err.println("[NativeLibraryLoader] Error loading library: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Check if the native library is loaded.
     */
    fun isLoaded(): Boolean = loaded
}
