package com.kuangru52.embysic

import android.content.res.Configuration
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private lateinit var selectionBar: View
    private lateinit var btnDeleteBatch: View
    private lateinit var btnCancelSelection: View
    
    private var apiService: EmbyApiService? = null
    private var serverUrl = ""
    private var accessToken = ""
    private var userId = ""
    
    private val authHeader: String
        get() = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.45\", Token=\"$accessToken\""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_library, container, false)
        recyclerView = view.findViewById(R.id.rvLibrary)
        progressBar = view.findViewById(R.id.progressBar)
        selectionBar = view.findViewById(R.id.selectionBar)
        btnDeleteBatch = view.findViewById(R.id.btnDeleteBatch)
        btnCancelSelection = view.findViewById(R.id.btnCancelSelection)

        btnCancelSelection.setOnClickListener {
            adapter.setSelectionMode(false)
        }

        btnDeleteBatch.setOnClickListener {
            val selectedItems = adapter.getSelectedItems()
            if (selectedItems.isNotEmpty()) {
                showBatchDeleteConfirmation(selectedItems)
            }
        }
        
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
        adapter = LibraryAdapter(
            onItemClick = { item ->
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
            },
            onItemDelete = { item ->
                showDeleteConfirmation(item)
            },
            onSelectionModeChanged = { enabled ->
                selectionBar.visibility = if (enabled) View.VISIBLE else View.GONE
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun showBatchDeleteConfirmation(items: List<EmbyItem>) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_confirm, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_Embysic_Dialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = getString(R.string.confirm_delete)
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = getString(R.string.batch_delete_msg, items.size)
        
        dialogView.findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<TextView>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            performBatchDelete(items)
        }

        dialog.show()
        setupDialogWindow(dialog, dialogView)
    }

    private fun performBatchDelete(items: List<EmbyItem>) {
        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            var successCount = 0
            var failCount = 0
            for (item in items) {
                try {
                    service.deleteItem(item.Id, authHeader)
                    successCount++
                } catch (e: Exception) {
                    failCount++
                }
            }
            progressBar.visibility = View.GONE
            Toast.makeText(context, "Deleted $successCount items" + (if (failCount > 0) ", $failCount failed" else ""), Toast.LENGTH_SHORT).show()
            adapter.setSelectionMode(false)
            // 刷新列表
            val artistId = arguments?.getString("artist_id")
            val artistName = arguments?.getString("artist_name")
            loadItems(artistId, artistName ?: "层级")
        }
    }

    private fun showDeleteConfirmation(item: EmbyItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_confirm, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_Embysic_Dialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = "确认删除"
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = "确定要永久删除 \"${item.Name}\" 吗？\n此操作不可撤销。"
        
        dialogView.findViewById<TextView>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<TextView>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            performDelete(item)
        }

        dialog.show()
        setupDialogWindow(dialog, dialogView)
    }

    private fun setupDialogWindow(dialog: AlertDialog, dialogView: View) {
        val dm = resources.displayMetrics
        val px38dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 38f, dm)
        val pxNeg60dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, -60f, dm)
        val px20dpRefHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, dm)
        val px10dpBlurRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, dm)

        val width = (dm.widthPixels * 0.82).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val container = (activity as? HomeActivity)?.findViewById<View>(R.id.fragment_container)
            
            dialogView.post {
                val dialogLoc = IntArray(2)
                dialogView.getLocationOnScreen(dialogLoc)
                val containerLoc = IntArray(2)
                container?.getLocationOnScreen(containerLoc)
                
                val relativeLeft = (dialogLoc[0] - containerLoc[0]).toFloat()
                val relativeTop = (dialogLoc[1] - containerLoc[1]).toFloat()
                
                val dialogRect = RectF(
                    relativeLeft,
                    relativeTop,
                    relativeLeft + dialogView.width,
                    relativeTop + dialogView.height
                )
                
                (activity as? HomeActivity)?.applyDialogEffect(dialogRect, px38dp, pxNeg60dp, px20dpRefHeight, px10dpBlurRadius)
            }
            
            dialog.setOnDismissListener {
                (activity as? HomeActivity)?.clearDialogEffect()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            dialog.window?.attributes?.let {
                it.blurBehindRadius = 64
                dialog.window?.attributes = it
            }
        }
    }

    private fun performDelete(item: EmbyItem) {
        val service = apiService ?: return
        lifecycleScope.launch {
            try {
                service.deleteItem(item.Id, authHeader)
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                // 刷新当前列表
                val artistId = arguments?.getString("artist_id")
                val artistName = arguments?.getString("artist_name")
                loadItems(artistId, artistName ?: "层级")
            } catch (e: Exception) {
                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("LibraryFragment", "Delete error", e)
            }
        }
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
                        auth = currentAuth,
                        fields = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,Filename,SortName,ChildCount,RecursiveItemCount,ParentId,HasLyrics"
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
        val currentMediaItem = controller.currentMediaItem
        val currentSessionId = currentMediaItem?.mediaMetadata?.extras?.getString("play_session_id")
        val currentId = currentMediaItem?.mediaId
        
        val playableItems = allItems.filter { !it.IsFolder }
        val isSameSong = item.Id == currentId
        
        val mediaItems = playableItems.map { song ->
            // 如果是同一首歌，或者我们在无缝更新播放列表，复用当前 SessionId
            val overrideId = if (song.Id == currentId) currentSessionId else null
            MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId, overrideSessionId = overrideId)
        }
        
        if (isSameSong) {
            // 无缝更新播放列表，不重置位置，Media3 会自动匹配 MediaId 并保持播放
            controller.setMediaItems(mediaItems, false)
        } else {
            // 切换到新歌
            val startIndex = playableItems.indexOfFirst { it.Id == item.Id }.coerceAtLeast(0)
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()
        }
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
