package com.kuangru52.embysic

import android.content.Intent
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@UnstableApi
class HomeActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private var selectedTab by mutableStateOf("Recent")
    private var lastBackTime: Long = 0
    private var apiService: EmbyApiService? = null
    private val activityScope = CoroutineScope(Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    // 完美比例的玻璃参数
    private var dockRect: RectF? = null
    private var dialogRect: RectF? = null
    private val glassRadius = 38f
    private val glassAmount = -60f
    private val glassHeight = 20f
    private val glassBlur = 10f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        // 沉浸式处理：让背景容器占满全屏，底栏悬浮
        val fragmentContainer = findViewById<FrameLayout>(R.id.fragment_container)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        initApiService()
        setupMediaController()

        if (savedInstanceState == null) {
            replaceFragment(RecentFragment())
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackTime < 2000) {
                    finish()
                } else {
                    lastBackTime = currentTime
                    Toast.makeText(this@HomeActivity, R.string.press_again_to_exit, Toast.LENGTH_SHORT).show()
                }
            }
        })
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

    private fun setupMediaController() {
        val sessionToken = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get() ?: return@addListener
            mediaController = controller
            
            if (controller.currentMediaItem == null) {
                restoreLastPlayedItem(controller)
            }

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
                    onLibraryScan = { scanLibrary() }
                )
            }
        }, MoreExecutors.directExecutor())
    }

    private fun restoreLastPlayedItem(controller: MediaController) {
        val service = apiService ?: return
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        val serverUrl = prefs.getString("server_url", "") ?: ""
        if (userId.isEmpty() || accessToken.isEmpty()) return
        
        val auth = "MediaBrowser Token=\"$accessToken\""
        activityScope.launch {
            try {
                val response = service.getRecentlyPlayedItems(userId, limit = 1, auth = auth)
                response.Items.firstOrNull()?.let { item ->
                    val mediaItem = MediaItemUtils.buildMediaItem(item, serverUrl, accessToken, userId)
                    controller.setMediaItem(mediaItem)
                    val lastPos = (item.UserData?.PlaybackPositionTicks ?: 0L) / 10000
                    if (lastPos > 0) controller.seekTo(lastPos)
                    controller.prepare()
                }
            } catch (e: Exception) { Log.e("HomeActivity", "Restore error: ${e.message}") }
        }
    }

    private fun scanLibrary() {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        val auth = "MediaBrowser Token=\"$accessToken\""
        activityScope.launch {
            try {
                val views = apiService?.getUserViews(userId, auth)
                val musicLibrary = views?.Items?.find { it.CollectionType == "music" }
                musicLibrary?.let {
                    apiService?.refreshItem(it.Id, auth)
                    Toast.makeText(this@HomeActivity, R.string.sync_library_started, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Log.e("HomeActivity", "Scan error: ${e.message}") }
        }
    }

    // --- 极致玻璃渲染逻辑：只模糊背景，不模糊文字 ---
    fun setDockRect(rect: RectF?) {
        dockRect = rect
        refreshSystemEffect()
    }

    fun applyDialogEffect(rect: RectF, radius: Float, amount: Float, height: Float, blur: Float) {
        dialogRect = rect
        refreshSystemEffect()
    }

    fun clearDialogEffect() {
        dialogRect = null
        refreshSystemEffect()
    }

    private fun refreshSystemEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val container = findViewById<View>(R.id.fragment_container) ?: return
            if (dockRect == null && dialogRect == null) {
                container.setRenderEffect(null)
                return
            }

            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dm = resources.displayMetrics
            val pxRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glassRadius, dm)
            val pxAmount = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glassAmount, dm)
            val pxHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glassHeight, dm)
            val pxBlur = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glassBlur, dm)

            val r1 = dockRect ?: RectF(-100f, -100f, -50f, -50f)
            
            // 关键修复：由于 fragment_container 可能有 padding (用于状态栏避让)，
            // 我们上报的坐标是相对于屏幕的，需要调整到相对于容器的绘制空间。
            val containerLoc = IntArray(2)
            container.getLocationOnScreen(containerLoc)
            
            val adjustedDockRect = dockRect?.let {
                RectF(it.left - containerLoc[0], it.top - containerLoc[1], 
                      it.right - containerLoc[0], it.bottom - containerLoc[1])
            } ?: RectF(-100f, -100f, -50f, -50f)

            val adjustedDialogRect = dialogRect?.let {
                RectF(it.left - containerLoc[0], it.top - containerLoc[1], 
                      it.right - containerLoc[0], it.bottom - containerLoc[1])
            }

            val effect = LiquidGlassFactory.createLiquidRenderEffect(
                container.width.toFloat(), container.height.toFloat(),
                adjustedDockRect, pxRadius, pxAmount, pxHeight, pxBlur, isDark, adjustedDialogRect
            )
            container.setRenderEffect(effect)
        }
    }

    // --- 导航 ---
    fun showPlayer() {
        val dialog = PlayerDialogFragment()
        mediaController?.let { dialog.setPlayer(it) }
        dialog.show(supportFragmentManager, "player")
    }

    fun replaceFragment(fragment: Fragment, tag: String? = null) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            R.anim.ios_slide_in_right, android.R.anim.fade_out,
            android.R.anim.fade_in, R.anim.ios_slide_out_right
        )
        transaction.replace(R.id.fragment_container, fragment)
        if (tag != null) transaction.addToBackStack(tag)
        transaction.commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
