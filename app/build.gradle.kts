plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties
import java.io.FileInputStream

android {
    namespace = "com.devindeed.aurelay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devindeed.aurelay"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("./key.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))

                keystoreProperties["storeFile"]?.let { storeFile = file(it.toString()) }
                keystoreProperties["storePassword"]?.let { storePassword = it.toString() }
                keystoreProperties["keyAlias"]?.let { keyAlias = it.toString() }
                keystoreProperties["keyPassword"]?.let { keyPassword = it.toString() }
            }
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            
            // Generate native debug symbols for crash reporting
            ndk {
                debugSymbolLevel = "FULL"
            }
            // Kill-switch for Ads in production builds (false by default)
            buildConfigField("boolean", "ENABLE_ADS", "false")
        }
        debug {
            // Also generate symbols for debug builds
            ndk {
                debugSymbolLevel = "FULL"
            }
            // Enable Ads for debug builds so test ads show locally
            buildConfigField("boolean", "ENABLE_ADS", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.media)
    // Compose animation and icons required for Material3 expressive UI
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.preference.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)

    // Google Play Billing
    implementation(libs.billing)

    // AdMob / Google Mobile Ads
    implementation(libs.play.services.ads)



}