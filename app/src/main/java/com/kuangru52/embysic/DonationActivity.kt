package com.kuangru52.embysic

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.load
import jp.wasabeef.transformers.coil.BlurTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

@OptIn(UnstableApi::class)
class DonationActivity : ComponentActivity() {

    private val aliPayCode = "a6x04287fr0sxv9zqrc0m31"
    private val telegramUrl = "https://t.me/+FFEviJJq9GkyOWFl"
    private val qqGroupId = "1027610757"

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            statusBarColor = AndroidColor.TRANSPARENT
        }

        setContent {
            DonationScreen(
                onAlipayClick = { donateToAlipaySafe() },
                onWeChatClick = { saveWeChatCodeToGallery() },
                onTelegramClick = { openTelegram() },
            ) {
                joinQQGroup()
            }
        }
    }

    private fun openUrl() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, telegramUrl.toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTelegram() {
        openUrl()
    }

    private fun joinQQGroup() {
        try {
            val url = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$qqGroupId&card_type=group&source=qrcode"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "未检测到 QQ，或无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveWeChatCodeToGallery() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeResource(resources, R.drawable.wechat)
                val fileName = "Embysic_WeChat_Donate_${System.currentTimeMillis()}.png"
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Embysic")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    val outputStream: OutputStream? = resolver.openOutputStream(it)
                    outputStream?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(it, contentValues, null, null)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DonationActivity, "收款码已保存至相册，请打开微信扫码", Toast.LENGTH_LONG).show()
                    }
                } ?: throw Exception("Failed to create MediaStore entry")

            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DonationActivity, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun donateToAlipaySafe() {
        try {
            val alipayIntent = packageManager.getLaunchIntentForPackage("com.eg.android.AlipayGphone")
            
            if (alipayIntent != null) {
                val qrUrl = "https://qr.alipay.com/$aliPayCode"
                val intentUrl = "https://render.alipay.com/p/s/i/?scheme=" + 
                               Uri.encode("alipays://platformapi/startapp?saId=10000007&qrcode=" + Uri.encode(qrUrl))

                val intent = Intent(Intent.ACTION_VIEW, intentUrl.toUri())
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(this, "未检测到支付宝，请安装后重试", Toast.LENGTH_LONG).show()
                val webIntent = Intent(Intent.ACTION_VIEW, "https://ds.alipay.com/".toUri())
                startActivity(webIntent)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "跳转失败，请手动打开支付宝", Toast.LENGTH_SHORT).show()
        }
    }
}

// --- Top Level Composables for Liquid Glass & Previews ---

@Composable
fun DonationScreen(
    onAlipayClick: () -> Unit = {},
    onWeChatClick: () -> Unit = {},
    onTelegramClick: () -> Unit = {},
    onQQClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("embysic_prefs", Context.MODE_PRIVATE) }
    val blurRadius = remember { prefs.getFloat("wallpaper_blur_radius", prefs.getFloat("glass_blur_radius", 18f)) }
    var wallpaperSource by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(Unit) {
        val src = WallpaperManager.getActiveWallpaperSource(context)
        wallpaperSource = src
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 全屏同步系统/自定义壁纸 (完全放弃 donate.png，与其他页面 100% 同步高斯模糊度)
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    val blurPx = blurRadius.coerceIn(1f, 100f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRenderEffect(
                            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
                        )
                    }
                    if (wallpaperSource is Int) {
                        setImageResource(wallpaperSource as Int)
                    } else if (wallpaperSource != null) {
                        load(wallpaperSource) {
                            crossfade(enable = false)
                            allowHardware(enable = false)
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                transformations(BlurTransformation(ctx, blurPx.toInt().coerceIn(1, 100), 4))
                            }
                        }
                    }
                }
            },
            update = { iv ->
                val blurPx = blurRadius.coerceIn(1f, 100f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    iv.setRenderEffect(
                        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
                    )
                }
                if (wallpaperSource is Int) {
                    iv.setImageResource(wallpaperSource as Int)
                } else if (wallpaperSource != null) {
                    iv.load(wallpaperSource) {
                        crossfade(enable = false)
                        allowHardware(enable = false)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            transformations(BlurTransformation(iv.context, blurPx.toInt().coerceIn(1, 100), 4))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 2. 黑色半透明质感蒙版
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )

        // 3. 前景捐赠文案与按钮
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "愿 Embysic 能在您忙碌的生活里，点亮片刻温暖的时光。\n\n我用彻夜未眠的满腔热血，希望能换您一杯咖啡的赞赏。\n\n捐赠全凭自愿，不捐赠也不会影响任何功能使用。\n\n愿每一段旋律，都能治愈平凡日常，让您重新爱上音乐。",
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(48.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onAlipayClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF108EE9).copy(alpha = 0.9f),
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = "支付宝捐赠",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }

                    Button(
                        onClick = onWeChatClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF07C160).copy(alpha = 0.9f),
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = "微信捐赠",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onTelegramClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3).copy(alpha = 0.9f),
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = "Telegram群",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }

                    Button(
                        onClick = onQQClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF12B7F5).copy(alpha = 0.9f),
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = "QQ交流群",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Donation Screen Preview", showBackground = true)
@Composable
fun DonationScreenPreview() {
    DonationScreen()
}
