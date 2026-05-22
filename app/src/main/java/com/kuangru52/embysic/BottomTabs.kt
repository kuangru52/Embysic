package com.kuangru52.embysic

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun BottomTabs(
    controller: MediaController,
    selectedTab: String,
    onPlayerClick: () -> Unit,
    onNavigation: (String) -> Unit,
    onLibraryScan: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTabletLandscape = (configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE 
            && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    val isDark = if (isTabletLandscape) true else isSystemInDarkTheme()
    val notPlayingStr = stringResource(R.string.not_playing)

    var title by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.title?.toString() ?: notPlayingStr) }
    var artist by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "") }
    var artworkUri by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.artworkUri) }
    var isPlaying by remember { mutableStateOf(controller.isPlaying) }
    var progress by remember { mutableFloatStateOf(0f) }

    // 新增：用于双击和连击的记录状态
    var lastRecentClickTime by remember { mutableLongStateOf(0L) }
    var libraryClickCount by remember { mutableIntStateOf(0) }
    var lastLibraryClickTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                title = metadata.title?.toString() ?: notPlayingStr
                artist = metadata.artist?.toString() ?: ""
                artworkUri = metadata.artworkUri
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val dur = controller.duration
            if (dur > 0) progress = (controller.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            delay(500)
        }
    }

    val contentColor = if (isDark) Color.White else Color.Black
    val subTextColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f)
    val accentColor = if (isDark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 进度条与火花层：放在底层且不加裁剪，火星可以飞到外部
        SparkingProgressBar(
            progress = progress,
            isPlaying = isPlaying,
            accentColor = accentColor,
            onProgressChange = { newProgress ->
                controller.seekTo((newProgress * controller.duration).toLong())
            },
            lineCenterOffset = -10f, // 关键：让引线中心向上偏移 10dp，正好压在玻璃栏上边沿
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 38.dp) // 同步 R 角调整，对齐直线起点
                .height(40.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-10).dp)
        )

        // 内容层容器：不再对自己应用任何渲染效果，只上报坐标给 Activity
        var lastRect by remember { mutableStateOf<RectF?>(null) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    val size = coordinates.size
                    val newRect = RectF(
                        position.x,
                        position.y,
                        position.x + size.width,
                        position.y + size.height
                    )
                    
                    // 优化：只有当坐标发生超过 1 像素的位移时才上报，减少 Activity 端的计算压力
                    if (lastRect == null || 
                        abs(lastRect!!.top - newRect.top) > 1f || 
                        abs(lastRect!!.left - newRect.left) > 1f) {
                        lastRect = newRect
                        (context as? HomeActivity)?.setDockRect(newRect)
                    }
                }
                .background(Color.Transparent)
                // 增加 1 像素细线边框：深色模式白色，浅色模式灰色
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(38.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPlayerClick() }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mediaId = controller.currentMediaItem?.mediaId
                    // 修复 CUE 分轨 ID 匹配问题：剥离 ID 后缀
                    val realId = remember(mediaId) { 
                        if (mediaId?.contains("_") == true) mediaId.substringBefore("_") else mediaId 
                    }
                    
                    val neteaseCovers = context.getSharedPreferences("netease_covers", android.content.Context.MODE_PRIVATE)
                    var cachedNeteaseUrl by remember(realId) { 
                        mutableStateOf(neteaseCovers.getString(realId, null)) 
                    }

                    DisposableEffect(realId) {
                        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                            if (key == realId) {
                                cachedNeteaseUrl = prefs.getString(key, null)
                            }
                        }
                        neteaseCovers.registerOnSharedPreferenceChangeListener(listener)
                        onDispose { neteaseCovers.unregisterOnSharedPreferenceChangeListener(listener) }
                    }

                    // 策略：缓存优先，且针对网易云 URL 限制尺寸为 200px 节省流量
                    val finalUri = remember(artworkUri, cachedNeteaseUrl) {
                        val url = cachedNeteaseUrl ?: artworkUri?.toString()
                        if (url?.startsWith("http") == true) {
                            if (url.contains("music.163.com") || url.contains("126.net")) {
                                val cleanUrl = if (url.contains("?")) url.substringBefore("?") else url
                                "$cleanUrl?param=200y200"
                            } else url
                        } else url
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(finalUri)
                            .crossfade(true)
                            .placeholder(R.drawable.cd)
                            .error(R.drawable.cd)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(14.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = contentColor,
                            modifier = Modifier.basicMarquee()
                        )
                        if (artist.isNotEmpty()) {
                            Text(
                                text = artist,
                                fontSize = 13.sp,
                                color = subTextColor,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (isPlaying) controller.pause() else controller.play() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painterResource(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector),
                            null,
                            tint = contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 导航栏
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf("Recent", "Library", "Search")
                    tabs.forEach { tab ->
                        val isFavorite = selectedTab == "Favorite" && tab == "Recent"
                        val isRecent = selectedTab == "Recent" && tab == "Recent"
                        val isSelected = if (tab == "Recent") isRecent || isFavorite else selectedTab == tab
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .glassNavItem(isSelected) {
                                    val currentTime = System.currentTimeMillis()
                                    when (tab) {
                                        "Recent" -> {
                                            if (currentTime - lastRecentClickTime < 300) {
                                                onNavigation("Favorite")
                                            } else {
                                                onNavigation("Recent")
                                            }
                                            lastRecentClickTime = currentTime
                                        }
                                        "Library" -> {
                                            onNavigation("Library")
                                            if (currentTime - lastLibraryClickTime < 500) {
                                                libraryClickCount++
                                            } else {
                                                libraryClickCount = 1
                                            }
                                            lastLibraryClickTime = currentTime
                                            if (libraryClickCount >= 5) {
                                                onLibraryScan()
                                                libraryClickCount = 0
                                            }
                                        }
                                        "Search" -> onNavigation("Search")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val iconRes = when (tab) {
                                    "Recent" -> if (isFavorite) R.drawable.ic_heart else R.drawable.ic_recent
                                    "Library" -> R.drawable.ic_music_note
                                    else -> R.drawable.ic_search
                                }
                                Icon(
                                    painterResource(iconRes),
                                    null,
                                    tint = if (isSelected) accentColor else subTextColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val textRes = when (tab) {
                                    "Recent" -> if (isFavorite) R.string.nav_favorite else R.string.nav_recent
                                    "Library" -> R.string.nav_library
                                    else -> R.string.nav_search
                                }
                                Text(
                                    text = stringResource(textRes),
                                    fontSize = 11.sp,
                                    color = if (isSelected) accentColor else subTextColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
