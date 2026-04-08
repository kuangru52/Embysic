package com.kuangru52.embysic

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EmbyApiService {
    @GET("emby/Users/{userId}/Views")
    suspend fun getUserViews(
        @Path("userId") userId: String,
        @Header("X-Emby-Authorization") auth: String
    ): EmbyItemsResponse

    @GET("emby/Items")
    suspend fun getItems(
        @Query("UserId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,Index",
        @Header("X-Emby-Authorization") auth: String
    ): EmbyItemsResponse

    @GET("emby/Users/{userId}/Items/Latest")
    suspend fun getLatestItems(
        @Path("userId") userId: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Audio",
        @Query("Limit") limit: Int = 20,
        @Query("Fields") fields: String = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,Index",
        @Header("X-Emby-Authorization") auth: String
    ): List<EmbyItem>

    @GET("emby/Users/{userId}/Items")
    suspend fun getRecentlyPlayedItems(
        @Path("userId") userId: String,
        @Query("SortBy") sortBy: String = "DatePlayed",
        @Query("SortOrder") sortOrder: String = "Descending",
        @Query("IncludeItemTypes") includeItemTypes: String = "Audio",
        @Query("Limit") limit: Int = 20,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,Index",
        @Header("X-Emby-Authorization") auth: String
    ): EmbyItemsResponse

    @POST("emby/Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Header("X-Emby-Authorization") auth: String,
        @retrofit2.http.Body info: PlaybackReportInfo
    )

    @POST("emby/Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Header("X-Emby-Authorization") auth: String,
        @retrofit2.http.Body info: PlaybackReportInfo
    )

    @POST("emby/Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Header("X-Emby-Authorization") auth: String,
        @retrofit2.http.Body info: PlaybackProgressInfo
    )

    @POST("emby/Users/{userId}/FavoriteItems/{itemId}")
    suspend fun markFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String
    ): UserData

    @DELETE("emby/Users/{userId}/FavoriteItems/{itemId}")
    suspend fun unmarkFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String
    )

    @POST("emby/Users/{userId}/PlayedItems/{itemId}")
    suspend fun markPlayed(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String
    ): UserData

    @GET("emby/Items/{itemId}/Lyrics")
    suspend fun getLyrics(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String
    ): LyricsResponse

    @GET("emby/Users/{userId}/Items/{itemId}")
    suspend fun getItem(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String
    ): EmbyItem

    @POST("emby/Users/{userId}/Items/{itemId}/UserData")
    suspend fun updateUserData(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String,
        @retrofit2.http.Body userData: UserData
    ): UserData

    @GET("emby/Artists")
    suspend fun getArtists(
        @Query("UserId") userId: String,
        @Header("X-Emby-Authorization") auth: String,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,SortName"
    ): EmbyItemsResponse
}

data class PlaybackProgressInfo(
    val ItemId: String,
    val PositionTicks: Long,
    val MediaSourceId: String? = null,
    val PlaySessionId: String? = null,
    val IsPaused: Boolean = false,
    val CanSeek: Boolean = true,
    val IsMuted: Boolean = false,
    val VolumeLevel: Int? = 100,
    val PlayMethod: String = "Transcode",
    val RepeatMode: String = "RepeatNone",
    val EventName: String? = "TimeUpdate",
    val UserId: String? = null
)

data class LyricsResponse(
    val Lines: List<LyricLine>? = null,
    val Lyrics: List<LyricLine>? = null,
    val LyricLines: List<LyricLine>? = null
)

data class LyricLine(
    val Text: String,
    val Start: Long? = null,
    val StartTicks: Long? = null
)

data class PlaybackReportInfo(
    val ItemId: String,
    val UserId: String? = null,
    val MediaSourceId: String? = null,
    val PlaySessionId: String? = null,
    val PositionTicks: Long? = null,
    val PlayMethod: String = "Transcode",
    val RepeatMode: String = "RepeatNone",
    val CanSeek: Boolean = true
)

data class UserData(
    val PlaybackPositionTicks: Long = 0,
    val PlayCount: Int = 0,
    val IsFavorite: Boolean = false,
    val LastPlayedDate: String? = null
)

data class EmbyItemsResponse(
    val Items: List<EmbyItem>,
    val TotalRecordCount: Int
)

data class EmbyItem(
    val Id: String,
    val Name: String,
    val Type: String,
    val IsFolder: Boolean,
    val CollectionType: String? = null,
    val Artists: List<String>? = null,
    val Path: String? = null,
    val AlbumId: String? = null,
    val AlbumArtist: String? = null,
    val ImageTags: Map<String, String>? = null,
    val MediaSources: List<MediaSource>? = null,
    val UserData: UserData? = null,
    val RunTimeTicks: Long? = null,
    val Index: Int? = null,
    val ParentIndexNumber: Int? = null
)

data class MediaSource(
    val Id: String,
    val Container: String? = null,
    val Size: Long? = null,
    val Bitrate: Int? = null,
    val MediaStreams: List<MediaStream>? = null
)

data class MediaStream(
    val Codec: String? = null,
    val DisplayTitle: String? = null,
    val BitRate: Int? = null,
    val SampleRate: Int? = null,
    val BitDepth: Int? = null,
    val Channels: Int? = null
)
