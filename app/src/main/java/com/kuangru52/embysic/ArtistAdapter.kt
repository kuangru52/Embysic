package com.kuangru52.embysic

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.compose.AsyncImage

class ArtistAdapter(private val onItemClick: (EmbyItem) -> Unit) :
    ListAdapter<EmbyItem, ArtistAdapter.ViewHolder>(DiffCallback()) {

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

        (holder.itemView as ComposeView).setContent {
            ArtistItemRow(
                item = item,
                imageUrl = imageUrl,
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

@Composable
fun ArtistItemRow(
    item: EmbyItem,
    imageUrl: String,
    onClick: () -> Unit,
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTabletLandscape = ((configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE)
            && (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    val isDark = if (isTabletLandscape) true else androidx.compose.foundation.isSystemInDarkTheme()

    val bgColor = if (isDark) Color(0x3328283F) else Color(0xD9EBF0F5)
    val textColor = if (isDark) Color.White else Color(0xFF1E1E2C)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
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
