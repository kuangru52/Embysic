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

    @androidx.media3.common.util.UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        val context = holder.itemView.context
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            || (context as? HomeActivity)?.isDarkForce() ?: false
            
        val textView = holder.itemView.findViewById<TextView>(R.id.tvLyricLine)
        textView.text = line.text
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textView.setPadding(32, 16, 32, 16) // 进一步缩小垂直内边距 (从 28 降至 16)
        
        // 增加文字阴影，防止背景亮时看不清
        if (isDark) {
            textView.setShadowLayer(4f, 2f, 2f, 0x80000000.toInt())
        } else {
            textView.setShadowLayer(2f, 1f, 1f, 0x40000000.toInt())
        }
        
        val isMetadata = line.timeMs == -1L
        
        if (isMetadata) {
            textView.setTextColor(if (isDark) 0x99FFFFFF.toInt() else 0x99000000.toInt())
            textView.textSize = 13f
            textView.alpha = 1.0f
            textView.paint.isFakeBoldText = false
        } else {
            if (position == currentLineIndex) {
                textView.setTextColor(if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                textView.textSize = 18f
                textView.alpha = 1.0f
                textView.paint.isFakeBoldText = true
            } else {
                textView.setTextColor(if (isDark) 0x66FFFFFF.toInt() else 0x66000000.toInt())
                textView.textSize = 15f
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
