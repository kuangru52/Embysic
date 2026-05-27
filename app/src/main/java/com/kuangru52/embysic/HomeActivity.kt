package com.kuangru52.embysic

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private var isPlayerShown = false
    private var lastAppliedDockRect: RectF? = null
    private var lastAppliedDialogRect: RectF? = null
    private var lastAppliedIsDark: Boolean? = null

    // 完美比例的玻璃参数
    private var dockRect: RectF? = null
    private var dialogRect: RectF? = null
    private var dialogRadius: Float = 38f
    private val glassRadius = 38f
    private val glassAmount = -60f
    private val glassHeight = 20f
    private val glassBlur = 10f

    private var isTablet: Boolean = false
    private var tabletPlayerHandler: TabletPlayerHandler? = null
    var isSwipingBack: Boolean = false

    fun isDarkForce(): Boolean {
        val isTabletLandscape = isTablet && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val systemIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return isTabletLandscape || systemIsDark
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("show_player", false)) {
            // 如果已经在首页，点击通知栏跳转到播放页
            showPlayer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 判断是否为平板
        isTablet = (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        requestedOrientation = if (isTablet) {
            // 平板：默认横屏，但允许旋转
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            // 手机：强制竖屏
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        // 沉浸式处理：让背景容器占满全屏，底栏悬浮
        findViewById<ImageView>(R.id.ivBlurBackground)?.setImageResource(R.drawable.bg_superman)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topInset = maxOf(systemBars.top, displayCutout.top)
            
            v.setPadding(systemBars.left, 0, systemBars.right, 0)

            if (isTablet) {
                findViewById<View>(R.id.statusBarSpacer)?.layoutParams?.height = topInset
            }
            
            insets
        }

        initApiService()
        setupMediaController()
        setupFragmentLifecycleListener()

        // 更新状态栏图标颜色
        updateStatusBarIcons()

        checkUpdate()

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
        apiService = RetrofitClient.getEmbyApiService(this)
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get() ?: return@addListener
            mediaController = controller
            
            if (controller.currentMediaItem == null) {
                restoreLastPlayedItem(controller)
            } else {
                // 冷启动且已有项目（可能由于 Service 重用），确保默认模式
                controller.repeatMode = Player.REPEAT_MODE_ALL
                controller.shuffleModeEnabled = false
            }

            findViewById<ComposeView>(R.id.bottom_container).setContent {
                // 关键修复：直接在 setContent 内部读取 selectedTab，确保 Compose 追踪其变化
                BottomTabs(
                    controller = controller,
                    selectedTab = selectedTab, 
                    onPlayerClick = { 
                        if (isTablet && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            // 平板横屏下，如果需要切换到播放器逻辑，可以在此处理，但目前需求是不弹出界面
                        } else {
                            showPlayer()
                        }
                    },
                    onNavigation = { tab ->
                        if (selectedTab != tab) {
                            selectedTab = tab
                            // 切换标签时，清理滤镜并立即刷新
                            lastAppliedDockRect = null
                            refreshSystemEffect()
                            
                            when (tab) {
                                "Recent" -> replaceFragment(RecentFragment())
                                "Favorite" -> replaceFragment(FavoriteFragment())
                                "Library" -> replaceFragment(LibraryFragment())
                                "Search" -> replaceFragment(SearchFragment())
                            }
                        }
                    },
                    onLibraryScan = { scanLibrary() }
                )
            }
            if (isTablet) {
                tabletPlayerHandler = TabletPlayerHandler(this, controller)
                tabletPlayerHandler?.init()
            }
            // 手机竖屏背景同步
            if (!isTablet) {
                controller.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateBackground(mediaItem)
                    }
                })
                updateBackground(controller.currentMediaItem)
            }

            // 处理启动或新 Intent 要求的播放页显示
            if (intent.getBooleanExtra("show_player", false)) {
                showPlayer()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateStatusBarIcons() {
        val isDark = isDarkForce()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    /**
     * 根据模式更新播放列表 (增量无缝版本)
     * @param isShuffle 是否切换到随机模式
     */
    fun updatePlaylistByMode(isShuffle: Boolean) {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        val currentId = currentItem.mediaId
        
        val service = apiService ?: return
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val auth = "MediaBrowser Token=\"$accessToken\""

        activityScope.launch {
            try {
                // 1. 准备新列表的数据
                val beforeItems = mutableListOf<MediaItem>()
                val afterItems = mutableListOf<MediaItem>()

                if (isShuffle) {
                    // 随机模式：全库拉取 200 首，排除当前项
                    val response = service.getItems(userId, limit = 200, sortBy = "Random", auth = auth)
                    val randomItems = response.Items.filter { it.Id != currentId }
                        .map { MediaItemUtils.buildMediaItem(this@HomeActivity, it, serverUrl, accessToken, userId) }
                    
                    // 随机模式下，我们将当前项作为起点，其余全部排在后面
                    afterItems.addAll(randomItems)
                } else {
                    // 列表/单曲模式：拉取同文件夹歌曲
                    var parentId = currentItem.mediaMetadata.extras?.getString("parent_id")
                    if (parentId == null) {
                        try {
                            val item = service.getItem(userId, currentId, auth, auth)
                            parentId = item.ParentId ?: item.AlbumId
                        } catch (_: Exception) {}
                    }

                    if (parentId != null) {
                        val response = service.getItems(userId, parentId = parentId, auth = auth)
                        val folderItems = response.Items.sortedBy { it.SortName ?: it.Name }
                        val splitIndex = folderItems.indexOfFirst { it.Id == currentId }
                        
                        if (splitIndex != -1) {
                            // 分割出当前项之前和之后的歌曲
                            beforeItems.addAll(folderItems.subList(0, splitIndex).map { 
                                MediaItemUtils.buildMediaItem(this@HomeActivity, it, serverUrl, accessToken, userId) 
                            })
                            afterItems.addAll(folderItems.subList(splitIndex + 1, folderItems.size).map { 
                                MediaItemUtils.buildMediaItem(this@HomeActivity, it, serverUrl, accessToken, userId) 
                            })
                        } else {
                            // 如果当前项不在文件夹列表中，则全部加在后面
                            afterItems.addAll(folderItems.map { 
                                MediaItemUtils.buildMediaItem(this@HomeActivity, it, serverUrl, accessToken, userId) 
                            })
                        }
                    }
                }

                // 2. 执行增量更新以保持播放无缝
                val currentIndex = controller.currentMediaItemIndex
                val totalItems = controller.mediaItemCount

                // a. 移除当前项之后的所有项目
                if (totalItems > currentIndex + 1) {
                    controller.removeMediaItems(currentIndex + 1, totalItems)
                }
                
                // b. 移除当前项之前的所有项目
                if (currentIndex > 0) {
                    controller.removeMediaItems(0, currentIndex)
                }

                // 此时，Playlist 中只剩下【当前正在播放的项目】，且索引必然为 0
                // c. 在当前项之前插入新项目
                if (beforeItems.isNotEmpty()) {
                    controller.addMediaItems(0, beforeItems)
                }
                
                // d. 在当前项之后插入新项目 (此时当前项索引已变为 beforeItems.size)
                if (afterItems.isNotEmpty()) {
                    controller.addMediaItems(controller.mediaItemCount, afterItems)
                }
                
                // 3. 确保播放器准备就绪（增量更新通常不需要重新 prepare，除非之前是 IDLE）
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateBackground(item: MediaItem?) {
        val ivBlurBackground = findViewById<ImageView>(R.id.ivBlurBackground) ?: return
        
        // 如果是手机模式（非平板），固定使用 bg_superman 的模糊版
        // 如果是平板模式，可以继续保留随歌曲切换背景的特性（或者也统一固定，根据你的喜好）
        val backgroundData = if (isTablet) {
            val metadata = item?.mediaMetadata
            val mediaId = item?.mediaId
            val artworkUri = metadata?.artworkUri
            val prefs = getSharedPreferences("netease_covers", MODE_PRIVATE)
            val cachedCover = mediaId?.let { prefs.getString(it, null) }
            cachedCover?.toUri() ?: artworkUri ?: R.drawable.bg_superman
        } else {
            R.drawable.bg_superman
        }

        ivBlurBackground.load(backgroundData) {
            crossfade(true)
            transformations(jp.wasabeef.transformers.coil.BlurTransformation(this@HomeActivity, 25, 3))
            if (isTablet && item != null) {
                listener(onError = { _, _ ->
                    val metadata = item.mediaMetadata
                    searchNeteaseCoverForBackground(
                        item.mediaId, 
                        metadata.title?.toString(), 
                        metadata.artist?.toString(),
                        metadata.albumTitle?.toString()
                    )
                })
            }
        }
    }

    private fun searchNeteaseCoverForBackground(mediaId: String, title: String?, artist: String?, album: String? = null) {
        val currentDuration = mediaController?.currentMediaItem?.mediaMetadata?.extras?.getLong("duration_ms") ?: 0L
        lifecycleScope.launch(Dispatchers.IO) {
            val rawTitle = title ?: ""
            val query = if (album.isNullOrBlank() || album == "未知专辑") {
                if (artist.isNullOrBlank() || artist == "未知歌手") rawTitle else "$rawTitle $artist"
            } else {
                "$album $artist"
            }
            if (query.isEmpty()) return@launch

            try {
                val neteaseApi = RetrofitClient.neteaseApi
                val searchResponse = neteaseApi.searchSong(query)
                val songs = searchResponse.result?.songs ?: return@launch
                
                // 使用优化后的匹配逻辑
                val song = LyricUtils.findBestMatch(songs, rawTitle, artist, album, currentDuration)
                    ?: songs.firstOrNull() ?: return@launch

                val detailResponse = neteaseApi.getSongDetail("[{\"id\":${song.id}}]")
                val picUrl = detailResponse.songs?.firstOrNull()?.al?.picUrl?.replace("http://", "https://")
                
                if (picUrl != null) {
                    withContext(Dispatchers.Main) {
                        if (mediaController?.currentMediaItem?.mediaId == mediaId) {
                            getSharedPreferences("netease_covers", MODE_PRIVATE)
                                .edit { putString(mediaId, picUrl) }
                            
                            findViewById<ImageView>(R.id.ivBlurBackground)?.load(picUrl) {
                                crossfade(true)
                                transformations(jp.wasabeef.transformers.coil.BlurTransformation(this@HomeActivity, 25, 3))
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
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
                    // 默认冷启动为列表循环，加载同文件夹列表
                    val parentId = item.ParentId ?: item.AlbumId
                    val folderResponse = if (parentId != null) {
                        service.getItems(userId, parentId = parentId, auth = auth)
                    } else null
                    
                    val currentId = item.Id
                    val mediaItems = if (folderResponse != null && folderResponse.Items.isNotEmpty()) {
                        val sorted = folderResponse.Items.sortedBy { it.SortName ?: it.Name }
                        sorted.map { MediaItemUtils.buildMediaItem(this@HomeActivity, it, serverUrl, accessToken, userId) }
                    } else {
                        listOf(MediaItemUtils.buildMediaItem(this@HomeActivity, item, serverUrl, accessToken, userId))
                    }
                    
                    val startIndex = mediaItems.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
                    controller.setMediaItems(mediaItems, startIndex, (item.UserData?.PlaybackPositionTicks ?: 0L) / 10000)
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    controller.shuffleModeEnabled = false
                    controller.prepare()
                }
            } catch (_: Exception) { }
        }
    }

    private fun scanLibrary() {
        val service = apiService ?: return
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""
        val auth = "MediaBrowser Token=\"$accessToken\""
        activityScope.launch {
            try {
                // 1. 获取所有顶级视图
                val views = service.getUserViews(userId, auth)
                // 2. 找到 CollectionType 为 "music" 的库（即音乐库）
                val musicLibrary = views.Items.find { it.CollectionType == "music" }
                
                if (musicLibrary != null) {
                    // 3. 只刷新这个特定音乐库的 ID
                    service.refreshItem(musicLibrary.Id, auth)
                    Toast.makeText(this@HomeActivity, R.string.sync_library_started, Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("HomeActivity", "Music library not found for scanning")
                }
            } catch (_: Exception) { 
            }
        }
    }

    private fun setupFragmentLifecycleListener() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: androidx.fragment.app.FragmentManager, f: Fragment) {
                if (f is PlayerDialogFragment) {
                    isPlayerShown = true
                    refreshSystemEffect()
                }
            }
            override fun onFragmentDestroyed(fm: androidx.fragment.app.FragmentManager, f: Fragment) {
                if (f is PlayerDialogFragment) {
                    isPlayerShown = false
                    refreshSystemEffect()
                }
            }
        }, false)
    }

    // --- 极致玻璃渲染逻辑：只模糊背景，不模糊文字 ---
    fun setDockRect(rect: RectF) {
        dockRect = rect
        refreshSystemEffect()
    }

    fun applyDialogEffect(rect: RectF, radius: Float, amount: Float, height: Float, blur: Float) {
        dialogRect = rect
        dialogRadius = radius
        refreshSystemEffect()
    }

    fun clearDialogEffect() {
        dialogRect = null
        refreshSystemEffect()
    }

    private fun refreshSystemEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val container = findViewById<View>(R.id.fragment_container) ?: return
            
            // 优化 1: 如果播放页打开，或者没有需要模糊的区域，彻底移除滤镜
            // 这里不直接判断 container.renderEffect，而是依赖内部逻辑。
            if (isPlayerShown || (dockRect == null && dialogRect == null)) {
                container.setRenderEffect(null)
                lastAppliedDockRect = null
                lastAppliedDialogRect = null
                lastAppliedIsDark = null
                return
            }

            val isDark = isDarkForce()
            
            // 优化 2: 如果参数没有变化，跳过 Effect 创建（创建 RenderEffect 非常耗电）
            if (dockRect == lastAppliedDockRect && dialogRect == lastAppliedDialogRect && isDark == lastAppliedIsDark) {
                return
            }

            val dm = resources.displayMetrics
            val pxRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glassRadius, dm)
            val pxDialogRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dialogRadius, dm)
            val pxAmount = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glassAmount, dm)
            val pxHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glassHeight, dm)
            val pxBlur = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glassBlur, dm)

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
                adjustedDockRect, pxRadius, pxAmount, pxHeight, pxBlur, isDark, 
                adjustedDialogRect, pxDialogRadius
            )
            container.setRenderEffect(effect)
            
            // 记录最后一次应用的状态
            lastAppliedDockRect = RectF(dockRect)
            lastAppliedDialogRect = dialogRect?.let { RectF(it) }
            lastAppliedIsDark = isDark
        }
    }

    // --- 导航 ---
    fun showPlayer() {
        // 如果播放器已经显示，或者 MediaController 还没准备好，则不执行
        if (isPlayerShown || supportFragmentManager.findFragmentByTag("player") != null || mediaController == null) {
            return
        }
        // 如果是平板且处于横屏模式，不弹出播放界面（因为已经常驻在中间栏了）
        if (isTablet && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return
        }
        val dialog = PlayerDialogFragment()
        mediaController?.let { dialog.setPlayer(it) }
        dialog.show(supportFragmentManager, "player")
    }

    fun replaceFragment(fragment: Fragment, tag: String? = null) {
        val transaction = supportFragmentManager.beginTransaction()
        
        // 如果是手势返回，我们希望 pop 过程没有动画
        // 但 setCustomAnimations 是在 commit 时固化的。
        // 所以我们改为：如果是进入子页面，根据 isSwipingBack 状态动态设置
        transaction.setCustomAnimations(
            R.anim.ios_slide_in_right, 0,
            0, R.anim.ios_slide_out_right
        )
        
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (tag != null && currentFragment != null) {
            transaction.add(R.id.fragment_container, fragment)
            transaction.hide(currentFragment)
            transaction.addToBackStack(tag)
        } else {
            transaction.replace(R.id.fragment_container, fragment)
        }
        transaction.commit()
    }

    private fun checkUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latestRelease = RetrofitClient.githubApi.getLatestRelease()
                val latestVersion = latestRelease.tag_name.replace("v", "")
                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewerVersion(currentVersion, latestVersion)) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(latestRelease)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val curr = currentParts.getOrElse(i) { 0 }
            val late = latestParts.getOrElse(i) { 0 }
            if (late > curr) return true
            if (curr > late) return false
        }
        return false
    }

    private fun showUpdateDialog(release: GithubRelease) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("发现新版本 ${release.tag_name}")
            .setMessage("更新日志：\n${release.body}")
            .setCancelable(false) // 必须用户手动点击按钮
            .setPositiveButton("立即去下载") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, release.html_url.toUri())
                startActivity(intent)
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }

    /**
     * 手势返回专用的 pop 方法：彻底禁用动画
     */
    fun popBackStackWithNoAnim() {
        isSwipingBack = true
        // 立即执行回退
        supportFragmentManager.popBackStackImmediate()
        // 稍微延长标记时间，确保所有 Fragment 的动画回调都已完成
        findViewById<View>(android.R.id.content).postDelayed({
            isSwipingBack = false
        }, 500)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateStatusBarIcons()
        refreshSystemEffect()
        tabletPlayerHandler?.onConfigurationChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
