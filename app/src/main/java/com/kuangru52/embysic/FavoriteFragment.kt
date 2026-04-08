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

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(onItemClick = { item -> playMusic(item, adapter.items) })
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun playMusic(item: EmbyItem, allItems: List<EmbyItem>) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return

        val mediaItems = allItems.map { song ->
            MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId)
        }

        val startIndex = mediaItems.indexOfFirst { it.mediaId == item.Id }.coerceAtLeast(0)
        controller.setMediaItems(mediaItems, startIndex, 0L)
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
                val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\", Token=\"$accessToken\""
                // 使用 getItems 并指定 Filters=IsFavorite
                val response = service.getItems(
                    userId = userId,
                    includeItemTypes = "Audio",
                    recursive = true,
                    fields = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,Index",
                    auth = authHeader
                )
                // 过滤出收藏的项目 (有些版本的 Emby 需要在客户端过滤，或者在 API 中传 Filters=IsFavorite)
                val favorites = response.Items.filter { it.UserData?.IsFavorite == true }
                adapter.submitList(favorites)
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
