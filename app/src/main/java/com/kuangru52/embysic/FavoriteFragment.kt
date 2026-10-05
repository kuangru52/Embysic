package com.kuangru52.embysic

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.launch

@UnstableApi
class FavoriteFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if ((activity as? HomeActivity)?.isSwipingBack == true) {
            return object : Animation() {}.apply { duration = 0 }
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
    private lateinit var adapter: LibraryAdapter
    private var apiService: EmbyApiService? = null
    
    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""

    private val authHeader: String
        get() {
            val ctx = context ?: return ""
            return "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceId(ctx)}\", DeviceId=\"${MediaItemUtils.getDeviceId(ctx)}\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$accessToken\""
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val composeView = ComposeView(ctx)
        composeView.setContent {
            RecentFragmentContent { _, rv, bg, pb ->
                ivFragmentBackground = bg
                recyclerView = rv
                progressBar = pb

                syncBackground()
                setupRecyclerView()
                loadPrefs()
                initApiService()
                loadFavoriteItems()
            }
        }
        return composeView
    }

    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlaybackState(mediaItem)
            syncBackground()
        }
        override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo, newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) {
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
        if (!::adapter.isInitialized) return
        val mediaId = mediaItem?.mediaId
        val albumId = mediaItem?.mediaMetadata?.extras?.getString("album_id")
        val path = mediaItem?.mediaMetadata?.extras?.getString("path")
        adapter.setCurrentMediaId(mediaId, albumId, path)
    }

    private fun loadPrefs() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
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
            onItemClick = { item -> playMusic(item) },
            onItemDelete = { item -> removeFavorite(item) },
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun loadFavoriteItems() {
        if (userId.isEmpty() || accessToken.isEmpty()) return

        // 1. 零延迟优先渲染内存收藏数据 (0ms 瞬间呈现，零转圈)
        MemoryCache.favoriteItems?.let { cached ->
            if (cached.isNotEmpty()) {
                val ctx = context ?: return@let
                if (::adapter.isInitialized) {
                    adapter.submitList(cached, ctx)
                }
                if (::progressBar.isInitialized) progressBar.visibility = View.GONE
            }
        }

        if (MemoryCache.favoriteItems.isNullOrEmpty() && ::progressBar.isInitialized) {
            progressBar.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val response = apiService?.getItems(
                    userId = userId,
                    includeItemTypes = "Audio",
                    recursive = true,
                    sortBy = "SortName",
                    auth = authHeader,
                )
                val allItems = response?.Items ?: emptyList()
                val favoriteItems = allItems.filter { it.UserData?.IsFavorite == true }
                MemoryCache.favoriteItems = favoriteItems

                val ctx = context ?: return@launch
                if (!::adapter.isInitialized) return@launch
                adapter.submitList(favoriteItems, ctx)
                updatePlaybackState((activity as? HomeActivity)?.mediaController?.currentMediaItem)
            } catch (e: Exception) {
                Log.e("FavoriteFragment", "Error loading favorites", e)
            } finally {
                if (::progressBar.isInitialized) progressBar.visibility = View.GONE
            }
        }
    }

    private fun playMusic(item: EmbyItem) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return
        
        val currentRepeatMode = controller.repeatMode
        val currentShuffleMode = controller.shuffleModeEnabled

        val currentMediaItem = controller.currentMediaItem
        val currentId = currentMediaItem?.mediaId
        
        val isSameSong = item.Id == currentId
        
        if (isSameSong) {
            controller.play()
        } else {
            val mediaItem = MediaItemUtils.buildMediaItem(requireContext(), item, serverUrl, accessToken, userId)
            controller.setMediaItem(mediaItem)
            
            controller.prepare()
            controller.play()

            (activity as? HomeActivity)?.updatePlaylistByMode(controller.shuffleModeEnabled)
        }

        controller.repeatMode = currentRepeatMode
        controller.shuffleModeEnabled = currentShuffleMode
        (activity as? HomeActivity)?.showPlayer()
    }

    private fun removeFavorite(item: EmbyItem) {
        val service = apiService ?: return
        lifecycleScope.launch {
            try {
                service.unmarkFavorite(userId, item.Id, authHeader)
                Toast.makeText(context, "已取消收藏: ${item.Name}", Toast.LENGTH_SHORT).show()
                loadFavoriteItems()
            } catch (e: Exception) {
                Log.e("FavoriteFragment", "Error removing favorite", e)
                Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
