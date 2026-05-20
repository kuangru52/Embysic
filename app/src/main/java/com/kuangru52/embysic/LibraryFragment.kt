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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@UnstableApi
class LibraryFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        val activity = activity as? HomeActivity
        if (activity?.isSwipingBack == true) {
            // 如果是手势返回，无论是进入还是退出，都返回一个时长为 0 的空动画
            // 彻底防止系统动画重叠
            return object : Animation() {}.apply { duration = 0 }
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    private lateinit var ivFragmentBackground: android.widget.ImageView
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
        ivFragmentBackground = view.findViewById(R.id.ivFragmentBackground)
        recyclerView = view.findViewById(R.id.rvLibrary)
        
        // 沉浸式：设置 PaddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // 同步全局模糊背景
        syncBackground()

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
            syncBackground()
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            updatePlaybackState((activity as? HomeActivity)?.mediaController?.currentMediaItem)
        }
    }

    private fun syncBackground() {
        if (!isAdded) return
        
        val isTabletLand = resources.configuration.smallestScreenWidthDp >= 600 && 
                          resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        
        if (isTabletLand) {
            ivFragmentBackground.visibility = View.GONE
            return
        }

        // 核心修复 1：物理遮挡。
        // 设置一个纯黑背景底色。即便图片还没加载出来，也能彻底遮挡下层文字。
        ivFragmentBackground.setBackgroundColor(android.graphics.Color.BLACK) 
        
        val activityBg = (activity as? HomeActivity)?.findViewById<android.widget.ImageView>(R.id.ivBlurBackground)
        if (activityBg != null && activityBg.drawable != null) {
            // 核心修复 2：克隆 Drawable 状态，并强制不透明。
            // 避免 Drawable 级别透明度污染
            val constantState = activityBg.drawable.constantState
            if (constantState != null) {
                val newDrawable = constantState.newDrawable().mutate()
                newDrawable.alpha = 255 
                ivFragmentBackground.setImageDrawable(newDrawable)
            } else {
                ivFragmentBackground.setImageDrawable(activityBg.drawable)
            }
        } else {
            ivFragmentBackground.setImageResource(R.drawable.bg_superman)
        }
        
        // 核心修复 3：强制 ImageView 视图层 Alpha 为 1.0
        ivFragmentBackground.alpha = 1.0f 
        ivFragmentBackground.visibility = View.VISIBLE
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
                if (item.Id == "BACK_FOLDER") {
                    parentFragmentManager.popBackStack()
                    return@LibraryAdapter
                }
                if (item.IsFolder) {
                    val fragment = LibraryFragment().apply {
                        arguments = Bundle().apply {
                            putString("artist_id", item.Id)
                            putString("artist_name", item.Name)
                        }
                    }
                    (activity as? HomeActivity)?.replaceFragment(fragment, "Library_${item.Id}")
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
            val msg = getString(R.string.deleted_success) + " $successCount items" + (if (failCount > 0) ", $failCount failed" else "")
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = getString(R.string.confirm_delete)
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = getString(R.string.delete_msg, item.Name)
        
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
                Toast.makeText(context, R.string.deleted_success, Toast.LENGTH_SHORT).show()
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

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val response = if (parentId == null) {
                    val views = service.getUserViews(currentUserId, currentAuth)
                    val musicLibrary = views.Items.find { it.CollectionType.equals("music", ignoreCase = true) }
                    if (musicLibrary != null) {
                        // 修复：直接加载音乐库内容，不再递归调用 loadItems 避免状态混乱
                        service.getItems(
                            userId = currentUserId,
                            parentId = musicLibrary.Id,
                            recursive = false,
                            auth = currentAuth,
                            includeItemTypes = "Audio,Folder,MusicAlbum,MusicArtist,CollectionFolder",
                            fields = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,Filename,SortName,ChildCount,RecursiveItemCount,ParentId,HasLyrics",
                            sortBy = "IsFolder,SortName",
                            sortOrder = "Ascending"
                        )
                    } else {
                        views
                    }
                } else {
                    service.getItems(
                        userId = currentUserId, 
                        parentId = parentId, 
                        recursive = false, 
                        auth = currentAuth,
                        includeItemTypes = "Audio,Folder,MusicAlbum,MusicArtist,CollectionFolder",
                        fields = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,Filename,SortName,ChildCount,RecursiveItemCount,ParentId,HasLyrics",
                        sortBy = "IsFolder,SortName",
                        sortOrder = "Ascending"
                    )
                }

                // 1. 过滤：排除名为 "Artwork" 的文件夹，以及没有任何音频内容的空文件夹（例如仅含图片的文件夹）
                val filteredItems = response.Items.filter { item ->
                    if (item.Name == "Artwork") return@filter false
                    // 如果是文件夹，检查其递归子项计数。如果为 0，说明该文件夹内没有音频或可播放内容。
                    if (item.IsFolder && item.Type != "MusicArtist" && (item.RecursiveItemCount ?: 0) <= 0 && (item.ChildCount ?: 0) <= 0) {
                        return@filter false
                    }
                    true
                }

                // 排序逻辑：遵循 Emby 服务器原始排序（处理中文）+ 音乐音轨强制排序
                // 1. 文件夹：直接信任 Emby 服务端的 SortName 排序结果，完美支持中文拼音
                val folders = filteredItems.filter { it.IsFolder || it.Type != "Audio" }
                
                // 2. 音乐文件：在当前目录下，依然强制按照【碟号 > 音轨号】排序，确保专辑曲目顺序正确
                val music = filteredItems.filter { !it.IsFolder && it.Type == "Audio" }
                    .sortedWith(
                        compareBy<EmbyItem> { it.ParentIndexNumber ?: 0 } // 先排碟号
                            .thenBy { it.IndexNumber ?: 0 }               // 再排音轨号
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.SortName ?: it.Name }
                    )

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
                
                adapter.submitList(finalItems, context)
            } catch (e: Exception) {
                // 静默处理所有加载失败，避免切换过快产生的弹窗
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun playMusic(item: EmbyItem, allItems: List<EmbyItem>) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return
        
        // 备份当前模式
        val currentRepeatMode = controller.repeatMode
        val currentShuffleMode = controller.shuffleModeEnabled

        val currentMediaItem = controller.currentMediaItem
        val currentSessionId = currentMediaItem?.mediaMetadata?.extras?.getString("play_session_id")
        val currentId = currentMediaItem?.mediaId
        
        val playableItems = allItems.filter { !it.IsFolder }
        val isSameSong = item.Id == currentId
        
        val mediaItems = playableItems.map { song ->
            val overrideId = if (song.Id == currentId) currentSessionId else null
            MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId, overrideSessionId = overrideId)
        }
        
        if (isSameSong) {
            controller.setMediaItems(mediaItems, false)
        } else {
            val startIndex = playableItems.indexOfFirst { it.Id == item.Id }.coerceAtLeast(0)
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()
        }

        // 核心修正：在 Media3 更新列表后，强制写回之前的播放模式，防止其重置为列表循环
        controller.repeatMode = currentRepeatMode
        controller.shuffleModeEnabled = currentShuffleMode

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
