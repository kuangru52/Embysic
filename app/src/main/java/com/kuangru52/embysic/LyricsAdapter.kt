package com.kuangru52.embysic

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LyricsAdapter(private val onLineClick: (Long) -> Unit) : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

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
        // 忽略元数据行 (timeMs == -1)
        val index = lines.indexOfLast { it.timeMs in 0..timeMs }
        if (index != currentLineIndex && index != -1) {
            val oldIndex = currentLineIndex
            currentLineIndex = index
            if (oldIndex != -1) notifyItemChanged(oldIndex)
            if (currentLineIndex != -1) notifyItemChanged(currentLineIndex)
            return currentLineIndex
        }
        return -1
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
        textView.setPadding(0, 32, 0, 32)
        
        val isMetadata = line.timeMs == -1L
        
        val context = holder.itemView.context
        val colorOnSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceVariant = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)

        if (isMetadata) {
            textView.setTextColor(colorOnSurfaceVariant)
            textView.textSize = 14f
            textView.alpha = 0.6f
            textView.setOnClickListener(null)
        } else {
            if (position == currentLineIndex) {
                textView.setTextColor(colorOnSurface)
                textView.textSize = 22f
                textView.alpha = 1.0f
                textView.paint.isFakeBoldText = true
            } else {
                textView.setTextColor(colorOnSurfaceVariant)
                textView.textSize = 18f
                textView.alpha = 0.5f
                textView.paint.isFakeBoldText = false
            }
            
            holder.itemView.setOnClickListener {
                if (line.timeMs >= 0) {
                    onLineClick(line.timeMs)
                }
            }
        }
    }

    override fun getItemCount(): Int = lines.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
