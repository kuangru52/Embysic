package com.kuangru52.embysic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 沉浸式状态栏
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            statusBarColor = Color.TRANSPARENT
        }
        
        // 检查是否已经登录
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val savedServer = prefs.getString("server_url", null)
        val savedToken = prefs.getString("access_token", null)
        val savedUserId = prefs.getString("user_id", null)

        if (!savedServer.isNullOrEmpty() && !savedToken.isNullOrEmpty() && !savedUserId.isNullOrEmpty()) {
            val intent = android.content.Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // 使用 Compose 构建登录页面
        setContent {
            LoginScreen(
                onLogin = { server, user, pass -> loginToEmby(server, user, pass) }
            )
        }
    }

    @Composable
    fun LoginScreen(onLogin: (String, String, String) -> Unit) {
        var server by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        Box(modifier = Modifier.fillMaxSize()) {
            // 背景图
            Image(
                painter = painterResource(id = R.drawable.login),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 占位，从 45% 高度开始
                Spacer(modifier = Modifier.fillMaxHeight(0.45f))

                // 输入框容器 - 实现高斯模糊/毛玻璃效果
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            0.5.dp, 
                            ComposeColor.White.copy(alpha = 0.2f), 
                            RoundedCornerShape(24.dp)
                        ),
                    color = ComposeColor.White.copy(alpha = 0.45f),
                    tonalElevation = 12.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LoginInput(
                            value = server,
                            onValueChange = { server = it },
                            placeholder = "emby地址 https://domain:port/",
                            isPassword = false
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LoginInput(
                            value = username,
                            onValueChange = { username = it },
                            placeholder = "用户名",
                            isPassword = false
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LoginInput(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "密码",
                            isPassword = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 登录按钮
                Surface(
                    onClick = { onLogin(server, username, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(
                            0.5.dp, 
                            ComposeColor.White.copy(alpha = 0.4f), 
                            RoundedCornerShape(28.dp)
                        ),
                    color = ComposeColor.White.copy(alpha = 0.2f),
                    tonalElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "登 录", 
                            fontSize = 18.sp, 
                            color = ComposeColor.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // About Us 按钮
                Surface(
                    onClick = {
                        val intent = android.content.Intent(this@MainActivity, DonationActivity::class.java)
                        startActivity(intent)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = ComposeColor.White.copy(alpha = 0.2f),
                    border = BorderStroke(
                        0.5.dp, 
                        ComposeColor.White.copy(alpha = 0.4f)
                    ),
                    tonalElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "About Us",
                            color = ComposeColor.Black.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 版权信息
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "Embysic  ${BuildConfig.VERSION_NAME}",
                        color = ComposeColor.Black.copy(alpha = 0.3f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
    }

    @Composable
    fun LoginInput(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        isPassword: Boolean
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
            placeholder = { 
                Text(
                    placeholder, 
                    color = ComposeColor.Black.copy(alpha = 0.4f)
                ) 
            },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ComposeColor.Transparent,
                unfocusedContainerColor = ComposeColor.Transparent,
                disabledContainerColor = ComposeColor.Transparent,
                focusedIndicatorColor = ComposeColor.Black.copy(alpha = 0.4f),
                unfocusedIndicatorColor = ComposeColor.Black.copy(alpha = 0.1f),
                focusedTextColor = ComposeColor.Black,
                unfocusedTextColor = ComposeColor.Black
            ),
            singleLine = true
        )
    }

    private fun loginToEmby(server: String, username: String, password: String) {
        val cleanServer = if (server.endsWith("/")) server.removeSuffix("/") else server
        val url = "$cleanServer/emby/Users/AuthenticateByName"

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    
                    val json = JSONObject().apply {
                        put("Username", username)
                        put("Pw", password)
                    }

                    val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                    val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"${BuildConfig.VERSION_NAME}\""

                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("X-Emby-Authorization", authHeader)
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        Pair(true, body)
                    } else {
                        Pair(false, response.message)
                    }
                }

                if (result.first) {
                    val responseBody = result.second
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    val accessToken = jsonResponse.optString("AccessToken")
                    val userObj = jsonResponse.optJSONObject("User")
                    val userId = userObj?.optString("Id") ?: ""

                    // 保存登录信息
                    val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("server_url", cleanServer)
                        putString("access_token", accessToken)
                        putString("user_id", userId)
                        apply()
                    }
                    
                    Toast.makeText(this@MainActivity, "登录成功！", Toast.LENGTH_SHORT).show()
                    Log.d("EmbyLogin", "Success: $responseBody")
                    
                    // 跳转到 HomeActivity
                    val intent = android.content.Intent(this@MainActivity, HomeActivity::class.java)
                    startActivity(intent)
                    finish() // 登录成功后销毁登录页面
                } else {
                    val errorMsg = result.second ?: "服务器返回错误"
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    Log.e("EmbyLogin", "Failed: $errorMsg")
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EmbyLogin", "Error", e)
            }
        }
    }
}