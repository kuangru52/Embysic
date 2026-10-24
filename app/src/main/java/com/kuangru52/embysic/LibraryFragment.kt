package com.kuangru52.embysic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.Modifier
import coil.load
import kotlinx.coroutines.launch

@UnstableApi
class LibraryFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        val activity = activity as? HomeActivity
        if (activity?.isSwipingBack == true) {
            return object : Animation() {}.apply { duration = 0 }
        }
        if (nextAnim != 0) {
            try {
                return AnimationUtils.loadAnimation(context, nextAnim)
            } catch (_: Exception) {}
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
        get() {
            val ctx = context ?: return ""
            return "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceName(ctx)}\", DeviceId=\"${MediaItemUtils.getDeviceId(ctx)}\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$accessToken\""
        }

    private var fragmentBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext())
        composeView.setContent {
            val libraryBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            fragmentBackdrop = libraryBackdrop
            val context = androidx.compose.ui.platform.LocalContext.current
            val homeActivity = remember(context) { context.findHomeActivity() }

            androidx.compose.runtime.SideEffect {
                homeActivity?.wallpaperBackdrop = libraryBackdrop
                homeActivity?.homeBackdrop = libraryBackdrop
            }

            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                // 1. 全屏壁纸底层：单独挂载 layerBackdrop(libraryBackdrop)，为卡片提供完全一致的抓图源！
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        android.widget.ImageView(context).apply {
                            id = R.id.ivFragmentBackground
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            ivFragmentBackground = this
                            syncBackground()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(libraryBackdrop),
                )

                // 2. 前景 Interaction 层：SwipeBackLayout, RecyclerView, selectionBar 等
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        val swipeBack = SwipeBackLayout(context).apply { id = R.id.swipeBackLayout }
                        val frameRoot = android.widget.FrameLayout(context)
                        val constraint = androidx.constraintlayout.widget.ConstraintLayout(context)
                        val searchCompose = androidx.compose.ui.platform.ComposeView(context).apply {
                            id = R.id.search_bar_compose
                        }
                        val rv = RecyclerView(context).apply {
                            id = R.id.rvLibrary
                            setPadding(0, 0, 0, (170 * context.resources.displayMetrics.density).toInt())
                            clipToPadding = false
                        }
                        val selBar = android.widget.LinearLayout(context).apply {
                            id = R.id.selectionBar
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            setPadding((16 * context.resources.displayMetrics.density).toInt(), 0, (16 * context.resources.displayMetrics.density).toInt(), 0)
                            visibility = View.GONE
                        }
                        val btnCancel = TextView(context).apply {
                            id = R.id.btnCancelSelection
                            text = getString(R.string.cancel)
                            textSize = 16f
                        }
                        val spaceView = View(context)
                        val btnDel = TextView(context).apply {
                            id = R.id.btnDeleteBatch
                            text = getString(R.string.delete_selected)
                            setTextColor(android.graphics.Color.RED)
                            textSize = 16f
                        }
                        selBar.addView(btnCancel)
                        selBar.addView(spaceView, android.widget.LinearLayout.LayoutParams(0, 0, 1f))
                        selBar.addView(btnDel)

                        val pb = ProgressBar(context).apply {
                            id = R.id.progressBar
                            visibility = View.GONE
                        }

                        val searchParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        }
                        val rvParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, 0).apply {
                            topToBottom = R.id.search_bar_compose
                            bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        }
                        val selParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT, (64 * context.resources.displayMetrics.density).toInt()).apply {
                            bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            bottomMargin = (180 * context.resources.displayMetrics.density).toInt()
                        }
                        val pbParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT, androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        }

                        constraint.addView(searchCompose, searchParams)
                        constraint.addView(rv, rvParams)
                        constraint.addView(selBar, selParams)
                        constraint.addView(pb, pbParams)

                        frameRoot.addView(constraint, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
                        swipeBack.addView(frameRoot, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))

                        recyclerView = rv
                        progressBar = pb
                        selectionBar = selBar
                        btnDeleteBatch = btnDel
                        btnCancelSelection = btnCancel

                        btnCancelSelection.setOnClickListener { adapter.setSelectionMode(enabled = false) }
                        btnDeleteBatch.setOnClickListener {
                            val selectedItems = adapter.getSelectedItems()
                            if (selectedItems.isNotEmpty()) showBatchDeleteConfirmation(selectedItems)
                        }

                        ViewCompat.setOnApplyWindowInsetsListener(constraint) { v, insets ->
                            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                            v.setPadding(0, systemBars.top, 0, 0)
                            insets
                        }

                        syncBackground()
                        setupRecyclerView()
                        loadPrefs()
                        initApiService()

                        val artistId = arguments?.getString("artist_id")
                        val artistName = arguments?.getString("artist_name")
                        val targetItemId = arguments?.getString("target_item_id")

                        (activity as? HomeActivity)?.findViewById<View>(R.id.bottom_container)?.visibility = View.VISIBLE

                        val fragments = parentFragmentManager.fragments
                        val myIndex = fragments.indexOf(this@LibraryFragment)
                        val prevFragment = if (myIndex > 0) fragments[myIndex - 1] else if (fragments.size >= 2) fragments[fragments.size - 2] else null

                        if (prevFragment != null && (parentFragmentManager.backStackEntryCount > 0 || artistId != null || targetItemId != null)) {
                            swipeBack.setPreviousView(prevFragment.view)
                            swipeBack.setOnSwipeBackListener {
                                requireActivity().onBackPressedDispatcher.onBackPressed()
                            }
                        }

                        if (targetItemId != null) {
                            locateAndLoadParent(targetItemId)
                        } else if (artistId != null) {
                            loadItems(artistId, artistName ?: "层级")
                        } else {
                            loadItems(null, "音乐库")
                        }

                        swipeBack
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return composeView
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
            } catch (_: Exception) {
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

    internal fun syncBackground() {
        if (!isAdded || !::ivFragmentBackground.isInitialized) return

        val homeActivity = activity as? HomeActivity
        if (WallpaperManager.getMode(requireContext()) == "none") {
            val isDark = homeActivity?.isAppDark() ?: false
            val noneColorInt = if (isDark) android.graphics.Color.rgb(18, 18, 24) else android.graphics.Color.rgb(248, 250, 252)
            ivFragmentBackground.setImageDrawable(noneColorInt.toDrawable())
            ivFragmentBackground.alpha = 1.0f
            ivFragmentBackground.visibility = View.VISIBLE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                ivFragmentBackground.setRenderEffect(null)
            }
            return
        }

        val isTabletLand = (resources.configuration.smallestScreenWidthDp >= 600) &&
                          (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        
        if (isTabletLand) {
            ivFragmentBackground.visibility = View.GONE
            return
        }

        val activityBg = homeActivity?.findViewById<android.widget.ImageView>(R.id.ivBlurBackground)
        if ((activityBg != null) && (activityBg.drawable != null)) {
            ivFragmentBackground.setImageDrawable(activityBg.drawable.constantState?.newDrawable() ?: activityBg.drawable)
            ivFragmentBackground.alpha = 1.0f
            ivFragmentBackground.visibility = View.VISIBLE
            homeActivity.applyWallpaperBlur(ivFragmentBackground)
        } else {
            lifecycleScope.launch {
                val ctx = context ?: return@launch
                val source = WallpaperManager.getActiveWallpaperSource(ctx)
                ivFragmentBackground.load(source) {
                    crossfade(enable = false)
                    allowHardware(enable = false)
                }
                ivFragmentBackground.alpha = 1.0f
                ivFragmentBackground.visibility = View.VISIBLE
                homeActivity?.applyWallpaperBlur(ivFragmentBackground)
            }
        }
    }

    private fun updatePlaybackState(mediaItem: MediaItem?) {
        if (!::adapter.isInitialized) return
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
                    playMusic(item)
                }
            },
            onItemDelete = { item ->
                showDeleteConfirmation(item)
            },
            backdropProvider = { fragmentBackdrop },
        ) { enabled ->
            selectionBar.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun showBatchDeleteConfirmation(items: List<EmbyItem>) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.batch_delete_msg, items.size))
            .setPositiveButton(R.string.confirm_delete) { _, _ -> performBatchDelete(items) }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                } catch (_: Exception) {
                    failCount++
                }
            }
            progressBar.visibility = View.GONE
            val msg = getString(R.string.deleted_success) + " $successCount items" + (if (failCount > 0) ", $failCount failed" else "")
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            adapter.setSelectionMode(enabled = false)
            val artistId = arguments?.getString("artist_id")
            val artistName = arguments?.getString("artist_name")
            loadItems(artistId, artistName ?: "层级")
        }
    }

    private fun showDeleteConfirmation(item: EmbyItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.delete_msg, item.Name))
            .setPositiveButton(R.string.confirm_delete) { _, _ -> performDelete(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        val cacheKey = parentId ?: "ROOT"

        // 1. 内存缓存 0ms 瞬间渲染，零转圈
        MemoryCache.libraryFoldersCache[cacheKey]?.let { cached ->
            if (cached.isNotEmpty()) {
                val ctx = context ?: return@let
                if (::adapter.isInitialized) {
                    adapter.submitList(cached, ctx)
                }
                if (::progressBar.isInitialized) progressBar.visibility = View.GONE
            }
        }

        if (MemoryCache.libraryFoldersCache[cacheKey].isNullOrEmpty() && ::progressBar.isInitialized) {
            progressBar.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val response = if (parentId == null) {
                    val views = service.getUserViews(currentUserId, currentAuth)
                    val musicLibrary = views.Items.find { it.CollectionType.equals("music", ignoreCase = true) }
                    if (musicLibrary != null) {
                        service.getItems(
                            userId = currentUserId,
                            parentId = musicLibrary.Id,
                            recursive = false,
                            auth = currentAuth,
                            includeItemTypes = "Audio,Folder,MusicAlbum,MusicArtist,CollectionFolder",
                            fields = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,IndexNumber,ParentIndexNumber,FileName,Filename,SortName,ChildCount,RecursiveItemCount,ParentId,HasLyrics",
                            sortBy = "IsFolder,SortName",
                            sortOrder = "Ascending",
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
                        sortOrder = "Ascending",
                    )
                }

                val filteredItems = response.Items.filter { item ->
                    if (item.Name == "Artwork") return@filter false
                    if (item.IsFolder && (item.Type != "MusicArtist") && ((item.RecursiveItemCount ?: 0) <= 0) && ((item.ChildCount ?: 0) <= 0)) {
                        return@filter false
                    }
                    true
                }

                val folders = filteredItems.filter { it.IsFolder || (it.Type != "Audio") }
                val music = filteredItems.asSequence().filter { !it.IsFolder && (it.Type == "Audio") }
                    .sortedWith(
                        compareBy<EmbyItem> { it.ParentIndexNumber ?: 0 }
                            .thenBy { it.IndexNumber ?: 0 }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.SortName ?: it.Name },
                    ).toList()

                val sortedItems = folders + music
                
                var showBackFolder = false
                if (parentId != null) {
                    val views = service.getUserViews(userId, authHeader)
                    val musicLibrary = views.Items.find { it.CollectionType == "music" }
                    if ((musicLibrary != null) && (parentId != musicLibrary.Id)) {
                        showBackFolder = true
                    }
                }

                val finalItems = if (showBackFolder) {
                    val parentItem = EmbyItem(
                        Id = "BACK_FOLDER",
                        Name = title,
                        Type = "Folder",
                        IsFolder = true,
                    )
                    listOf(parentItem) + sortedItems
                } else {
                    sortedItems
                }

                MemoryCache.libraryFoldersCache[cacheKey] = finalItems
                
                val ctx = context ?: return@launch
                if (!::adapter.isInitialized) return@launch
                adapter.submitList(finalItems, ctx)
            } catch (_: Exception) {
            } finally {
                if (::progressBar.isInitialized) progressBar.visibility = View.GONE
            }
        }
    }

    private fun playMusic(item: EmbyItem) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return
        
        // 备份当前模式
        val currentRepeatMode = controller.repeatMode
        val currentShuffleMode = controller.shuffleModeEnabled

        val currentMediaItem = controller.currentMediaItem
        val currentId = currentMediaItem?.mediaId
        val isSameSong = item.Id == currentId
        
        if (isSameSong) {
            controller.play()
        } else {
            // 构建一个只包含当前歌曲的列表，然后通过 updatePlaylistByMode 自动拉取同文件夹列表
            val mediaItem = MediaItemUtils.buildMediaItem(requireContext(), item, serverUrl, accessToken, userId)
            controller.setMediaItem(mediaItem)
            
            controller.prepare()
            controller.play()

            // 无论当前模式是什么，都根据当前模式更新为同文件夹或随机列表
            (activity as? HomeActivity)?.updatePlaylistByMode(controller.shuffleModeEnabled)
        }

        // 核心修正：在 Media3 更新列表后，强制写回之前的播放模式，防止其重置为列表循环
        controller.repeatMode = currentRepeatMode
        controller.shuffleModeEnabled = currentShuffleMode

        (activity as? HomeActivity)?.showPlayer()
    }

    private fun loadPrefs() {
        val ctx = context ?: return
        ctx.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE).apply {
            serverUrl = getString("server_url", "") ?: ""
            accessToken = getString("access_token", "") ?: ""
            userId = getString("user_id", "") ?: ""
        }
    }

    private fun initApiService() {
        val ctx = context ?: return
        apiService = RetrofitClient.getEmbyApiService(ctx)
    }

    companion object {
        fun newInstance(parentId: String, title: String): LibraryFragment {
            return LibraryFragment().apply {
                arguments = Bundle().apply {
                    putString("parent_id", parentId)
                    putString("title", title)
                }
            }
        }
    }
}
