package com.devindeed.aurelay.ui

import androidx.compose.ui.window.singleWindowApplication
import com.devindeed.aurelay.ui.screens.AudioVisualizerScreen

fun main() = singleWindowApplication(title = "Audio Preview") {
    // AudioVisualizerScreen now accepts an optional AudioEngine, defaults to null/dummy
    AudioVisualizerScreen()
}
