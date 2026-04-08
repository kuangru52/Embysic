package com.kuangru52.embysic

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
object CacheManager {
    private var cache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.getExternalFilesDir(null), "music_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            // 设置 1GB 缓存上限，超过后自动删除最早的文件 (LRU)
            val evictor = LeastRecentlyUsedCacheEvictor(1024 * 1024 * 1024L) 
            val databaseProvider = StandaloneDatabaseProvider(context)
            
            cache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return cache!!
    }
}
