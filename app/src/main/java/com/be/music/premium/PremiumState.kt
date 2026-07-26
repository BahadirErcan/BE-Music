package com.be.music.premium

data class PremiumState(
    val isPremium: Boolean = false,
    val premiumExpirationTimestamp: Long = 0L,
    val rewardedAdCount: Int = 50,
    val lastTrustedOnlineTimestamp: Long = 0L
) {
    val remainingDays: Int
        get() {
            if (!isPremium || premiumExpirationTimestamp <= 0L) return 0
            val now = lastTrustedOnlineTimestamp.coerceAtLeast(System.currentTimeMillis())
            val diff = premiumExpirationTimestamp - now
            return if (diff > 0) (diff / (1000 * 60 * 60 * 24)).toInt() else 0
        }

    val isExpired: Boolean
        get() {
            if (!isPremium) return true
            val now = lastTrustedOnlineTimestamp.coerceAtLeast(System.currentTimeMillis())
            return premiumExpirationTimestamp > 0L && premiumExpirationTimestamp < now
        }

    companion object {
        const val REWARD_THRESHOLD = 50
        const val PREMIUM_DURATION_DAYS = 30L
    }
}
