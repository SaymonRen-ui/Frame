package com.saymon.frame.core.ads

import android.app.Activity
import android.content.Context
import com.saymon.frame.BuildConfig
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

/**
 * Межстраничная реклама РСЯ для Frame.
 * Логика как в PDF2CBZ: реклама предзагружается заранее.
 * Показываем её при открытии произведения (не во время чтения).
 */
object AdManager {

    // ID блоков РСЯ для Frame.
    const val INTERSTITIAL_AD_UNIT_ID = "R-M-20317549-2"
    const val BANNER_AD_UNIT_ID = "R-M-20317549-1"

    private var loader: InterstitialAdLoader? = null
    private var ad: InterstitialAd? = null

    /** Пауза между показами: не чаще раза в 5 минут. */
    private const val COOLDOWN_MS = 5 * 60 * 1000L
    private const val PREFS = "ads"
    private const val KEY_LAST = "last_shown"

    private fun lastShown(context: Context): Long =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getLong(KEY_LAST, 0)

    private fun markShown(context: Context) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()

    fun preload(context: Context) {
        if (ad != null) return
        val l = InterstitialAdLoader(context.applicationContext)
        loader = l
        try {
            l.loadAd(
                AdRequest.Builder(INTERSTITIAL_AD_UNIT_ID).build(),
                object : InterstitialAdLoadListener {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        AdManager.ad = ad
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        AdManager.ad = null
                    }
                },
            )
        } catch (_: Exception) {
            // без рекламы — просто продолжаем
        }
    }

    fun show(activity: Activity, onDone: () -> Unit = {}) {
        val cooledDown =
            System.currentTimeMillis() - lastShown(activity) >= COOLDOWN_MS
        val a = if (cooledDown) ad else null
        ad = null
        if (a == null) {
            onDone()
            preload(activity)
            return
        }
        markShown(activity)
        a.setAdEventListener(object : InterstitialAdEventListener {
            override fun onAdShown() {}
            override fun onAdFailedToShow(error: AdError) {}
            override fun onAdDismissed() { onDone() }
            override fun onAdClicked() {}
            override fun onAdImpression(data: ImpressionData?) {}
        })
        try {
            a.show(activity)
        } catch (_: Exception) {
            onDone()
        }
        onDone()
        preload(activity)
    }
}