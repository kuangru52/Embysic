package com.kuangru52.embysic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
class SettingsFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (!enter && ((activity as? HomeActivity)?.isSwipingBack == true)) {
            return AnimationUtils.loadAnimation(context, R.anim.ios_slide_out_right).apply {
                duration = 0
            }
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val frameRoot = android.widget.FrameLayout(requireContext())
        val bg = android.widget.ImageView(requireContext()).apply {
            id = R.id.ivFragmentBackground
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val homeActivity = activity as? HomeActivity
        val activityBg = homeActivity?.findViewById<android.widget.ImageView>(R.id.ivBlurBackground)
        if ((activityBg != null) && (activityBg.drawable != null)) {
            bg.setImageDrawable(activityBg.drawable.constantState?.newDrawable() ?: activityBg.drawable)
            homeActivity.applyWallpaperBlur(bg)
        }
        frameRoot.addView(bg)

        var swipeBackLayoutRef: SwipeBackLayout? = null

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                SettingsContent(
                    onBackClick = {
                        parentFragmentManager.popBackStack()
                    },
                    onAboutClick = {
                        val intent = Intent(requireContext(), DonationActivity::class.java)
                        startActivity(intent)
                    },
                ) { sliderView ->
                    swipeBackLayoutRef?.addIgnoreView(sliderView)
                }
            }
        }
        frameRoot.addView(composeView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val swipeBack = SwipeBackLayout(requireContext()).apply {
            id = R.id.swipeBackLayout
            addView(frameRoot, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            
            setOnSwipeBackListener {
                (activity as? HomeActivity)?.popBackStackWithNoAnim()
            }
        }
        swipeBackLayoutRef = swipeBack

        return swipeBack
    }
}

