package com.kuangru52.embysic

import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class DonationActivity : ComponentActivity() {

    private val aliPayCode = "a6x04287fr0sxv9zqrc0m31"
    private val telegramUrl = "https://t.me/+FFEviJJq9GkyOWFl"
    private val qqGroupId = "1027610757"

    override fun onCreate(savedInstanceState: Bundle?) {
        // 判断是否为平板
        val isTablet = (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        if (isTablet) {
            // 平板：默认横屏，但允许旋转
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            // 手机：强制竖屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        super.onCreate(savedInstanceState)

        // 沉浸式状态栏
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            statusBarColor = AndroidColor.TRANSPARENT
        }

        setContent {
            DonationScreen()
        }
    }

    @Composable
    fun DonationScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景图
            Image(
                painter = painterResource(id = R.drawable.donate),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 蒙层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "愿 Embysic 能在您忙碌的生活里，点亮片刻温暖的时光。\n\n我用彻夜未眠的满腔热血，希望能换您一杯咖啡的赞赏。\n\n捐赠全凭自愿，不捐赠也不会影响任何功能使用。\n\n愿每一段旋律，都能治愈平凡日常，让您重新爱上音乐。",
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(48.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { donateToAlipaySafe(aliPayCode) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF108EE9).copy(alpha = 0.9f)
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "支付宝捐赠",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = { saveWeChatCodeToGallery() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF07C160).copy(alpha = 0.9f)
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "微信捐赠",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { openTelegram(telegramUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3).copy(alpha = 0.9f)
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Telegram群",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = { joinQQGroup(qqGroupId) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF12B7F5).copy(alpha = 0.9f)
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "QQ交流群",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // 版权信息
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "https://github.com/kuangru52/embysic",
                        color = Color(0xFF2196F3), // 使用更鲜明的 Material Blue，确保用户识别为可点击链接
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium, // 加粗一点点
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable {
                                openUrl("https://github.com/kuangru52/embysic")
                            }
                            .padding(4.dp)
                    )
                    Text(
                        text = "Embysic  ${BuildConfig.VERSION_NAME}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTelegram(url: String) {
        openUrl(url)
    }

    private fun joinQQGroup(groupId: String) {
        try {
            val url = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$groupId&card_type=group&source=qrcode"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
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

    private fun donateToAlipaySafe(payCode: String) {
        try {
            val packageManager = packageManager
            val alipayIntent = packageManager.getLaunchIntentForPackage("com.eg.android.AlipayGphone")
            
            if (alipayIntent != null) {
                // 使用官方推荐的 Universal Link 结合二维码链接的方式，这是目前最稳妥、最少警告的方案
                // 它模拟了“通过外部安全链接唤起支付宝扫码结果”的过程
                val qrUrl = "https://qr.alipay.com/$payCode"
                val intentUrl = "https://render.alipay.com/p/s/i/?scheme=" + 
                               Uri.encode("alipays://platformapi/startapp?saId=10000007&qrcode=" + Uri.encode(qrUrl))

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(this, "未检测到支付宝，请安装后重试", Toast.LENGTH_LONG).show()
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ds.alipay.com/"))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "跳转失败，请手动打开支付宝", Toast.LENGTH_SHORT).show()
        }
    }
}
