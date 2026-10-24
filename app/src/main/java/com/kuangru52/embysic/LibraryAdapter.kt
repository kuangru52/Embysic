@file:Suppress("UnstableApiUsage", "OPT_IN_IS_NOT_ENABLED")

package com.kuangru52.embysic

import kotlin.OptIn
import android.graphics.Bitmap
import android.util.LruCache
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.recyclerview.widget.DiffUtil
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.compose.AsyncImage

@UnstableApi
class LibraryAdapter(
    private val onItemClick: (EmbyItem) -> Unit,
    private val onItemDoubleClick: ((EmbyItem) -> Unit)? = null,
    private val onItemDelete: ((EmbyItem) -> Unit)? = null,
    var backdropProvider: (() -> com.kyant.backdrop.backdrops.LayerBackdrop?)? = null,
    private val onSelectionModeChanged: ((Boolean) -> Unit)? = null,
) : ListAdapter<EmbyItem, LibraryAdapter.ViewHolder>(DiffCallback()) {

    var items: List<EmbyItem> = emptyList()
        private set

    private var currentMediaId: String? = null
    private var currentAlbumId: String? = null
    private var currentPlayingPath: String? = null
    private var expandedDeletePosition: Int = -1
    
    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<String>()

    private var serverUrl: String = ""
    private var accessToken: String = ""

    private val bitmapCache = LruCache<String, Bitmap>(100)
    private var lastClickTime: Long = 0

    fun setCurrentMediaId(mediaId: String?, albumId: String? = null, path: String? = null) {
        if ((currentMediaId != mediaId) || (currentAlbumId != albumId) || (currentPlayingPath != path)) {
            currentMediaId = mediaId
            currentAlbumId = albumId
            currentPlayingPath = path
            notifyDataSetChanged()
        }
    }

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) selectedIds.clear()
            onSelectionModeChanged?.invoke(enabled)
            notifyDataSetChanged()
        }
    }

    fun getSelectedItems() = currentList.filter { (it.Id in selectedIds) && (it.Id != "BACK_FOLDER") }

    fun clearExpandedDelete(): Boolean {
        if (expandedDeletePosition != -1) {
            val prev = expandedDeletePosition
            expandedDeletePosition = -1
            notifyItemChanged(prev)
            return true
        }
        return false
    }

    fun toggleSelection(itemId: String) {
        if (selectedIds.contains(itemId)) {
            selectedIds.remove(itemId)
        } else {
            selectedIds.add(itemId)
        }
        notifyDataSetChanged()
    }

    override fun submitList(newItems: List<EmbyItem>?) {
        this.items = newItems ?: emptyList()
        super.submitList(newItems)
    }

    @Suppress("UNUSED_PARAMETER")
    fun submitList(newItems: List<EmbyItem>, context: android.content.Context?) {
        submitList(newItems)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val context = recyclerView.context
        val prefs = context.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", "") ?: ""
        accessToken = prefs.getString("access_token", "") ?: ""
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val composeView = ComposeView(parent.context)
        return ViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentPos = holder.bindingAdapterPosition
        if (currentPos == RecyclerView.NO_POSITION) return
        val item = getItem(currentPos)

        val itemBaseId = item.Id.split("_").firstOrNull() ?: item.Id
        val cleanMediaId = currentMediaId?.split("_")?.firstOrNull() ?: currentMediaId
        val cleanAlbumId = currentAlbumId?.split("_")?.firstOrNull() ?: currentAlbumId

        // 判定 1：当前条目为正在播放的曲目
        val isPlayingTrack = (cleanMediaId != null) && ((item.Id == cleanMediaId) || (itemBaseId == cleanMediaId))
        
        // 判定 2：当前条目为包含正在播放曲目的文件夹
        val isPlayingFolder = item.IsFolder && (
            ((cleanAlbumId != null) && ((item.Id == cleanAlbumId) || (itemBaseId == cleanAlbumId))) ||
            ((!item.Path.isNullOrEmpty()) && (currentPlayingPath?.startsWith(item.Path) == true)) ||
            ((currentPlayingPath?.contains(item.Name, ignoreCase = true)) == true)
        )
        val isPlayingThis = isPlayingTrack || isPlayingFolder
        val isSelected = selectedIds.contains(item.Id)
        val showDelete = expandedDeletePosition == currentPos

        val hasPrimary = item.ImageTags?.containsKey("Primary") == true
        val imageId = when {
            hasPrimary -> item.Id
            !item.AlbumId.isNullOrEmpty() -> item.AlbumId
            !item.ParentId.isNullOrEmpty() -> item.ParentId
            else -> item.Id
        }
        val serverImageUrl = "${serverUrl.trimEnd('/')}/emby/Items/$imageId/Images/Primary?MaxWidth=300&api_key=$accessToken"

        val activeBackdrop = backdropProvider?.invoke()

        (holder.itemView as ComposeView).setContent {
            LibraryItemRow(
                item = item,
                isPlayingThis = isPlayingThis,
                isSelected = isSelected,
                showDelete = showDelete,
                imageUrl = serverImageUrl,
                cachedBitmap = bitmapCache[item.Id],
                backdrop = activeBackdrop,
                onClick = {
                    if (item.Id == "BACK_FOLDER") return@LibraryItemRow
                    if (isSelectionMode) {
                        toggleSelection(item.Id)
                        return@LibraryItemRow
                    }
                    if (expandedDeletePosition != -1) {
                        val prev = expandedDeletePosition
                        expandedDeletePosition = -1
                        notifyItemChanged(prev)
                        return@LibraryItemRow
                    }

                    val currentTime = System.currentTimeMillis()
                    if ((currentTime - lastClickTime) < 300) {
                        onItemDoubleClick?.invoke(item)
                    } else {
                        onItemClick(item)
                    }
                    lastClickTime = currentTime
                },
                onLongClick = {
                    if (item.Id == "BACK_FOLDER" || onItemDelete == null) return@LibraryItemRow
                    val prev = expandedDeletePosition
                    expandedDeletePosition = currentPos
                    if (prev != -1) notifyItemChanged(prev)
                    notifyItemChanged(expandedDeletePosition)
                },
                onDeleteClick = {
                    expandedDeletePosition = -1
                    notifyItemChanged(currentPos)
                    onItemDelete?.invoke(item)
                },
            )
        }
    }

    class ViewHolder(view: ComposeView) : RecyclerView.ViewHolder(view)

    class DiffCallback : DiffUtil.ItemCallback<EmbyItem>() {
        override fun areItemsTheSame(oldItem: EmbyItem, newItem: EmbyItem): Boolean = oldItem.Id == newItem.Id
        override fun areContentsTheSame(oldItem: EmbyItem, newItem: EmbyItem): Boolean {
            return (oldItem.Id == newItem.Id) &&
                    (oldItem.Name == newItem.Name) &&
                    (oldItem.UserData?.IsFavorite == newItem.UserData?.IsFavorite) &&
                    (oldItem.UserData?.PlaybackPositionTicks == newItem.UserData?.PlaybackPositionTicks)
        }
    }
}

