package com.be.music

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.unity3d.ads.UnityAds
import com.be.music.ads.InterstitialAdManager
import com.be.music.ads.RewardedAdManager
import com.be.music.premium.PremiumManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BeMusicApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var premiumManager: PremiumManager

    companion object {
        lateinit var rewardedAdManager: RewardedAdManager
            private set
        lateinit var interstitialAdManager: InterstitialAdManager
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 1. Kütüphane Başlatma
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            
            // YouTube engellerini aşmak için motoru arka planda güncelle
            applicationScope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this@BeMusicApplication)
                    Log.d("BE-Music", "YoutubeDL başarıyla güncellendi.")
                } catch (e: Exception) {
                    Log.e("BE-Music", "Motor güncelleme hatası: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("BE-Music", "Başlatma Hatası", e)
        }

        // 2. NewPipe Extractor (Arama) Başlatma
        try {
            org.schabi.newpipe.extractor.NewPipe.init(com.be.music.youtube.YoutubeDownloader())
        } catch (e: Exception) {
            Log.e("NewPipe", "Başlatma Hatası", e)
        }

        // 3. Unity Ads Başlatma
        UnityAds.initialize(this, "6153407", false)
        rewardedAdManager = RewardedAdManager(this)
        rewardedAdManager.loadAd()
        interstitialAdManager = InterstitialAdManager(this)
        interstitialAdManager.loadAd()
    }
}
