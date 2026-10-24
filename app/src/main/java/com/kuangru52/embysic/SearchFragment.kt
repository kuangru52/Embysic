package com.kuangru52.embysic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

@UnstableApi
class SearchFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if ((activity as? HomeActivity)?.isSwipingBack == true) {
            return object : Animation() {}.apply { duration = 0 }
        }
        if (nextAnim != 0) {
            try {
                return android.view.animation.AnimationUtils.loadAnimation(context, nextAnim)
            } catch (_: Exception) {}
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    private lateinit var ivFragmentBackground: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: LibraryAdapter

    private var fragmentBackdrop: LayerBackdrop? = null

    private var apiService: EmbyApiService? = null
    private var serverUrl = ""
    private var accessToken = ""
    private var userId = ""

    private val authHeader: String
        get() {
            val ctx = context ?: return ""
            return "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceName(ctx)}\", DeviceId=\"${MediaItemUtils.getDeviceId(ctx)}\", Version=\"${BuildConfig.VERSION_NAME}\", Token=\"$accessToken\""
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                val context = LocalContext.current
                val homeActivity = remember(context) { context.findHomeActivity() }
                val currentThemeMode = homeActivity?.themeMode ?: "system"
                val appLanguage = homeActivity?.appLanguage ?: "system"

                val isDark = when (currentThemeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }

                val localizedContext = remember(appLanguage) {
                    LanguageHelper.getLocalizedContext(context, appLanguage)
                }

                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedContext.resources.configuration,
                ) {
                    val searchBackdrop = rememberLayerBackdrop()
                    fragmentBackdrop = searchBackdrop
                    SideEffect {
                        homeActivity?.wallpaperBackdrop = searchBackdrop
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. 全屏壁纸底层：单独挂载 layerBackdrop(searchBackdrop)，纯抓取背景图！
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    id = R.id.ivFragmentBackground
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                    )
                                    ivFragmentBackground = this
                                    syncBackground()
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(searchBackdrop),
                        )

                        // 2. RecyclerView 列表与 ProgressBar 视图层
                        AndroidView(
                            factory = { context ->
                                val root = FrameLayout(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                }

                                val contentContainer = FrameLayout(context).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                    )
                                }

                                ViewCompat.setOnApplyWindowInsetsListener(contentContainer) { v, insets ->
                                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                                    v.setPadding(0, systemBars.top, 0, 0)
                                    insets
                                }

                                recyclerView = RecyclerView(context).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                    ).apply {
                                        topMargin = (80 * context.resources.displayMetrics.density).toInt()
                                    }
                                    layoutManager = LinearLayoutManager(context)
                                    setPadding(0, 0, 0, (180 * context.resources.displayMetrics.density).toInt())
                                    clipToPadding = false
                                }

                                progressBar = ProgressBar(context).apply {
                                    val size = (48 * context.resources.displayMetrics.density).toInt()
                                    layoutParams = FrameLayout.LayoutParams(size, size).apply {
                                        gravity = android.view.Gravity.CENTER
                                    }
                                    visibility = View.GONE
                                }

                                contentContainer.addView(recyclerView)
                                contentContainer.addView(progressBar)
                                root.addView(contentContainer)

                                setupRecyclerView()
                                loadPrefs()
                                initApiService()

                                root
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // 3. 顶层纯 Compose 搜索框：挂载状态栏 padding，直接订阅 HomeActivity 的 9 项 State，100% 实时跟手重绘！
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding(),
                        ) {
                            XiaomiSearchBar(
                                backdrop = searchBackdrop,
                                isDark = isDark,
                            ) { query -> performSearch(query) }
                        }
                    }
                }
            }
        }
        return composeView
    }

    @Composable
    fun XiaomiSearchBar(
        backdrop: LayerBackdrop? = null,
        isDark: Boolean = true,
        onSearch: (String) -> Unit,
    ) {
        var text by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        val context = LocalContext.current
        val homeActivity = remember(context) { context.findHomeActivity() }

        val searchCornerRadius = homeActivity?.searchCornerRadius ?: 26f
        val searchBlurRadius = homeActivity?.searchBlurRadius ?: 18f
        val searchRefractionAmount = homeActivity?.searchRefractionAmount ?: -60f
        val searchRefractionHeight = homeActivity?.searchRefractionHeight ?: 12f
        val searchBleedAmount = homeActivity?.searchBleedAmount ?: 10f
        val searchBleedOpacity = homeActivity?.searchBleedOpacity ?: 0.3f
        val searchChromaMultiplier = homeActivity?.searchChromaMultiplier ?: 1.5f
        val searchContrast = homeActivity?.searchContrast ?: 0.15f
        val searchWhitePoint = homeActivity?.searchWhitePoint ?: 0.20f

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        val isDevMode = homeActivity?.isDevMode ?: false

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(searchCornerRadius.dp))
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(searchCornerRadius.dp) },
                            effects = {
                                listOf(
                                    vibrancy(),
                                    blur(radius = searchBlurRadius),
                                    lens(
                                        refractionAmount = kotlin.math.abs(searchRefractionAmount),
                                        refractionHeight = searchRefractionHeight.coerceAtMost(searchCornerRadius),
                                    ),
                                )
                            },
                        )
                    } else {
                        Modifier.background(Color(0x331E293B), RoundedCornerShape(searchCornerRadius.dp))
                    },
                )
                .then(
                    if (isDevMode) {
                        Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 12f) {
                                    homeActivity.let {
                                        it.tuningTarget = "search"
                                        it.showGlassTuningDialog = true
                                        it.updateTuningOverlay()
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if ((size.width <= 1f) || (size.height <= 1f)) return@Canvas

                val strokeWidthPx = (1.2f + (searchWhitePoint * 0.8f)).dp.toPx()
                val cornerPx = searchCornerRadius.dp.toPx()
                val corner = CornerRadius(cornerPx, cornerPx)

                if ((searchBleedAmount > 0.1f) && (searchBleedOpacity > 0.01f)) {
                    val bleedAlpha = (searchBleedOpacity * 0.35f).coerceIn(0.02f, 0.5f)
                    val bleedColor = if (isDark) Color(0xFF38BDF8).copy(alpha = bleedAlpha) else Color.White.copy(alpha = bleedAlpha)
                    drawRoundRect(
                        color = bleedColor,
                        cornerRadius = corner,
                    )
                }

                val rimAlpha = (0.25f + (searchChromaMultiplier * 0.1f) + (searchContrast * 0.15f)).coerceIn(0.15f, 0.95f)
                val rimColor = if (isDark) Color.White.copy(alpha = rimAlpha) else Color.Gray.copy(alpha = rimAlpha)

                drawRoundRect(
                    color = rimColor,
                    cornerRadius = corner,
                    style = Stroke(width = strokeWidthPx),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF0F172A),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onSearch(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(if (isDark) Color.White else Color(0xFF007AFF)),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_hint),
                                    style = TextStyle(
                                        color = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF64748B),
                                        fontSize = 16.sp,
                                    ),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            text = ""
                            onSearch("")
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear),
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF475569),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        (activity as? HomeActivity)?.replaceFragment(SettingsFragment(), "Settings")
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.title_settings),
                        tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF0F172A),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(
            onItemClick = { item -> handleItemClick(item) },
            onItemDelete = { item -> confirmDelete(item) },
            backdropProvider = { fragmentBackdrop },
        )
        recyclerView.adapter = adapter
    }

    private fun handleItemClick(item: EmbyItem) {
        if (item.IsFolder) {
            (activity as? HomeActivity)?.replaceFragment(
                LibraryFragment.newInstance(item.Id, item.Name),
                "Library_${item.Id}",
            )
        } else {
            playMusic(item)
        }
    }

    internal fun syncBackground() {
        if (!isAdded || !::ivFragmentBackground.isInitialized) return

        val homeActivity = activity as? HomeActivity
        if (WallpaperManager.getMode(requireContext()) == "none") {
            val isDark = homeActivity?.isAppDark() ?: false
            val noneColorInt = if (isDark) android.graphics.Color.rgb(18, 18, 24) else android.graphics.Color.rgb(248, 250, 252)
            ivFragmentBackground.setImageDrawable(noneColorInt.toDrawable())
            ivFragmentBackground.alpha = 1.0f
            ivFragmentBackground.visibility = View.VISIBLE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                ivFragmentBackground.setRenderEffect(null)
            }
            return
        }

        val isTabletLandscape = ((resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE)
                && (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)

        if (isTabletLandscape) {
            ivFragmentBackground.visibility = View.GONE
            return
        }

        val activityBg = homeActivity?.findViewById<ImageView>(R.id.ivBlurBackground)
        if ((activityBg != null) && (activityBg.drawable != null)) {
            ivFragmentBackground.setImageDrawable(activityBg.drawable.constantState?.newDrawable() ?: activityBg.drawable)
            ivFragmentBackground.alpha = 1.0f
            ivFragmentBackground.visibility = View.VISIBLE
            homeActivity.applyWallpaperBlur(ivFragmentBackground)
        } else {
            lifecycleScope.launch {
                val ctx = context ?: return@launch
                val source = WallpaperManager.getActiveWallpaperSource(ctx)
                ivFragmentBackground.load(source) {
                    crossfade(enable = false)
                    allowHardware(enable = false)
                }
                ivFragmentBackground.alpha = 1.0f
                ivFragmentBackground.visibility = View.VISIBLE
                homeActivity?.applyWallpaperBlur(ivFragmentBackground)
            }
        }
    }

    private fun confirmDelete(item: EmbyItem) {
        val ctx = context ?: return
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.delete_msg, item.Name))
            .setPositiveButton(R.string.confirm_delete) { _, _ -> deleteItem(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteItem(item: EmbyItem) {
        val service = apiService ?: return
        lifecycleScope.launch {
            try {
                service.deleteItem(item.Id, authHeader)
                Toast.makeText(context, getString(R.string.deleted_success, item.Name), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SearchFragment", "Error deleting item", e)
                Toast.makeText(context, getString(R.string.delete_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            adapter.submitList(emptyList())
            return
        }

        if (userId.isEmpty() || accessToken.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = apiService?.getItems(
                    userId = userId,
                    searchTerm = query,
                    includeItemTypes = "Audio",
                    recursive = true,
                    limit = 50,
                    auth = authHeader,
                )
                val items = response?.Items ?: emptyList()
                val ctx = context ?: return@launch
                adapter.submitList(items, ctx)
            } catch (e: Exception) {
                Log.e("SearchFragment", "Search error", e)
                Toast.makeText(context, getString(R.string.load_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun playMusic(item: EmbyItem) {
        val controller = (activity as? HomeActivity)?.mediaController ?: return

        val mediaItem = MediaItemUtils.buildMediaItem(requireContext(), item, serverUrl, accessToken, userId)
        controller.setMediaItem(mediaItem)
        
        controller.prepare()
        controller.play()
        
        (activity as? HomeActivity)?.updatePlaylistByMode(controller.shuffleModeEnabled)
        (activity as? HomeActivity)?.showPlayer()
    }

    private fun loadPrefs() {
        val ctx = context ?: return
        ctx.getSharedPreferences("embysic_prefs", AppCompatActivity.MODE_PRIVATE).apply {
            serverUrl = getString("server_url", "") ?: ""
            accessToken = getString("access_token", "") ?: ""
            userId = getString("user_id", "") ?: ""
        }
    }

    private fun initApiService() {
        val ctx = context ?: return
        apiService = RetrofitClient.getEmbyApiService(ctx)
    }

    override fun onResume() {
        super.onResume()
        syncBackground()
    }
}
