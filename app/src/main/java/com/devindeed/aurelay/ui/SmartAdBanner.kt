package com.devindeed.aurelay.ui

import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

/**
 * A small Compose helper that hosts an AdMob AdView and loads an adaptive banner.
 * Defaults to Google's adaptive-banner test ad unit id (set in `strings.xml`).
 */
@Composable
fun SmartAdBanner(
    modifier: Modifier = Modifier,
    adUnitId: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Ensure SDK initialized (no-op if already initialized)
    MobileAds.initialize(context) {}

    val resolvedAdUnitId = adUnitId ?: context.getString(com.devindeed.aurelay.R.string.admob_banner_id)

    AndroidView(
        factory = { ctx ->
            val adView = AdView(ctx)
            adView.adUnitId = resolvedAdUnitId
            adView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adView
        },
        modifier = modifier,
        update = { adView ->
            // Calculate adaptive ad size for current orientation & width
            val configuration = context.resources.configuration
            val screenWidthDp = configuration.screenWidthDp
            val adWidthPx = (screenWidthDp * density.density).toInt()
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthPx)
            adView.adSize = adSize

            val adRequest = AdRequest.Builder().build()
            adView.adListener = object : AdListener() {}
            adView.loadAd(adRequest)
        }
    )
}
