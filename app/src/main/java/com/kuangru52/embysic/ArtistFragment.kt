package com.kuangru52.embysic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@UnstableApi
class ArtistFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        val activity = activity as? HomeActivity
        if (activity?.isSwipingBack == true) {
            return object : Animation() {}.apply { duration = 0 }
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    private lateinit var ivFragmentBackground: android.widget.ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ArtistAdapter
    
    private var apiService: EmbyApiService? = null
    private var serverUrl = ""
    private var accessToken = ""
    private var userId = ""

    private val authHeader: String
        get() = "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceName(requireContext())}\", DeviceId=\"${MediaItemUtils.getDeviceId(requireContext())}\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$accessToken\""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_artist, container, false)
        ivFragmentBackground = android.widget.ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
        (view as ViewGroup).addView(ivFragmentBackground, 0)

        recyclerView = view.findViewById(R.id.rvArtists)
        progressBar = view.findViewById(R.id.pbArtists)
        
        // 沉浸式：设置 PaddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setupRecyclerView()
        loadPrefs()
        initApiService()
        loadArtists()
        
        syncBackground()
        return view
    }

    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            syncBackground()
        }
    }

    override fun onStart() {
        super.onStart()
        (activity as? HomeActivity)?.mediaController?.addListener(playerListener)
    }

    override fun onStop() {
        super.onStop()
        (activity as? HomeActivity)?.mediaController?.removeListener(playerListener)
    }

    private fun syncBackground() {
        if (!isAdded) return

        // 平板横屏模式下，隐藏 Fragment 自己的背景，实现与全局背景完全一体
        val isTabletLand = (resources.configuration.smallestScreenWidthDp >= 600) &&
                          resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (isTabletLand) {
            ivFragmentBackground.visibility = View.GONE
            return
        }

        val activityBg = (activity as? HomeActivity)?.findViewById<android.widget.ImageView>(R.id.ivBlurBackground)
        if (activityBg != null && activityBg.drawable != null) {
            ivFragmentBackground.setImageDrawable(activityBg.drawable)
            ivFragmentBackground.alpha = activityBg.alpha
            ivFragmentBackground.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        adapter = ArtistAdapter { artist ->
            // 点击艺人：跳转到 LibraryFragment 并加载该艺人的内容
            val fragment = LibraryFragment()
            val args = Bundle()
            args.putString("artist_id", artist.Id)
            args.putString("artist_name", artist.Name)
            fragment.arguments = args
            
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }
        // 使用网格布局显示艺人，每行 3 个
        recyclerView.layoutManager = GridLayoutManager(context, 3)
        recyclerView.adapter = adapter
    }

    private fun loadPrefs() {
        requireContext().getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE).apply {
            serverUrl = getString("server_url", "") ?: ""
            accessToken = getString("access_token", "") ?: ""
            userId = getString("user_id", "") ?: ""
        }
    }

    private fun initApiService() {
        apiService = RetrofitClient.getEmbyApiService(requireContext())
    }

    private fun loadArtists() {
        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val response = service.getArtists(userId, authHeader)
                adapter.submitList(response.Items, context)
            } catch (e: Exception) {
                Toast.makeText(context, "加载艺人失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
