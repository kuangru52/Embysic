package com.kuangru52.embysic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ArtistAdapter(private val onItemClick: (EmbyItem) -> Unit) :
    RecyclerView.Adapter<ArtistAdapter.ViewHolder>() {

    var items: List<EmbyItem> = emptyList()

    fun submitList(newList: List<EmbyItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.Name
        
        val prefs = holder.itemView.context.getSharedPreferences("embysic_prefs", android.content.Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val imageUrl = "${baseUrl}emby/Items/${item.Id}/Images/Primary?api_key=$accessToken"

        holder.ivCover.load(imageUrl) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_report_image)
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.ivArtistCover)
        val tvName: TextView = view.findViewById(R.id.tvArtistName)
    }
}
