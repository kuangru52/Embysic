package com.kuangru52.embysic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // 设置页面已统一采用 AAC 256kbps 转码策略，不再提供手动设置项
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
}
