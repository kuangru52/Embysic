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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
        get() = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.40\", Token=\"$accessToken\""

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
        val targetItemId = arguments?.getString("target_item_id")

        (activity as? HomeActivity)?.findViewById<View>(R.id.bottom_container)?.visibility = View.VISIBLE

        if (targetItemId != null) {
            // 如果是从播放页跳转来的，先获取该歌曲的父级 ID
            locateAndLoadParent(targetItemId)
        } else if (artistId != null) {
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

    private fun locateAndLoadParent(itemId: String) {
        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                // 1. 获取歌曲详情以拿到 ParentId
                val item = service.getItem(userId, itemId, authHeader)
                val parentId = item.ParentId
                if (parentId != null) {
                    // 2. 加载父文件夹内容
                    loadItems(parentId, "所在目录")
                } else {
                    loadItems(null, "音乐库")
                }
            } catch (e: Exception) {
                loadItems(null, "音乐库")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlaybackState(mediaItem)
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
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

                // 1. 过滤：排除名为 "Artwork" 的文件夹
                // 2. 增强过滤：如果是文件夹类型，则检查其是否有音乐或子文件夹，避免显示只有图片的空壳文件夹
                val filteredItems = response.Items.filter { item ->
                    if (item.Name == "Artwork") return@filter false
                    
                    // 如果是文件夹或专辑，检查其内容计数（Emby 返回的 RecursiveItemCount 或 ChildCount）
                    if (item.IsFolder || item.Type == "MusicAlbum" || item.Type == "MusicArtist" || item.Type == "CollectionFolder") {
                        // 如果有 RecursiveItemCount 且为 0，说明没有任何后代（包括音乐和子文件夹）
                        val hasContent = (item.RecursiveItemCount ?: 1) > 0 || (item.ChildCount ?: 1) > 0
                        hasContent
                    } else {
                        // 非文件夹（即 Audio 等）直接保留
                        true
                    }
                }

                // 排序逻辑：
                val folders = filteredItems.filter { it.Type != "Audio" }
                val music = filteredItems.filter { it.Type == "Audio" }.sortedWith { a, b ->
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
                
                // 增加“面包屑/母文件夹”功能
                // 逻辑：只有当 parentId 存在，且 parentId 对应的不是“音乐库”根目录时显示
                var showBackFolder = false
                if (parentId != null) {
                    val views = service.getUserViews(userId, authHeader)
                    val musicLibrary = views.Items.find { it.CollectionType == "music" }
                    // 如果当前 parentId 不是音乐库 ID，说明在子文件夹里
                    if (musicLibrary != null && parentId != musicLibrary.Id) {
                        showBackFolder = true
                    }
                }

                val finalItems = if (showBackFolder) {
                    val parentItem = EmbyItem(
                        Id = "BACK_FOLDER",
                        Name = title,
                        Type = "Folder",
                        IsFolder = true
                    )
                    listOf(parentItem) + sortedItems
                } else {
                    sortedItems
                }
                
                adapter.submitList(finalItems)
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
