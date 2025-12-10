package com.devindeed.aurelay.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration


object AdsManager {
    fun initialize(context: Context) {
        // Force test device IDs during development to avoid serving live ads
        val testDeviceIds = listOf(AdRequest.DEVICE_ID_EMULATOR)
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)
        MobileAds.initialize(context) { }
    }

    fun loadBanner(adView: AdView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {}
            override fun onAdFailedToLoad(error: Int) {}
        }
    }
}
