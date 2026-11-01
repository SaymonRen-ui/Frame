package com.saymon.frame.core.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
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
 * Показывается внизу экранов (библиотека, история).
 * Не показывается во время чтения и на экране произведения.
 *
 * В дебажной сборке показывает заглушку, чтобы можно было
 * проверить размещение и размер.
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
) {
    if (com.saymon.frame.BuildConfig.DEBUG) {
        // Дебажная заглушка — показывает место под рекламу
        androidx.compose.material3.Card(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    "Реклама (тестовый баннер)",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

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
                android.util.Log.d("BannerAd", "Banner loaded successfully")
            }
            override fun onAdFailedToLoad(error: com.yandex.mobile.ads.common.AdRequestError) {
                android.util.Log.e("BannerAd", "Banner failed to load: ${error.description} (code: ${error.code})")
            }
            override fun onAdClicked() {}
            override fun onImpression(impressionData: com.yandex.mobile.ads.common.ImpressionData?) {}
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