package com.saymon.frame

import android.app.Application
import com.saymon.frame.core.ads.AdManager
import com.yandex.mobile.ads.common.YandexAds

class FrameApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YandexAds.enableLogging(true)
            YandexAds.setUserConsent(false)
            YandexAds.initialize(this) {
                AdManager.preload(this)
            }
        } catch (_: Exception) {
        }
    }
}
