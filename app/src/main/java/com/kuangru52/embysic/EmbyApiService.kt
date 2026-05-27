@file:Suppress("unused", "PropertyName")
package com.kuangru52.embysic

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface EmbyApiService {
    @POST("emby/Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Header("X-Emby-Authorization") auth: String,
        @retrofit2.http.Body request: AuthenticateRequest
    ): AuthenticateResponse

    @GET("emby/Users/{userId}/Views")
    suspend fun getUserViews(
        @Path("userId") userId: String,
        @Header("X-Emby-Authorization") auth: String
    ): EmbyItemsResponse

    @GET("emby/Items")
    suspend fun getItems(
        @Query("UserId") userId: String,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = "Audio",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,Filename,SortName,ChildCount,RecursiveItemCount,ParentId,HasLyrics",
        @Header("X-Emby-Authorization") auth: String,
        @Query("StartIndex") startIndex: Int? = null,
        @Query("Limit") limit: Int? = null,
        @Query("SortBy") sortBy: String? = "Random",
        @Query("SortOrder") sortOrder: String? = null,
        @Query("MediaTypes") mediaTypes: String? = null,
        @Query("ParentId") parentId: String? = null,
        @Query("Ids") ids: String? = null
    ): EmbyItemsResponse

    @GET("emby/Search/Hints")
    suspend fun getSearchHints(
        @Query("UserId") userId: String,
        @Query("SearchTerm") searchTerm: String,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Limit") limit: Int? = null,
        @Header("X-Emby-Authorization") auth: String
    ): com.google.gson.JsonElement

    @GET("emby/Items")
    suspend fun getItemsFlexible(
        @Query("UserId") userId: String,
        @Query("Ids") ids: String? = null,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,ParentId,HasLyrics",
        @Header("X-Emby-Authorization") auth: String
    ): com.google.gson.JsonElement

    @GET("emby/Library/Browse")
    suspend fun browseDirectory(
        @Query("Id") id: String,
        @Header("X-Emby-Authorization") auth: String
    ): EmbyItemsResponse

    @GET("emby/Users/{userId}/Items/Latest")
    suspend fun getLatestItems(
        @Path("userId") userId: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Audio",
        @Query("Limit") limit: Int = 20,
        @Query("Fields") fields: String = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,ParentId,HasLyrics",
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
        @Query("Fields") fields: String = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,ParentId,HasLyrics",
        @Header("X-Emby-Authorization") auth: String
    ): EmbyItemsResponse

    @POST("emby/Sessions/Playing/Stopped")
    suspend fun reportPlaybackStop(
        @Header("X-Emby-Authorization") auth: String,
        @retrofit2.http.Body info: PlaybackReportInfo
    )

    @POST("emby/Sessions/Playing")
    suspend fun reportPlaybackStart(
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
        @Header("X-Emby-Authorization") auth: String,
        @Query("api_key") apiKey: String? = null
    ): LyricsResponse

    @GET("emby/Users/{userId}/Items/{itemId}")
    suspend fun getItem(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String,
        @Query("Fields") fields: String = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,ParentId,HasLyrics,ArtistItems,AlbumArtists"
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

    @POST("emby/Items/{itemId}/Refresh")
    suspend fun refreshItem(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String,
        @Query("MetadataRefreshMode") metadataRefreshMode: String = "Default",
        @Query("ImageRefreshMode") imageRefreshMode: String = "Default",
        @Query("ReplaceAllMetadata") replaceAllMetadata: Boolean = false,
        @Query("ReplaceAllImages") replaceAllImages: Boolean = false
    )

    @GET("emby/Items")
    suspend fun getDirectoryItems(
        @Query("ParentId") parentId: String,
        @Query("UserId") userId: String,
        @Header("X-Emby-Authorization") auth: String,
        @Query("Fields") fields: String = "Path,ParentId",
        @Query("Recursive") recursive: Boolean = false
    ): EmbyItemsResponse

    @DELETE("emby/Items/{itemId}")
    suspend fun deleteItem(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String
    )

    @GET("emby/Items/{itemId}/Download")
    @Streaming
    suspend fun downloadFile(
        @Path("itemId") itemId: String,
        @Header("X-Emby-Authorization") auth: String
    ): okhttp3.ResponseBody
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
    val Lyrics: com.google.gson.JsonElement? = null,
    val LyricLines: List<LyricLine>? = null
)

data class LyricLine(
    val Text: String? = null,
    val Start: Double? = null,
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

data class SearchHintsResponse(
    val SearchHints: List<SearchHint>,
    val TotalRecordCount: Int
)

data class SearchHint(
    val Id: String? = null,
    val ItemId: String? = null,
    val Name: String? = null,
    val Type: String? = null
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
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val ParentId: String? = null,
    val FileName: String? = null,
    val Filename: String? = null,
    val SortName: String? = null,
    val Album: String? = null,
    val ChildCount: Int? = null,
    val RecursiveItemCount: Int? = null,
    val HasLyrics: Boolean? = null,
    val ArtistItems: List<ArtistItem>? = null,
    val AlbumArtists: List<ArtistItem>? = null
)

data class ArtistItem(
    val Name: String?,
    val Id: String?
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

data class AuthenticateRequest(
    val Username: String? = null,
    val Pw: String? = null
)

data class AuthenticateResponse(
    val AccessToken: String? = null,
    val User: EmbyUser? = null
)

data class EmbyUser(
    val Id: String? = null,
    val Name: String? = null
)
