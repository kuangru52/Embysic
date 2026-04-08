package com.kuangru52.embysic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class LibraryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: LibraryAdapter
    
    private var apiService: EmbyApiService? = null
    private var serverUrl = ""
    private var accessToken = ""
    private var userId = ""
    
    private val authHeader: String
        get() = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\", Token=\"$accessToken\""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_library, container, false)
        recyclerView = view.findViewById(R.id.rvLibrary)
        progressBar = view.findViewById(R.id.progressBar)
        
        // 隐藏常驻搜索框（如果布局里有的话）
        view.findViewById<View>(R.id.searchView)?.visibility = View.GONE
        
        setupRecyclerView()
        loadPrefs()
        initApiService()

        val artistId = arguments?.getString("artist_id")
        val artistName = arguments?.getString("artist_name")
        if (artistId != null) {
            loadItems(artistId, artistName ?: "艺人")
        } else {
            loadItems(null, "音乐库")
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        })
        return view
    }

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(onItemClick = { item ->
            if (item.IsFolder) {
                val fragment = LibraryFragment().apply {
                    arguments = Bundle().apply {
                        putString("artist_id", item.Id)
                        putString("artist_name", item.Name)
                    }
                }
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.ios_slide_in_right,
                        R.anim.ios_exit_push_left,
                        R.anim.ios_pull_right,
                        R.anim.ios_slide_out_right
                    )
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            } else {
                playMusic(item, adapter.items)
            }
        })
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun loadItems(parentId: String?, title: String) {
        val service = apiService ?: return
        (activity as? AppCompatActivity)?.supportActionBar?.title = title

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val response = if (parentId == null) {
                    val views = service.getUserViews(userId, authHeader)
                    views.Items.find { it.CollectionType == "music" }?.let { music ->
                        loadItems(music.Id, music.Name)
                        return@launch
                    }
                    views
                } else {
                    service.getItems(userId, parentId, recursive = false, auth = authHeader)
                }
                
                val sortedItems = response.Items.sortedWith(compareBy({ !it.IsFolder }, { it.Index ?: 0 }))
                adapter.submitList(sortedItems)
            } catch (e: Exception) {
                Log.e("LibraryFragment", "Load failed: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun playMusic(item: EmbyItem, allItems: List<EmbyItem>) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return
        val mediaItems = allItems.filter { !it.IsFolder }.map { song ->
            MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId)
        }
        val startIndex = mediaItems.indexOfFirst { it.mediaId == item.Id }.coerceAtLeast(0)
        controller.stop()
        controller.setMediaItems(mediaItems, startIndex, 0L)
        
        item.UserData?.PlaybackPositionTicks?.let { 
            if (it > 1000000) controller.seekTo(it / 10000)
        }
        controller.prepare()
        controller.play()

        // 播放后自动弹出播放页面
        (activity as? HomeActivity)?.showPlayer()
    }

    private fun loadPrefs() {
        requireContext().getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE).apply {
            serverUrl = getString("server_url", "") ?: ""
            accessToken = getString("access_token", "") ?: ""
            userId = getString("user_id", "") ?: ""
        }
    }

    private fun initApiService() {
        if (serverUrl.isEmpty()) return
        apiService = Retrofit.Builder()
            .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EmbyApiService::class.java)
    }
}
