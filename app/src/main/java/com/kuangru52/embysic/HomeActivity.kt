package com.kuangru52.embysic

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private var selectedTab by mutableStateOf("Recent")
    private var lastBackTime: Long = 0
    private var apiService: EmbyApiService? = null
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        initApiService()

        controllerFuture = MediaController.Builder(this, 
            androidx.media3.session.SessionToken(this, 
                android.content.ComponentName(this, PlaybackService::class.java))).buildAsync()

        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            if (controller != null) {
                mediaController = controller
                setupController(controller)
                
                findViewById<ComposeView>(R.id.bottom_container).setContent {
                    BottomTabs(
                        controller = controller,
                        selectedTab = selectedTab,
                        onPlayerClick = { showPlayer() },
                        onNavigation = { tab ->
                            selectedTab = tab
                            when (tab) {
                                "Recent" -> replaceFragment(RecentFragment())
                                "Favorite" -> replaceFragment(FavoriteFragment())
                                "Library" -> replaceFragment(LibraryFragment())
                                "Search" -> replaceFragment(SearchFragment())
                            }
                        },
                        onLibraryScan = {
                            val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
                            val userId = prefs.getString("user_id", "") ?: ""
                            val accessToken = prefs.getString("access_token", "") ?: ""
                            val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\", Token=\"$accessToken\""
                            
                            activityScope.launch {
                                try {
                                    val views = apiService?.getUserViews(userId, authHeader)
                                    val musicLibrary = views?.Items?.find { it.CollectionType == "music" }
                                    musicLibrary?.let {
                                        apiService?.refreshItem(it.Id, authHeader)
                                        android.widget.Toast.makeText(this@HomeActivity, "Music library scan started", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("HomeActivity", "Scan error: ${e.message}")
                                }
                            }
                        }
                    )
                }

                // 核心：实现真正的物理折射
                // 直接将 RenderEffect 施加到 fragment_container，使其处理背景内容
                findViewById<FrameLayout>(R.id.fragment_container).let { container ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        container.post {
                            val bottomOffsetPx = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 180f, resources.displayMetrics
                            )
                            val effect = LiquidGlassFactory.createLiquidRenderEffect(
                                width = container.width.toFloat(),
                                height = container.height.toFloat(),
                                refraction = 0.5f, // 折射率
                                aberration = 4.0f, // 色散强度
                                bottomOffset = bottomOffsetPx
                            )
                            container.setRenderEffect(effect)
                        }
                    }
                }
            }
        }, MoreExecutors.directExecutor())

        if (savedInstanceState == null) {
            replaceFragment(RecentFragment())
        }

        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }
                if (selectedTab == "Search") {
                    selectedTab = "Library"
                    replaceFragment(LibraryFragment())
                    return
                }
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackTime < 2000) {
                    finish()
                } else {
                    lastBackTime = currentTime
                    android.widget.Toast.makeText(this@HomeActivity, R.string.press_again_to_exit, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.kuangru52.embysic.SHOW_PLAYER") {
            showPlayer()
        }
    }

    private fun initApiService() {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        if (serverUrl.isNotEmpty()) {
            apiService = Retrofit.Builder()
                .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EmbyApiService::class.java)
        }
    }

    private fun setupController(controller: MediaController) {
        if (controller.currentMediaItem == null) {
            restoreLastPlayedItem(controller)
        }
    }

    private fun restoreLastPlayedItem(controller: MediaController) {
        val service = apiService ?: return
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        if (userId.isEmpty() || accessToken.isEmpty()) return

        val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\", Token=\"$accessToken\""

        activityScope.launch {
            try {
                val response = service.getRecentlyPlayedItems(userId, limit = 1, auth = authHeader)
                response.Items.firstOrNull()?.let { lastItem ->
                    val serverUrl = prefs.getString("server_url", "") ?: ""
                    val mediaItem = MediaItemUtils.buildMediaItem(lastItem, serverUrl, accessToken, userId)
                    controller.setMediaItem(mediaItem)
                    val lastPos = (lastItem.UserData?.PlaybackPositionTicks ?: 0L) / 10000
                    if (lastPos > 0) controller.seekTo(lastPos)
                    controller.prepare() 
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Restore error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    fun showPlayer() {
        PlayerDialogFragment().show(supportFragmentManager, "player")
    }

    fun replaceFragment(fragment: Fragment, tag: String? = null) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            R.anim.ios_slide_in_right,
            android.R.anim.fade_out,
            android.R.anim.fade_in,
            R.anim.ios_slide_out_right
        )
        transaction.replace(R.id.fragment_container, fragment)
        if (tag != null) transaction.addToBackStack(tag)
        transaction.commit()
    }
}
