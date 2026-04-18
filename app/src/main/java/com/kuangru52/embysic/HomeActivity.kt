package com.kuangru52.embysic

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeActivity : FragmentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
    private var apiService: EmbyApiService? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastBackTime = 0L
    var selectedTab by mutableStateOf("Recent")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        initApiService()

        // 统一处理系统栏缩进，确保状态栏不被遮挡
        val root = findViewById<View>(R.id.main_root)
        val fragmentContainer = findViewById<View>(R.id.fragment_container)
        val bottomContainer = findViewById<View>(R.id.bottom_container)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // 重要：只给 fragmentContainer 设置顶部 padding，避开状态栏
            fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            
            // 底部避开导航栏
            bottomContainer.setPadding(0, 0, 0, systemBars.bottom)

            insets
        }

        val sessionToken = androidx.media3.session.SessionToken(this, android.content.ComponentName(this, com.kuangru52.embysic.PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            mediaController?.let { controller ->
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
            }
        }, MoreExecutors.directExecutor())

        if (savedInstanceState == null) {
            replaceFragment(RecentFragment())
        }

        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 如果 Fragment 回退栈中有内容，先回退 Fragment
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                // 如果当前是在搜索页，返回到曲库 (Library)
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
        
        // 设置进场和出场动画：进场时从右侧滑入，出场时向右侧滑出
        transaction.setCustomAnimations(
            R.anim.ios_slide_in_right,  // 进入动画
            android.R.anim.fade_out,    // 被覆盖时的动画（保持不动或轻微淡出）
            android.R.anim.fade_in,      // 返回时的进场（下面的卡片显现）
            R.anim.ios_slide_out_right   // 返回时的出场（当前的卡片向右滑走）
        )
        
        transaction.replace(R.id.fragment_container, fragment)
        if (tag != null) transaction.addToBackStack(tag)
        transaction.commit()
    }
}
