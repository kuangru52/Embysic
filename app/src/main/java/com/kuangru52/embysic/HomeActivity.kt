package com.kuangru52.embysic

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@UnstableApi
class HomeActivity : AppCompatActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
    private var apiService: EmbyApiService? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initApiService()

        val bottomContainer = findViewById<ComposeView>(R.id.bottom_container)
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get() ?: return@addListener
            mediaController = controller
            setupController(controller)
            
            bottomContainer.setContent {
                ModernBottomBar(controller)
            }
        }, MoreExecutors.directExecutor())

        if (savedInstanceState == null) {
            replaceFragment(RecentFragment())
        }

        // 处理点击通知栏跳转
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_player", false) == true) {
            showPlayer()
        }
    }

    @Composable
    fun ModernBottomBar(controller: MediaController) {
        var title by remember { mutableStateOf(controller.mediaMetadata.title?.toString() ?: "未在播放") }
        var artist by remember { mutableStateOf(controller.mediaMetadata.artist?.toString() ?: "") }
        var isPlaying by remember { mutableStateOf(controller.isPlaying) }
        var artworkUri by remember { mutableStateOf(controller.mediaMetadata.artworkUri) }
        var progress by remember { mutableFloatStateOf(0f) }

        DisposableEffect(controller) {
            val listener = object : Player.Listener {
                override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                    title = metadata.title?.toString() ?: "未在播放"
                    artist = metadata.artist?.toString() ?: ""
                    artworkUri = metadata.artworkUri
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            controller.addListener(listener)
            onDispose { controller.removeListener(listener) }
        }

        // 持续更新进度
        LaunchedEffect(isPlaying, controller.currentMediaItem) {
            while (isPlaying) {
                val dur = controller.duration
                if (dur > 0) {
                    progress = controller.currentPosition.toFloat() / dur.toFloat()
                }
                kotlinx.coroutines.delay(500)
            }
        }

        val isDark = isSystemInDarkTheme()
        // 针对红蓝背景优化的底栏颜色
        // 深色模式下：使用极低饱和度的深紫色，配合较低的 Alpha (0.7) 实现“磨砂玻璃”透出背景的效果
        // 浅色模式下：使用高亮度的白紫色，配合 Alpha (0.8)
        val containerColor = if (isDark) {
            Color(0xAA120812) // 深色透红蓝
        } else {
            Color(0xCCFDF7FF) // 浅色透淡彩
        }

        // 底部整体容器：玻璃质感
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .height(130.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                ),
            color = containerColor,
            tonalElevation = 0.dp // 禁用自带的叠加色，使用我们自定义的 containerColor
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 播放进度条：使用更鲜艳的强调色
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (isDark) Color(0xFFFF4081) else MaterialTheme.colorScheme.primary,
                    trackColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f),
                )

                // 迷你播放器区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { showPlayer() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 封面图
                    AsyncImage(
                        model = ImageRequest.Builder(this@HomeActivity)
                            .data(artworkUri)
                            .crossfade(400)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)),
                        contentScale = ContentScale.Crop
                    )

                    // 文字信息
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = title,
                            color = if (isDark) Color.White else Color(0xFF231A17),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (artist.isNotEmpty()) {
                            Text(
                                text = artist,
                                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF6D5D59),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // 播放按钮
                    IconButton(
                        onClick = { if (isPlaying) controller.pause() else controller.play() },
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.1f) 
                                else Color.Black.copy(alpha = 0.05f), 
                                CircleShape
                            )
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause 
                                else android.R.drawable.ic_media_play
                            ),
                            contentDescription = null,
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    thickness = 0.5.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )

                // 导航区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var currentTab by remember { mutableStateOf("最近") }
                    
                    ModernNavItem(
                        label = if (currentTab == "收藏") "收藏" else "最近", 
                        iconRes = if (currentTab == "收藏") R.drawable.ic_heart else android.R.drawable.ic_media_play, // 临时使用 play 替代 recent
                        isSelected = currentTab == "最近" || currentTab == "收藏",
                        onClick = { 
                            currentTab = "最近"
                            replaceFragment(RecentFragment()) 
                        },
                        onDoubleClick = { 
                            currentTab = "收藏"
                            replaceFragment(FavoriteFragment()) 
                        }
                    )
                    ModernNavItem(
                        label = "曲库", 
                        iconRes = android.R.drawable.ic_menu_compass, 
                        isSelected = currentTab == "曲库",
                        onClick = { 
                            currentTab = "曲库"
                            replaceFragment(LibraryFragment()) 
                        }
                    )
                    ModernNavItem(
                        label = "搜索", 
                        iconRes = android.R.drawable.ic_menu_search, 
                        isSelected = currentTab == "搜索",
                        onClick = { 
                            currentTab = "搜索"
                            replaceFragment(SearchFragment()) 
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun ModernNavItem(label: String, iconRes: Int, isSelected: Boolean, onClick: () -> Unit, onDoubleClick: (() -> Unit)? = null) {
        var lastClickTime by remember { mutableLongStateOf(0L) }
        val isDark = isSystemInDarkTheme()
        val activeColor = if (isDark) Color(0xFFFF4081) else MaterialTheme.colorScheme.primary
        val inactiveColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color(0xFF6D5D59)
        
        val color = if (isSelected) activeColor else inactiveColor
        
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 300 && onDoubleClick != null) {
                        onDoubleClick()
                    } else {
                        onClick()
                    }
                    lastClickTime = currentTime
                }
                .padding(vertical = 4.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label, 
                color = color,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }

    private fun initApiService() {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        if (serverUrl.isNotEmpty()) {
            apiService = Retrofit.Builder()
                .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EmbyApiService::class.java)
        }
    }

    private fun setupController(controller: MediaController) {
        if (controller.currentMediaItem == null) {
            restoreLastPlayedItem(controller)
        }
    }

    private fun restoreLastPlayedItem(controller: MediaController) {
        val service = apiService ?: return
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        if (userId.isEmpty() || accessToken.isEmpty()) return

        val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\", Token=\"$accessToken\""

        activityScope.launch {
            try {
                val response = service.getRecentlyPlayedItems(userId, limit = 1, auth = authHeader)
                response.Items.firstOrNull()?.let { lastItem ->
                    val serverUrl = prefs.getString("server_url", "") ?: ""
                    val mediaItem = MediaItemUtils.buildMediaItem(lastItem, serverUrl, accessToken, userId)
                    controller.setMediaItem(mediaItem)
                    val lastPos = (lastItem.UserData?.PlaybackPositionTicks ?: 0L) / 10000
                    if (lastPos > 0) controller.seekTo(lastPos)
                    controller.prepare() 
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Restore error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    fun showPlayer() {
        PlayerDialogFragment().show(supportFragmentManager, "player")
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
