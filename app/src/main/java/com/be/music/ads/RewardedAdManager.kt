package com.be.music.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.be.music.premium.PremiumManager
import com.be.music.premium.PremiumState
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAds.UnityAdsLoadError
import com.unity3d.ads.UnityAds.UnityAdsShowCompletionState
import com.unity3d.ads.UnityAds.UnityAdsShowError

class RewardedAdManager(private val context: Context) {

    companion object {
        private const val TAG = "RewardedAd"
        private const val PLACEMENT_ID = "BE-Music-Rewarded"
        private const val PREFS_NAME = "ad_download_prefs"
        private const val KEY_DOWNLOAD_COUNT = "download_count"
        private const val REWARD_THRESHOLD = 10
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var isAdLoaded = false
    private var onAdDismissed: (() -> Unit)? = null
    private var onRewardedCompleted: (() -> Unit)? = null

    val downloadCount: Int
        get() = prefs.getInt(KEY_DOWNLOAD_COUNT, 0)

    fun incrementDownloadCount(): Int {
        val current = downloadCount
        if (current >= REWARD_THRESHOLD) return current
        val newCount = current + 1
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, newCount).apply()
        Log.d(TAG, "İndirme sayacı: $newCount/$REWARD_THRESHOLD")
        return newCount
    }

    fun resetDownloadCount() {
        prefs.edit().putInt(KEY_DOWNLOAD_COUNT, 0).apply()
        Log.d(TAG, "İndirme sayacı sıfırlandı")
    }

    fun shouldShowAd(): Boolean {
        if (downloadCount >= REWARD_THRESHOLD && !PremiumManager.isPremiumStatic)
            return true
        return false
    }

        fun loadAd() {
            if (isAdLoaded) return

            val loadListener = object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isAdLoaded = true
                    Log.d(TAG, "Rewarded ad yüklendi: $placementId")
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAdsLoadError,
                    message: String
                ) {
                    isAdLoaded = false
                    Log.e(TAG, "Rewarded ad yüklenemedi: $error - $message")
                }
            }

            UnityAds.load(PLACEMENT_ID, loadListener)
        }

        fun showAd(
            activity: Activity,
            onDismissed: () -> Unit = {},
            onRewardEarned: (() -> Unit)? = null
        ) {
            if (!isAdLoaded) {
                Log.w(TAG, "Rewarded ad henüz yüklenmedi")
                loadAd()
                onDismissed()
                return
            }

            onAdDismissed = onDismissed
            onRewardedCompleted = onRewardEarned

            val showListener = object : IUnityAdsShowListener {
                override fun onUnityAdsShowComplete(
                    placementId: String,
                    showCompletionState: UnityAdsShowCompletionState
                ) {
                    isAdLoaded = false
                    if (showCompletionState == UnityAdsShowCompletionState.COMPLETED) {
                        Log.d(TAG, "Rewarded ad tamamlandı - ödül verildi")
                        resetDownloadCount()
                        onRewardedCompleted?.invoke()
                    } else {
                        Log.d(TAG, "Rewarded ad tamamlanmadı: $showCompletionState")
                    }
                    onAdDismissed?.invoke()
                    onRewardedCompleted = null
                    loadAd()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAdsShowError,
                    message: String
                ) {
                    isAdLoaded = false
                    Log.e(TAG, "Rewarded ad gösterilemedi: $error - $message")
                    onAdDismissed?.invoke()
                    loadAd()
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Rewarded ad başladı")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Rewarded ad tıklandı")
                }
            }

            UnityAds.show(activity, PLACEMENT_ID, showListener)
        }
    }

