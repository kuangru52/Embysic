package com.kuangru52.embysic

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.compose.AsyncImage
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@androidx.media3.common.util.UnstableApi
class ArtistAdapter(
    var backdropProvider: (() -> com.kyant.backdrop.backdrops.LayerBackdrop?)? = null,
    private val onItemClick: (EmbyItem) -> Unit,
) : ListAdapter<EmbyItem, ArtistAdapter.ViewHolder>(DiffCallback()) {

    private var serverUrl: String = ""
    private var accessToken: String = ""

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val prefs = recyclerView.context.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", "") ?: ""
        accessToken = prefs.getString("access_token", "") ?: ""
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val composeView = ComposeView(parent.context)
        return ViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val imageUrl = "${baseUrl}emby/Items/${item.Id}/Images/Primary?api_key=$accessToken"
        val activeBackdrop = backdropProvider?.invoke()

        (holder.itemView as ComposeView).setContent {
            ArtistItemRow(
                item = item,
                imageUrl = imageUrl,
                backdrop = activeBackdrop,
                onClick = { onItemClick(item) },
            )
        }
    }

    class ViewHolder(view: ComposeView) : RecyclerView.ViewHolder(view)

    class DiffCallback : DiffUtil.ItemCallback<EmbyItem>() {
        override fun areItemsTheSame(oldItem: EmbyItem, newItem: EmbyItem): Boolean = oldItem.Id == newItem.Id
        override fun areContentsTheSame(oldItem: EmbyItem, newItem: EmbyItem): Boolean = oldItem == newItem
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun ArtistItemRow(
    item: EmbyItem,
    imageUrl: String,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    onClick: () -> Unit,
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTabletLandscape = ((configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE)
            && (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    val context = LocalContext.current
    val homeActivity = remember(context) { context.findHomeActivity() }
    val isDark = if (isTabletLandscape) true else (homeActivity?.isAppDark() ?: androidx.compose.foundation.isSystemInDarkTheme())

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

    val glassBgColor = if (isDark) Color(0x281E293B) else Color(0x33FFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF1E1E2C)

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if ((size.width <= 1f) || (size.height <= 1f)) return@Canvas

            val strokeWidthPx = (1.2f + (searchWhitePoint * 0.8f)).dp.toPx()
            val cornerPx = cardCornerRadius.dp.toPx()
            val corner = CornerRadius(cornerPx, cornerPx)



            if ((searchBleedAmount > 0.1f) && (searchBleedOpacity > 0.01f)) {
                val bleedAlpha = (searchBleedOpacity * 0.35f).coerceIn(0.02f, 0.5f)
                val bleedColor = if (isDark) Color(0xFF38BDF8).copy(alpha = bleedAlpha) else Color.White.copy(alpha = bleedAlpha)
                drawRoundRect(
                    color = bleedColor,
                    cornerRadius = corner,
                )
            }

            val rimAlpha = (0.25f + (searchChromaMultiplier * 0.1f) + (searchContrast * 0.15f)).coerceIn(0.15f, 0.95f)
            val rimColor = if (isDark) Color.White.copy(alpha = rimAlpha) else Color.Gray.copy(alpha = rimAlpha)

            drawRoundRect(
                color = rimColor,
                cornerRadius = corner,
                style = Stroke(width = strokeWidthPx),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.logo),
                error = painterResource(id = R.drawable.logo)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = item.Name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@androidx.media3.common.util.UnstableApi
@androidx.compose.ui.tooling.preview.Preview(name = "Artist Item Row Preview", showBackground = true)
@Composable
fun ArtistItemRowPreview() {
    androidx.compose.material3.MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E2C))
                .padding(16.dp)
        ) {
            ArtistItemRow(
                item = EmbyItem(
                    Id = "artist_1",
                    Name = "周杰伦",
                    Type = "MusicArtist",
                    IsFolder = true
                ),
                imageUrl = "",
                onClick = {}
            )
        }
    }
}
