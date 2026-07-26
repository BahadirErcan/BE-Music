package com.be.music.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAds.UnityAdsLoadError
import com.unity3d.ads.UnityAds.UnityAdsShowCompletionState
import com.unity3d.ads.UnityAds.UnityAdsShowError

class InterstitialAdManager(private val context: Context) {

    companion object {
        private const val TAG = "InterstitialAd"
        private const val PLACEMENT_ID = "BE-Music-Interstitial"
    }

    private var isAdLoaded = false

    fun loadAd() {
        if (isAdLoaded) return

        val loadListener = object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                isAdLoaded = true
                Log.d(TAG, "Interstitial ad yüklendi: $placementId")
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String,
                error: UnityAdsLoadError,
                message: String
            ) {
                isAdLoaded = false
                Log.e(TAG, "Interstitial ad yüklenemedi: $error - $message")
            }
        }

        UnityAds.load(PLACEMENT_ID, loadListener)
    }

    fun showAd(activity: Activity) {
        if (com.be.music.premium.PremiumManager.isPremiumStatic) {
            Log.d(TAG, "Premium active, skipping interstitial ad")
            return
        }
        if (!isAdLoaded) {
            Log.w(TAG, "Interstitial ad henüz yüklenmedi")
            loadAd()
            return
        }

        val showListener = object : IUnityAdsShowListener {
            override fun onUnityAdsShowComplete(
                placementId: String,
                showCompletionState: UnityAdsShowCompletionState
            ) {
                isAdLoaded = false
                Log.d(TAG, "Interstitial ad tamamlandı")
                loadAd()
            }

            override fun onUnityAdsShowFailure(
                placementId: String,
                error: UnityAdsShowError,
                message: String
            ) {
                isAdLoaded = false
                Log.e(TAG, "Interstitial ad gösterilemedi: $error - $message")
                loadAd()
            }

            override fun onUnityAdsShowStart(placementId: String) {
                Log.d(TAG, "Interstitial ad başladı")
            }

            override fun onUnityAdsShowClick(placementId: String) {
                Log.d(TAG, "Interstitial ad tıklandı")
            }
        }

        UnityAds.show(activity, PLACEMENT_ID, showListener)
    }
}
