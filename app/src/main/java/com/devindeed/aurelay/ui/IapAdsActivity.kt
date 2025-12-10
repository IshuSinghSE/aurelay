package com.devindeed.aurelay.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.devindeed.aurelay.R
import com.devindeed.aurelay.ads.AdsManager
import com.devindeed.aurelay.iap.BillingManager
import com.devindeed.aurelay.iap.PurchaseManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.android.gms.ads.AdView

class IapAdsActivity : AppCompatActivity() {

    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iap_ads)

        billingManager = BillingManager(this)
        billingManager.startConnection()

        AdsManager.initialize(this)
        val adView = findViewById<AdView>(R.id.adView)
        AdsManager.loadBanner(adView)

        // Wire mock PurchaseManager: hide ads for premium users
        lifecycleScope.launch {
            PurchaseManager.isPremium.collectLatest { premium ->
                adView.visibility = if (premium) android.view.View.GONE else android.view.View.VISIBLE
            }
        }

        findViewById(android.R.id.content).rootView.findViewById<android.widget.Button>(R.id.btn_purchase_remove_ads)?.setOnClickListener {
            // For now, use the mock buy flow to toggle premium locally
            PurchaseManager.buyPremium()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
    }
}
