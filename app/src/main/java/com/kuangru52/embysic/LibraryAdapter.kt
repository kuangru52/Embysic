package com.kuangru52.embysic

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
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
) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

    var items: List<EmbyItem> = emptyList()
        private set

    private var currentMediaId: String? = null
    private var currentAlbumId: String? = null
    private var currentPlayingPath: String? = null
    private var expandedDeletePosition: Int = -1
    
    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<String>()

    // 缓存颜色和偏好设置，避免 in bind 中重复读取
    private var primaryColor: Int = 0
    private var secondaryColor: Int = 0
    private val highlightColor = android.graphics.Color.parseColor("#FFA000")
    private var serverUrl: String = ""
    private var accessToken: String = ""

    private val neteasePrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        notifyDataSetChanged()
    }

    // 预定义颜色矩阵，避免 bind 时创建对象
    private val folderHighlightMatrix = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix(floatArrayOf(
            0f,   0f,   1.0f, 0f, 0f, // R'
            0.2f, 0.2f, 0.6f, 0f, 0f, // G'
            0.5f, 0.5f, 0f,   0f, 0f, // B'
            0f,   0f,   0f,   1f, 0f  // A'
        ))
    )

    private val parentFolderMatrix = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix(floatArrayOf(
            0.8f, 0f,   0f,   0f, 0f, // R' (带点微红/橙，区分母文件夹)
            0f,   0.6f, 0f,   0f, 0f, // G'
            0f,   0f,   0.2f, 0f, 0f, // B'
            0f,   0f,   0f,   1f, 0f  // A'
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
    fun getSelectedItems() = items.filter { it.Id in selectedIds && it.Id != "BACK_FOLDER" }

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

    fun submitList(newItems: List<EmbyItem>, context: android.content.Context? = null) {
        // 更新强制深色状态
        isDarkForce = (context as? HomeActivity)?.isDarkForce() ?: false
        
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].Id == newItems[newItemPosition].Id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = items[oldItemPosition]
                val newItem = newItems[newItemPosition]
                return oldItem.Id == newItem.Id &&
                        oldItem.Name == newItem.Name &&
                        oldItem.UserData?.IsFavorite == newItem.UserData?.IsFavorite &&
                        oldItem.UserData?.PlaybackPositionTicks == newItem.UserData?.PlaybackPositionTicks
            }
        })
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val context = recyclerView.context
        
        // 预加载偏好设置
        val prefs = context.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", "") ?: ""
        accessToken = prefs.getString("access_token", "") ?: ""

        // 预加载主题颜色
        val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary, android.R.attr.textColorSecondary))
        primaryColor = typedArray.getColor(0, android.graphics.Color.BLACK)
        secondaryColor = typedArray.getColor(1, android.graphics.Color.GRAY)
        typedArray.recycle()

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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

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
            tvName.text = item.Name
            val isParentFolder = item.Id == "BACK_FOLDER"
            
            // 动态解析颜色，解决“红蓝背景”瞬间切换感
            val currentPrimary = if (isDarkForce) android.graphics.Color.WHITE else primaryColor
            val currentSecondary = if (isDarkForce) android.graphics.Color.parseColor("#B0FFFFFF") else secondaryColor

            // 处理删除按钮显示状态
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
            
            // 彻底清除状态
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
                tvName.setTextColor(secondaryColor)
                tvName.text = item.Name
                tvArtist.visibility = View.GONE
                ivIcon.colorFilter = parentFolderMatrix
                
                // 彻底禁用点击
                itemView.isClickable = false
                itemView.isFocusable = false
                itemView.foreground = null
            } else {
                // 正在播放或所在文件夹/专辑，使用黄色淡化卡片+细边框
                // 选中状态优先：使用深色/高亮背景
                if (isSelectionMode && isSelected) {
                    itemContainer.setBackgroundResource(R.drawable.bg_playing_card) // 复用高亮背景
                } else if (isPlayingThis || isCurrentAlbumOrParent) {
                    itemContainer.setBackgroundResource(R.drawable.bg_playing_card)
                } else {
                    itemContainer.setBackgroundResource(R.drawable.bg_compact_card)
                }

                // 恢复普通项的点击属性
                itemView.isClickable = true
                itemView.isFocusable = true
                
                if (isPlayingThis) {
                    tvIndex.visibility = View.VISIBLE
                    tvIndex.background = null
                    tvIndex.text = if (item.IndexNumber != null) String.format("%02d", item.IndexNumber) else ""
                    tvIndex.setTextColor(highlightColor)
                    tvName.setTextColor(highlightColor)
                    tvArtist.setTextColor(highlightColor)
                    tvDuration.setTextColor(highlightColor)
                    tvDuration.alpha = 1.0f
                } else if (isCurrentAlbumOrParent) {
                    tvIndex.visibility = View.GONE 
                    tvName.setTextColor(highlightColor)
                    tvArtist.setTextColor(highlightColor)
                    tvDuration.setTextColor(highlightColor)
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
                    tvDuration.setTextColor(currentPrimary)
                    tvDuration.alpha = 1.0f
                }
            }

            // 移除背景设置，保持干干净净
            ivIcon.background = null
            ivIcon.clipToOutline = true

            if (isFolderLike) {
                ivIcon.tag = "IS_FOLDER"
                ivIcon.setImageResource(R.drawable.folder)
                ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                // 文件夹不使用圆角背景，保持原样
                ivIcon.background = null
                
                if (!isParentFolder) {
                    tvArtist.visibility = if (item.Type == "MusicAlbum") View.VISIBLE else View.GONE
                    if (item.Type == "MusicAlbum") {
                        val artists = item.Artists?.joinToString(", ") ?: ""
                        tvArtist.text = artists
                    }
                }
            } else {
                tvArtist.visibility = View.VISIBLE
                val artists = item.Artists?.joinToString(", ") ?: ""
                val album = item.Album ?: ""
                tvArtist.text = if (album.isNotEmpty()) "$artists    $album" else artists
                ivIcon.tag = item.Id
                ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                
                // 音乐文件封面使用圆角矩形背景（12dp 圆角）
                ivIcon.setBackgroundResource(R.drawable.bg_compact_card)
                ivIcon.clipToOutline = true

                val cachedBitmap = bitmapCache.get(item.Id)
                if (cachedBitmap != null) {
                    ivIcon.setImageBitmap(cachedBitmap)
                } else {
                    val neteaseCovers = itemView.context.getSharedPreferences("netease_covers", AppCompatActivity.MODE_PRIVATE)
                    val cachedUrl = neteaseCovers.getString(item.Id, null)
                    
                    val hasPrimary = item.ImageTags?.containsKey("Primary") == true
                    val imageId = if (hasPrimary) item.Id else (item.AlbumId ?: item.Id)
                    // 列表页使用 200px 缩略图，极度节省流量
                    val serverImageUrl = "${serverUrl.trimEnd('/')}/emby/Items/$imageId/Images/Primary?MaxWidth=200&api_key=$accessToken"
                    
                    // 优先级：服务器封面（歌曲或专辑）-> 网易云缓存 -> 服务器封面（作为兜底）
                    val finalImageUrl = if (hasPrimary || item.AlbumId != null) {
                        serverImageUrl
                    } else if (cachedUrl != null) {
                        // 统一清洗 URL 并应用 200px 尺寸
                        if (cachedUrl.startsWith("http")) {
                            val cleanUrl = if (cachedUrl.contains("?")) cachedUrl.substringBefore("?") else cachedUrl
                            "$cleanUrl?param=200y200"
                        } else cachedUrl
                    } else {
                        serverImageUrl
                    }

                    ivIcon.load(finalImageUrl) {
                        placeholder(R.drawable.cd)
                        error(R.drawable.cd)
                        crossfade(true)
                        listener(onError = { _, _ -> 
                            if (finalImageUrl == serverImageUrl && !hasPrimary && cachedUrl != null) {
                                // 如果服务器加载失败且有缓存，尝试加载缓存
                                ivIcon.load(cachedUrl) { crossfade(true) }
                            } else if (finalImageUrl == serverImageUrl && !hasPrimary) {
                                loadCoverFromTags(item, serverUrl, accessToken)
                            }
                        })
                    }
                }
            }
            itemView.setOnClickListener {
                if (item.Id == "BACK_FOLDER") return@setOnClickListener
                
                if (isSelectionMode) {
                    toggleSelection(item.Id)
                    return@setOnClickListener
                }

                // 如果当前有显示的删除按钮，点击任何地方先隐藏它
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

            itemView.setOnLongClickListener {
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
                                // 保存到磁盘持久化，让底部条和播放页也能看到
                                MediaItemUtils.saveCoverToFile(itemView.context, item.Id, b)
                            }
                        }
                    } catch (e: Exception) { null } finally { retriever.release() }
                }
                if (ivIcon.tag == item.Id && bitmap != null) ivIcon.setImageBitmap(bitmap)
            }
        }
    }
}
