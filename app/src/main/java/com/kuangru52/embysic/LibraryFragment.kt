package com.kuangru52.embysic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LibraryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: LibraryAdapter
    
    private var apiService: EmbyApiService? = null
    private var serverUrl = ""
    private var accessToken = ""
    private var userId = ""
    
    private val authHeader: String
        get() = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.5\", Token=\"$accessToken\""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_library, container, false)
        recyclerView = view.findViewById(R.id.rvLibrary)
        progressBar = view.findViewById(R.id.progressBar)
        
        setupRecyclerView()
        loadPrefs()
        initApiService()

        val swipeBackLayout = view.findViewById<SwipeBackLayout>(R.id.swipeBackLayout)
        val artistId = arguments?.getString("artist_id")
        val artistName = arguments?.getString("artist_name")

        (activity as? HomeActivity)?.findViewById<View>(R.id.bottom_container)?.visibility = View.VISIBLE

        if (artistId != null) {
            val fragments = parentFragmentManager.fragments
            if (fragments.size >= 2) {
                val prevFragment = fragments[fragments.size - 2]
                swipeBackLayout.setPreviousView(prevFragment.view)
            }
            swipeBackLayout.setOnSwipeBackListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            loadItems(artistId, artistName ?: "层级")
        } else {
            loadItems(null, "音乐库")
        }

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
                    .setCustomAnimations(R.anim.ios_slide_in_right, 0, 0, 0)
                    .add(R.id.fragment_container, fragment)
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
        val currentUserId = userId 
        val currentAuth = authHeader
        (activity as? AppCompatActivity)?.supportActionBar?.title = title

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val response = if (parentId == null) {
                    val views = service.getUserViews(currentUserId, currentAuth)
                    val musicLibrary = views.Items.find { it.CollectionType == "music" }
                    if (musicLibrary != null) {
                        loadItems(musicLibrary.Id, musicLibrary.Name)
                        return@launch
                    }
                    views
                } else {
                    service.getItems(
                        userId = currentUserId, 
                        parentId = parentId, 
                        recursive = false, 
                        auth = currentAuth
                    )
                }

                // 为了完全匹配 Emby 服务器的文件夹排序，我们直接使用服务器返回的顺序：
                // 1. 提取非音乐项（文件夹、专辑、歌手等），保持服务器原始排序（支持自然排序）。
                // 2. 提取音乐项（Audio），并按碟片号 + 音轨号进行人工排序。
                // 3. 最后将两者合并，确保文件夹在上方。
                val allItems = response.Items
                val folders = allItems.filter { it.Type != "Audio" && it.Name != "Artwork" }
                val music = allItems.filter { it.Type == "Audio" }.sortedWith { a, b ->
                    val discA = a.ParentIndexNumber ?: 1
                    val discB = b.ParentIndexNumber ?: 1
                    if (discA != discB) {
                        discA.compareTo(discB)
                    } else {
                        val idxA = a.IndexNumber ?: Int.MAX_VALUE
                        val idxB = b.IndexNumber ?: Int.MAX_VALUE
                        if (idxA != idxB) {
                            idxA.compareTo(idxB)
                        } else {
                            a.Name.compareTo(b.Name, ignoreCase = true)
                        }
                    }
                }
                val sortedItems = folders + music
                adapter.submitList(sortedItems)
            } catch (e: Exception) {
                // 静默处理所有加载失败，避免切换过快产生的弹窗
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun playMusic(item: EmbyItem, allItems: List<EmbyItem>) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return
        val playableItems = allItems.filter { !it.IsFolder }
        val mediaItems = playableItems.map { song ->
            MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId)
        }
        val startIndex = playableItems.indexOfFirst { it.Id == item.Id }.coerceAtLeast(0)
        controller.stop()
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
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
