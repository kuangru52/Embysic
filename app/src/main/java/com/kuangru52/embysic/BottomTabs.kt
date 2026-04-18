package com.kuangru52.embysic

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val notPlayingStr = stringResource(R.string.not_playing)

    var title by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.title?.toString() ?: notPlayingStr) }
    var artist by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "") }
    var artworkUri by remember { mutableStateOf(controller.currentMediaItem?.mediaMetadata?.artworkUri) }
    var isPlaying by remember { mutableStateOf(controller.isPlaying) }
    var progress by remember { mutableStateOf(0f) }

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

    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isDark) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp) // 增加外边距，使其更像悬浮 Dock
            .navigationBarsPadding()
            // 玻璃质感的关键：更细的高光边框，匹配大 R 角
            .border(
                width = 0.6.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                    } else {
                        listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.2f))
                    }
                ),
                shape = RoundedCornerShape(32.dp) // 增加到 32dp，匹配小米大 R 角视觉
            ),
        shape = RoundedCornerShape(32.dp),
        color = if (isDark) {
            Color(0xFF1A1A1A).copy(alpha = 0.75f) // 深色稍微调亮一点，增加通透感
        } else {
            Color.White.copy(alpha = 0.8f)
        },
        tonalElevation = 0.dp
    ) {
        Column {
            // 进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )

            // 第一行：播放器区域 (迷你播放条)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPlayerClick() }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artworkUri).placeholder(R.drawable.cd).error(R.drawable.cd).build(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title, 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold, 
                            maxLines = 1,
                            color = contentColor
                        )
                        if (artist.isNotEmpty()) {
                            Text(
                                artist, 
                                fontSize = 12.sp, 
                                maxLines = 1, 
                                color = subTextColor
                            )
                        }
                    }
                }

                IconButton(onClick = { if (isPlaying) controller.pause() else controller.play() }) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow), 
                        null,
                        tint = contentColor
                    )
                }
            }

            // 第二行：三个按键 (底部导航)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var lastRecentClickTime by remember { mutableLongStateOf(0L) }
                var libraryClickCount by remember { mutableIntStateOf(0) }
                var lastLibraryClickTime by remember { mutableLongStateOf(0L) }

                // 第一个按钮：最近 / 收藏
                val isFavorite = selectedTab == "Favorite"
                val isRecent = selectedTab == "Recent"
                val isFirstTabActive = isRecent || isFavorite
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            if (isRecent && now - lastRecentClickTime < 500) {
                                onNavigation("Favorite")
                                lastRecentClickTime = 0
                            } else {
                                onNavigation("Recent")
                                lastRecentClickTime = now
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(if (isFavorite) R.drawable.ic_heart else R.drawable.ic_recent),
                        contentDescription = null,
                        tint = if (isFirstTabActive) MaterialTheme.colorScheme.primary else subTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(if (isFavorite) R.string.nav_favorite else R.string.nav_recent),
                        fontSize = 10.sp,
                        color = if (isFirstTabActive) MaterialTheme.colorScheme.primary else subTextColor
                    )
                }

                // 第二个按钮：曲库
                val isLibrary = selectedTab == "Library"
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onNavigation("Library")
                            val now = System.currentTimeMillis()
                            if (now - lastLibraryClickTime < 2000) {
                                libraryClickCount++
                            } else {
                                libraryClickCount = 1
                            }
                            lastLibraryClickTime = now
                            if (libraryClickCount >= 5) {
                                onLibraryScan()
                                libraryClickCount = 0
                                Toast.makeText(context, "开始扫描媒体库...", Toast.LENGTH_SHORT).show()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = if (isLibrary) MaterialTheme.colorScheme.primary else subTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.nav_library),
                        fontSize = 10.sp,
                        color = if (isLibrary) MaterialTheme.colorScheme.primary else subTextColor
                    )
                }

                // 第三个按钮：搜索
                val isSearch = selectedTab == "Search"
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onNavigation("Search")
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = if (isSearch) MaterialTheme.colorScheme.primary else subTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.nav_search),
                        fontSize = 10.sp,
                        color = if (isSearch) MaterialTheme.colorScheme.primary else subTextColor
                    )
                }
            }
        }
    }
}
