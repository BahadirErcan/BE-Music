package com.be.music.premium

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumManager @Inject constructor(
    private val premiumRepository: PremiumRepository,
    private val timeProvider: TimeProvider
) {
    companion object {
        private const val TAG = "PremiumManager"

        @Volatile
        var isPremiumStatic: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PremiumState())
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    init {
        scope.launch {
            premiumRepository.premiumState.collect { savedState ->
                _state.value = savedState
                isPremiumStatic = savedState.isPremium && !savedState.isExpired
            }
        }
        scope.launch {
            syncTime()
        }
    }

    val isPremium: Boolean
        get() {
            val s = _state.value
            return s.isPremium && !s.isExpired
        }

    val rewardCount: Int
        get() = _state.value.rewardedAdCount

    suspend fun syncTime(): Long {
        val onlineTime = timeProvider.getUtcTimestamp()
        if (onlineTime > 0L) {
            val current = _state.value
            val trustedTimestamp = maxOf(onlineTime, current.lastTrustedOnlineTimestamp)
            premiumRepository.updateLastTrustedTimestamp(trustedTimestamp)

            if (current.isPremium && current.premiumExpirationTimestamp > 0L) {
                val deviceTime = System.currentTimeMillis()
                if (deviceTime < trustedTimestamp - 60_000) {
                    Log.w(TAG, "Device clock is behind trusted time, not extending premium")
                }
            }
            return onlineTime
        }
        return -1L
    }

    suspend fun onRewardedAdCompleted() {
        val onlineTime = syncTime()
        val trustedTime = if (onlineTime > 0L) onlineTime else System.currentTimeMillis()

        val current = _state.value
        if (current.isPremium && !current.isExpired) return

        val newCount = current.rewardedAdCount + 1

        if (newCount >= PremiumState.REWARD_THRESHOLD) {
            val expirationTime = trustedTime + (PremiumState.PREMIUM_DURATION_DAYS * 24 * 60 * 60 * 1000)
            premiumRepository.activatePremium(expirationTime, trustedTime)
            Log.d(TAG, "Premium activated! Expires: $expirationTime")
        } else {
            val updated = current.copy(
                rewardedAdCount = newCount,
                lastTrustedOnlineTimestamp = trustedTime
            )
            premiumRepository.setPremiumState(updated)
            Log.d(TAG, "Reward count: $newCount/${PremiumState.REWARD_THRESHOLD}")
        }
    }

    suspend fun checkAndRefreshPremium() {
        val current = premiumRepository.getPremiumState()
        if (current.isPremium) {
            val onlineTime = syncTime()
            val trustedTime = if (onlineTime > 0L) onlineTime else System.currentTimeMillis()
            val updated = current.copy(lastTrustedOnlineTimestamp = trustedTime)
            premiumRepository.setPremiumState(updated)

            if (updated.isExpired) {
                premiumRepository.clearPremium()
                Log.d(TAG, "Premium expired, cleared")
            }
        }
    }

    fun isQualityPremium(quality: String): Boolean {
        val normalized = quality.trim().lowercase()
        return normalized == "1080p" || normalized == "highest" || normalized == "enyüksek"
    }

    val keepAliveEnabled: kotlinx.coroutines.flow.Flow<Boolean>
        get() = premiumRepository.keepAliveEnabled

    suspend fun setKeepAliveEnabled(enabled: Boolean) {
        premiumRepository.setKeepAliveEnabled(enabled)
    }
}
