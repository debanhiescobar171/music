
package com.violetmusic.app

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

internal object AppOpenAdController {

    private const val TEST_APP_OPEN_AD_UNIT_ID =
        "ca-app-pub-3940256099942544/9257395921"

    private var isLoading = false
    private var isShowing = false

    fun loadAndShow(activity: Activity) {
        if (isLoading || isShowing) return
        if (activity.isFinishing || activity.isDestroyed) return

        isLoading = true

        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            activity,
            TEST_APP_OPEN_AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {

                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoading = false

                    if (activity.isFinishing || activity.isDestroyed) {
                        return
                    }

                    ad.fullScreenContentCallback =
                        object : FullScreenContentCallback() {

                            override fun onAdShowedFullScreenContent() {
                                isShowing = true
                            }

                            override fun onAdDismissedFullScreenContent() {
                                isShowing = false
                            }

                            override fun onAdFailedToShowFullScreenContent(
                                adError: AdError
                            ) {
                                isShowing = false
                            }
                        }

                    isShowing = true
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                }
            }
        )
    }
}
