@file:Suppress("unused", "PropertyName")
package com.kuangru52.embysic

import retrofit2.http.GET
import retrofit2.http.Query

interface NeteaseApiService {
    @GET("api/search/get")
    suspend fun searchSong(
        @Query("s") keywords: String,
        @Query("type") type: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
    ): NeteaseSearchResponse

    @GET("api/v3/song/detail")
    suspend fun getSongDetail(
        @Query("c") ids: String,
    ): NeteaseSongDetailResponse

    @GET("api/song/lyric")
    suspend fun getLyric(
        @Query("id") songId: Long,
        @Query("lv") lv: Int = -1,
        @Query("kv") kv: Int = -1,
        @Query("tv") tv: Int = -1,
    ): NeteaseLyricResponse
}

data class NeteaseSearchResponse(
    val result: NeteaseSearchResult?,
    val code: Int,
)

data class NeteaseSearchResult(
    val songs: List<NeteaseSearchSong>?,
    val songCount: Int,
)

data class NeteaseSearchSong(
    val id: Long,
    val name: String,
    val artists: List<NeteaseArtist>?,
    val album: NeteaseAlbum?,
    val duration: Long?,
)

data class NeteaseSong(
    val id: Long,
    val name: String,
    val ar: List<NeteaseArtist>?,
    val al: NeteaseAlbum?,
)

data class NeteaseAlbum(
    val id: Long,
    val name: String,
    val picUrl: String?,
    val pic: Long?,
)

data class NeteaseArtist(
    val id: Long,
    val name: String,
    val picUrl: String?,
)

data class NeteaseSongDetailResponse(
    val songs: List<NeteaseSong>?,
    val code: Int,
)

data class NeteaseLyricResponse(
    val lrc: NeteaseLrc?,
    val tlyric: NeteaseLrc?,
    val code: Int,
)

data class NeteaseLrc(
    val lyric: String?,
    val version: Int,
)