private fun getQualityTag(item: EmbyItem): String? {
    val source = item.MediaSources?.firstOrNull() ?: return null
    val stream = source.MediaStreams?.firstOrNull { (it.Codec != null) && (it.Codec.lowercase() != "mjpeg") && (it.Codec.lowercase() != "png") }
    val codec = stream?.Codec?.uppercase() ?: source.Container?.uppercase() ?: return null
    val sampleRate = stream?.SampleRate ?: 0
    val bitRate = stream?.BitRate ?: source.Bitrate ?: 0

    return when (codec) {
        "FLAC" -> if (sampleRate >= 88200 || bitRate > 1500000) "Hi-Res 24bit" else if (sampleRate >= 48000) "FLAC 24bit" else "FLAC"
        "ALAC", "DSD", "WAV" -> "Hi-Res"
        "AAC", "MP3" -> if (bitRate >= 320000) "320K" else codec
        else -> codec
    }
}

@UnstableApi
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryItemRow(
    item: EmbyItem,
    isPlayingThis: Boolean,
    isSelected: Boolean,
    showDelete: Boolean,
    imageUrl: String,
    cachedBitmap: Bitmap?,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTabletLandscape = (configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE 
            && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = androidx.compose.ui.platform.LocalContext.current
    val homeActivity = remember(context) { context.findHomeActivity() }
    val isDark = if (isTabletLandscape) true else (homeActivity?.isAppDark() ?: androidx.compose.foundation.isSystemInDarkTheme())

    val isParentFolder = item.Id == "BACK_FOLDER"
    val isFolderLike = (item.IsFolder) || (item.Type == "CollectionFolder") || (item.Type == "MusicArtist") || (item.Type == "MusicAlbum") || (isParentFolder)

    val activeBackdrop = backdrop ?: homeActivity?.wallpaperBackdrop ?: homeActivity?.homeBackdrop

    val cardCornerRadius = 12f
    val searchBlurRadius = homeActivity?.searchBlurRadius ?: 18f
    val searchRefractionAmount = homeActivity?.searchRefractionAmount ?: -60f
    val searchRefractionHeight = homeActivity?.searchRefractionHeight ?: 12f
    val searchBleedAmount = homeActivity?.searchBleedAmount ?: 10f
    val searchBleedOpacity = homeActivity?.searchBleedOpacity ?: 0.3f
    val searchChromaMultiplier = homeActivity?.searchChromaMultiplier ?: 1.5f
    val searchContrast = homeActivity?.searchContrast ?: 0.15f
    val searchWhitePoint = homeActivity?.searchWhitePoint ?: 0.20f

    val glassBgColor = when {
        isParentFolder -> Color(0x33EE6C4D)
        isSelected || isPlayingThis -> Color(0x33FFA000)
        else -> if (isDark) Color(0x281E293B) else Color(0x33FFFFFF)
    }

    val textColor = when {
        isParentFolder -> if (isDark) Color(0xB0FFFFFF) else Color(0xFF0F172A)
        isPlayingThis -> Color(0xFFFFA000)
        isDark -> Color.White
        else -> Color(0xFF0F172A)
    }

    val subTextColor = when {
        isParentFolder -> if (isDark) Color(0xB0FFFFFF) else Color(0xFF475569)
        isPlayingThis -> Color(0xFFFFA000)
        isDark -> Color(0xB0FFFFFF)
        else -> Color(0xFF475569)
    }

    val durationColor = when {
        isPlayingThis -> Color(0xFFFFA000)
        isDark -> Color(0xB0FFFFFF)
        else -> Color(0xFF475569)
    }

    val qualityTag = if (!isFolderLike) getQualityTag(item) else null
    val isFavorite = item.UserData?.IsFavorite == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.5.dp)
            .clip(RoundedCornerShape(cardCornerRadius.dp))
            .then(
                if (activeBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = activeBackdrop,
                        shape = { RoundedCornerShape(cardCornerRadius.dp) },
                        effects = {
                            listOf(
                                vibrancy(),
                                blur(radius = searchBlurRadius),
                                lens(
                                    refractionAmount = kotlin.math.abs(searchRefractionAmount),
                                    refractionHeight = searchRefractionHeight.coerceAtMost(cardCornerRadius),
                                ),
                            )
                        },
                    )
                } else {
                    Modifier.background(glassBgColor, RoundedCornerShape(cardCornerRadius.dp))
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if ((size.width <= 1f) || (size.height <= 1f)) return@Canvas

            val strokeWidthPx = (1.2f + (searchWhitePoint * 0.8f)).dp.toPx()
            val cornerPx = cardCornerRadius.dp.toPx()
            val corner = CornerRadius(cornerPx, cornerPx)

            // 仅在选中、正在播放或上级文件夹状态下，在 Canvas 中叠加轻微状态着色，普通卡片保持与 SearchBar 100% 一致的纯净无遮挡液态透镜折射与高斯模糊
            if (isParentFolder || isSelected || isPlayingThis) {
                val statusTint = when {
                    isParentFolder -> Color(0x22EE6C4D)
                    else -> Color(0x22FFA000)
                }
                drawRoundRect(
                    color = statusTint,
                    cornerRadius = corner,
                )
            }

            if ((searchBleedAmount > 0.1f) && (searchBleedOpacity > 0.01f)) {
                val bleedAlpha = (searchBleedOpacity * 0.35f).coerceIn(0.02f, 0.5f)
                val bleedColor = when {
                    isParentFolder -> Color(0xFFEE6C4D).copy(alpha = bleedAlpha)
                    isSelected || isPlayingThis -> Color(0xFFFFA000).copy(alpha = bleedAlpha)
                    isDark -> Color(0xFF38BDF8).copy(alpha = bleedAlpha)
                    else -> Color.White.copy(alpha = bleedAlpha)
                }
                drawRoundRect(
                    color = bleedColor,
                    cornerRadius = corner,
                )
            }

            val rimAlpha = (0.25f + (searchChromaMultiplier * 0.1f) + (searchContrast * 0.15f)).coerceIn(0.15f, 0.95f)
            val rimColor = when {
                isParentFolder -> Color(0xFFEE6C4D).copy(alpha = rimAlpha)
                isSelected || isPlayingThis -> Color(0xFFFFA000).copy(alpha = rimAlpha)
                isDark -> Color.White.copy(alpha = rimAlpha)
                else -> Color.Gray.copy(alpha = rimAlpha)
            }

            drawRoundRect(
                color = rimColor,
                cornerRadius = corner,
                style = Stroke(width = strokeWidthPx),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 音轨号（优先 IndexNumber，其次从文件名/标题前缀数字解析；若无音轨号则留空 28.dp 占位保持列表封面完美整齐对齐）
            if (!isParentFolder && !isFolderLike) {
                val trackNum = item.IndexNumber ?: run {
                    val nameToMatch = item.FileName ?: item.Filename ?: item.Name
                    val match = Regex("""^(\d{1,3})[\s._\-]""").find(nameToMatch.trim())
                    match?.groupValues?.get(1)?.toIntOrNull()
                }
                val trackText = if (trackNum != null && trackNum > 0) trackNum.toString() else ""
                Text(
                    text = trackText,
                    fontSize = 14.sp,
                    color = subTextColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp)
                )
            }

            // 封面 / 图标
            if (cachedBitmap != null) {
                Image(
                    bitmap = cachedBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (isFolderLike) {
                val folderFilter = when {
                    isParentFolder -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFEE6C4D))
                    isPlayingThis -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFFFA000))
                    else -> null
                }
                Image(
                    painter = painterResource(id = R.drawable.folder),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = folderFilter
                )
            } else {
                val context = androidx.compose.ui.platform.LocalContext.current
                val localCover = remember(item.Id) { java.io.File(context.cacheDir, "covers/${item.Id}.jpg") }
                val imageModel = remember(item.Id, imageUrl) {
                    coil.request.ImageRequest.Builder(context)
                        .data(if (localCover.exists()) localCover else imageUrl)
                        .crossfade(enable = true)
                        .placeholder(R.drawable.logo)
                        .error(R.drawable.logo)
                        .build()
                }

                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 歌名与歌手 · 专辑
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.Name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1
                )

                val artistText = item.Artists?.joinToString(", ") ?: item.AlbumArtist ?: ""
                val albumText = item.Album ?: ""
                val subText = when {
                    artistText.isNotEmpty() && albumText.isNotEmpty() -> "$artistText · $albumText"
                    artistText.isNotEmpty() -> artistText
                    albumText.isNotEmpty() -> albumText
                    else -> ""
                }

                if (subText.isNotEmpty() && !isFolderLike) {
                    Text(
                        text = subText,
                        fontSize = 12.sp,
                        color = subTextColor,
                        maxLines = 1
                    )
                }
            }

            // 右侧两行容器：上行显示音质标签与红心收藏，下行显示歌曲时长
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                // 上行：音质标签 (Hi-Res / FLAC) 与红心图标
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!qualityTag.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x33FFC107),
                        ) {
                            Text(
                                text = qualityTag,
                                fontSize = 10.sp,
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isFavorite && !isFolderLike) {
                        if (!qualityTag.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        androidx.compose.material3.Icon(
                            painter = painterResource(id = R.drawable.ic_heart),
                            contentDescription = "已收藏",
                            tint = Color(0xFFFF4081),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 下行：歌曲时长 (例如 3:45 / 5:40)
                if (!isFolderLike && item.RunTimeTicks != null && item.RunTimeTicks > 0) {
                    val totalSeconds = item.RunTimeTicks / 10000000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds),
                        fontSize = 11.sp,
                        color = durationColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 删除按钮
            if (showDelete) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = onDeleteClick,
                    color = Color(0x22FF5252),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FF5252)),
                ) {
                    Text(
                        text = "删除",
                        fontSize = 13.sp,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@UnstableApi
@androidx.compose.ui.tooling.preview.Preview(name = "Library Item Row Light Preview", showBackground = true)
@Composable
fun LibraryItemRowLightPreview() {
    androidx.compose.material3.MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x4DEBF0F5))
                .padding(16.dp),
        ) {
            LibraryItemRow(
                item = EmbyItem(
                    Id = "1",
                    Name = "晴天",
                    Type = "Audio",
                    IsFolder = false,
                    Artists = listOf("周杰伦"),
                    Album = "叶惠美",
                    RunTimeTicks = 2690000000L,
                    UserData = UserData(IsFavorite = true)
                ),
                isPlayingThis = false,
                isSelected = false,
                showDelete = false,
                imageUrl = "",
                cachedBitmap = null,
                onClick = {},
                onLongClick = {},
                onDeleteClick = {}
            )
        }
    }
}

@UnstableApi
@androidx.compose.ui.tooling.preview.Preview(name = "Library Item Row Dark Preview", showBackground = true)
@Composable
fun LibraryItemRowDarkPreview() {
    androidx.compose.material3.MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E2C))
                .padding(16.dp)
        ) {
            LibraryItemRow(
                item = EmbyItem(
                    Id = "2",
                    Name = "稻香",
                    Type = "Audio",
                    IsFolder = false,
                    Artists = listOf("周杰伦"),
                    Album = "魔杰座",
                    RunTimeTicks = 2230000000L,
                    UserData = UserData(IsFavorite = true)
                ),
                isPlayingThis = true,
                isSelected = false,
                showDelete = false,
                imageUrl = "",
                cachedBitmap = null,
                onClick = {},
                onLongClick = {},
                onDeleteClick = {},
            )
        }
    }
}
