package com.kuangru52.embysic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FavoriteFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: LibraryAdapter
    private var apiService: EmbyApiService? = null
    
    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_recent, container, false) // 重用 fragment_recent 布局
        recyclerView = view.findViewById(R.id.rvRecent)
        progressBar = view.findViewById(R.id.progressBar)
        
        setupRecyclerView()
        loadPrefs()
        initApiService()
        loadFavoriteItems()
        
        return view
    }

    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlaybackState(mediaItem)
        }
        override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo, newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) {
            updatePlaybackState((activity as? HomeActivity)?.mediaController?.currentMediaItem)
        }
    }

    private fun updatePlaybackState(mediaItem: MediaItem?) {
        val extras = mediaItem?.mediaMetadata?.extras
        val albumId = extras?.getString("album_id")
        val path = extras?.getString("path")
        adapter.setCurrentMediaId(mediaItem?.mediaId, albumId, path)
    }

    override fun onStart() {
        super.onStart()
        val controller = (activity as? HomeActivity)?.mediaController
        controller?.let { 
            it.addListener(playerListener)
            updatePlaybackState(it.currentMediaItem)
        }
    }

    override fun onStop() {
        super.onStop()
        (activity as? HomeActivity)?.mediaController?.removeListener(playerListener)
    }

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(
            onItemClick = { item -> playMusic(item, adapter.items) },
            onItemDoubleClick = { replaceFragment(RecentFragment()) }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun replaceFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun playMusic(item: EmbyItem, allItems: List<EmbyItem>) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return

        val mediaItems = allItems.map { song ->
            MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId)
        }

        val startIndex = mediaItems.indexOfFirst { it.mediaId == item.Id }.coerceAtLeast(0)
        controller.setMediaItems(mediaItems, startIndex, 0L)
        
        // 断点续听
        val lastPositionTicks = item.UserData?.PlaybackPositionTicks ?: 0L
        if (lastPositionTicks > 0) {
            controller.seekTo(lastPositionTicks / 10000)
        }

        controller.prepare()
        controller.play()

        // 播放后自动弹出播放页面
        (activity as? HomeActivity)?.showPlayer()
    }

    private fun loadPrefs() {
        val prefs = requireContext().getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", "") ?: ""
        accessToken = prefs.getString("access_token", "") ?: ""
        userId = prefs.getString("userId", "") ?: "" // 修正: 获取正确的 userId
        if (userId.isEmpty()) {
            userId = prefs.getString("user_id", "") ?: ""
        }
    }

    private fun initApiService() {
        if (serverUrl.isNotEmpty()) {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit.create(EmbyApiService::class.java)
        }
    }

    private fun loadFavoriteItems() {
        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.36\", Token=\"$accessToken\""
                // 优先使用 Filters=IsFavorite 让服务端过滤，减少带宽消耗
                val response = service.getItems(
                    userId = userId,
                    includeItemTypes = "Audio",
                    recursive = true,
                    fields = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,Index",
                    auth = authHeader,
                    // 注意：Emby API 某些版本可能不支持 Filters，此时会在下面手动过滤
                )
                // 客户端二次过滤确保准确性
                val favorites = response.Items.filter { it.UserData?.IsFavorite == true }
                adapter.submitList(favorites)
            } catch (e: Exception) {
                // 静默处理加载失败
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
