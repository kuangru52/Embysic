package com.kuangru52.embysic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryAdapter(
    private val onItemClick: (EmbyItem) -> Unit,
    private val onItemDoubleClick: ((EmbyItem) -> Unit)? = null,
    private val onItemDelete: ((EmbyItem) -> Unit)? = null,
    private val onSelectionModeChanged: ((Boolean) -> Unit)? = null
) : ListAdapter<EmbyItem, LibraryAdapter.ViewHolder>(DiffCallback()) {

    var items: List<EmbyItem> = emptyList()
        private set

    private var currentMediaId: String? = null
    private var currentAlbumId: String? = null
    private var currentPlayingPath: String? = null
    private var expandedDeletePosition: Int = -1
    
    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<String>()

    companion object {
        private const val COLOR_WHITE = android.graphics.Color.WHITE
        private const val COLOR_SECONDARY_WHITE = -0x4f000001 // #B0FFFFFF
        private const val COLOR_BLACK = android.graphics.Color.BLACK
        private val COLOR_SECONDARY_BLACK = android.graphics.Color.parseColor("#E6000000")
        private const val HIGHLIGHT_COLOR = -0x6000 // #FFA000
    }

    private var serverUrl: String = ""
    private var accessToken: String = ""

    private val neteasePrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        notifyDataSetChanged()
    }

    private val folderHighlightMatrix = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix(floatArrayOf(
            0f,   0f,   1.0f, 0f, 0f, 0.2f, 0.2f, 0.6f, 0f, 0f, 0.5f, 0.5f, 0f,   0f, 0f, 0f,   0f,   0f,   1f, 0f
        ))
    )

    private val parentFolderMatrix = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix(floatArrayOf(
            0.8f, 0f,   0f,   0f, 0f, 0f,   0.6f, 0f,   0f, 0f, 0f,   0f,   0.2f, 0f, 0f, 0f,   0f,   0f,   1f, 0f
        ))
    )

    fun setCurrentMediaId(mediaId: String?, albumId: String? = null, path: String? = null) {
        if (currentMediaId != mediaId || currentAlbumId != albumId || currentPlayingPath != path) {
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

    fun isSelectionMode() = isSelectionMode
    fun getSelectedItems() = currentList.filter { it.Id in selectedIds && it.Id != "BACK_FOLDER" }

    fun toggleSelection(itemId: String) {
        if (selectedIds.contains(itemId)) {
            selectedIds.remove(itemId)
        } else {
            selectedIds.add(itemId)
        }
        notifyDataSetChanged()
    }

    private val bitmapCache = LruCache<String, Bitmap>(100)
    private var lastClickTime: Long = 0

    private var isDarkForce = false
    private var isSystemDarkMode = true

    override fun submitList(newItems: List<EmbyItem>?) {
        this.items = newItems ?: emptyList()
        super.submitList(newItems)
    }

    fun submitList(newItems: List<EmbyItem>, context: android.content.Context?) {
        isDarkForce = (context as? HomeActivity)?.isDarkForce() ?: false
        context?.let {
            val mode = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            isSystemDarkMode = mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        submitList(newItems)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val context = recyclerView.context
        val mode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        isSystemDarkMode = mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        val prefs = context.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", "") ?: ""
        accessToken = prefs.getString("access_token", "") ?: ""

        val neteasePrefs = context.getSharedPreferences("netease_covers", android.content.Context.MODE_PRIVATE)
        neteasePrefs.registerOnSharedPreferenceChangeListener(neteasePrefsListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        val prefs = recyclerView.context.getSharedPreferences("netease_covers", android.content.Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(neteasePrefsListener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_library, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        private val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        private val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        private val itemContainer: View = view.findViewById(R.id.itemContainer)
        private val btnDelete: TextView = view.findViewById(R.id.btnDelete)

        private val itemViewContext = view.context

        fun bind(item: EmbyItem) {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return

            val currentContext = itemView.context
            val mode = currentContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            isSystemDarkMode = mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            tvName.text = item.Name
            val isParentFolder = item.Id == "BACK_FOLDER"
            
            val isDark = isSystemDarkMode || isDarkForce
            val currentPrimary = if (isDark) COLOR_WHITE else COLOR_BLACK
            val currentSecondary = if (isDark) COLOR_SECONDARY_WHITE else COLOR_SECONDARY_BLACK
            
            tvName.setTextColor(currentPrimary)
            tvArtist.setTextColor(currentSecondary)
            tvDuration.setTextColor(currentPrimary)
            tvIndex.setTextColor(currentSecondary)

            if (!isParentFolder && onItemDelete != null && expandedDeletePosition == position) {
                btnDelete.visibility = View.VISIBLE
            } else {
                btnDelete.visibility = View.GONE
            }

            btnDelete.setOnClickListener {
                expandedDeletePosition = -1
                notifyItemChanged(position)
                onItemDelete?.invoke(item)
            }
            if (!item.IsFolder && item.RunTimeTicks != null && item.RunTimeTicks!! > 0) {
                val totalSeconds = item.RunTimeTicks!! / 10000000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                tvDuration.text = String.format("%d:%02d", minutes, seconds)
                tvDuration.visibility = View.VISIBLE
            } else {
                tvDuration.visibility = View.GONE
            }
            
            ivIcon.dispose() 
            ivIcon.clearColorFilter()
            ImageViewCompat.setImageTintList(ivIcon, null)

            val isFolderLike = item.IsFolder || item.Type == "CollectionFolder" || item.Type == "MusicArtist" || item.Type == "MusicAlbum" || isParentFolder
            val isPlayingThis = item.Id == currentMediaId
            val isSelected = selectedIds.contains(item.Id)
            
            var isCurrentAlbumOrParent = false
            if (isFolderLike && currentPlayingPath != null && item.Path != null) {
                isCurrentAlbumOrParent = currentPlayingPath!!.contains(item.Path!!)
            }

            if (isParentFolder) {
                itemContainer.setBackgroundResource(R.drawable.bg_parent_folder_card)
                tvIndex.visibility = View.GONE
                tvName.setTextColor(currentSecondary)
                tvName.text = item.Name
                tvArtist.visibility = View.GONE
                ivIcon.colorFilter = parentFolderMatrix
                itemView.isClickable = false
                itemView.isFocusable = false
                itemView.foreground = null
            } else {
                if (isSelectionMode && isSelected) {
                    itemContainer.setBackgroundResource(R.drawable.bg_playing_card)
                } else if (isPlayingThis || isCurrentAlbumOrParent) {
                    itemContainer.setBackgroundResource(R.drawable.bg_playing_card)
                } else {
                    itemContainer.setBackgroundResource(R.drawable.bg_compact_card)
                }

                itemView.isClickable = false
                itemView.isFocusable = false
                itemContainer.isClickable = true
                itemContainer.isFocusable = true

                if (isPlayingThis) {
                    tvIndex.visibility = View.VISIBLE
                    tvIndex.background = null
                    tvIndex.text = if (item.IndexNumber != null) String.format("%02d", item.IndexNumber) else ""
                    tvIndex.setTextColor(HIGHLIGHT_COLOR)
                    tvName.setTextColor(HIGHLIGHT_COLOR)
                    tvArtist.setTextColor(HIGHLIGHT_COLOR)
                    tvDuration.setTextColor(HIGHLIGHT_COLOR)
                    tvDuration.alpha = 1.0f
                } else if (isCurrentAlbumOrParent) {
                    tvIndex.visibility = View.GONE 
                    tvName.setTextColor(HIGHLIGHT_COLOR)
                    tvArtist.setTextColor(HIGHLIGHT_COLOR)
                    tvDuration.setTextColor(HIGHLIGHT_COLOR)
                    tvDuration.alpha = 1.0f
                    ivIcon.colorFilter = folderHighlightMatrix
                } else if (isFolderLike) {
                    tvIndex.visibility = View.GONE
                    tvName.setTextColor(currentPrimary)
                    tvArtist.setTextColor(currentSecondary)
                    tvDuration.setTextColor(currentPrimary)
                    tvDuration.alpha = 1.0f
                } else {
                    tvIndex.visibility = View.VISIBLE
                    tvIndex.background = null
                    tvIndex.text = if (item.IndexNumber != null) String.format("%02d", item.IndexNumber) else ""
                    tvIndex.setTextColor(currentSecondary)
                    tvName.setTextColor(currentPrimary)
                    tvArtist.setTextColor(currentSecondary)
                    tvDuration.setTextColor(currentSecondary)
                    tvDuration.alpha = 1.0f
                }
            }

            ivIcon.background = null
            ivIcon.clipToOutline = true

            if (isFolderLike) {
                ivIcon.tag = "IS_FOLDER"
                ivIcon.setImageResource(R.drawable.folder)
                ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                ivIcon.background = null
                
                if (!isParentFolder) {
                    tvArtist.visibility = if (item.Type == "MusicAlbum") View.VISIBLE else View.GONE
                    if (item.Type == "MusicAlbum") {
                        val artists = item.Artists?.joinToString(", ") ?: "未知艺术家"
                        tvArtist.text = artists
                        tvArtist.setTextColor(currentSecondary)
                    }
                }
            } else {
                tvArtist.visibility = View.VISIBLE
                val artists = item.Artists?.joinToString(", ") ?: "未知艺术家"
                val album = item.Album ?: ""
                tvArtist.text = if (album.isNotEmpty()) "$artists - $album" else artists
                
                when {
                    isPlayingThis -> tvArtist.setTextColor(HIGHLIGHT_COLOR)
                    isCurrentAlbumOrParent -> tvArtist.setTextColor(HIGHLIGHT_COLOR)
                    else -> tvArtist.setTextColor(currentSecondary)
                }
                ivIcon.tag = item.Id
                ivIcon.setBackgroundResource(R.drawable.bg_rounded_icon)
                ivIcon.clipToOutline = true

                val cachedBitmap = bitmapCache.get(item.Id)
                if (cachedBitmap != null) {
                    ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                    ivIcon.setPadding(0, 0, 0, 0)
                    ivIcon.setImageBitmap(cachedBitmap)
                } else {
                    val neteaseCovers = itemView.context.getSharedPreferences("netease_covers", AppCompatActivity.MODE_PRIVATE)
                    val cachedUrl = neteaseCovers.getString(item.Id, null)
                    
                    val hasPrimary = item.ImageTags?.containsKey("Primary") == true
                    val imageId = if (hasPrimary) item.Id else (item.AlbumId ?: item.Id)
                    val serverImageUrl = "${serverUrl.trimEnd('/')}/emby/Items/$imageId/Images/Primary?MaxWidth=200&api_key=$accessToken"
                    
                    val finalImageUrl = if (hasPrimary || item.AlbumId != null) {
                        serverImageUrl
                    } else if (cachedUrl != null) {
                        if (cachedUrl.startsWith("http")) {
                            val cleanUrl = if (cachedUrl.contains("?")) cachedUrl.substringBefore("?") else cachedUrl
                            "$cleanUrl?param=200y200"
                        } else cachedUrl
                    } else {
                        serverImageUrl
                    }

                    ivIcon.load(finalImageUrl) {
                        placeholder(R.drawable.logo)
                        error(R.drawable.logo)
                        crossfade(true)
                        listener(
                            onStart = {
                                ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                                val p = (6 * itemViewContext.resources.displayMetrics.density).toInt()
                                ivIcon.setPadding(p, p, p, p)
                            },
                            onSuccess = { _, _ -> 
                                ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                                ivIcon.setPadding(0, 0, 0, 0)
                            },
                            onError = { _, _ ->
                                ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                                val p = (6 * itemViewContext.resources.displayMetrics.density).toInt()
                                ivIcon.setPadding(p, p, p, p)
                                
                                if (finalImageUrl == serverImageUrl && !hasPrimary && cachedUrl != null) {
                                    ivIcon.load(cachedUrl) { crossfade(true) }
                                } else if (finalImageUrl == serverImageUrl && !hasPrimary) {
                                    loadCoverFromTags(item, serverUrl, accessToken)
                                }
                            }
                        )
                    }
                }
            }
            itemContainer.setOnClickListener {
                if (item.Id == "BACK_FOLDER") return@setOnClickListener
                
                if (isSelectionMode) {
                    toggleSelection(item.Id)
                    return@setOnClickListener
                }

                if (expandedDeletePosition != -1) {
                    val prev = expandedDeletePosition
                    expandedDeletePosition = -1
                    notifyItemChanged(prev)
                    return@setOnClickListener
                }

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 300) {
                    onItemDoubleClick?.invoke(item)
                } else {
                    onItemClick(item)
                }
                lastClickTime = currentTime
            }

            itemContainer.setOnLongClickListener {
                if (item.Id == "BACK_FOLDER" || onItemDelete == null) return@setOnLongClickListener true
                
                val prev = expandedDeletePosition
                expandedDeletePosition = position
                if (prev != -1) notifyItemChanged(prev)
                notifyItemChanged(expandedDeletePosition)
                true
            }
        }

        private fun loadCoverFromTags(item: EmbyItem, serverUrl: String, accessToken: String) {
            val streamUrl = "${serverUrl.trimEnd('/')}/emby/Audio/${item.Id}/stream?static=true"
            val scope = (itemView.context as? AppCompatActivity)?.lifecycleScope ?: return
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(streamUrl, mapOf("X-Emby-Token" to accessToken))
                        retriever.embeddedPicture?.let {
                            BitmapFactory.decodeByteArray(it, 0, it.size)?.also { b -> 
                                bitmapCache.put(item.Id, b)
                                MediaItemUtils.saveCoverToFile(itemView.context, item.Id, b)
                            }
                        }
                    } catch (e: Exception) { null } finally { retriever.release() }
                }
                if (ivIcon.tag == item.Id && bitmap != null) ivIcon.setImageBitmap(bitmap)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<EmbyItem>() {
        override fun areItemsTheSame(oldItem: EmbyItem, newItem: EmbyItem): Boolean = oldItem.Id == newItem.Id
        override fun areContentsTheSame(oldItem: EmbyItem, newItem: EmbyItem): Boolean {
            return oldItem.Id == newItem.Id &&
                    oldItem.Name == newItem.Name &&
                    oldItem.UserData?.IsFavorite == newItem.UserData?.IsFavorite &&
                    oldItem.UserData?.PlaybackPositionTicks == newItem.UserData?.PlaybackPositionTicks
        }
    }
}
