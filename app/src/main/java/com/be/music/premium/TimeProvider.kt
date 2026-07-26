package com.be.music.premium

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TimeProvider"
        private const val TIME_API_URL = "https://worldtimeapi.org/api/ip"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun getUtcTimestamp(): Long = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) {
                Log.w(TAG, "No network available")
                return@withContext -1L
            }

            val request = Request.Builder()
                .url(TIME_API_URL)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext -1L

            val json = JSONObject(body)
            json.getLong("unixtime") * 1000L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch UTC time: ${e.message}")
            -1L
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
