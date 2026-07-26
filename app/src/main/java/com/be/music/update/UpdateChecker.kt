package com.be.music.update

import com.be.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateResult {
    data object UpToDate : UpdateResult()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateResult()
    data class MandatoryUpdate(val info: UpdateInfo) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

@Singleton
class UpdateChecker @Inject constructor(
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val VERSION_URL =
            "https://residential-argument-surgery-jpeg.trycloudflare.com/apps/android/BE-Music/version.json"
    }

    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(VERSION_URL)
                .cacheControl(okhttp3.CacheControl.Builder().noCache().build())
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateResult.Error("HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: return@withContext UpdateResult.Error("Empty response body")

            val info = json.decodeFromString<UpdateInfo>(body)
            val currentCode = BuildConfig.VERSION_CODE

            if (info.latest_version_code > currentCode) {
                if (info.mandatory) {
                    return@withContext UpdateResult.MandatoryUpdate(info)
                }
                return@withContext UpdateResult.UpdateAvailable(info)
            }

            return@withContext UpdateResult.UpToDate
        } catch (e: Exception) {
            return@withContext UpdateResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}
