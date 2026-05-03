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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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

    private val authHeader: String
        get() = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.45\", Token=\"$accessToken\""

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
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                loadRecentItems()
            } catch (e: Exception) {
                Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
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

        val mediaItems = allItems.map { song ->
            val overrideId = if (song.Id == currentId) currentSessionId else null
            MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId, overrideSessionId = overrideId)
        }

        val isSameSong = item.Id == currentId
        if (isSameSong) {
            controller.setMediaItems(mediaItems, false)
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
                val response = service.getRecentlyPlayedItems(userId, auth = authHeader)
                adapter.submitList(response.Items)
            } catch (e: Exception) {
                // 静默处理加载失败
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
