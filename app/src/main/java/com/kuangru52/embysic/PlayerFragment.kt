package com.kuangru52.embysic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class PlayerFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = TextView(context).apply {
            text = "播放页面"
            gravity = android.view.Gravity.CENTER
            textSize = 24f
        }
        return view
    }
}