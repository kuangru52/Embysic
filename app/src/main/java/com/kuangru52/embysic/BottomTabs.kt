package com.kuangru52.embysic

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

@Composable
fun BottomTabs(
    controller: MediaController,
    selectedTab: String,
    onPlayerClick: () -> Unit,
    onNavigation: (String) -> Unit,
    onLibraryScan: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val notPlayingStr = stringResource(R.string.not_playing)

    var title by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.title?.toString() ?: notPlayingStr) }
    var artist by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "") }
    var artworkUri by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.artworkUri) }
    var isPlaying by remember { mutableStateOf(controller.isPlaying) }
    var progress by remember { mutableStateOf(0f) }

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

    var targetIndicatorX by remember { mutableFloatStateOf(0f) }
    var targetIndicatorY by remember { mutableFloatStateOf(0f) }
    val indicatorRadius = with(density) { 26.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.Transparent)
            // 添加纤细的玻璃边缘轮廓
            .border(
                width = 0.5.dp,
                color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(30.dp)
            )
    ) {
        // 1. 内容层：纯净显示，不再受折射滤镜干扰
        Column(modifier = Modifier.fillMaxWidth()) {
            // 播放进度条 (置于上边缘，避开圆角部分)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp) // 核心要求：避开两端 30.dp 的圆角区域，仅在直边显示
                    .height(1.5.dp)
                    .background(accentColor.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(accentColor.copy(alpha = 0.7f))
                )
            }

            // 播放控制条 (上部)
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
                AsyncImage(
                    model = ImageRequest.Builder(context).data(artworkUri).placeholder(R.drawable.cd).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = contentColor)
                    if (artist.isNotEmpty()) {
                        Text(text = artist, fontSize = 13.sp, color = subTextColor, maxLines = 1)
                    }
                }

                IconButton(
                    onClick = { if (isPlaying) controller.pause() else controller.play() },
                    modifier = Modifier.size(44.dp) // 移除 background
                ) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp) // 增大图标，更清晰
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
                            .onGloballyPositioned { coords ->
                                if (isSelected) {
                                    val pos = coords.positionInParent()
                                    targetIndicatorX = pos.x + coords.size.width / 2f
                                    targetIndicatorY = pos.y + coords.size.height / 2f
                                }
                            }
                            .glassNavItem(isSelected) {
                                val currentTime = System.currentTimeMillis()
                                when (tab) {
                                    "Recent" -> {
                                        // 双击逻辑：300ms内再次点击进入收藏
                                        if (currentTime - lastRecentClickTime < 300) {
                                            onNavigation("Favorite")
                                        } else {
                                            onNavigation("Recent")
                                        }
                                        lastRecentClickTime = currentTime
                                    }
                                    "Library" -> {
                                        onNavigation("Library")
                                        // 连点5次逻辑
                                        if (currentTime - lastLibraryClickTime < 500) {
                                            libraryClickCount++
                                        } else {
                                            libraryClickCount = 1
                                        }
                                        lastLibraryClickTime = currentTime
                                        if (libraryClickCount >= 5) {
                                            onLibraryScan()
                                            libraryClickCount = 0
                                            Toast.makeText(context, "正在同步媒体库...", Toast.LENGTH_SHORT).show()
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
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) accentColor else subTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
