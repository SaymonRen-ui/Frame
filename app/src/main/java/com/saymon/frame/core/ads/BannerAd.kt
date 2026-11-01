package com.saymon.frame.core.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest

/**
 * Баннерная реклама РСЯ для Frame.
 * Показывается внизу экранов «Библиотека» и «История» — над панелью навигации.
 * Не показывается во время чтения и на экране произведения.
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val bannerAdView = remember {
        BannerAdView(context).apply {
            setAdSize(BannerAdSize.sticky(context, screenWidthDp))
        }
    }

    DisposableEffect(bannerAdView) {
        val adRequest = AdRequest.Builder(AdManager.BANNER_AD_UNIT_ID).build()
        bannerAdView.setBannerAdEventListener(object : BannerAdEventListener {
            override fun onAdLoaded() {
                android.util.Log.d("FrameAds", "banner loaded")
            }

            override fun onAdFailedToLoad(error: com.yandex.mobile.ads.common.AdRequestError) {
                android.util.Log.w(
                    "FrameAds",
                    "banner failed: ${error.code} ${error.description}")
            }

            override fun onAdClicked() {}
            override fun onImpression(impressionData: com.yandex.mobile.ads.common.ImpressionData?) {
                android.util.Log.d("FrameAds", "banner impression")
            }
        })
        bannerAdView.loadAd(adRequest)
        onDispose {
            bannerAdView.destroy()
        }
    }

    AndroidView(
        factory = { bannerAdView },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
    )
}
