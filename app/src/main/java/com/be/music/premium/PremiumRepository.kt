package com.be.music.premium

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.premiumDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "premium_prefs"
)

@Singleton
class PremiumRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val PREMIUM_EXPIRATION = longPreferencesKey("premium_expiration")
        val REWARDED_AD_COUNT = intPreferencesKey("rewarded_ad_count")
        val LAST_TRUSTED_TIMESTAMP = longPreferencesKey("last_trusted_timestamp")
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
    }

    val premiumState: Flow<PremiumState> = context.premiumDataStore.data.map { prefs ->
        PremiumState(
            isPremium = prefs[Keys.IS_PREMIUM] ?: false,
            premiumExpirationTimestamp = prefs[Keys.PREMIUM_EXPIRATION] ?: 0L,
            rewardedAdCount = prefs[Keys.REWARDED_AD_COUNT] ?: 0,
            lastTrustedOnlineTimestamp = prefs[Keys.LAST_TRUSTED_TIMESTAMP] ?: 0L
        )
    }

    suspend fun getPremiumState(): PremiumState {
        return premiumState.first()
    }

    suspend fun setPremiumState(state: PremiumState) {
        context.premiumDataStore.edit { prefs ->
            prefs[Keys.IS_PREMIUM] = state.isPremium
            prefs[Keys.PREMIUM_EXPIRATION] = state.premiumExpirationTimestamp
            prefs[Keys.REWARDED_AD_COUNT] = state.rewardedAdCount
            prefs[Keys.LAST_TRUSTED_TIMESTAMP] = state.lastTrustedOnlineTimestamp
        }
    }

    suspend fun incrementRewardedAdCount(): Int {
        context.premiumDataStore.edit { prefs ->
            val current = prefs[Keys.REWARDED_AD_COUNT] ?: 0
            prefs[Keys.REWARDED_AD_COUNT] = current + 1
        }
        return getPremiumState().rewardedAdCount
    }

    suspend fun resetRewardedAdCount() {
        context.premiumDataStore.edit { prefs ->
            prefs[Keys.REWARDED_AD_COUNT] = 0
        }
    }

    suspend fun activatePremium(expirationTimestamp: Long, trustedTimestamp: Long) {
        context.premiumDataStore.edit { prefs ->
            prefs[Keys.IS_PREMIUM] = true
            prefs[Keys.PREMIUM_EXPIRATION] = expirationTimestamp
            prefs[Keys.REWARDED_AD_COUNT] = 0
            prefs[Keys.LAST_TRUSTED_TIMESTAMP] = trustedTimestamp
        }
    }

    suspend fun updateLastTrustedTimestamp(timestamp: Long) {
        context.premiumDataStore.edit { prefs ->
            prefs[Keys.LAST_TRUSTED_TIMESTAMP] = timestamp
        }
    }

    suspend fun clearPremium() {
        context.premiumDataStore.edit { prefs ->
            prefs[Keys.IS_PREMIUM] = false
            prefs[Keys.PREMIUM_EXPIRATION] = 0L
            prefs[Keys.LAST_TRUSTED_TIMESTAMP] = 0L
        }
    }

    val keepAliveEnabled: Flow<Boolean> = context.premiumDataStore.data.map { prefs ->
        prefs[Keys.KEEP_ALIVE_ENABLED] ?: false
    }

    suspend fun setKeepAliveEnabled(enabled: Boolean) {
        context.premiumDataStore.edit { prefs ->
            prefs[Keys.KEEP_ALIVE_ENABLED] = enabled
        }
    }
}
