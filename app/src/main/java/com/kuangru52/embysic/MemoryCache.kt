package com.kuangru52.embysic

object MemoryCache {
    var recentItems: List<EmbyItem>? = null
    var favoriteItems: List<EmbyItem>? = null
    val libraryFoldersCache = mutableMapOf<String, List<EmbyItem>>()
}
