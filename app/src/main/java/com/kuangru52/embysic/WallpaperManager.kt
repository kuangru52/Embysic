package com.kuangru52.embysic

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object WallpaperManager {

    private const val PREF_NAME = "embysic_prefs"
    private const val KEY_MODE = "wallpaper_mode" // "bing", "local", "none"
    private const val KEY_LOCAL_URIS = "local_wallpaper_uris"
    private const val KEY_LOCAL_INDEX = "local_wallpaper_index"
    private const val KEY_LAST_SWITCH_TIME = "last_wallpaper_switch_time"
    private const val KEY_BING_URL = "bing_wallpaper_url"

    private const val ONE_HOUR_MS = 3600_000L

    fun getMode(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, "bing") ?: "bing"
    }

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_MODE, mode)
        }
    }

    fun getLocalUris(context: Context): Set<String> {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_LOCAL_URIS, emptySet()) ?: emptySet()
    }

    fun setLocalUris(context: Context, uris: List<Uri>) {
        val dir = File(context.filesDir, "wallpapers")
        if (!dir.exists()) dir.mkdirs()

        // 清理旧本地壁纸
        dir.listFiles()?.forEach { it.delete() }

        val savedPaths = mutableSetOf<String>()
        val resolver = context.contentResolver

        uris.forEachIndexed { index, uri ->
            try {
                val destFile = File(dir, "local_wall_$index.jpg")
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    savedPaths.add(destFile.absolutePath)
                }
            } catch (e: Exception) {
                Log.e("WallpaperManager", "Failed to copy local wallpaper $uri", e)
            }
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putStringSet(KEY_LOCAL_URIS, savedPaths)
            putInt(KEY_LOCAL_INDEX, 0)
            putLong(KEY_LAST_SWITCH_TIME, System.currentTimeMillis())
            putString(KEY_MODE, "local")
        }
    }

    suspend fun getActiveWallpaperSource(context: Context): Any {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_MODE, "bing") ?: "bing"

        return when (mode) {
            "none" -> R.drawable.logo
            "local" -> {
                val paths = prefs.getStringSet(KEY_LOCAL_URIS, emptySet())?.toList() ?: emptyList()
                if (paths.isEmpty()) {
                    fetchBingDailyWallpaperUrl(context) ?: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=1920"
                } else if (paths.size == 1) {
                    File(paths[0])
                } else {
                    // 多图轮播 (1小时刷新)
                    var index = prefs.getInt(KEY_LOCAL_INDEX, 0)
                    val lastTime = prefs.getLong(KEY_LAST_SWITCH_TIME, 0L)
                    val now = System.currentTimeMillis()

                    if (now - lastTime >= ONE_HOUR_MS) {
                        index = (index + 1) % paths.size
                        prefs.edit {
                            putInt(KEY_LOCAL_INDEX, index)
                            putLong(KEY_LAST_SWITCH_TIME, now)
                        }
                    }
                    val safeIndex = index.coerceIn(0, paths.size - 1)
                    File(paths[safeIndex])
                }
            }
            else -> {
                // 默认使用微软每日必应壁纸
                fetchBingDailyWallpaperUrl(context) ?: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=1920"
            }
        }
    }

    private suspend fun fetchBingDailyWallpaperUrl(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val cachedUrl = prefs.getString(KEY_BING_URL, null)

        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrEmpty()) {
                    val jsonObj = JSONObject(jsonStr)
                    val images = jsonObj.optJSONArray("images")
                    if (images != null && images.length() > 0) {
                        val relativeUrl = images.getJSONObject(0).optString("url")
                        if (relativeUrl.isNotEmpty()) {
                            val fullUrl = "https://cn.bing.com$relativeUrl"
                            prefs.edit { putString(KEY_BING_URL, fullUrl) }
                            return@withContext fullUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WallpaperManager", "Failed to fetch Bing daily wallpaper", e)
        }

        cachedUrl
    }
}
