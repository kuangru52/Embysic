package com.kuangru52.embysic

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.content.res.Configuration
import androidx.recyclerview.widget.RecyclerView

class LyricsAdapter(
    private val onItemClick: () -> Unit
) : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

    private fun Context.getColorFromAttr(@AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    var lines: List<LrcLine> = emptyList()
        set(value) {
            field = value
            currentLineIndex = -1
            notifyDataSetChanged()
        }

    private var currentLineIndex = -1

    fun updateActiveLine(timeMs: Long): Int {
        val index = lines.indexOfLast { it.timeMs in 0..timeMs }
        if (index != currentLineIndex && index != -1) {
            val oldIndex = currentLineIndex
            currentLineIndex = index
            if (oldIndex != -1) notifyItemChanged(oldIndex)
            if (currentLineIndex != -1) notifyItemChanged(currentLineIndex)
        }
        return index
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_lyric, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        val textView = holder.itemView.findViewById<TextView>(R.id.tvLyricLine)
        textView.text = line.text
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textView.setPadding(32, 16, 32, 16) // 进一步缩小垂直内边距 (从 28 降至 16)
        
        // 增加文字阴影，防止背景亮时看不清
        textView.setShadowLayer(4f, 2f, 2f, 0x80000000.toInt())
        
        val isMetadata = line.timeMs == -1L
        
        val isLandscape = holder.itemView.context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        if (isMetadata) {
            textView.setTextColor(0x99FFFFFF.toInt()) // 半透明白
            textView.textSize = if (isLandscape) 16f else 13f
            textView.alpha = 1.0f
            textView.paint.isFakeBoldText = false
        } else {
            if (position == currentLineIndex) {
                textView.setTextColor(android.graphics.Color.WHITE) // 纯白
                textView.textSize = if (isLandscape) 24f else 18f
                textView.alpha = 1.0f
                textView.paint.isFakeBoldText = true
            } else {
                textView.setTextColor(0x66FFFFFF.toInt()) // 更明显的非激活态透明度
                textView.textSize = if (isLandscape) 20f else 15f
                textView.alpha = 1.0f
                textView.paint.isFakeBoldText = false
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick()
        }
    }

    override fun getItemCount(): Int = lines.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
