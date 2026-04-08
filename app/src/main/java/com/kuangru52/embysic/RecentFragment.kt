package com.kuangru52.embysic

import android.net.Uri
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
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RecentFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: LibraryAdapter
    private var apiService: EmbyApiService? = null
    
    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_recent, container, false)
        recyclerView = view.findViewById(R.id.rvRecent)
        progressBar = view.findViewById(R.id.progressBar)
        
        setupRecyclerView()
        loadPrefs()
        initApiService()
        loadRecentItems()
        
        return view
    }

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(
            onItemClick = { item -> playMusic(item, adapter.items) },
            onItemDoubleClick = { replaceFragment(FavoriteFragment()) }
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
        userId = prefs.getString("user_id", "") ?: ""
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

    private fun loadRecentItems() {
        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\", Token=\"$accessToken\""
                // 改为调用 getRecentlyPlayedItems 获取真正的最近播放列表
                val response = service.getRecentlyPlayedItems(userId, auth = authHeader)
                adapter.submitList(response.Items)
            } catch (e: Exception) {
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
