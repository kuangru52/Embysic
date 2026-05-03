package com.kuangru52.embysic

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val NETEASE_BASE_URL = "https://music.163.com/"

    val neteaseApi: NeteaseApiService by lazy {
        Retrofit.Builder()
            .baseUrl(NETEASE_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeteaseApiService::class.java)
    }

    fun getEmbyApiService(context: Context): EmbyApiService? {
        val prefs = context.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        if (serverUrl.isEmpty()) return null

        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return try {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EmbyApiService::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
