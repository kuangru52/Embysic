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
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

@UnstableApi
class RecentFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if ((activity as? HomeActivity)?.isSwipingBack == true) {
            return object : Animation() {}.apply { duration = 0 }
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    private lateinit var ivFragmentBackground: android.widget.ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: LibraryAdapter
    private var apiService: EmbyApiService? = null
    
    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""

    private val authHeader: String
        get() = "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceName(requireContext())}\", DeviceId=\"${MediaItemUtils.getDeviceId(requireContext())}\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$accessToken\""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_recent, container, false)
        ivFragmentBackground = view.findViewById(R.id.ivFragmentBackground)
        recyclerView = view.findViewById(R.id.rvRecent)
        progressBar = view.findViewById(R.id.progressBar)
        
        // 沉浸式：设置 PaddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // 同步全局模糊背景
        syncBackground()

        setupRecyclerView()
        loadPrefs()
        initApiService()
        loadRecentItems()
        
        return view
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

        // 平板横屏模式下，隐藏 Fragment 自己的背景，实现与全局背景完全一体
        val isTabletLand = (resources.configuration.smallestScreenWidthDp >= 600) &&
                resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (isTabletLand) {
            ivFragmentBackground.visibility = View.GONE
            return
        }

        val activityBg = (activity as? HomeActivity)?.findViewById<android.widget.ImageView>(R.id.ivBlurBackground)
        if (activityBg != null && activityBg.drawable != null) {
            ivFragmentBackground.setImageDrawable(activityBg.drawable)
            ivFragmentBackground.alpha = activityBg.alpha
            ivFragmentBackground.visibility = View.VISIBLE
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
            onItemClick = { item -> playMusic(item, adapter.items) },
            onItemDoubleClick = { replaceFragment(FavoriteFragment()) },
            onItemDelete = { item -> showDeleteConfirmation(item) }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
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

        val dm = resources.displayMetrics
        val px38dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 38f, dm)
        val pxNeg60dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, -60f, dm)
        val px20dpRefHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, dm)
        val px10dpBlurRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, dm)

        val width = (dm.widthPixels * 0.82).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
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
                loadRecentItems()
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.delete_failed, e.message), Toast.LENGTH_SHORT).show()
                Log.e("RecentFragment", "Delete error", e)
            }
        }
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
        val currentMediaItem = controller.currentMediaItem
        val currentSessionId = currentMediaItem?.mediaMetadata?.extras?.getString("play_session_id")
        val currentId = currentMediaItem?.mediaId

        // 备份当前模式
        val currentShuffleMode = controller.shuffleModeEnabled

        val mediaItems = allItems.map { song ->
            val overrideId = if (song.Id == currentId) currentSessionId else null
            MediaItemUtils.buildMediaItem(requireContext(), song, serverUrl, accessToken, userId, overrideSessionId = overrideId)
        }

        val isSameSong = item.Id == currentId
        if (isSameSong) {
            controller.setMediaItems(mediaItems, false)
            controller.play()
        } else {
            val startIndex = mediaItems.indexOfFirst { it.mediaId == item.Id }.coerceAtLeast(0)
            controller.setMediaItems(mediaItems, startIndex, 0L)
            
            // 断点续听
            val lastPositionTicks = item.UserData?.PlaybackPositionTicks ?: 0L
            if (lastPositionTicks > 0) {
                controller.seekTo(lastPositionTicks / 10000)
            }
            
            controller.prepare()
            controller.play()
            
            // 核心修正：同步当前的随机模式状态
            if (currentShuffleMode) {
                (activity as? HomeActivity)?.updatePlaylistByMode(true)
            }
        }

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
        apiService = RetrofitClient.getEmbyApiService(requireContext())
    }

    private fun loadRecentItems() {
        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val response = service.getRecentlyPlayedItems(userId, auth = authHeader)
                adapter.submitList(response.Items, context)
            } catch (_: Exception) {
                // Silent handle
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
