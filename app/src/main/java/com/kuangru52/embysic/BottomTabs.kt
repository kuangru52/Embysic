package com.kuangru52.embysic

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BottomTabs(
    controller: MediaController?,
    selectedTab: String,
    onPlayerClick: () -> Unit,
    onNavigation: (String) -> Unit,
    onLibraryScan: () -> Unit = {},
    isDevMode: Boolean = false,
    isDark: Boolean = true,
    onOpenGlassTuning: () -> Unit = {},
    cornerRadius: Float = 38f,
    blurRadius: Float = 18f,
    refractionAmount: Float = 60f,
    refractionHeight: Float = 12f,
    bleedAmount: Float = 10f,
    bleedOpacity: Float = 0.3f,
    chromaMultiplier: Float = 1.5f,
    contrast: Float = 0.15f,
    whitePoint: Float = 0.20f,
    backdrop: LayerBackdrop? = null,
) {
    val notPlayingStr = stringResource(R.string.not_playing)

    var title by remember { mutableStateOf(controller?.currentMediaItem?.mediaMetadata?.title?.toString() ?: notPlayingStr) }
    var artist by remember { mutableStateOf(controller?.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "") }
    var album by remember { mutableStateOf(controller?.currentMediaItem?.mediaMetadata?.albumTitle?.toString() ?: "") }
    var artworkUri by remember { mutableStateOf(controller?.currentMediaItem?.mediaMetadata?.artworkUri) }
    var isPlaying by remember { mutableStateOf(controller?.isPlaying ?: false) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                title = metadata.title?.toString() ?: notPlayingStr
                artist = metadata.artist?.toString() ?: ""
                album = metadata.albumTitle?.toString() ?: ""
                artworkUri = metadata.artworkUri
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    LaunchedEffect(controller, isPlaying) {
        while (isPlaying && (controller != null)) {
            val extras = controller.currentMediaItem?.mediaMetadata?.extras
            val injectDuration = extras?.getLong("duration_ms") ?: 0L
            val dur = if (controller.duration > 0) controller.duration else injectDuration
            if (dur > 0) {
                progress = (controller.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(500.milliseconds)
        }
    }

    BottomTabsUI(
        selectedTab = selectedTab,
        title = title,
        artist = artist,
        album = album,
        artworkUri = artworkUri,
        isPlaying = isPlaying,
        progress = progress,
        isDark = isDark,
        onPlayerClick = onPlayerClick,
        onPlayPauseClick = {
            val ctrl = controller ?: return@BottomTabsUI
            if (isPlaying) ctrl.pause() else ctrl.play()
        },
        onNavigation = onNavigation,
        onLibraryScan = onLibraryScan,
        isDevMode = isDevMode,
        onOpenGlassTuning = onOpenGlassTuning,
        cornerRadius = cornerRadius,
        blurRadius = blurRadius,
        refractionAmount = refractionAmount,
        refractionHeight = refractionHeight,
        bleedAmount = bleedAmount,
        bleedOpacity = bleedOpacity,
        chromaMultiplier = chromaMultiplier,
        contrast = contrast,
        whitePoint = whitePoint,
        backdrop = backdrop,
    )
}

@OptIn(UnstableApi::class)
@Composable
fun BottomTabsUI(
    selectedTab: String,
    title: String,
    artist: String,
    album: String = "",
    artworkUri: Uri? = null,
    isPlaying: Boolean,
    progress: Float,
    isDark: Boolean = true,
    onPlayerClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNavigation: (String) -> Unit,
    onLibraryScan: () -> Unit = {},
    isDevMode: Boolean = false,
    onOpenGlassTuning: () -> Unit = {},
    cornerRadius: Float = 38f,
    blurRadius: Float = 18f,
    refractionAmount: Float = 60f,
    refractionHeight: Float = 12f,
    bleedAmount: Float = 10f,
    bleedOpacity: Float = 0.3f,
    chromaMultiplier: Float = 1.5f,
    contrast: Float = 0.15f,
    whitePoint: Float = 0.20f,
    backdrop: LayerBackdrop? = null,
) {
    val configuration = LocalConfiguration.current
    val isTabletLandscape = ((configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE)
            && (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    
    val context = LocalContext.current
    val homeActivity = remember(context) { context.findHomeActivity() }
    val currentThemeMode = homeActivity?.themeMode ?: "system"
    
    val effectiveIsDark = if (isTabletLandscape) true else when (currentThemeMode) {
        "light" -> false
        "dark" -> true
        else -> isDark
    }

    var lastRecentClickTime by remember { mutableLongStateOf(0L) }
    var libraryClickCount by remember { mutableIntStateOf(0) }
    var lastLibraryClickTime by remember { mutableLongStateOf(0L) }

    val contentColor = if (effectiveIsDark) Color.White else Color(0xFF0F172A)
    val subTextColor = if (effectiveIsDark) Color.White.copy(alpha = 0.70f) else Color(0xFF334155)
    val accentColor = if (effectiveIsDark) Color.White else Color(0xFF0F172A)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // =========================================================================
        // Layer 2 (中层液态玻璃底座): 纯正 Kyant0 官方原版 Backdrop + lens() + blur() 组合！
        // 抓取被 layerBackdrop 捕获的真实底层内容，调用官方原版 3D 液态透镜与高斯模糊！
        // =========================================================================
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(cornerRadius.dp))
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(cornerRadius.dp) },
                            effects = {
                                listOf(
                                    lens(
                                        refractionAmount = kotlin.math.abs(refractionAmount),
                                        refractionHeight = refractionHeight.coerceAtMost(cornerRadius),
                                    ),
                                    blur(radius = blurRadius),
                                )
                            },
                        )
                    } else {
                        Modifier.background(Color(0x1F1E293B), RoundedCornerShape(cornerRadius.dp))
                    }
                )
                .pointerInput(isDevMode) {
                    if (isDevMode) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -12f) {
                                onOpenGlassTuning()
                            }
                        }
                    }
                },
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize(),
            ) {
                if ((size.width <= 1f) || (size.height <= 1f)) return@Canvas

                val strokeWidthPx = (1.2f + (whitePoint * 0.8f)).dp.toPx()
                val cornerPx = cornerRadius.dp.toPx()
                val corner = CornerRadius(cornerPx, cornerPx)

                if ((bleedAmount > 0.1f) && (bleedOpacity > 0.01f)) {
                    val bleedPx = bleedAmount.dp.toPx()
                    val bleedAlpha = (bleedOpacity * 0.35f).coerceIn(0.02f, 0.5f)
                    val bleedColor = if (isDark) Color(0xFF38BDF8).copy(alpha = bleedAlpha) else Color.White.copy(alpha = bleedAlpha)
                    drawRoundRect(
                        color = bleedColor,
                        cornerRadius = CornerRadius(cornerPx + bleedPx, cornerPx + bleedPx),
                    )
                }

                // 1. 全周 1.2dp 基础边框
                val rimAlpha = (0.25f + (chromaMultiplier * 0.1f) + (contrast * 0.15f)).coerceIn(0.15f, 0.95f)
                val rimColor = if (isDark) Color.White.copy(alpha = rimAlpha) else Color.Gray.copy(alpha = rimAlpha)

                drawRoundRect(
                    color = rimColor,
                    cornerRadius = corner,
                    style = Stroke(width = strokeWidthPx),
                )

                // 2. 播放进度条：仅占底栏的上边边框（1.2dp 线宽），100% 完全重合，仅以颜色区分进度
                val p = progress.coerceIn(0f, 1f)
                if (p > 0f) {
                    val progressWidth = size.width * p
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(0f, 0f, progressWidth, strokeWidthPx * 2.5f)
                    drawRoundRect(
                        color = accentColor,
                        cornerRadius = corner,
                        style = Stroke(width = strokeWidthPx),
                    )
                    drawContext.canvas.restore()
                }
            }
        }

        // =========================================================================
        // Layer 3 (顶层前景内容): 100% 独立在 drawBackdrop 之外！
        // 包含封面、标题、歌手、播放按键、最近/曲库/搜索 Tab 按钮 -> 100% 绝对清晰！
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(isDevMode) {
                    if (isDevMode) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -12f) {
                                onOpenGlassTuning()
                            }
                        }
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlayerClick,
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artworkUri)
                        .crossfade(enable = true)
                        .placeholder(R.drawable.logo)
                        .error(R.drawable.logo)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = contentColor,
                    )
                    if (artist.isNotEmpty()) {
                        val displayArtist = if (album.isNotEmpty()) "$artist - $album" else artist
                        Text(
                            text = displayArtist,
                            fontSize = 13.sp,
                            color = subTextColor,
                            maxLines = 1,
                        )
                    }
                }

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = listOf("Recent", "Library", "Search")
                tabs.forEach { tab ->
                    val isFavorite = (selectedTab == "Favorite") && (tab == "Recent")
                    val isRecent = (selectedTab == "Recent") && (tab == "Recent")
                    val isSelected = if (tab == "Recent") isRecent || isFavorite else selectedTab == tab

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .glassNavItem(isSelected) {
                                val currentTime = System.currentTimeMillis()
                                when (tab) {
                                    "Recent" -> {
                                        if ((currentTime - lastRecentClickTime) < 300) {
                                            onNavigation("Favorite")
                                        } else {
                                            onNavigation("Recent")
                                        }
                                        lastRecentClickTime = currentTime
                                    }
                                    "Library" -> {
                                        val currentTime = System.currentTimeMillis()
                                        if ((currentTime - lastLibraryClickTime) < 300) {
                                            libraryClickCount++
                                        } else {
                                            libraryClickCount = 1
                                        }
                                        lastLibraryClickTime = currentTime
                                        if (libraryClickCount >= 5) {
                                            onLibraryScan()
                                            libraryClickCount = 0
                                        } else {
                                            onNavigation("Library")
                                        }
                                    }
                                    "Search" -> onNavigation("Search")
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val iconRes = when (tab) {
                                "Recent" -> if (isFavorite) R.drawable.ic_heart else R.drawable.ic_recent
                                "Library" -> R.drawable.ic_music_note
                                else -> R.drawable.ic_search
                            }
                            Icon(
                                painterResource(iconRes),
                                contentDescription = null,
                                tint = if (isSelected) accentColor else subTextColor,
                                modifier = Modifier.size(24.dp),
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
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.glassNavItem(isSelected: Boolean, onClick: () -> Unit): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "glassNavItemScale",
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
}

// --- Preview ---

@Preview(name = "Bottom Tabs Preview", showBackground = true)
@Composable
fun BottomTabsPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F1A))
                .padding(vertical = 16.dp),
        ) {
            BottomTabsUI(
                selectedTab = "Recent",
                title = "示例歌曲 - 旋律之旅",
                artist = "示例歌手",
                album = "示例专辑",
                artworkUri = null,
                isPlaying = true,
                progress = 0.4f,
                onPlayerClick = {},
                onPlayPauseClick = {},
                onNavigation = {},
                onLibraryScan = {},
            )
        }
    }
}
