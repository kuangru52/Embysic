package com.kuangru52.embysic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SearchFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchView: androidx.appcompat.widget.SearchView
    private lateinit var adapter: LibraryAdapter

    private var apiService: EmbyApiService? = null
    private var serverUrl = ""
    private var accessToken = ""
    private var userId = ""

    private val authHeader: String
        get() = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\", Token=\"$accessToken\""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_library, container, false) // 复用布局
        recyclerView = view.findViewById(R.id.rvLibrary)
        progressBar = view.findViewById(R.id.progressBar)
        searchView = view.findViewById(R.id.searchView)
        
        // 搜索页必须显示搜索框
        searchView.visibility = View.VISIBLE
        searchView.isIconified = false
        searchView.queryHint = "搜索歌曲、艺人..."

        // 强制弹出输入法
        searchView.post {
            searchView.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchView.findFocus(), InputMethodManager.SHOW_IMPLICIT)
        }

        setupRecyclerView()
        loadPrefs()
        initApiService()
        setupSearchLogic()

        return view
    }

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(onItemClick = { item ->
            if (item.Type == "MusicArtist") {
                // 搜索结果如果是艺人，跳转到曲库查看该艺人的作品
                val fragment = LibraryFragment().apply {
                    arguments = Bundle().apply {
                        putString("artist_id", item.Id)
                        putString("artist_name", item.Name)
                    }
                }
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.ios_slide_in_right,
                        R.anim.ios_exit_push_left,
                        R.anim.ios_pull_right,
                        R.anim.ios_slide_out_right
                    )
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            } else {
                playMusic(item, adapter.items)
            }
        })
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun setupSearchLogic() {
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    performSearch(query)
                    searchView.clearFocus() // 点击搜索（回车）后，收起输入法
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = true
        })
    }

    private fun performSearch(query: String) {
        val service = apiService ?: return
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                // 仅请求音频和艺人，排除文件夹(Folder/Collection)和专辑(MusicAlbum)
                val response = service.getItems(
                    userId = userId,
                    searchTerm = query,
                    includeItemTypes = "Audio,MusicArtist",
                    recursive = true,
                    fields = "Path,ItemCounts,PrimaryImageAspectRatio,Artists,AlbumId,ImageTags,MediaSources,RunTimeTicks,UserData,Index",
                    auth = authHeader
                )
                adapter.submitList(response.Items)
            } catch (e: Exception) {
                Toast.makeText(context, "搜索失败", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun playMusic(item: EmbyItem, allItems: List<EmbyItem>) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return
        val mediaItems = allItems.filter { !it.IsFolder }.map { song ->
            MediaItemUtils.buildMediaItem(song, serverUrl, accessToken, userId)
        }
        val startIndex = mediaItems.indexOfFirst { it.mediaId == item.Id }.coerceAtLeast(0)
        controller.stop()
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
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
}
