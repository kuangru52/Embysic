@file:Suppress("UnstableApiUsage", "OPT_IN_IS_NOT_ENABLED")

package com.kuangru52.embysic

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.compose.ui.platform.ComposeView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import jp.wasabeef.transformers.coil.BlurTransformation
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class HomeActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? by mutableStateOf(null)
        private set

    private var selectedTab by mutableStateOf("Recent")
    private var lastBackTime: Long = 0
    private var apiService: EmbyApiService? = null

    private var isPlayerShown = false

    var tuningTarget by mutableStateOf("dock")
    var wallpaperVersion by mutableIntStateOf(0)
    var wallpaperBackdrop: LayerBackdrop? by mutableStateOf(null)
    var homeBackdrop: LayerBackdrop? by mutableStateOf(null)
    var themeMode by mutableStateOf("system")
    var appLanguage by mutableStateOf("system")
    var isDevMode by mutableStateOf(value = false)
    var previousTabBeforeSettings: String = "Recent"

    // 全局壁纸模糊度 (100% 独立，绝不混淆或影响底栏 glassBlurRadius)
    var wallpaperBlurRadius by mutableFloatStateOf(18f)

    // 底栏专属 3D 玻璃参数
    var glassCornerRadius by mutableFloatStateOf(38f)
    var glassBlurRadius by mutableFloatStateOf(18f)
    var glassRefractionAmount by mutableFloatStateOf(-60f)
    var glassRefractionHeight by mutableFloatStateOf(12f)
    var glassBleedAmount by mutableFloatStateOf(10f)
    var glassBleedOpacity by mutableFloatStateOf(0.3f)
    var glassChromaMultiplier by mutableFloatStateOf(1.5f)
    var glassContrast by mutableFloatStateOf(0.15f)
    var glassWhitePoint by mutableFloatStateOf(0.20f)

    // 搜索框专属参数
    var searchCornerRadius by mutableFloatStateOf(26f)
    var searchBlurRadius by mutableFloatStateOf(18f)
    var searchRefractionAmount by mutableFloatStateOf(-60f)
    var searchRefractionHeight by mutableFloatStateOf(12f)
    var searchBleedAmount by mutableFloatStateOf(10f)
    var searchBleedOpacity by mutableFloatStateOf(0.3f)
    var searchChromaMultiplier by mutableFloatStateOf(1.5f)
    var searchContrast by mutableFloatStateOf(0.15f)
    var searchWhitePoint by mutableFloatStateOf(0.20f)

    var showGlassTuningDialog by mutableStateOf(value = false)

    val authHeader: String
        get() {
            val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
            val accessToken = prefs.getString("access_token", "") ?: ""
            return "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceName(this)}\", DeviceId=\"${MediaItemUtils.getDeviceId(this)}\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$accessToken\""
        }

    private var isTablet: Boolean = false
    private var tabletPlayerHandler: TabletPlayerHandler? = null
    var isSwipingBack: Boolean = false

    fun isDarkForce(): Boolean = true

    private val recentFragment by lazy { RecentFragment() }
    private val favoriteFragment by lazy { FavoriteFragment() }
    private val libraryFragment by lazy { LibraryFragment() }
    private val searchFragment by lazy { SearchFragment() }

    fun switchTab(tabTag: String) {
        selectedTab = tabTag
        
        val container = findViewById<FrameLayout>(R.id.fragment_container) ?: return
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        val targetFragment = when (tabTag) {
            "Recent" -> recentFragment
            "Favorite" -> favoriteFragment
            "Library" -> libraryFragment
            "Search" -> searchFragment
            else -> recentFragment
        }

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(container.id, targetFragment, tabTag)
        transaction.commitAllowingStateLoss()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("show_player", false)) {
            showPlayer()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        isTablet = (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }

        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val savedTab = savedInstanceState?.getString("selected_tab") ?: prefs.getString("last_active_tab", "Recent") ?: "Recent"
        selectedTab = savedTab

        themeMode = prefs.getString("app_theme_mode", "system") ?: "system"
        appLanguage = prefs.getString("app_language", "system") ?: "system"
        isDevMode = prefs.getBoolean("developer_mode", false)
        wallpaperBlurRadius = prefs.getFloat("wallpaper_blur_radius", prefs.getFloat("glass_blur_radius", 18f))

        glassCornerRadius = prefs.getFloat("glass_corner_radius", 38f)
        glassBlurRadius = prefs.getFloat("glass_blur_radius", 18f)
        glassRefractionAmount = prefs.getFloat("glass_refraction_amount", -60f)
        glassRefractionHeight = prefs.getFloat("glass_refraction_height", 12f)
        glassBleedAmount = prefs.getFloat("glass_bleed_amount", 10f)
        glassBleedOpacity = prefs.getFloat("glass_bleed_opacity", 0.3f)
        glassChromaMultiplier = prefs.getFloat("glass_chroma_multiplier", 1.5f)
        glassContrast = prefs.getFloat("glass_contrast", 0.15f)
        glassWhitePoint = prefs.getFloat("glass_white_point", 0.20f)

        searchCornerRadius = prefs.getFloat("search_corner_radius", 26f)
        searchBlurRadius = prefs.getFloat("search_blur_radius", 18f)
        searchRefractionAmount = prefs.getFloat("search_refraction_amount", -60f)
        searchRefractionHeight = prefs.getFloat("search_refraction_height", 12f)
        searchBleedAmount = prefs.getFloat("search_bleed_amount", 10f)
        searchBleedOpacity = prefs.getFloat("search_bleed_opacity", 0.3f)
        searchChromaMultiplier = prefs.getFloat("search_chroma_multiplier", 1.5f)
        searchContrast = prefs.getFloat("search_contrast", 0.15f)
        searchWhitePoint = prefs.getFloat("search_white_point", 0.20f)

        initApiService()

        setContent {
            val localizedContext = remember(appLanguage) {
                LanguageHelper.getLocalizedContext(this@HomeActivity, appLanguage)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
            ) {
                val isTabletLandscape = isTablet && (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

                HomeScreen(
                    isTabletLandscape = isTabletLandscape,
                    isDark = isAppDark(),
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> switchTab(tab) },
                    onShowPlayer = { showPlayer() },
                    onLibraryScan = { scanLibrary() },
                    mediaController = mediaController,
                    isDevMode = isDevMode,
                    showGlassTuningDialog = showGlassTuningDialog,
                    tuningTarget = tuningTarget,
                    onOpenGlassTuning = {
                        tuningTarget = "dock"
                        showGlassTuningDialog = true
                        updateTuningOverlay()
                    },
                    onCloseGlassTuning = { showGlassTuningDialog = false },
                    glassCornerRadius = glassCornerRadius,
                    glassBlurRadius = glassBlurRadius,
                    glassRefractionAmount = glassRefractionAmount,
                    glassRefractionHeight = glassRefractionHeight,
                    glassBleedAmount = glassBleedAmount,
                    glassBleedOpacity = glassBleedOpacity,
                    glassChromaMultiplier = glassChromaMultiplier,
                    glassContrast = glassContrast,
                    glassWhitePoint = glassWhitePoint,
                    onParamChange = { radius, blur, amount, height, bleed, opacity, chroma, cnt, wp ->
                        glassCornerRadius = radius
                        glassBlurRadius = blur
                        glassRefractionAmount = amount
                        glassRefractionHeight = height
                        glassBleedAmount = bleed
                        glassBleedOpacity = opacity
                        glassChromaMultiplier = chroma
                        glassContrast = cnt
                        glassWhitePoint = wp
                        updateBottomDockUI()
                    },
                    onReset = {
                        glassCornerRadius = 38f
                        glassBlurRadius = 18f
                        glassRefractionAmount = -60f
                        glassRefractionHeight = 12f
                        glassBleedAmount = 10f
                        glassBleedOpacity = 0.3f
                        glassChromaMultiplier = 1.5f
                        glassContrast = 0.15f
                        glassWhitePoint = 0.20f
                        updateBottomDockUI()
                    },
                    onSave = {
                        getSharedPreferences("embysic_prefs", MODE_PRIVATE).edit {
                            putFloat("glass_corner_radius", glassCornerRadius)
                            putFloat("glass_blur_radius", glassBlurRadius)
                            putFloat("glass_refraction_amount", glassRefractionAmount)
                            putFloat("glass_refraction_height", glassRefractionHeight)
                            putFloat("glass_bleed_amount", glassBleedAmount)
                            putFloat("glass_bleed_opacity", glassBleedOpacity)
                            putFloat("glass_chroma_multiplier", glassChromaMultiplier)
                            putFloat("glass_contrast", glassContrast)
                            putFloat("glass_white_point", glassWhitePoint)
                        }
                        Toast.makeText(this@HomeActivity, "已保存底栏 Liquid Glass 参数", Toast.LENGTH_SHORT).show()
                    },
                    searchCornerRadius = searchCornerRadius,
                    searchBlurRadius = searchBlurRadius,
                    searchRefractionAmount = searchRefractionAmount,
                    searchRefractionHeight = searchRefractionHeight,
                    searchBleedAmount = searchBleedAmount,
                    searchBleedOpacity = searchBleedOpacity,
                    searchChromaMultiplier = searchChromaMultiplier,
                    searchContrast = searchContrast,
                    searchWhitePoint = searchWhitePoint,
                    onSearchParamChange = { radius, blur, amount, height, bleed, opacity, chroma, cnt, wp ->
                        searchCornerRadius = radius
                        searchBlurRadius = blur
                        searchRefractionAmount = amount
                        searchRefractionHeight = height
                        searchBleedAmount = bleed
                        searchBleedOpacity = opacity
                        searchChromaMultiplier = chroma
                        searchContrast = cnt
                        searchWhitePoint = wp
                    },
                    onSearchReset = {
                        searchCornerRadius = 26f
                        searchBlurRadius = 18f
                        searchRefractionAmount = -60f
                        searchRefractionHeight = 12f
                        searchBleedAmount = 10f
                        searchBleedOpacity = 0.3f
                        searchChromaMultiplier = 1.5f
                        searchContrast = 0.15f
                        searchWhitePoint = 0.20f
                    },
                    onSearchSave = {
                        getSharedPreferences("embysic_prefs", MODE_PRIVATE).edit {
                            putFloat("search_corner_radius", searchCornerRadius)
                            putFloat("search_blur_radius", searchBlurRadius)
                            putFloat("search_refraction_amount", searchRefractionAmount)
                            putFloat("search_refraction_height", searchRefractionHeight)
                            putFloat("search_bleed_amount", searchBleedAmount)
                            putFloat("search_bleed_opacity", searchBleedOpacity)
                            putFloat("search_chroma_multiplier", searchChromaMultiplier)
                            putFloat("search_contrast", searchContrast)
                            putFloat("search_white_point", searchWhitePoint)
                        }
                        Toast.makeText(this@HomeActivity, "已保存搜索框 Liquid Glass 参数", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
                        if (selectedTab == "Settings") {
                            replaceFragment(SettingsFragment(), "Settings")
                        } else {
                            switchTab(selectedTab)
                        }
                    }
                }
            }
        }

        setupMediaController()
        setupFragmentLifecycleListener()
        updateStatusBarIcons()
        updateWallpaper()
        checkUpdate()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    if (showGlassTuningDialog) {
                        showGlassTuningDialog = false
                        return
                    }

                    val activeFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (activeFragment != null) {
                        val fields = activeFragment.javaClass.declaredFields
                        for (field in fields) {
                            if (LibraryAdapter::class.java.isAssignableFrom(field.type)) {
                                field.isAccessible = true
                                val adapter = field[activeFragment] as? LibraryAdapter
                                if (adapter?.clearExpandedDelete() == true) {
                                    return
                                }
                            }
                        }
                    }

                    // 核心逻辑：若 Fragment 回退栈中有子目录/子层级/设置 Fragment，优先 popBackStack 返回上级
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        if (selectedTab == "Settings") {
                            selectedTab = previousTabBeforeSettings
                        }
                        supportFragmentManager.popBackStack()
                        return
                    }

                    if (selectedTab != "Library") {
                        selectedTab = "Library"
                        switchTab("Library")
                        return
                    }

                    val currentTime = System.currentTimeMillis()
                    if ((currentTime - lastBackTime) < 2000) {
                        finish()
                    } else {
                        lastBackTime = currentTime
                        Toast.makeText(this@HomeActivity, R.string.press_again_to_exit, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    private fun initApiService() {
        apiService = RetrofitClient.getEmbyApiService(this)
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller

                if (controller.currentMediaItem == null) {
                    restoreLastPlayedItem(controller)
                } else {
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    controller.shuffleModeEnabled = false
                    updatePlaylistByMode(isShuffle = false)
                }

                if (isTablet) {
                    tabletPlayerHandler = TabletPlayerHandler(this, controller)
                    tabletPlayerHandler?.init()
                }
                if (!isTablet) {
                    controller.addListener(
                        object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                updateBackground(mediaItem)
                                mediaItem?.let {
                                    saveLastPlayedMetadata(it)
                                }
                            }
                        },
                    )
                    updateBackground(controller.currentMediaItem)
                }

                if (intent.getBooleanExtra("show_player", false)) {
                    showPlayer()
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun updatePlaylistByMode(isShuffle: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val controller = mediaController ?: return@launch
            val service = apiService ?: return@launch
            val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
            val userId = prefs.getString("user_id", "") ?: ""

            try {
                val currentMediaItem = controller.currentMediaItem ?: return@launch
                val extras = currentMediaItem.mediaMetadata.extras ?: return@launch
                val parentId = extras.getString("parent_id") ?: return@launch

                val folderResponse = service.getItems(
                    userId = userId,
                    parentId = parentId,
                    sortBy = if (isShuffle) "Random" else "SortName",
                    sortOrder = "Ascending",
                    auth = authHeader,
                )

                val folderItems = folderResponse.Items.filter { (!it.IsFolder) && (it.Type == "Audio") }
                if (folderItems.isEmpty()) return@launch

                val currentId = currentMediaItem.mediaId.split("_").firstOrNull() ?: currentMediaItem.mediaId
                val splitIndex = folderItems.indexOfFirst { it.Id == currentId }.coerceAtLeast(0)

                val serverUrl = prefs.getString("server_url", "") ?: ""
                val accessToken = prefs.getString("access_token", "") ?: ""

                val beforeItems = mutableListOf<MediaItem>()
                val afterItems = mutableListOf<MediaItem>()

                if (splitIndex > 0) {
                    beforeItems.addAll(
                        folderItems.subList(0, splitIndex).map {
                            MediaItemUtils.buildMediaItem(this@HomeActivity, it, serverUrl, accessToken, userId)
                        },
                    )
                }

                if (splitIndex < (folderItems.size - 1)) {
                    afterItems.addAll(
                        folderItems.subList(splitIndex + 1, folderItems.size).map {
                            MediaItemUtils.buildMediaItem(this@HomeActivity, it, serverUrl, accessToken, userId)
                        },
                    )
                }

                withContext(Dispatchers.Main) {
                    val currentIndex = controller.currentMediaItemIndex
                    val totalItems = controller.mediaItemCount

                    if (totalItems > (currentIndex + 1)) {
                        controller.removeMediaItems(currentIndex + 1, totalItems)
                    }

                    if (currentIndex > 0) {
                        controller.removeMediaItems(0, currentIndex)
                    }

                    if (beforeItems.isNotEmpty()) {
                        controller.addMediaItems(0, beforeItems)
                    }

                    if (afterItems.isNotEmpty()) {
                        controller.addMediaItems(controller.mediaItemCount, afterItems)
                    }

                    if (controller.playbackState == Player.STATE_IDLE) {
                        controller.prepare()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun applyWallpaperBlur(imageView: ImageView) {
        val blurPx = wallpaperBlurRadius.coerceIn(0f, 100f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            imageView.setRenderEffect(
                if (blurPx > 0.1f) RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP) else null
            )
        }
    }

    fun updateWallpaperBlurLive(blurRadius: Float) {
        wallpaperBlurRadius = blurRadius
        val blurPx = blurRadius.coerceIn(0f, 100f)
        val rootContent = (window?.decorView as? android.view.ViewGroup) ?: return
        val backgroundViews = mutableListOf<ImageView>()
        fun findAllBgViews(v: View) {
            if (v is ImageView && (v.id == R.id.ivBlurBackground || v.id == R.id.ivFragmentBackground)) {
                backgroundViews.add(v)
            } else if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    findAllBgViews(v.getChildAt(i))
                }
            }
        }
        findAllBgViews(rootContent)

        for (iv in backgroundViews) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                iv.setRenderEffect(
                    if (blurPx > 0.1f) RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP) else null
                )
            }
        }
    }

    fun updateAppTheme(mode: String) {
        themeMode = mode
        getSharedPreferences("embysic_prefs", MODE_PRIVATE).edit {
            putString("app_theme_mode", mode)
        }
        updateStatusBarIcons()
        updateWallpaper()
    }

    fun updateAppLanguage(langCode: String) {
        appLanguage = langCode
        getSharedPreferences("embysic_prefs", MODE_PRIVATE).edit {
            putString("app_language", langCode)
        }
    }

    fun updateWallpaper() {
        val mode = WallpaperManager.getMode(this)
        val blurPx = wallpaperBlurRadius.coerceIn(0f, 100f)
        val isDark = isAppDark()

        lifecycleScope.launch {
            val rootContent = window?.decorView as? android.view.ViewGroup ?: return@launch
            val backgroundViews = mutableListOf<ImageView>()
            fun findAllBgViews(v: View) {
                if (v is ImageView && (v.id == R.id.ivBlurBackground || v.id == R.id.ivFragmentBackground)) {
                    backgroundViews.add(v)
                } else if (v is android.view.ViewGroup) {
                    for (i in 0 until v.childCount) {
                        findAllBgViews(v.getChildAt(i))
                    }
                }
            }
            findAllBgViews(rootContent)

            if (mode == "none") {
                val noneColorInt = if (isDark) android.graphics.Color.rgb(18, 18, 24) else android.graphics.Color.rgb(248, 250, 252)
                for (iv in backgroundViews) {
                    iv.visibility = View.VISIBLE
                    iv.alpha = 1.0f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        iv.setRenderEffect(null)
                    }
                    iv.setImageDrawable(noneColorInt.toDrawable())
                }
                return@launch
            }

            val source = WallpaperManager.getActiveWallpaperSource(this@HomeActivity)
            for (iv in backgroundViews) {
                iv.visibility = View.VISIBLE
                iv.alpha = if (iv.id == R.id.ivBlurBackground) 0.6f else 1.0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    iv.setRenderEffect(
                        if (blurPx > 0.1f) RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP) else null
                    )
                }
                if (source is Int) {
                    iv.setImageResource(source)
                    wallpaperVersion++
                } else {
                    iv.load(source) {
                        crossfade(enable = false)
                        allowHardware(enable = false)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            transformations(BlurTransformation(this@HomeActivity, blurPx.toInt().coerceIn(1, 100), 4))
                        }
                        listener(
                            onSuccess = { _, _ ->
                                wallpaperVersion++
                            }
                        )
                    }
                }
            }
        }
    }

    fun updateBackground(item: MediaItem?) {
        if (WallpaperManager.getMode(this) != "default") {
            updateWallpaper()
            return
        }
        val ivBlurBackground = findViewById<ImageView>(R.id.ivBlurBackground) ?: return
        
        val fallbackUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=1920"
        val backgroundData: Any = if (isTablet) {
            val metadata = item?.mediaMetadata
            val mediaId = item?.mediaId
            val artworkUri = metadata?.artworkUri
            val prefs = getSharedPreferences("netease_covers", MODE_PRIVATE)
            val cachedCover = mediaId?.let { prefs.getString(it, null) }
            cachedCover?.toUri() ?: artworkUri ?: fallbackUrl
        } else {
            fallbackUrl
        }

        ivBlurBackground.load(backgroundData) {
            crossfade(enable = true)
        }
    }

    fun saveLastPlayedMetadata(item: MediaItem) {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val title = item.mediaMetadata.title?.toString()
        if (!title.isNullOrEmpty()) {
            prefs.edit {
                putString("last_played_id", item.mediaId)
                putString("last_played_title", title)
                putString("last_played_artist", item.mediaMetadata.artist?.toString() ?: "")
                putString("last_played_album", item.mediaMetadata.albumTitle?.toString() ?: "")
                putString("last_played_artwork", item.mediaMetadata.artworkUri?.toString() ?: "")
            }
        }
    }

    private fun restoreLastPlayedItem(controller: MediaController) {
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val lastId = prefs.getString("last_played_id", null)
        val lastTitle = prefs.getString("last_played_title", null)
        val lastArtist = prefs.getString("last_played_artist", "") ?: ""
        val lastAlbum = prefs.getString("last_played_album", "") ?: ""
        val lastArtwork = prefs.getString("last_played_artwork", null)

        // 1. 冷启动 0ms 瞬间本地离线渲染底栏，播放条绝不为空
        if (!lastId.isNullOrEmpty() && !lastTitle.isNullOrEmpty()) {
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(lastTitle)
                .setArtist(lastArtist)
                .setAlbumTitle(lastAlbum)
                .setArtworkUri(lastArtwork?.toUri())
                .build()

            val localItem = MediaItem.Builder()
                .setMediaId(lastId)
                .setUri(lastArtwork?.toUri() ?: "http://dummy/audio.mp3".toUri())
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(localItem)
            controller.prepare()
        }

        val service = apiService ?: return
        val userId = prefs.getString("user_id", "") ?: ""
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val accessToken = prefs.getString("access_token", "") ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = service.getRecentlyPlayedItems(userId = userId, limit = 1, auth = authHeader)
                response.Items.firstOrNull()?.let { item ->
                    val parentId = item.ParentId ?: item.AlbumId
                    val folderResponse = parentId?.let {
                        service.getItems(userId, parentId = it, auth = authHeader)
                    }
                    
                    val currentId = item.Id
                    val mediaItems = if ((folderResponse != null) && folderResponse.Items.isNotEmpty()) {
                        val sorted = folderResponse.Items.sortedBy { it.SortName ?: it.Name }
                        sorted.map { MediaItemUtils.buildMediaItem(this@HomeActivity, it, serverUrl, accessToken, userId) }
                    } else {
                        listOf(MediaItemUtils.buildMediaItem(this@HomeActivity, item, serverUrl, accessToken, userId))
                    }
                    
                    val startIndex = mediaItems.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
                    withContext(Dispatchers.Main) {
                        controller.setMediaItems(mediaItems, startIndex, (item.UserData?.PlaybackPositionTicks ?: 0L) / 10000)
                        controller.prepare()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun scanLibrary() {
        Toast.makeText(this, "正在扫描曲库...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiService?.refreshItem("1", authHeader)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "扫描完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun isAppDark(): Boolean {
        return when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun updateStatusBarIcons() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            val dark = isAppDark()
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun setupFragmentLifecycleListener() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: androidx.fragment.app.FragmentManager, f: Fragment) {
                super.onFragmentResumed(fm, f)
                if (f is PlayerDialogFragment) {
                    isPlayerShown = true
                }
                if (f is SettingsFragment) {
                    selectedTab = "Settings"
                }
            }

            override fun onFragmentPaused(fm: androidx.fragment.app.FragmentManager, f: Fragment) {
                super.onFragmentPaused(fm, f)
                if (f is PlayerDialogFragment) {
                    isPlayerShown = false
                }
                if (f is SettingsFragment && (selectedTab == "Settings")) {
                    selectedTab = "Library"
                }
            }
        }, false)
    }

    fun updateBottomDockUI() {}

    fun updateTuningOverlay() {
        val tuningContainer = findViewById<ComposeView>(R.id.tuning_container) ?: return
        if (!showGlassTuningDialog) {
            tuningContainer.visibility = View.GONE
            return
        }
        tuningContainer.visibility = View.VISIBLE
        tuningContainer.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = Alignment.TopStart,
            ) {
                if (tuningTarget == "search") {
                    SearchGlassTuningDialog(
                        cornerRadius = searchCornerRadius,
                        blurRadius = searchBlurRadius,
                        refractionAmount = searchRefractionAmount,
                        refractionHeight = searchRefractionHeight,
                        bleedAmount = searchBleedAmount,
                        bleedOpacity = searchBleedOpacity,
                        chromaMultiplier = searchChromaMultiplier,
                        contrast = searchContrast,
                        whitePoint = searchWhitePoint,
                        onParamChange = { radius, blur, amount, height, bleed, opacity, chroma, cnt, wp ->
                            searchCornerRadius = radius
                            searchBlurRadius = blur
                            searchRefractionAmount = amount
                            searchRefractionHeight = height
                            searchBleedAmount = bleed
                            searchBleedOpacity = opacity
                            searchChromaMultiplier = chroma
                            searchContrast = cnt
                            searchWhitePoint = wp
                        },
                        onReset = {
                            searchCornerRadius = 26f
                            searchBlurRadius = 18f
                            searchRefractionAmount = -60f
                            searchRefractionHeight = 12f
                            searchBleedAmount = 10f
                            searchBleedOpacity = 0.3f
                            searchChromaMultiplier = 1.5f
                            searchContrast = 0.15f
                            searchWhitePoint = 0.20f
                        },
                        onSave = {
                            getSharedPreferences("embysic_prefs", MODE_PRIVATE).edit {
                                putFloat("search_corner_radius", searchCornerRadius)
                                putFloat("search_blur_radius", searchBlurRadius)
                                putFloat("search_refraction_amount", searchRefractionAmount)
                                putFloat("search_refraction_height", searchRefractionHeight)
                                putFloat("search_bleed_amount", searchBleedAmount)
                                putFloat("search_bleed_opacity", searchBleedOpacity)
                                putFloat("search_chroma_multiplier", searchChromaMultiplier)
                                putFloat("search_contrast", searchContrast)
                                putFloat("search_white_point", searchWhitePoint)
                            }
                            Toast.makeText(this@HomeActivity, "已保存搜索框 Liquid Glass 参数", Toast.LENGTH_SHORT).show()
                        },
                        onClose = {
                            showGlassTuningDialog = false
                            updateTuningOverlay()
                        },
                    )
                } else {
                    DockGlassTuningDialog(
                        cornerRadius = glassCornerRadius,
                        blurRadius = glassBlurRadius,
                        refractionAmount = glassRefractionAmount,
                        refractionHeight = glassRefractionHeight,
                        bleedAmount = glassBleedAmount,
                        bleedOpacity = glassBleedOpacity,
                        chromaMultiplier = glassChromaMultiplier,
                        contrast = glassContrast,
                        whitePoint = glassWhitePoint,
                        onParamChange = { radius, blur, amount, height, bleed, opacity, chroma, cnt, wp ->
                            val blurChanged = glassBlurRadius != blur
                            glassCornerRadius = radius
                            glassBlurRadius = blur
                            glassRefractionAmount = amount
                            glassRefractionHeight = height
                            glassBleedAmount = bleed
                            glassBleedOpacity = opacity
                            glassChromaMultiplier = chroma
                            glassContrast = cnt
                            glassWhitePoint = wp
                            if (blurChanged) {
                                updateWallpaper()
                            }
                        },
                        onReset = {
                            glassCornerRadius = 38f
                            glassBlurRadius = 18f
                            glassRefractionAmount = -60f
                            glassRefractionHeight = 12f
                            glassBleedAmount = 10f
                            glassBleedOpacity = 0.3f
                            glassChromaMultiplier = 1.5f
                            glassContrast = 0.15f
                            glassWhitePoint = 0.20f
                            updateWallpaper()
                        },
                        onSave = {
                            getSharedPreferences("embysic_prefs", MODE_PRIVATE).edit {
                                putFloat("glass_corner_radius", glassCornerRadius)
                                putFloat("glass_blur_radius", glassBlurRadius)
                                putFloat("glass_refraction_amount", glassRefractionAmount)
                                putFloat("glass_refraction_height", glassRefractionHeight)
                                putFloat("glass_bleed_amount", glassBleedAmount)
                                putFloat("glass_bleed_opacity", glassBleedOpacity)
                                putFloat("glass_chroma_multiplier", glassChromaMultiplier)
                                putFloat("glass_contrast", glassContrast)
                                putFloat("glass_white_point", glassWhitePoint)
                            }
                            Toast.makeText(this@HomeActivity, "已保存底栏 Liquid Glass 参数", Toast.LENGTH_SHORT).show()
                        },
                        onClose = {
                            showGlassTuningDialog = false
                            updateTuningOverlay()
                        },
                    )
                }
            }
        }
    }

    fun showPlayer() {
        if ((isPlayerShown || (supportFragmentManager.findFragmentByTag("player") != null)) || (mediaController == null)) {
            return
        }
        if (isTablet && (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)) {
            return
        }
        val dialog = PlayerDialogFragment()
        mediaController?.let { dialog.setPlayer(it) }
        dialog.show(supportFragmentManager, "player")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        tabletPlayerHandler?.onConfigurationChanged()
    }

    fun replaceFragment(fragment: Fragment, tag: String? = null) {
        if (fragment is SettingsFragment) {
            if (selectedTab != "Settings") {
                previousTabBeforeSettings = selectedTab
            }
            selectedTab = "Settings"
        }
        val fragmentTag = tag ?: fragment.javaClass.simpleName
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            R.anim.ios_slide_in_right, 0,
            0, R.anim.ios_slide_out_right,
        )
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment != null) {
            transaction.add(R.id.fragment_container, fragment)
            transaction.hide(currentFragment)
            transaction.addToBackStack(fragmentTag)
        } else {
            transaction.replace(R.id.fragment_container, fragment, fragmentTag)
        }
        transaction.commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selected_tab", selectedTab)
        getSharedPreferences("embysic_prefs", MODE_PRIVATE).edit {
            putString("last_active_tab", selectedTab)
        }
    }

    fun popBackStackWithNoAnim() {
        if (selectedTab == "Settings") {
            selectedTab = previousTabBeforeSettings
        }
        supportFragmentManager.popBackStack()
    }

    private fun checkUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latestRelease = RetrofitClient.githubApi.getLatestRelease()
                val latestVersion = latestRelease.tag_name.replace("v", "")

                if (isNewerVersion(latestVersion)) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(latestRelease)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Check update failed", e)
            }
        }
    }

    private fun isNewerVersion(latestVersion: String): Boolean {
        val currentVersion = BuildConfig.VERSION_NAME
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latestVersion.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    private fun showUpdateDialog(release: GithubRelease) {
        val message = "版本：${release.tag_name}\n\n更新内容：\n${release.body}"
        val downloadUrl = release.html_url

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("发现新版本")
            .setMessage(message)
            .setPositiveButton("立即更新") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
                startActivity(intent)
            }
            .setNegativeButton("稍后", null)
            .show()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun HomeScreen(
    isTabletLandscape: Boolean,
    isDark: Boolean = true,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onShowPlayer: () -> Unit,
    onLibraryScan: () -> Unit,
    mediaController: MediaController?,
    isDevMode: Boolean,
    showGlassTuningDialog: Boolean,
    tuningTarget: String = "dock",
    onOpenGlassTuning: () -> Unit,
    onCloseGlassTuning: () -> Unit,
    glassCornerRadius: Float,
    glassBlurRadius: Float,
    glassRefractionAmount: Float,
    glassRefractionHeight: Float,
    glassBleedAmount: Float,
    glassBleedOpacity: Float,
    glassChromaMultiplier: Float,
    glassContrast: Float,
    glassWhitePoint: Float,
    onParamChange: (Float, Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    searchCornerRadius: Float = 26f,
    searchBlurRadius: Float = 18f,
    searchRefractionAmount: Float = -60f,
    searchRefractionHeight: Float = 12f,
    searchBleedAmount: Float = 10f,
    searchBleedOpacity: Float = 0.3f,
    searchChromaMultiplier: Float = 1.5f,
    searchContrast: Float = 0.15f,
    searchWhitePoint: Float = 0.20f,
    onSearchParamChange: (Float, Float, Float, Float, Float, Float, Float, Float, Float) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onSearchReset: () -> Unit = {},
    onSearchSave: () -> Unit = {},
    onContainerReady: () -> Unit = {},
) {
    val isPreview = LocalInspectionMode.current
    val wallpaperBackdrop = rememberLayerBackdrop()
    val homeBackdrop = rememberLayerBackdrop()
    val homeContext = LocalContext.current
    val homeActivity = remember(homeContext) { homeContext.findHomeActivity() }
    val currentIsDark = if (isTabletLandscape) true else isDark
    val currentWallpaperVersion = homeActivity?.wallpaperVersion ?: 0

    SideEffect {
        homeActivity?.wallpaperBackdrop = wallpaperBackdrop
        homeActivity?.homeBackdrop = homeBackdrop
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1 (底层): 壁纸 + 歌曲列表容器 -> 挂载 Modifier.layerBackdrop(homeBackdrop) 实时抓取像素画面供底栏采样！
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isPreview) Modifier.layerBackdrop(homeBackdrop) else Modifier),
        ) {
            if (isPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor(0xFF1E1E2C)),
                )
            } else {
                // 1.1 壁纸底层 -> 挂载 layerBackdrop(wallpaperBackdrop) 专门捕获壁纸图像供卡片采样！
                key(currentWallpaperVersion) {
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply {
                                id = R.id.ivBlurBackground
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        update = { iv ->
                            (iv.context as? HomeActivity)?.updateWallpaper()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!isPreview) Modifier.layerBackdrop(wallpaperBackdrop) else Modifier),
                    )
                }

                // 1.2 Fragment 容器（包含 LibraryFragment, RecentFragment, SwipeBackLayout 等）
                AndroidView(
                    factory = { context ->
                        FrameLayout(context).apply {
                            id = R.id.fragment_container
                            post { onContainerReady() }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Layer 2 & 3 (中层玻璃底座 + 顶层前景内容): 只有在非 Settings 状态下才显示底栏！
        if (selectedTab != "Settings") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                BottomTabs(
                    controller = mediaController,
                    selectedTab = selectedTab,
                    onPlayerClick = onShowPlayer,
                    onNavigation = onTabSelected,
                    onLibraryScan = onLibraryScan,
                    isDevMode = isDevMode,
                    isDark = currentIsDark,
                    onOpenGlassTuning = onOpenGlassTuning,
                    cornerRadius = glassCornerRadius,
                    blurRadius = glassBlurRadius,
                    refractionAmount = glassRefractionAmount,
                    refractionHeight = glassRefractionHeight,
                    bleedAmount = glassBleedAmount,
                    bleedOpacity = glassBleedOpacity,
                    chromaMultiplier = glassChromaMultiplier,
                    contrast = glassContrast,
                    whitePoint = glassWhitePoint,
                    backdrop = homeBackdrop,
                )
            }
        }

        // Layer 4 (最外层/调参 Overlay)
        if (showGlassTuningDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = Alignment.TopStart,
            ) {
                if (tuningTarget == "search") {
                    SearchGlassTuningDialog(
                        cornerRadius = searchCornerRadius,
                        blurRadius = searchBlurRadius,
                        refractionAmount = searchRefractionAmount,
                        refractionHeight = searchRefractionHeight,
                        bleedAmount = searchBleedAmount,
                        bleedOpacity = searchBleedOpacity,
                        chromaMultiplier = searchChromaMultiplier,
                        contrast = searchContrast,
                        whitePoint = searchWhitePoint,
                        onParamChange = onSearchParamChange,
                        onReset = onSearchReset,
                        onSave = onSearchSave,
                        onClose = onCloseGlassTuning,
                    )
                } else {
                    DockGlassTuningDialog(
                        cornerRadius = glassCornerRadius,
                        blurRadius = glassBlurRadius,
                        refractionAmount = glassRefractionAmount,
                        refractionHeight = glassRefractionHeight,
                        bleedAmount = glassBleedAmount,
                        bleedOpacity = glassBleedOpacity,
                        chromaMultiplier = glassChromaMultiplier,
                        contrast = glassContrast,
                        whitePoint = glassWhitePoint,
                        onParamChange = onParamChange,
                        onReset = onReset,
                        onSave = onSave,
                        onClose = onCloseGlassTuning,
                    )
                }
            }
        }

        AndroidView(
            factory = { context ->
                HeartLayout(context).apply {
                    id = R.id.heartLayout
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Suppress("Unused")
@Composable
fun HomeScreenPreviewContent(isTabletLandscape: Boolean) {
    if (isTabletLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
            )
        }
    }
}

@Suppress("UnstableApiUsage")
@OptIn(UnstableApi::class)
fun android.content.Context.findHomeActivity(): HomeActivity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is HomeActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
