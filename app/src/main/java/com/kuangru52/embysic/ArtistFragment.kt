package com.kuangru52.embysic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        if (!enter && (activity as? HomeActivity)?.isSwipingBack == true) {
            return AnimationUtils.loadAnimation(context, R.anim.ios_slide_out_right).apply {
                duration = 0
            }
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ArtistAdapter
    
    private var apiService: EmbyApiService? = null
    private var serverUrl = ""
    private var accessToken = ""
    private var userId = ""

    private val authHeader: String
        get() = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.42\", Token=\"$accessToken\""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_artist, container, false)
        recyclerView = view.findViewById(R.id.rvArtists)
        progressBar = view.findViewById(R.id.pbArtists)
        
        setupRecyclerView()
        loadPrefs()
        initApiService()
        loadArtists()
        
        (activity as? AppCompatActivity)?.supportActionBar?.title = "艺人"
        
        return view
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
        if (serverUrl.isEmpty()) return
        apiService = Retrofit.Builder()
            .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EmbyApiService::class.java)
    }

    private fun loadArtists() {
        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val response = service.getArtists(userId, authHeader)
                adapter.submitList(response.Items)
            } catch (e: Exception) {
                Toast.makeText(context, "加载艺人失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
