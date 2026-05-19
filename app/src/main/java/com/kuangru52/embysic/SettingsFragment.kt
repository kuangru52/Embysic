package com.kuangru52.embysic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (!enter && (activity as? HomeActivity)?.isSwipingBack == true) {
            // 手势返回时，强制将退出动画设为 0 耗时，避免二次重播
            return AnimationUtils.loadAnimation(context, R.anim.ios_slide_out_right).apply {
                duration = 0
            }
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // 设置页面已统一采用 AAC 256kbps 转码策略，不再提供手动设置项
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
}
