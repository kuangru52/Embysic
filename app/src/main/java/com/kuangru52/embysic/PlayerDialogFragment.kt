package com.kuangru52.embysic

import android.annotation.SuppressLint
import android.app.Dialog
import kotlin.time.Duration.Companion.milliseconds
import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.wasabeef.transformers.coil.BlurTransformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
class PlayerDialogFragment : BottomSheetDialogFragment() {

    private var player: Player? = null
    fun setPlayer(player: Player) {
        this.player = player
    }

    private var apiService: EmbyApiService? = null
    private var heartLayoutInstance: HeartLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_PlayerBottomSheet)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isHideable = true
            isDraggable = true
        }
        dialog.window?.let { window ->
            window.setWindowAnimations(R.style.PlayerDialogAnimation)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        return dialog
    }

    @SuppressLint("DiscouragedApi")
    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.let {
            val res = requireContext().resources
            val bottomSheetId = res.getIdentifier("design_bottom_sheet", "id", "com.google.android.material")
            val bottomSheet = it.findViewById<View>(bottomSheetId)
            bottomSheet?.let { sheet ->
                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
                sheet.fitsSystemWindows = false
                
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }

            val containerId = res.getIdentifier("container", "id", "com.google.android.material")
            val container = it.findViewById<View>(containerId)
            val coordinatorId = res.getIdentifier("coordinator", "id", "com.google.android.material")
            val coordinator = it.findViewById<View>(coordinatorId)
            
            container?.fitsSystemWindows = false
            container?.let { c -> ViewCompat.setOnApplyWindowInsetsListener(c) { _, insets -> insets } }
            
            coordinator?.fitsSystemWindows = false
            coordinator?.let { co -> ViewCompat.setOnApplyWindowInsetsListener(co) { _, insets -> insets } }
            
            it.window?.let { window ->
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }

                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        apiService = RetrofitClient.getEmbyApiService(requireContext())
        val p = player ?: (activity as? HomeActivity)?.mediaController

        val composeView = ComposeView(requireContext())
        composeView.setContent {
            var heartLayoutState by remember { mutableStateOf<HeartLayout?>(null) }

            Box(modifier = Modifier.fillMaxSize()) {
                p?.let { activePlayer ->
                    PlayerComposeScreen(
                        player = activePlayer,
                        apiService = apiService,
                        onDismiss = { dismiss() },
                        heartLayout = heartLayoutState,
                    )
                }

                AndroidView(
                    factory = { context ->
                        HeartLayout(context).also { 
                            heartLayoutInstance = it 
                            heartLayoutState = it
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return composeView
    }
}

@OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerComposeScreen(
    player: Player,
    apiService: EmbyApiService?,
    onDismiss: () -> Unit,
    heartLayout: HeartLayout? = null,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val handler = remember { Handler(Looper.getMainLooper()) }

    var currentItem by remember { mutableStateOf(player.currentMediaItem) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }
    var shuffleMode by remember { mutableStateOf(player.shuffleModeEnabled) }

    var isLyricsVisible by remember { mutableStateOf(false) }
    var lyricsLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var activeLyricIndex by remember { mutableIntStateOf(0) }

    var isDraggingSeekBar by remember { mutableStateOf(false) }
    var dragProgressMs by remember { mutableLongStateOf(0L) }

    var showModeHint by remember { mutableStateOf(false) }
    var modeHintText by remember { mutableStateOf("") }

    var isVolumeHintVisible by remember { mutableStateOf(false) }
    var volumeProgress by remember { mutableIntStateOf(0) }
    var volumeMax by remember { mutableIntStateOf(15) }

    var discRotation by remember { mutableFloatStateOf(0f) }
    var volumeDotViewRef by remember { mutableStateOf<VolumeDotView?>(null) }

    // 旋转动画与进度更新
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            discRotation = (discRotation + 0.5f) % 360f
            if (!isDraggingSeekBar) {
                currentPosition = player.currentPosition
                val injectDur = player.currentMediaItem?.mediaMetadata?.extras?.getLong("duration_ms") ?: 0L
                duration = if (player.duration > 0) player.duration else injectDur
            }
            delay(30.milliseconds)
        }
    }

    // 监听 Player 事件
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentItem = mediaItem
                lyricsLines = emptyList()
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onRepeatModeChanged(rm: Int) { repeatMode = rm }
            override fun onShuffleModeEnabledChanged(sh: Boolean) { shuffleMode = sh }
            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                currentItem = player.currentMediaItem
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 唱针旋转动画
    val needleAngle by animateFloatAsState(
        targetValue = if (isPlaying) -8f else -35f,
        animationSpec = tween(400),
        label = "needleRotation"
    )

    // 歌词高光计算
    LaunchedEffect(currentPosition, lyricsLines) {
        if (lyricsLines.isNotEmpty()) {
            val idx = lyricsLines.indexOfLast { it.timeMs <= currentPosition }
            if (idx >= 0) activeLyricIndex = idx
        }
    }

    // 元数据提取
    val metadata = currentItem?.mediaMetadata
    val rawMediaId = currentItem?.mediaId ?: ""
    val itemId = rawMediaId.substringBefore('_')
    val title = metadata?.title?.toString() ?: stringResource(R.string.unknown_song)
    val artist = metadata?.artist?.toString() ?: stringResource(R.string.unknown_artist)
    val album = metadata?.albumTitle?.toString() ?: ""
    val artistAlbumText = if (album.isEmpty()) artist else "$artist - $album"
    val isFavorite = metadata?.extras?.getBoolean("is_favorite", false) ?: false
    val isTranscoding = metadata?.extras?.getBoolean("is_transcoding", false) ?: false
    val codec = metadata?.extras?.getString("codec", "未知") ?: "未知"
    val bitrate = (metadata?.extras?.getInt("bitrate", 0) ?: 0) / 1000
    val artworkUri = metadata?.artworkUri

    val safeDuration = if (duration > 0) duration else 1L
    val displayPos = if (isDraggingSeekBar) dragProgressMs else currentPosition.coerceAtMost(safeDuration)

    // 定制拉取 Emby/网易云歌词
    LaunchedEffect(itemId, isLyricsVisible) {
        if (isLyricsVisible && lyricsLines.isEmpty() && itemId.isNotEmpty()) {
            val prefs = context.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
            val accessToken = prefs.getString("access_token", "") ?: ""
            val authHeader = "MediaBrowser Token=\"$accessToken\""

            if (LyricUtils.lyricsCache.containsKey(itemId)) {
                lyricsLines = LyricUtils.lyricsCache[itemId]!!
            } else {
                try {
                    val response = apiService?.getLyrics(itemId, authHeader)
                    val rawLines = response?.Lines ?: response?.Lyrics ?: response?.LyricLines
                    @Suppress("UNCHECKED_CAST")
                    val actualLines = rawLines as? List<LyricLine>
                    if (!actualLines.isNullOrEmpty()) {
                        val header = listOf(LrcLine(-1, title), LrcLine(-1, artist))
                        val parsed = actualLines.asSequence().map { LrcLine(it.StartTicks?.let { t -> t / 10000 } ?: it.Start?.let { t -> (t * 1000).toLong() } ?: 0L, it.Text ?: "") }
                            .filter { it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()
                        val finalLrc = header + parsed
                        LyricUtils.lyricsCache[itemId] = finalLrc
                        lyricsLines = finalLrc
                    } else {
                        LyricUtils.fetchNeteaseMetadata(
                            context = context,
                            itemId = itemId,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = safeDuration,
                        ) { lines ->
                            lyricsLines = lines
                        }
                    }
                } catch (_: Exception) {
                    LyricUtils.fetchNeteaseMetadata(
                        context = context,
                        itemId = itemId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = safeDuration,
                        onLyricsReady = { lines -> lyricsLines = lines }
                    )
                }
            }
        }
    }

    val screenCornerRadiusDp = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val window = (context as? android.app.Activity)?.window
            val insets = window?.decorView?.rootWindowInsets
            val topLeft = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
            val topRight = insets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)
            val radiusPx = topLeft?.radius ?: topRight?.radius ?: (28 * context.resources.displayMetrics.density).toInt()
            (radiusPx / context.resources.displayMetrics.density).dp
        } else {
            28.dp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(screenCornerRadiusDp))
            .background(Color.Black)
            .clickable { if (isLyricsVisible) isLyricsVisible = false }
    ) {
        // 1. 动态高斯模糊背景 (根据当前唱片/封面图高斯模糊渲染，完全还原原版)
        val coverFile = remember(itemId) { File(context.cacheDir, "covers/$itemId.jpg") }
        val prefs = remember { context.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE) }
        val serverUrl = remember { prefs.getString("server_url", "")?.trimEnd('/') ?: "" }
        val imageUrl = "$serverUrl/emby/Items/$itemId/Images/Primary?MaxWidth=800"

        val coverModel: Any = when {
            artworkUri != null -> artworkUri
            coverFile.exists() -> coverFile
            else -> imageUrl
        }

        val blurRequest = remember(coverModel) {
            ImageRequest.Builder(context)
                .data(coverModel)
                .allowHardware(false)
                .transformations(BlurTransformation(context, 25, 4))
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = blurRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 黑色 25% 遮罩层 (#40000000)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x40000000))
        )

        // 2. 主内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 中间唱片区 / 歌词区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!isLyricsVisible) {
                    // 唱片区 (rlDiscContainer: translationY = 30dp)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = 30.dp)
                            .clickable { isLyricsVisible = true },
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. 唱片盘框 Ring (310dp x 310dp)
                        Surface(
                            shape = CircleShape,
                            color = Color(0x10FFFFFF),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.size(310.dp)
                        ) {}

                        // 2. 黑胶唱片 (300dp x 300dp, 旋转)
                        Box(
                            modifier = Modifier
                                .size(300.dp)
                                .rotate(discRotation),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.disk),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // 200dp 圆形专辑封面
                            Surface(
                                shape = CircleShape,
                                modifier = Modifier.size(200.dp)
                            ) {
                                AsyncImage(
                                    model = coverModel,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(id = R.drawable.logo),
                                    error = painterResource(id = R.drawable.logo)
                                )
                            }
                        }

                        // 3. 唱针 ivNeedle (132dp x 161dp, translationX = 30dp, translationY = -145dp, pivot = 25dp)
                        Image(
                            painter = painterResource(id = R.drawable.styli2),
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 132.dp, height = 161.dp)
                                .offset(x = 30.dp, y = (-145).dp)
                                .graphicsLayer {
                                    rotationZ = needleAngle
                                    transformOrigin = TransformOrigin(25f / 132f, 25f / 161f)
                                }
                        )
                    }
                } else {
                    // 歌词区：点击歌词跳转播放，点击空白部位返回旋转唱片
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isLyricsVisible = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(activeLyricIndex) {
                            if (lyricsLines.isNotEmpty() && (activeLyricIndex in lyricsLines.indices)) {
                                listState.animateScrollToItem(activeLyricIndex)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(lyricsLines) { idx, line ->
                                val isActive = idx == activeLyricIndex
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = line.text,
                                        fontSize = if (isActive) 18.sp else 15.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .clickable {
                                                if (line.timeMs >= 0) {
                                                    player.seekTo(line.timeMs)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 模式提示词 / 音量提示 (flHintContainer: 32dp height, marginTop 8dp, marginBottom 4dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showModeHint) {
                    Text(
                        text = modeHintText,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                } else if (isVolumeHintVisible) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        LinearProgressIndicator(
                            progress = { if (volumeMax > 0) volumeProgress.toFloat() / volumeMax.toFloat() else 0f },
                            modifier = Modifier
                                .width(180.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // 歌名、歌手与收藏按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (itemId.isNotEmpty()) {
                                onDismiss()
                                val fragment = LibraryFragment().apply {
                                    arguments = Bundle().apply { putString("target_item_id", rawMediaId) }
                                }
                                (activity as? HomeActivity)?.replaceFragment(fragment, "library_locate")
                            }
                        }
                ) {
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artistAlbumText,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        modifier = Modifier
                            .basicMarquee()
                            .clickable {
                                val artistId = metadata?.extras?.getString("artist_id")
                                if (artistId != null) {
                                    onDismiss()
                                    val fragment = LibraryFragment().apply {
                                        arguments = Bundle().apply {
                                            putString("artist_id", artistId)
                                            putString("artist_name", artist)
                                        }
                                    }
                                    (activity as? HomeActivity)?.replaceFragment(fragment, "artist_$artistId")
                                }
                            }
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 收藏按钮 (带 Q 弹缩放 + HeartLayout 爱心碎裂/飞出动效)
                var isHeartBouncing by remember { mutableStateOf(false) }
                val heartScale by animateFloatAsState(
                    targetValue = if (isHeartBouncing) 1.35f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "heartScale",
                    finishedListener = { isHeartBouncing = false }
                )
                var heartCenterPos by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

                IconButton(
                    onClick = {
                        isHeartBouncing = true
                        val isFav = metadata?.extras?.getBoolean("is_favorite", false) ?: false
                        val prefsEdit = context.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
                        val uId = prefsEdit.getString("user_id", "") ?: ""
                        val token = prefsEdit.getString("access_token", "") ?: ""
                        val auth = "MediaBrowser Token=\"$token\""

                        activity?.lifecycleScope?.launch {
                            try {
                                val dm = context.resources.displayMetrics
                                val centerX = heartCenterPos?.x ?: (dm.widthPixels - 40f * dm.density)
                                val centerY = heartCenterPos?.y ?: (dm.heightPixels - 250f * dm.density)

                                if (isFav) {
                                    apiService?.unmarkFavorite(uId, itemId, auth)
                                    heartLayout?.shatterHeart(centerX, centerY)
                                } else {
                                    apiService?.markFavorite(uId, itemId, auth)
                                    repeat(5) { step ->
                                        handler.postDelayed(
                                            {
                                                heartLayout?.addHeart(centerX, centerY)
                                            },
                                            step * 100L,
                                        )
                                    }
                                }
                                metadata?.extras?.putBoolean("is_favorite", !isFav)
                                Toast.makeText(context, if (!isFav) "已添加收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            val sz = coords.size
                            heartCenterPos = androidx.compose.ui.geometry.Offset(pos.x + (sz.width / 2f), pos.y + (sz.height / 2f))
                        }
                        .graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                        }
                ) {
                    Icon(
                        painter = painterResource(id = if (isFavorite) R.drawable.ic_heart else R.drawable.ic_heart_border),
                        contentDescription = "收藏",
                        tint = if (isFavorite) Color.Red else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 音质标签 (tvAudioQuality: 11sp, marginTop 12dp)
            var qualityClickCount by remember { mutableIntStateOf(0) }
            var lastQualityClickTime by remember { mutableLongStateOf(0L) }

            val qualityText = if (!isTranscoding) "⚠️ 源码播放 $codec ${bitrate}kbps" else "正在转码 $codec ${bitrate}kbps"
            Text(
                text = qualityText,
                fontSize = 11.sp,
                color = if (!isTranscoding) Color.Yellow else Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable {
                        val now = System.currentTimeMillis()
                        if (now - lastQualityClickTime < 500) {
                            qualityClickCount++
                        } else {
                            qualityClickCount = 1
                        }
                        lastQualityClickTime = now

                        if (qualityClickCount >= 5) {
                            qualityClickCount = 0
                            MediaItemUtils.isForceDirectMode = !MediaItemUtils.isForceDirectMode
                            val currentPos = player.currentPosition
                            val prefsEdit = context.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE)
                            val server = prefsEdit.getString("server_url", "") ?: ""
                            val token = prefsEdit.getString("access_token", "") ?: ""
                            val uId = prefsEdit.getString("user_id", "") ?: ""
                            val auth = "MediaBrowser Token=\"$token\""

                            activity?.lifecycleScope?.launch {
                                try {
                                    val embyItem = apiService?.getItem(uId, itemId, auth) ?: return@launch
                                    val newItem = MediaItemUtils.buildMediaItem(
                                        context, embyItem, server, token, uId,
                                        forceDirect = MediaItemUtils.isForceDirectMode
                                    )
                                    player.setMediaItem(newItem)
                                    player.seekTo(currentPos)
                                    player.prepare()
                                    player.play()
                                } catch (_: Exception) {}
                            }
                        }
                    }
            )

            // 进度条 (1:1 还原原版半月/竖向胶囊滑块与 2.5dp 细轨道)
            val progressFraction = if (safeDuration > 0) displayPos.toFloat() / safeDuration.toFloat() else 0f
            val density = androidx.compose.ui.platform.LocalDensity.current

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(18.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isDraggingSeekBar = true
                                val width = size.width.toFloat()
                                if (width > 0) {
                                    val fraction = (offset.x / width).coerceIn(0f, 1f)
                                    dragProgressMs = (fraction * safeDuration).toLong()
                                }
                            },
                            onDragEnd = {
                                val targetMs = dragProgressMs
                                MediaItemUtils.seekToPosition(player, targetMs)
                                activity?.lifecycleScope?.launch {
                                    delay(500.milliseconds)
                                    isDraggingSeekBar = false
                                }
                            },
                            onDragCancel = {
                                isDraggingSeekBar = false
                            },
                            onHorizontalDrag = { change, _ ->
                                val width = size.width.toFloat()
                                if (width > 0) {
                                    val fraction = (change.position.x / width).coerceIn(0f, 1f)
                                    dragProgressMs = (fraction * safeDuration).toLong()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.25.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .background(Color.White)
                    )
                }

                // 8dp 纯白圆点滑块
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .offset(x = with(density) { (progressFraction * (context.resources.displayMetrics.widthPixels - 56.dp.toPx())).toDp() })
                        .size(8.dp)
                ) {}
            }

            // 播放时间行 (marginTop = 4dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(displayPos),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                // 播放模式按钮 (24dp)
                val playModeIcon = when {
                    shuffleMode -> R.drawable.ic_shuffle_vector
                    repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_vector
                    else -> R.drawable.ic_repeat_vector
                }

                IconButton(
                    onClick = {
                        val nextRepeat: Int
                        val nextShuffle: Boolean
                        val hint: String

                        when {
                            shuffleMode -> {
                                nextRepeat = Player.REPEAT_MODE_ONE
                                nextShuffle = false
                                hint = context.getString(R.string.repeat_one)
                            }
                            repeatMode == Player.REPEAT_MODE_ONE -> {
                                nextRepeat = Player.REPEAT_MODE_ALL
                                nextShuffle = false
                                hint = context.getString(R.string.repeat_all)
                            }
                            else -> {
                                nextRepeat = Player.REPEAT_MODE_ALL
                                nextShuffle = true
                                hint = context.getString(R.string.shuffle)
                            }
                        }

                        player.repeatMode = nextRepeat
                        player.shuffleModeEnabled = nextShuffle
                        (activity as? HomeActivity)?.updatePlaylistByMode(nextShuffle)

                        modeHintText = hint
                        showModeHint = true
                        activity?.lifecycleScope?.launch {
                            delay(2000.milliseconds)
                            showModeHint = false
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = playModeIcon),
                        contentDescription = "播放模式",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = formatTime(safeDuration),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // 播放控制按钮行 (marginTop = 16dp, marginBottom = 8dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        player.seekToPrevious()
                        player.play()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_previous_vector),
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(
                    onClick = {
                        if (isPlaying) player.pause() else player.play()
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        painter = painterResource(id = if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector),
                        contentDescription = "播放暂停",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(
                    onClick = {
                        player.seekToNext()
                        player.play()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_next_vector),
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // 底部 64dp 音量手势触摸区域 (1/3 屏幕宽度灵敏度手势 + VolumeDotView 动效)
            var dragStartX by remember { mutableFloatStateOf(0f) }
            var dragStartVolume by remember { mutableIntStateOf(0) }

            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 20.dp)
                    .height(64.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        VolumeDotView(ctx).also { volumeDotViewRef = it }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    audioManager?.let { am ->
                                        volumeMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        volumeProgress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        dragStartVolume = volumeProgress
                                        dragStartX = offset.x
                                        isVolumeHintVisible = true
                                    }
                                    volumeDotViewRef?.setTouchPosition(offset.x, offset.y, true)
                                    volumeDotViewRef?.animate()?.alpha(1f)?.setDuration(200)?.start()
                                },
                                onDragEnd = {
                                    isVolumeHintVisible = false
                                    volumeDotViewRef?.setTouchPosition(0f, 0f, false)
                                    volumeDotViewRef?.animate()?.alpha(0f)?.setDuration(200)?.start()
                                },
                                onDragCancel = {
                                    isVolumeHintVisible = false
                                    volumeDotViewRef?.setTouchPosition(0f, 0f, false)
                                    volumeDotViewRef?.animate()?.alpha(0f)?.setDuration(200)?.start()
                                },
                                onHorizontalDrag = { change, _ ->
                                    audioManager?.let { am ->
                                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
                                        val sensitivityWidth = (screenWidth / 3f).coerceAtLeast(100f)
                                        val deltaX = change.position.x - dragStartX
                                        val volumeDelta = (deltaX / sensitivityWidth * max).toInt()
                                        val targetVolume = (dragStartVolume + volumeDelta).coerceIn(0, max)

                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                                        volumeProgress = targetVolume
                                        volumeMax = max
                                    }
                                    volumeDotViewRef?.setTouchPosition(change.position.x, change.position.y, true)
                                }
                            )
                        }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}

// --- Android Studio 1:1 高保真动态背景与效果预览 ---

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerDialogPreviewContent(
    title: String = "老男孩",
    artist: String = "筷子兄弟",
    album: String = "老男孩",
    isPlaying: Boolean = true,
    isFavorite: Boolean = false,
    isTranscoding: Boolean = true,
    codec: String = "AAC",
    bitrate: Int = 256,
    currentPosMs: Long = 0L,
    totalDurationMs: Long = 299000L,
    isLyricsVisible: Boolean = false,
    sampleLyrics: List<LrcLine> = listOf(
        LrcLine(-1, "来源: 网易云音乐"),
        LrcLine(-1, "作词 : 肖央/Udo Jürgens"),
        LrcLine(0, "作曲 : Udo Jürgens"),
        LrcLine(10000, "晚饭后我对她说"),
        LrcLine(20000, "我想要出去买一包香烟"),
        LrcLine(30000, "她叫我回来时轻一点"),
        LrcLine(40000, "不要打扰孩子的睡眠"),
        LrcLine(50000, "我轻轻打开门走出去"),
        LrcLine(60000, "楼道里昏暗灯光亮起"),
    )
) {
    val context = LocalContext.current
    var lyricsVisible by remember { mutableStateOf(isLyricsVisible) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 高斯模糊根据封面渲染背景
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=1920")
                .allowHardware(enable = false)
                .transformations(BlurTransformation(context, 25, 4))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x40000000))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!lyricsVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = 30.dp)
                            .clickable { lyricsVisible = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x10FFFFFF),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.size(310.dp)
                        ) {}

                        Box(
                            modifier = Modifier.size(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.disk),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            Surface(
                                shape = CircleShape,
                                modifier = Modifier.size(200.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Image(
                            painter = painterResource(id = R.drawable.styli2),
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 132.dp, height = 161.dp)
                                .offset(x = 30.dp, y = (-145).dp)
                                .graphicsLayer {
                                    rotationZ = if (isPlaying) -8f else -35f
                                    transformOrigin = TransformOrigin(25f / 132f, 25f / 161f)
                                }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                lyricsVisible = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(sampleLyrics) { idx, line ->
                                val isActive = idx == 3
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = line.text,
                                        fontSize = if (isActive) 18.sp else 15.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .clickable { lyricsVisible = false }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {}

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$artist - $album",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = {},
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = if (isFavorite) R.drawable.ic_heart else R.drawable.ic_heart_border),
                        contentDescription = "收藏",
                        tint = if (isFavorite) Color.Red else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            val qualityText = if (!isTranscoding) "⚠️ 源码播放 $codec ${bitrate}kbps" else "正在转码 $codec ${bitrate}kbps"
            Text(
                text = qualityText,
                fontSize = 11.sp,
                color = if (!isTranscoding) Color.Yellow else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 12.dp)
            )

            // 进度条 (1:1 还原原版半月/竖向胶囊滑块与 2.5dp 细轨道)
            val previewProgressFraction = if (totalDurationMs > 0) currentPosMs.toFloat() / totalDurationMs.toFloat() else 0f
            val densityPreview = androidx.compose.ui.platform.LocalDensity.current

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.25.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(previewProgressFraction)
                            .background(Color.White)
                    )
                }

                // 8dp 纯白圆点滑块
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .offset(x = with(densityPreview) { (previewProgressFraction * (390.dp.toPx() - 56.dp.toPx())).toDp() })
                        .size(8.dp)
                ) {}
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentPosMs),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_repeat_vector),
                        contentDescription = "播放模式",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = formatTime(totalDurationMs),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_previous_vector),
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(onClick = {}, modifier = Modifier.size(72.dp)) {
                    Icon(
                        painter = painterResource(id = if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector),
                        contentDescription = "播放暂停",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_next_vector),
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(
    name = "唱片主界面 1:1 预览",
    showBackground = true,
    backgroundColor = 0xFF000000,
    widthDp = 390,
    heightDp = 844
)
@Composable
fun PlayerDialogDiscPreview() {
    PlayerDialogPreviewContent(isLyricsVisible = false)
}

@Preview(
    name = "歌词动态界面 1:1 预览",
    showBackground = true,
    backgroundColor = 0xFF000000,
    widthDp = 390,
    heightDp = 844
)
@Composable
fun PlayerDialogLyricsPreview() {
    PlayerDialogPreviewContent(isLyricsVisible = true)
}
