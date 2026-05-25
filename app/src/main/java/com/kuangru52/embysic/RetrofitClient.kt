package com.kuangru52.embysic

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Protocol
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val NETEASE_BASE_URL = "https://music.163.com/"
    private const val DISCOGS_BASE_URL = "https://api.discogs.com/"

    private val discogsOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "EmbySic/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    val discogsApi: DiscogsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DISCOGS_BASE_URL)
            .client(discogsOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DiscogsApiService::class.java)
    }

    private val neteaseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor { chain ->
                val request = chain.request()
                val newRequest = request.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://music.163.com/")
                    .header("Cookie", "os=pc; appver=2.10.12; osver=Microsoft-Windows-10-Professional-build-19045-64bit; MUSIC_U=; __remember_me=true")
                    .build()
                chain.proceed(newRequest)
            }
            .build()
    }

    val neteaseApi: NeteaseApiService by lazy {
        Retrofit.Builder()
            .baseUrl(NETEASE_BASE_URL)
            .client(neteaseOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NeteaseApiService::class.java)
    }

    private var embyApiService: EmbyApiService? = null
    private var currentServerUrl: String? = null

    fun getEmbyApiService(context: Context): EmbyApiService? {
        val prefs = context.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        if (serverUrl.isEmpty()) {
            embyApiService = null
            currentServerUrl = null
            return null
        }

        if (embyApiService != null && serverUrl == currentServerUrl) return embyApiService

        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return try {
            val newService = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EmbyApiService::class.java)
            embyApiService = newService
            currentServerUrl = serverUrl
            newService
        } catch (e: Exception) {
            null
        }
    }
}
