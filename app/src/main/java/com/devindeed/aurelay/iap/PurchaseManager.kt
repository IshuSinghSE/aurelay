package com.devindeed.aurelay.iap

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Developer mock for premium state. Toggle `isPremium` to test UI flows without backend.
 */
object PurchaseManager {
    // CHANGE THIS BOOLEAN to test your UI
    // true = Premium User (No Ads)
    // false = Free User (Ads Visible)
    val isPremium = MutableStateFlow(false)

    fun buyPremium() {
        // Fake the purchase for now
        isPremium.value = true
    }
}
