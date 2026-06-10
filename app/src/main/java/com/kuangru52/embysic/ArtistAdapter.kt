package com.kuangru52.embysic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load

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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivCover: ImageView = view.findViewById(R.id.ivArtistCover)
        private val tvName: TextView = view.findViewById(R.id.tvArtistName)

        fun bind(item: EmbyItem) {
            tvName.text = item.Name
            tvName.setTextColor(android.graphics.Color.WHITE)
            
            val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            val imageUrl = "${baseUrl}emby/Items/${item.Id}/Images/Primary?api_key=$accessToken"

            ivCover.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.logo)
                error(R.drawable.logo)
            }

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<EmbyItem>() {
        override fun areItemsTheSame(oldItem: EmbyItem, newItem: EmbyItem): Boolean {
            return oldItem.Id == newItem.Id
        }

        override fun areContentsTheSame(oldItem: EmbyItem, newItem: EmbyItem): Boolean {
            return oldItem == newItem
        }
    }
}
