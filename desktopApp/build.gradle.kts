plugins {
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared")) {
                    // Exclude AndroidX Compose desktop artifacts that may be pulled
                    // transitively from the Android/shared module to avoid mixing
                    // AndroidX jvm-stubs with JetBrains Compose Desktop implementations.
                    exclude(group = "androidx.compose.ui", module = "ui-util-desktop")
                    exclude(group = "androidx.compose.ui", module = "ui-unit-desktop")
                    exclude(group = "androidx.compose.ui", module = "ui-text-desktop")
                    exclude(group = "androidx.compose.ui", module = "ui-graphics-desktop")
                    exclude(group = "androidx.compose.ui", module = "ui-geometry-desktop")
                    exclude(group = "androidx.compose.material", module = "material-icons-extended-desktop")
                    exclude(group = "androidx.compose.material", module = "material-icons-core-desktop")
                }
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                // Use JetBrains desktop-specific ui util/unit implementations
                implementation("org.jetbrains.compose.ui:ui-util-desktop:1.9.3")
                implementation("org.jetbrains.compose.ui:ui-unit-desktop:1.9.3")
                // Provide desktop material icons (icons/core + icons/extended)
                implementation("androidx.compose.material:material-icons-core-desktop:1.7.8")
                implementation("androidx.compose.material:material-icons-extended-desktop:1.7.8")
                implementation("net.java.dev.jna:jna:5.13.0")
            }
        }
    }
}

// Keep default resolution: prefer JetBrains Compose Desktop artifacts (org.jetbrains.compose)


compose.desktop {
    application {
        mainClass = "com.devindeed.aurelay.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "AurelaySender"
            packageVersion = "1.0.0"
        }
    }
}

tasks.register<Exec>("cargoBuild") {
    group = "rust"
    description = "Builds the rust_engine library and generates Kotlin bindings"

    val rustDir = rootProject.file("rust_engine")
    workingDir(rustDir)

    commandLine("cargo", "build", "--release")

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val libName = if (osName.contains("win")) "rust_engine.dll" else "librust_engine.so"

        val source = rustDir.resolve("target/release/$libName")
        val destDir = project.file("src/jvmMain/resources")
        val kotlinOutDir = project.file("src/jvmMain/kotlin/com/devindeed/aurelay/desktop")

        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        if (source.exists()) {
            source.copyTo(destDir.resolve(libName), overwrite = true)
            println("Copied $libName to resources")

            // Generate bindings
            // Prefer invoking the `uniffi-bindgen` CLI directly when available.
            // If it's not on PATH, instruct the user to install it (e.g. `cargo install uniffi-bindgen`).
            val generateCmd = listOf("uniffi-bindgen", "generate", "--library", source.absolutePath, "--language", "kotlin", "--out-dir", kotlinOutDir.absolutePath)

            try {
                val processBuilder = ProcessBuilder(generateCmd)
                    .directory(rustDir)
                    .redirectErrorStream(true)
                val process = processBuilder.start()
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    logger.warn("Couldn't run 'uniffi-bindgen' to generate Kotlin bindings (exit=$exitCode).")
                    logger.warn("Install the CLI with `cargo install uniffi-bindgen` and ensure it's on your PATH, or generate bindings manually.")
                    logger.warn("Continuing build with fallback Kotlin stubs (if present).")
                }
            } catch (e: Exception) {
                logger.warn("Failed to start 'uniffi-bindgen' process: ${e.message}")
                logger.warn("Skipping UniFFI binding generation; continuing with fallback Kotlin stubs (if present).")
            }

            println("Generated Kotlin bindings")

        } else {
            throw GradleException("Rust build failed or library not found at $source")
        }
    }
}

// Make cargo build run before Kotlin compilation
afterEvaluate {
    tasks.named("compileKotlinJvm").configure {
        dependsOn("cargoBuild")
    }
}
