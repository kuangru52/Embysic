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
    private val onItemDoubleClick: ((EmbyItem) -> Unit)? = null
) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

    var items: List<EmbyItem> = emptyList()
        private set

    private var currentMediaId: String? = null
    private var currentAlbumId: String? = null
    private var currentPlayingPath: String? = null

    // 缓存颜色和偏好设置，避免在 bind 中重复读取
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

    fun setCurrentMediaId(mediaId: String?, albumId: String? = null, path: String? = null) {
        if (currentMediaId != mediaId || currentAlbumId != albumId || currentPlayingPath != path) {
            currentMediaId = mediaId
            currentAlbumId = albumId
            currentPlayingPath = path
            notifyDataSetChanged()
        }
    }

    private val bitmapCache = LruCache<String, Bitmap>(100)
    private var lastClickTime: Long = 0

    fun submitList(newItems: List<EmbyItem>) {
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

        fun bind(item: EmbyItem) {
            tvName.text = item.Name
            
            // 彻底清除状态
            ivIcon.dispose() 
            ivIcon.clearColorFilter()
            ImageViewCompat.setImageTintList(ivIcon, null)

            val isFolderLike = item.IsFolder || item.Type == "CollectionFolder" || item.Type == "MusicArtist" || item.Type == "MusicAlbum"
            val isPlayingThis = item.Id == currentMediaId
            
            var isCurrentAlbumOrParent = false
            if (isFolderLike && currentPlayingPath != null && item.Path != null) {
                // 仅在路径包含时进行简单判定，减少正则/替换开销
                isCurrentAlbumOrParent = currentPlayingPath!!.contains(item.Path!!)
            }

            if (isPlayingThis) {
                tvIndex.visibility = View.VISIBLE
                tvIndex.background = null
                tvIndex.text = if (item.IndexNumber != null) String.format("%02d", item.IndexNumber) else ""
                tvIndex.setTextColor(highlightColor)
                tvName.setTextColor(highlightColor)
                tvArtist.setTextColor(highlightColor)
            } else if (isCurrentAlbumOrParent) {
                tvIndex.visibility = View.GONE 
                tvName.setTextColor(highlightColor)
                tvArtist.setTextColor(highlightColor)
                ivIcon.colorFilter = folderHighlightMatrix
            } else if (isFolderLike) {
                tvIndex.visibility = View.GONE
                tvName.setTextColor(primaryColor)
                tvArtist.setTextColor(secondaryColor)
            } else {
                tvIndex.visibility = View.VISIBLE
                tvIndex.background = null
                tvIndex.text = if (item.IndexNumber != null) String.format("%02d", item.IndexNumber) else ""
                tvIndex.setTextColor(secondaryColor)
                tvName.setTextColor(primaryColor)
                tvArtist.setTextColor(secondaryColor)
            }

            ivIcon.setBackgroundResource(R.drawable.rounded_corners_8)
            ivIcon.clipToOutline = true

            if (isFolderLike) {
                ivIcon.tag = "IS_FOLDER"
                ivIcon.setImageResource(R.drawable.folder)
                ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                
                tvArtist.visibility = if (item.Type == "MusicAlbum") View.VISIBLE else View.GONE
                if (item.Type == "MusicAlbum") {
                    tvArtist.text = item.Artists?.joinToString(", ") ?: ""
                }
            } else {
                tvArtist.visibility = View.VISIBLE
                tvArtist.text = item.Artists?.joinToString(", ") ?: ""
                ivIcon.tag = item.Id
                ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP

                val cachedBitmap = bitmapCache.get(item.Id)
                if (cachedBitmap != null) {
                    ivIcon.setImageBitmap(cachedBitmap)
                } else {
                    val neteaseCovers = itemView.context.getSharedPreferences("netease_covers", AppCompatActivity.MODE_PRIVATE)
                    val cachedUrl = neteaseCovers.getString(item.Id, null)
                    
                    val imageId = if (item.ImageTags?.containsKey("Primary") == true) {
                        item.Id
                    } else {
                        item.AlbumId ?: item.Id
                    }
                    val serverImageUrl = "${serverUrl.trimEnd('/')}/emby/Items/$imageId/Images/Primary?MaxWidth=500&api_key=$accessToken"
                    val finalImageUrl = cachedUrl ?: serverImageUrl

                    ivIcon.load(finalImageUrl) {
                        placeholder(R.drawable.cd)
                        error(R.drawable.cd)
                        listener(onError = { _, _ -> 
                            // 修正：不再重试 serverImageUrl，因为已经在 load 中尝试过了。
                            // 只有当 finalImageUrl 已经是 serverImageUrl 且失败时，才尝试解析内嵌封面。
                            if (finalImageUrl == serverImageUrl) {
                                loadCoverFromTags(item, serverUrl, accessToken)
                            }
                        })
                    }
                }
            }
            itemView.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 300) {
                    onItemDoubleClick?.invoke(item)
                } else {
                    onItemClick(item)
                }
                lastClickTime = currentTime
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
                            BitmapFactory.decodeByteArray(it, 0, it.size)?.also { b -> bitmapCache.put(item.Id, b) }
                        }
                    } catch (e: Exception) { null } finally { retriever.release() }
                }
                if (ivIcon.tag == item.Id && bitmap != null) ivIcon.setImageBitmap(bitmap)
            }
        }
    }
}
