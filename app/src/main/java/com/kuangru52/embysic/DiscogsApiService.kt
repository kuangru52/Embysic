@file:Suppress("unused", "PropertyName")
package com.kuangru52.embysic

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DiscogsApiService {
    @GET("database/search")
    suspend fun searchRelease(
        @Query("release_title") album: String,
        @Query("artist") artist: String,
        @Query("type") type: String = "release",
        @Header("User-Agent") userAgent: String = "EmbySic/1.0",
        @Header("Authorization") auth: String,
    ): DiscogsSearchResponse
}

data class DiscogsSearchResponse(
    val results: List<DiscogsResult>?,
)

data class DiscogsResult(
    val id: Long,
    val title: String,
    val cover_image: String?,
    val thumb: String?,
    val resource_url: String?,
)
