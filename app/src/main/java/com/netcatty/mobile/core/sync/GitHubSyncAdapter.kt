package com.netcatty.mobile.core.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub Gist 同步适配器。
 * 使用 GitHub Gist 作为云存储，与桌面端 GitHubAdapter 兼容。
 */
@Singleton
class GitHubSyncAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val PREFS_NAME = "netcatty_sync_prefs"
        private const val KEY_GIST_ID = "github_gist_id"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val GIST_FILENAME = "netcatty-sync.json.enc"
        private const val GITHUB_API = "https://api.github.com"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val syncPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isConfigured(): Boolean = syncPrefs.getString(KEY_GITHUB_TOKEN, null) != null

    fun getDeviceId(): String = syncPrefs.getString(KEY_DEVICE_ID, null)
        ?: "android-${System.currentTimeMillis()}".also {
            syncPrefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    fun configure(token: String) {
        syncPrefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    /**
     * 上传加密数据到 GitHub Gist
     */
    suspend fun upload(encryptedData: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = syncPrefs.getString(KEY_GITHUB_TOKEN, null)
                ?: return@withContext Result.failure(Exception("GitHub token not configured"))

            val gistId = syncPrefs.getString(KEY_GIST_ID, null)
            val gistBody = if (gistId != null) {
                // Update existing gist
                """{"description":"Netcatty Sync","files":{"$GIST_FILENAME":{"content":"${encryptedData.replace("\"", "\\\"").replace("\n", "\\n")}"}}}"""
            } else {
                // Create new gist
                """{"description":"Netcatty Sync","public":false,"files":{"$GIST_FILENAME":{"content":"${encryptedData.replace("\"", "\\\"").replace("\n", "\\n")}"}}}"""
            }

            val url = if (gistId != null) "$GITHUB_API/gists/$gistId" else "$GITHUB_API/gists"
            val method = if (gistId != null) "PATCH" else "POST"

            val request = Request.Builder()
                .url(url)
                .method(method, gistBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "token $token")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API error: ${response.code}"))
            }

            val responseBody = response.body?.string() ?: ""
            // Parse gist ID from response
            val newGistId = if (gistId == null) {
                val idMatch = """"id"\s*:\s*"([^"]+)"""".toRegex().find(responseBody)
                idMatch?.groupValues?.get(1)?.also {
                    syncPrefs.edit().putString(KEY_GIST_ID, it).apply()
                }
            } else gistId

            Result.success(newGistId ?: "unknown")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从 GitHub Gist 下载加密数据
     */
    suspend fun download(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = syncPrefs.getString(KEY_GITHUB_TOKEN, null)
                ?: return@withContext Result.failure(Exception("GitHub token not configured"))

            val gistId = syncPrefs.getString(KEY_GIST_ID, null)
                ?: return@withContext Result.failure(Exception("No gist ID found - upload first"))

            val request = Request.Builder()
                .url("$GITHUB_API/gists/$gistId")
                .addHeader("Authorization", "token $token")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API error: ${response.code}"))
            }

            val responseBody = response.body?.string() ?: ""
            // Extract content from gist response
            val contentMatch = """"content"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex().find(responseBody)
            val content = contentMatch?.groupValues?.get(1)
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")

            if (content != null) {
                Result.success(content)
            } else {
                Result.failure(Exception("Could not parse gist content"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
