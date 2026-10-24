package com.kuangru52.embysic

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch

@UnstableApi
class RecentFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): android.view.animation.Animation? {
        if ((activity as? HomeActivity)?.isSwipingBack == true) {
            return object : android.view.animation.Animation() {}.apply { duration = 0 }
        }
        if (nextAnim != 0) {
            try {
                return android.view.animation.AnimationUtils.loadAnimation(context, nextAnim)
            } catch (_: Exception) {}
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    private lateinit var ivFragmentBackground: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private var adapter = LibraryAdapter(
        onItemClick = { item -> handleItemClick(item) },
        onItemDoubleClick = { item -> handleItemDoubleClick(item) },
        onItemDelete = { item -> confirmDelete(item) },
    )

    private var apiService: EmbyApiService? = null
    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""

    private val authHeader: String
        get() {
            val ctx = context ?: return ""
            return "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceName(ctx)}\", DeviceId=\"${MediaItemUtils.getDeviceId(ctx)}\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$accessToken\""
        }

    private var fragmentBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val composeView = ComposeView(requireContext())
        composeView.setContent {
            val recentBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            fragmentBackdrop = recentBackdrop
            val context = androidx.compose.ui.platform.LocalContext.current
            val homeActivity = androidx.compose.runtime.remember(context) { context.findHomeActivity() }

            androidx.compose.runtime.SideEffect {
                homeActivity?.wallpaperBackdrop = recentBackdrop
                homeActivity?.homeBackdrop = recentBackdrop
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // 1. 全屏壁纸底层：单独挂载 layerBackdrop(recentBackdrop)！
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            id = R.id.ivFragmentBackground
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            )
                            ivFragmentBackground = this
                            syncBackground()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(recentBackdrop),
                )

                // 2. 前景 Interaction 层：RecyclerView 列表与 ProgressBar
                AndroidView(
                    factory = { ctx ->
                        val root = FrameLayout(ctx).apply { id = R.id.fragment_recent_root }
                        val contentContainer = FrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            )
                        }
                        val rv = RecyclerView(ctx).apply {
                            id = R.id.rvRecent
                            setPadding(0, 0, 0, (170 * ctx.resources.displayMetrics.density).toInt())
                            clipToPadding = false
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            )
                        }
                        val pb = ProgressBar(ctx).apply {
                            id = R.id.progressBar
                            visibility = View.GONE
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.view.Gravity.CENTER,
                            )
                        }

                        contentContainer.addView(rv)
                        contentContainer.addView(pb)
                        root.addView(contentContainer)

                        ViewCompat.setOnApplyWindowInsetsListener(contentContainer) { v, insets ->
                            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                            v.setPadding(0, systemBars.top, 0, 0)
                            insets
                        }

                        recyclerView = rv
                        progressBar = pb

                        setupRecyclerView()
                        loadPrefs()
                        initApiService()
                        loadRecentItems()

                        root
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return composeView
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivFragmentBackground.setRenderEffect(null)
            }
            return
        }

        val isTabletLandscape = ((resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE)
                && (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

        if (isTabletLandscape) {
            ivFragmentBackground.visibility = View.GONE
            return
        }

        val activityBg = homeActivity?.findViewById<ImageView>(R.id.ivBlurBackground)
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
        val currentMediaId = mediaItem?.mediaId?.split("_")?.firstOrNull() ?: mediaItem?.mediaId
        val currentAlbumId = mediaItem?.mediaMetadata?.extras?.getString("album_id")
        val currentPath = mediaItem?.mediaMetadata?.extras?.getString("path")
        adapter.setCurrentMediaId(currentMediaId, currentAlbumId, currentPath)
    }

    private fun loadPrefs() {
        val prefs = context?.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE) ?: return
        serverUrl = prefs.getString("server_url", "") ?: ""
        accessToken = prefs.getString("access_token", "") ?: ""
        userId = prefs.getString("user_id", "") ?: ""
    }

    private fun initApiService() {
        val ctx = context ?: return
        apiService = RetrofitClient.getEmbyApiService(ctx)
    }

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(
            onItemClick = { item -> handleItemClick(item) },
            onItemDoubleClick = { item -> handleItemDoubleClick(item) },
            onItemDelete = { item -> confirmDelete(item) },
            backdropProvider = { fragmentBackdrop },
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun handleItemClick(item: EmbyItem) {
        if (item.IsFolder) {
            (activity as? HomeActivity)?.replaceFragment(
                LibraryFragment.newInstance(item.Id, item.Name),
                "Library_${item.Id}",
            )
        } else {
            playSong(item)
        }
    }

    private fun handleItemDoubleClick(item: EmbyItem) {
        if (!item.IsFolder) {
            val ctx = context ?: return
            val controller = (activity as? HomeActivity)?.mediaController ?: return
            controller.clearMediaItems()
            val mediaItem = MediaItemUtils.buildMediaItem(ctx, item, serverUrl, accessToken, userId)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            (activity as? HomeActivity)?.showPlayer()
            Toast.makeText(ctx, "已清空队列并播放：${item.Name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playSong(item: EmbyItem) {
        val ctx = context ?: return
        val controller = (activity as? HomeActivity)?.mediaController ?: return
        val items = adapter.items.filter { !it.IsFolder }
        val startIndex = items.indexOfFirst { it.Id == item.Id }

        if (startIndex != -1) {
            val mediaItems = items.map { MediaItemUtils.buildMediaItem(ctx, it, serverUrl, accessToken, userId) }
            controller.setMediaItems(mediaItems, startIndex, 0)
            controller.prepare()
            controller.play()
        } else {
            val mediaItem = MediaItemUtils.buildMediaItem(ctx, item, serverUrl, accessToken, userId)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
        (activity as? HomeActivity)?.showPlayer()
    }

    private fun loadRecentItems() {
        if (userId.isEmpty() || accessToken.isEmpty()) return

        // 1. 零延迟优先渲染内存数据 (0ms，瞬间呈现，零转圈)
        MemoryCache.recentItems?.let { cached ->
            if (cached.isNotEmpty()) {
                val ctx = context ?: return@let
                adapter.submitList(cached, ctx)
                if (::progressBar.isInitialized) progressBar.visibility = View.GONE
            }
        }

        if (MemoryCache.recentItems.isNullOrEmpty() && ::progressBar.isInitialized) {
            progressBar.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val response = apiService?.getRecentlyPlayedItems(userId = userId, limit = 50, auth = authHeader)
                val items = response?.Items ?: emptyList()
                MemoryCache.recentItems = items
                val ctx = context ?: return@launch
                adapter.submitList(items, ctx)
                updatePlaybackState((activity as? HomeActivity)?.mediaController?.currentMediaItem)
            } catch (e: Exception) {
                Log.e("RecentFragment", "Error loading recent items", e)
            } finally {
                if (::progressBar.isInitialized) progressBar.visibility = View.GONE
            }
        }
    }

    private fun confirmDelete(item: EmbyItem) {
        val ctx = context ?: return
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("确认删除")
            .setMessage("确定要从媒体库中永久删除 \"${item.Name}\" 吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ -> deleteItem(item) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteItem(item: EmbyItem) {
        val service = apiService ?: return
        lifecycleScope.launch {
            try {
                service.deleteItem(item.Id, authHeader)
                Toast.makeText(context, "已删除: ${item.Name}", Toast.LENGTH_SHORT).show()
                loadRecentItems()
            } catch (e: Exception) {
                Log.e("RecentFragment", "Error deleting item", e)
                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? HomeActivity)?.mediaController?.addListener(playerListener)
        updatePlaybackState((activity as? HomeActivity)?.mediaController?.currentMediaItem)
        syncBackground()
    }

    override fun onPause() {
        (activity as? HomeActivity)?.mediaController?.removeListener(playerListener)
        super.onPause()
    }
}
