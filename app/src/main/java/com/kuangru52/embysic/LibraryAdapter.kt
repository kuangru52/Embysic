package com.kuangru52.embysic

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

    private val bitmapCache = LruCache<String, Bitmap>(100)
    private var lastClickTime: Long = 0

    fun submitList(newItems: List<EmbyItem>) {
        items = newItems
        notifyDataSetChanged()
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
            
            val prefs = itemView.context.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
            val serverUrl = prefs.getString("server_url", "") ?: ""
            val accessToken = prefs.getString("access_token", "") ?: ""

            // 1. 彻底清除所有状态，防止复用污染
            ivIcon.dispose() 
            ivIcon.setImageDrawable(null)
            ivIcon.clearColorFilter()
            ImageViewCompat.setImageTintList(ivIcon, null)
            ivIcon.background = null // 关键：彻底移除 XML 中设置的灰色背景

            // 判定是否是文件夹类项目
            val isFolderLike = item.IsFolder || item.Type == "CollectionFolder" || item.Type == "MusicArtist" || item.Type == "MusicAlbum"

            if (isFolderLike) {
                ivIcon.tag = "IS_FOLDER"
                
                // 2. 加载您的自定义 folder.png
                ivIcon.setImageResource(R.drawable.folder)
                ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                
                // 专辑显示歌手名，纯文件夹隐藏
                tvArtist.visibility = if (item.Type == "MusicAlbum") View.VISIBLE else View.GONE
                if (item.Type == "MusicAlbum") {
                    tvArtist.text = item.Artists?.joinToString(", ") ?: ""
                }
                tvIndex.visibility = View.GONE
            } else {
                // 歌曲显示封面
                tvArtist.visibility = View.VISIBLE
                tvArtist.text = item.Artists?.joinToString(", ") ?: ""
                
                tvIndex.visibility = if (item.Index != null) {
                    tvIndex.text = String.format("%02d", item.Index)
                    View.VISIBLE
                } else View.GONE

                ivIcon.tag = item.Id
                ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP

                val cachedBitmap = bitmapCache.get(item.Id)
                if (cachedBitmap != null) {
                    ivIcon.setImageBitmap(cachedBitmap)
                } else {
                    // 1. 优先检查是否有缓存的网易云封面 URL
                    val neteaseCovers = itemView.context.getSharedPreferences("netease_covers", AppCompatActivity.MODE_PRIVATE)
                    val cachedUrl = neteaseCovers.getString(item.Id, null)
                    
                    val hasPrimary = item.ImageTags?.containsKey("Primary") == true
                    val serverImageUrl = if (hasPrimary) "${serverUrl.trimEnd('/')}/emby/Items/${item.Id}/Images/Primary" else null
                    
                    // 2. 如果有缓存 URL，优先使用；否则使用 Emby URL
                    val finalImageUrl = cachedUrl ?: serverImageUrl

                    ivIcon.load(finalImageUrl) {
                        if (finalImageUrl == serverImageUrl) {
                            addHeader("X-Emby-Token", accessToken)
                        }
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_media_play)
                        listener(onError = { _, _ -> 
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
