plugins {
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    kotlin("multiplatform")
}

kotlin {
    jvm {
        withJava()
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation("net.java.dev.jna:jna:5.13.0")
            }
        }
    }
}

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
                val result = exec {
                    workingDir = rustDir
                    isIgnoreExitValue = true
                    commandLine(generateCmd)
                }

                if (result.exitValue != 0) {
                    logger.warn("Couldn't run 'uniffi-bindgen' to generate Kotlin bindings (exit=${result.exitValue}).")
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

tasks.named("processResources") {
    dependsOn("cargoBuild")
}

tasks.named("compileKotlinJvm") {
    dependsOn("cargoBuild")
}
