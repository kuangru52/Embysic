package com.kuangru52.embysic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch

@UnstableApi
class ArtistFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ArtistAdapter
    private var apiService: EmbyApiService? = null

    private var serverUrl: String = ""
    private var accessToken: String = ""
    private var userId: String = ""

    private val authHeader: String
        get() {
            val ctx = context ?: return ""
            return "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceId(ctx)}\", DeviceId=\"${MediaItemUtils.getDeviceId(ctx)}\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$accessToken\""
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val composeView = ComposeView(ctx)
        composeView.setContent {
            AndroidView(
                factory = { context ->
                    val root = FrameLayout(context)
                    val rv = RecyclerView(context).apply {
                        id = R.id.rvArtists
                        val p = (8 * context.resources.displayMetrics.density).toInt()
                        setPadding(p, p, p, p)
                        clipToPadding = false
                    }
                    val pb = ProgressBar(context).apply {
                        id = R.id.pbArtists
                        visibility = View.GONE
                    }
                    root.addView(rv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    root.addView(pb, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
                    
                    recyclerView = rv
                    progressBar = pb
                    setupRecyclerView()
                    loadPrefs()
                    initApiService()
                    loadArtists()
                    root
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return composeView
    }

    private fun loadPrefs() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", "") ?: ""
        accessToken = prefs.getString("access_token", "") ?: ""
        userId = prefs.getString("user_id", "") ?: ""
    }

    private fun initApiService() {
        val ctx = context ?: return
        apiService = RetrofitClient.getEmbyApiService(ctx)
    }

    private fun setupRecyclerView() {
        adapter = ArtistAdapter { artistItem ->
            (activity as? HomeActivity)?.replaceFragment(
                LibraryFragment().apply {
                    arguments = Bundle().apply {
                        putString("artist_id", artistItem.Id)
                        putString("artist_name", artistItem.Name)
                    }
                },
                "Artist_${artistItem.Id}",
            )
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun loadArtists() {
        if (userId.isEmpty() || accessToken.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = apiService?.getArtists(userId = userId, auth = authHeader)
                val items = response?.Items ?: emptyList()
                if (!::adapter.isInitialized) return@launch
                adapter.submitList(items)
            } catch (e: Exception) {
                Log.e("ArtistFragment", "Error loading artists", e)
                Toast.makeText(context, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
