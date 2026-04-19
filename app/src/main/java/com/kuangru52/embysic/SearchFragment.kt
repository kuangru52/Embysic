package com.kuangru52.embysic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SearchFragment : Fragment() {

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
        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. Compose 搜索框容器
        val composeView = ComposeView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 80.dpToPx())
            setContent {
                XiaomiSearchBar { query -> performSearch(query) }
            }
        }
        root.addView(composeView)

        // 2. RecyclerView
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                topMargin = 80.dpToPx()
            }
            layoutManager = LinearLayoutManager(context)
            // 使用 padding 而不是 margin，配合 clipToPadding = false 实现悬浮效果
            // 底部 padding 为底栏留出足够空间：迷你播放条(64dp) + 底部导航(64dp) + 外边距/系统栏(约40dp) = 约168dp
            setPadding(0, 0, 0, 180.dpToPx()) 
            clipToPadding = false
        }
        root.addView(recyclerView)

        // 3. ProgressBar
        progressBar = ProgressBar(requireContext()).apply {
            val size = 48.dpToPx()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER
            }
            visibility = View.GONE
        }
        root.addView(progressBar)

        setupRecyclerView()
        loadPrefs()
        initApiService()

        return root
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    @Composable
    fun XiaomiSearchBar(onSearch: (String) -> Unit) {
        var text by remember { mutableStateOf("") }
        val isDark = isSystemInDarkTheme()
        val bgColor = if (isDark) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.8f)
        
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(32.dp),
            color = bgColor
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 0.6.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    BasicTextField(
                        value = text,
                        onValueChange = { 
                            text = it
                            onSearch(it)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = if (isDark) Color.White else Color.Black,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                        }),
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    text = "搜索艺人或歌曲...",
                                    color = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(requireContext(), DonationActivity::class.java)
                            requireContext().startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
                        )
                    }
                }
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
        recyclerView.adapter = adapter
    }

    private fun performSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return

        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val response = service.getItems(
                    userId = userId,
                    searchTerm = q,
                    includeItemTypes = "Audio,MusicArtist",
                    recursive = true,
                    auth = authHeader,
                    limit = 100
                )

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
                val results = folders + music
                adapter.submitList(results)
            } catch (e: Exception) {
                // 静默处理取消异常和 Job was cancelled 错误
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