@UnstableApi
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsContent(
    onBackClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onSliderViewCreated: (View) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE) }

    val homeActivity = remember(context) { context.findHomeActivity() }
    val isDark = homeActivity?.isAppDark() ?: isSystemInDarkTheme()

    var themeMode by remember { mutableStateOf(homeActivity?.themeMode ?: prefs.getString("app_theme_mode", "system") ?: "system") }
    var currentLanguage by remember { mutableStateOf(prefs.getString("app_language", "system") ?: "system") }
    var wallpaperMode by remember { mutableStateOf(WallpaperManager.getMode(context)) }
    var isDevMode by remember { mutableStateOf(prefs.getBoolean("developer_mode", false)) }
    var wallpaperBlur by remember {
        mutableFloatStateOf(homeActivity?.wallpaperBlurRadius ?: prefs.getFloat("wallpaper_blur_radius", 18f))
    }
    var localUrisCount by remember(wallpaperMode) {
        mutableIntStateOf(WallpaperManager.getLocalUris(context).size)
    }

    var versionClickCount by remember { mutableIntStateOf(0) }
    var lastVersionClickTime by remember { mutableLongStateOf(0L) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            WallpaperManager.setLocalUris(context, uris)
            wallpaperMode = "local"
            localUrisCount = uris.size
            val toastMsg = context.getString(R.string.set_local_wallpapers_success, uris.size)
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            homeActivity?.updateWallpaper()
        }
    }

    val localizedContext = remember(currentLanguage) {
        LanguageHelper.getLocalizedContext(context, currentLanguage)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
    ) {

        val cardBg = if (isDark) Color(0x26FFFFFF) else Color(0x73FFFFFF)
        val textColor = if (isDark) Color.White else Color(0xFF0F172A)
        val subTextColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF475569)

        val devEnabledMsg = stringResource(R.string.dev_mode_enabled)
        val devDisabledMsg = stringResource(R.string.dev_mode_disabled)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // 顶部导航标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = onBackClick,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = textColor,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.title_settings),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. 主题与壁纸（整合外观主题 + 背景壁纸模式 + 高斯模糊度）
                    ExpandableThemeWallpaperCard(
                        themeMode = themeMode,
                        wallpaperMode = wallpaperMode,
                        wallpaperBlur = wallpaperBlur,
                        localCount = localUrisCount,
                        isDark = isDark,
                        bgColor = cardBg,
                        textColor = textColor,
                        subTextColor = subTextColor,
                        onThemeChange = { selectedTheme ->
                            themeMode = selectedTheme
                            homeActivity?.updateAppTheme(selectedTheme)
                        },
                        onWallpaperModeChange = { selectedMode ->
                            wallpaperMode = selectedMode
                            WallpaperManager.setMode(context, selectedMode)
                            homeActivity?.updateWallpaper()
                        },
                        onSelectLocal = {
                            photoPickerLauncher.launch("image/*")
                        },
                        onBlurChange = { newBlur ->
                            wallpaperBlur = newBlur
                            prefs.edit { putFloat("wallpaper_blur_radius", newBlur) }
                            homeActivity?.wallpaperBlurRadius = newBlur
                            homeActivity?.updateWallpaperBlurLive(newBlur)
                        },
                        onSliderViewCreated = onSliderViewCreated,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. 应用语言（横向 SegmentedToggleBar 排列，0ms 瞬间切换文字，零卡顿、不重装 Activity）
                    ExpandableLanguageCard(
                        currentLanguage = currentLanguage,
                        isDark = isDark,
                        bgColor = cardBg,
                        textColor = textColor,
                        subTextColor = subTextColor,
                    ) { selectedLang ->
                        if (currentLanguage != selectedLang) {
                            currentLanguage = selectedLang
                            homeActivity?.updateAppLanguage(selectedLang)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. 关于与赞赏
                    SettingsItemCard(
                        icon = Icons.Default.Favorite,
                        title = stringResource(R.string.about_and_donate),
                        subtitle = stringResource(R.string.about_and_donate_desc),
                        isDark = isDark,
                        bgColor = cardBg,
                        textColor = textColor,
                        subTextColor = subTextColor,
                        onClick = onAboutClick,
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 底部固定显示：开源链接 + 版本号（连点5次触发开发者模式） + 可更新提示（100% 始终立于页面最底部）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "https://github.com/kuangru52/embysic",
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/kuangru52/embysic".toUri())
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .padding(4.dp),
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    var hasUpdate by remember { mutableStateOf(value = false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.Dispatchers.IO.let {
                            try {
                                val latestRelease = RetrofitClient.githubApi.getLatestRelease()
                                val latestVersion = latestRelease.tag_name.replace("v", "")
                                val currentVersion = BuildConfig.VERSION_NAME
                                if (latestVersion != currentVersion) {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        hasUpdate = true
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Embysic  v${BuildConfig.VERSION_NAME}",
                            color = if (isDevMode) Color(0xFFFF4D4D) else subTextColor.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = if (isDevMode) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val now = System.currentTimeMillis()
                                    if ((now - lastVersionClickTime) > 2000) {
                                        versionClickCount = 0
                                    }
                                    lastVersionClickTime = now
                                    versionClickCount++

                                    if (versionClickCount >= 5) {
                                        isDevMode = !isDevMode
                                        prefs.edit { putBoolean("developer_mode", isDevMode) }
                                        versionClickCount = 0
                                        val msg = if (isDevMode) devEnabledMsg else devDisabledMsg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        homeActivity?.updateBottomDockUI()
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )

                        if (hasUpdate) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF07C160))
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/kuangru52/embysic/releases/latest".toUri())
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.update_available),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableThemeWallpaperCard(
    themeMode: String,
    wallpaperMode: String,
    wallpaperBlur: Float,
    localCount: Int,
    isDark: Boolean,
    bgColor: Color,
    textColor: Color,
    subTextColor: Color,
    onThemeChange: (String) -> Unit,
    onWallpaperModeChange: (String) -> Unit,
    onSelectLocal: () -> Unit,
    onBlurChange: (Float) -> Unit,
    onSliderViewCreated: (View) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(value = true) }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(18.dp),
        ) {
            // 卡片头部：图标 + 标题 "主题与壁纸" + 展开/折叠箭头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.theme_and_wallpaper),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = subTextColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(18.dp))

                // 1. 外观主题
                Text(
                    text = stringResource(R.string.theme_setting),
                    fontSize = 14.sp,
                    color = subTextColor,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val themeOptions = listOf(
                    stringResource(R.string.theme_system),
                    stringResource(R.string.theme_light),
                    stringResource(R.string.theme_dark),
                )
                val themeIndex = when (themeMode) {
                    "light" -> 1
                    "dark" -> 2
                    else -> 0
                }
                SegmentedToggleBar(
                    options = themeOptions,
                    selectedIndex = themeIndex,
                    isDark = isDark,
                ) { idx ->
                    val selected = when (idx) {
                        1 -> "light"
                        2 -> "dark"
                        else -> "system"
                    }
                    onThemeChange(selected)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. 背景壁纸模式
                Text(
                    text = stringResource(R.string.wallpaper_mode),
                    fontSize = 14.sp,
                    color = subTextColor,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val wallpaperOptions = listOf(
                    stringResource(R.string.wallpaper_none),
                    stringResource(R.string.wallpaper_bing),
                    stringResource(R.string.wallpaper_local),
                )
                val wallpaperIndex = when (wallpaperMode) {
                    "none" -> 0
                    "local" -> 2
                    else -> 1
                }
                SegmentedToggleBar(
                    options = wallpaperOptions,
                    selectedIndex = wallpaperIndex,
                    isDark = isDark,
                    onOptionSelected = { idx ->
                        val selected = when (idx) {
                            0 -> "none"
                            2 -> "local"
                            else -> "bing"
                        }
                        onWallpaperModeChange(selected)
                    },
                )

                if (wallpaperMode == "local") {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = onSelectLocal,
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.2.dp, if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.25f)),
                            modifier = Modifier.height(38.dp),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.select_local_images),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (localCount > 0) stringResource(R.string.selected_images_count, localCount) else stringResource(R.string.no_images_selected),
                            fontSize = 12.5.sp,
                            color = subTextColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. 壁纸高斯模糊度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.wallpaper_blur),
                        fontSize = 14.sp,
                        color = textColor,
                    )
                    Text(
                        text = "${wallpaperBlur.toInt()}dp",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                AndroidView(
                    factory = { ctx ->
                        ComposeView(ctx).apply {
                            onSliderViewCreated(this)
                        }
                    },
                    update = { composeView ->
                        composeView.setContent {
                            ExquisiteGlassSlider(
                                value = wallpaperBlur,
                                onValueChange = onBlurChange,
                                isDark = isDark,
                                valueRange = 0f..100f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun SegmentedToggleBar(
    options: List<String>,
    selectedIndex: Int,
    isDark: Boolean = true,
    onOptionSelected: (Int) -> Unit,
) {
    val barBg = if (isDark) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.08f)
    val barBorder = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
    val selectedPillBg = if (isDark) Color.White.copy(alpha = 0.15f) else Color.White
    val selectedPillBorder = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.20f)
    val selectedTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val unselectedTextColor = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF475569)

    Surface(
        color = barBg,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, barBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(selectedPillBg)
                                    .border(
                                        width = 1.2.dp,
                                        color = selectedPillBorder,
                                        shape = RoundedCornerShape(20.dp),
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onOptionSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) selectedTextColor else unselectedTextColor,
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableLanguageCard(
    currentLanguage: String,
    isDark: Boolean,
    bgColor: Color,
    textColor: Color,
    subTextColor: Color,
    onLanguageSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.language_setting),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (currentLanguage) {
                                "zh" -> stringResource(R.string.language_zh)
                                "en" -> stringResource(R.string.language_en)
                                else -> stringResource(R.string.language_system)
                            },
                            fontSize = 12.5.sp,
                            color = subTextColor,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = subTextColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                val langOptions = listOf(
                    stringResource(R.string.language_system),
                    stringResource(R.string.language_zh),
                    stringResource(R.string.language_en),
                )
                val langIndex = when (currentLanguage) {
                    "zh" -> 1
                    "en" -> 2
                    else -> 0
                }

                SegmentedToggleBar(
                    options = langOptions,
                    selectedIndex = langIndex,
                    isDark = isDark,
                ) { idx ->
                    val selected = when (idx) {
                        1 -> "zh"
                        2 -> "en"
                        else -> "system"
                    }
                    onLanguageSelect(selected)
                }
            }
        }
    }
}

@Composable
fun SettingsItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDark: Boolean,
    bgColor: Color,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = subTextColor,
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Preview(name = "Settings Screen Preview", showBackground = true)
@Composable
fun SettingsFragmentPreview() {
    SettingsContent()
}
