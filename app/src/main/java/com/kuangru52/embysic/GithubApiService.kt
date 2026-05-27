@file:Suppress("unused", "PropertyName")
package com.kuangru52.embysic

import retrofit2.http.GET

interface GithubApiService {
    @GET("repos/kuangru52/embysic/releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}

data class GithubRelease(
    val tag_name: String,
    val html_url: String,
    val body: String
)
