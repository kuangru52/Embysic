package com.kuangru52.embysic

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView

class LyricsAdapter(
    private val onItemClick: () -> Unit,
    private val onSeekTo: ((Long) -> Unit)? = null,
) : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

    var lines: List<LrcLine> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            currentLineIndex = -1
            notifyDataSetChanged()
        }

    private var currentLineIndex = -1
    private var clickedLineIndex = -1
    private val handler = Handler(Looper.getMainLooper())
    private val resetClickedIndexRunnable = Runnable {
        val oldIndex = clickedLineIndex
        clickedLineIndex = -1
        if (oldIndex != -1) notifyItemChanged(oldIndex)
    }

    fun updateActiveLine(timeMs: Long): Int {
        val index = lines.indexOfLast { (it.timeMs >= 0) && (it.timeMs <= timeMs) }
        if ((index != currentLineIndex) && (index != -1)) {
            val oldIndex = currentLineIndex
            currentLineIndex = index
            if (oldIndex != -1) notifyItemChanged(oldIndex)
            if (currentLineIndex != -1) notifyItemChanged(currentLineIndex)
        }
        return index
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val composeView = ComposeView(parent.context)
        return ViewHolder(composeView)
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val line = lines[position]
        val isCurrent = position == currentLineIndex
        val isClicked = position == clickedLineIndex

        (holder.itemView as ComposeView).setContent {
            LyricItemRow(
                line = line,
                isCurrent = isCurrent,
                isClicked = isClicked,
                onContentClick = {
                    if ((line.timeMs >= 0) && (onSeekTo != null)) {
                        val oldClicked = clickedLineIndex
                        clickedLineIndex = holder.bindingAdapterPosition
                        if (oldClicked != -1) notifyItemChanged(oldClicked)
                        if (clickedLineIndex != -1) notifyItemChanged(clickedLineIndex)

                        handler.removeCallbacks(resetClickedIndexRunnable)
                        handler.postDelayed(resetClickedIndexRunnable, 2000)

                        onSeekTo.invoke(line.timeMs)
                    } else {
                        onItemClick()
                    }
                },
            ) { onItemClick() }
        }
    }

    override fun getItemCount(): Int = lines.size

    class ViewHolder(view: ComposeView) : RecyclerView.ViewHolder(view)
}

@Composable
fun LyricItemRow(
    line: LrcLine,
    isCurrent: Boolean,
    isClicked: Boolean,
    onContentClick: () -> Unit,
    onBlankClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBlankClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clickable { onContentClick() }
                .padding(horizontal = 40.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isClicked) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play_vector),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 8.dp),
                )
            }

            val isMetadata = line.timeMs == -1L
            val fontSize = if (isMetadata) 13.sp else if (isCurrent) 18.sp else 15.sp
            val textColor = if (isCurrent && !isMetadata) Color.White else Color.White.copy(alpha = 0.7f)
            val fontWeight = if (isCurrent && !isMetadata) FontWeight.Bold else FontWeight.Normal

            Text(
                text = line.text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
