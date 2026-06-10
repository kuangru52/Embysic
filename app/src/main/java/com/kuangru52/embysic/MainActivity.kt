package com.kuangru52.embysic

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        
        // 平板允许旋转，手机强制竖屏
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        super.onCreate(savedInstanceState)

        // 设置播放模式默认值
        MediaItemUtils.isForceDirectMode = false
        
        // 沉浸式与键盘适配
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        // 检查是否已经登录
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val savedServer = prefs.getString("server_url", null)
        val savedToken = prefs.getString("access_token", null)
        val savedUserId = prefs.getString("user_id", null)

        if (!savedServer.isNullOrEmpty() && !savedToken.isNullOrEmpty() && !savedUserId.isNullOrEmpty()) {
            val intent = android.content.Intent(this, HomeActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
            return
        }

        // 使用 Compose 构建登录页面
        setContent {
            val ipAddress = ""

            // 监听手机端发来的登录信息
            val loginConfig by remember { mutableStateOf<Any?>(null) }
            LaunchedEffect(loginConfig) {
                loginConfig?.let {
                    // loginToEmby(it.url, it.user, it.pass)
                }
            }

            LoginScreen(
                onLogin = { server, user, pass -> loginToEmby(server, user, pass) },
                ipAddress = ipAddress
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    @Composable
    fun LoginScreen(onLogin: (String, String, String) -> Unit, ipAddress: String) {
        val configuration = LocalConfiguration.current
        // 大屏平板且处于横屏时使用平板布局
        val useTabletLayout = ((configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE) 
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        var serverState by remember { mutableStateOf("") }
        var usernameState by remember { mutableStateOf("") }
        var passwordState by remember { mutableStateOf("") }

        if (useTabletLayout) {
            TabletLoginLayout(
                server = serverState,
                onServerChange = { serverState = it },
                username = usernameState,
                onUsernameChange = { usernameState = it },
                password = passwordState,
                onPasswordChange = { passwordState = it },
                onLogin = { onLogin(serverState, usernameState, passwordState) },
                ipAddress = ipAddress
            )
        } else {
            PhoneLoginLayout(
                server = serverState,
                onServerChange = { serverState = it },
                username = usernameState,
                onUsernameChange = { usernameState = it },
                password = passwordState,
                onPasswordChange = { passwordState = it },
                onLogin = { onLogin(serverState, usernameState, passwordState) },
                ipAddress = ipAddress
            )
        }
    }

    @Composable
    fun PhoneLoginLayout(
        server: String,
        onServerChange: (String) -> Unit,
        username: String,
        onUsernameChange: (String) -> Unit,
        password: String,
        onPasswordChange: (String) -> Unit,
        onLogin: () -> Unit,
        ipAddress: String
    ) {
        val backgroundBrush = getLoginBackgroundBrush()
        val isDark = isSystemInDarkTheme()
        val contentColor = if (isDark) ComposeColor.White else ComposeColor.Black
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 登录表单
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(64.dp))

                // 标题与 Slogan
                Text(
                    text = "Embysic",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "重新爱上音乐！",
                    fontSize = 16.sp,
                    color = contentColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // 占位，使表单居中靠下
                Spacer(modifier = Modifier.weight(1f))

                LoginForm(
                    server = server,
                    onServerChange = onServerChange,
                    username = username,
                    onUsernameChange = onUsernameChange,
                    password = password,
                    onPasswordChange = onPasswordChange,
                    onLogin = onLogin
                )

                Spacer(modifier = Modifier.height(32.dp))

                AboutUsButton()

                Spacer(modifier = Modifier.height(24.dp))
                
                VersionText()
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun getLoginBackgroundBrush(): Brush {
        val configuration = LocalConfiguration.current
        val isTabletLandscape = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE 
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val isDark = if (isTabletLandscape) true else isSystemInDarkTheme()
        return if (isDark) {
            // 对应 drawable-night/bg_superman.xml 的渐变色 (135°: BR to TL)
            Brush.linearGradient(
                colors = listOf(
                    ComposeColor(0xFF540004),
                    ComposeColor(0xFF2B002B),
                    ComposeColor(0xFF000040)
                ),
                start = Offset.Infinite,
                end = Offset.Zero
            )
        } else {
            // 对应 drawable/bg_superman.xml 的渐变色 (270°: T to B)
            Brush.verticalGradient(
                colors = listOf(
                    ComposeColor(0xFFCBD9E5),
                    ComposeColor(0xFFE3EBF2),
                    ComposeColor(0xFFF2F5F8)
                )
            )
        }
    }

    @Composable
    fun TabletLoginLayout(
        server: String,
        onServerChange: (String) -> Unit,
        username: String,
        onUsernameChange: (String) -> Unit,
        password: String,
        onPasswordChange: (String) -> Unit,
        onLogin: () -> Unit,
        ipAddress: String
    ) {
        val backgroundBrush = getLoginBackgroundBrush()
        val contentColor = ComposeColor.White

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧：登录表单
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // 登录表单内容
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 48.dp)
                            .statusBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(64.dp))
                        
                        Text(
                            text = "Embysic",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = contentColor
                        )
                        Text(
                            text = "重新爱上音乐！",
                            fontSize = 14.sp,
                            color = contentColor.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        LoginForm(
                            server = server,
                            onServerChange = onServerChange,
                            username = username,
                            onUsernameChange = onUsernameChange,
                            password = password,
                            onPasswordChange = onPasswordChange,
                            onLogin = onLogin
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                        AboutUsButton()
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        VersionText()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // 分割线
                VerticalDivider()

                // 中间：播放器占位
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 唱片占位
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(320.dp)
                                    .background(contentColor.copy(alpha = 0.06f), CircleShape)
                            )
                            Image(
                                painter = painterResource(id = R.drawable.disk),
                                contentDescription = null,
                                modifier = Modifier.size(310.dp)
                            )
                            Surface(
                                modifier = Modifier.size(200.dp),
                                shape = RoundedCornerShape(100.dp),
                                color = contentColor.copy(alpha = 0.1f)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo),
                                    contentDescription = null,
                                    modifier = Modifier.padding(20.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(40.dp))
                        
                        // 文字占位
                        Text("未在播放", color = contentColor.copy(alpha = 0.7f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("登录后开启音乐之旅", color = contentColor.copy(alpha = 0.4f), fontSize = 14.sp)
                        
                        Spacer(modifier = Modifier.height(40.dp))
                        
                        // 按钮占位
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(id = R.drawable.ic_skip_previous_vector), null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.width(32.dp))
                            Icon(painterResource(id = R.drawable.ic_play_vector), null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.width(32.dp))
                            Icon(painterResource(id = R.drawable.ic_skip_next_vector), null, tint = contentColor.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
                        }
                    }
                }

                // 分割线
                VerticalDivider()

                // 右侧：歌词占位
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        repeat(5) { i ->
                            Text(
                                if (i == 2) "听你想听的音乐" else "...",
                                color = if (i == 2) contentColor.copy(alpha = 0.7f) else contentColor.copy(alpha = 0.2f),
                                fontSize = if (i == 2) 24.sp else 18.sp,
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TvLoginHint(ipAddress: String) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        
        Surface(
            color = ComposeColor.Black.copy(alpha = if (isFocused) 0.6f else 0.4f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(bottom = 24.dp)
                .focusable(interactionSource = interactionSource),
            border = BorderStroke(
                width = if (isFocused) 4.dp else 1.dp,
                color = if (isFocused) ComposeColor(0xFFFFD700) else ComposeColor.White.copy(alpha = 0.2f)
            ),
            tonalElevation = if (isFocused) 16.dp else 2.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = ComposeColor.Unspecified
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "电视快速登录", 
                        color = if (isFocused) ComposeColor(0xFFFFD700) else ComposeColor.White, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("请在手机浏览器访问以下地址进行登录：", color = ComposeColor.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "http://$ipAddress:8080",
                    color = ComposeColor(0xFFFFD700), // 金色
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }

    @Composable
    fun VerticalDivider() {
        val configuration = LocalConfiguration.current
        val isTabletLandscape = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE 
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val isDark = if (isTabletLandscape) true else isSystemInDarkTheme()
        val color = if (isDark) ComposeColor.White else ComposeColor.Black
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .padding(vertical = 64.dp)
                .background(color.copy(alpha = 0.1f))
        )
    }

    @Composable
    fun LoginForm(
        server: String,
        onServerChange: (String) -> Unit,
        username: String,
        onUsernameChange: (String) -> Unit,
        password: String,
        onPasswordChange: (String) -> Unit,
        onLogin: () -> Unit
    ) {
        val configuration = LocalConfiguration.current
        val isTabletLandscape = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE 
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val isDark = if (isTabletLandscape) true else isSystemInDarkTheme()
        val contentColor = if (isDark) ComposeColor.White else ComposeColor.Black
        
        Column {
            // 输入框容器
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp, 
                        contentColor.copy(alpha = 0.15f), 
                        RoundedCornerShape(24.dp)
                    ),
                color = contentColor.copy(alpha = 0.08f),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LoginInput(
                        value = server,
                        onValueChange = onServerChange,
                        placeholder = "emby地址 https://domain:port/",
                        isPassword = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LoginInput(
                        value = username,
                        onValueChange = onUsernameChange,
                        placeholder = "用户名",
                        isPassword = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LoginInput(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = "密码",
                        isPassword = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 登录按钮
            Surface(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        0.5.dp, 
                        contentColor.copy(alpha = 0.4f), 
                        RoundedCornerShape(28.dp)
                    ),
                color = contentColor.copy(alpha = 0.1f),
                tonalElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "登 录", 
                        fontSize = 18.sp, 
                        color = contentColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    @Composable
    fun AboutUsButton() {
        val configuration = LocalConfiguration.current
        val isTabletLandscape = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE 
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val isDark = if (isTabletLandscape) true else isSystemInDarkTheme()
        val contentColor = if (isDark) ComposeColor.White else ComposeColor.Black

        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Surface(
            onClick = {
                val intent = android.content.Intent(this@MainActivity, DonationActivity::class.java)
                startActivity(intent)
            },
            interactionSource = interactionSource,
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = if (isFocused) 0.dp else 4.dp),
            shape = RoundedCornerShape(22.dp),
            color = if (isFocused) ComposeColor(0xFFFFD700).copy(alpha = 0.2f) else contentColor.copy(alpha = 0.1f),
            border = BorderStroke(
                if (isFocused) 3.dp else 0.5.dp, 
                if (isFocused) ComposeColor(0xFFFFD700) else contentColor.copy(alpha = 0.3f)
            ),
            tonalElevation = if (isFocused) 16.dp else 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "About Us",
                    color = if (isFocused) ComposeColor(0xFFFFD700) else contentColor.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }

    @Composable
    fun VersionText() {
        val configuration = LocalConfiguration.current
        val isTabletLandscape = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE 
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val isDark = if (isTabletLandscape) true else isSystemInDarkTheme()
        val contentColor = if (isDark) ComposeColor.White else ComposeColor.Black
        Text(
            text = "Embysic  ${BuildConfig.VERSION_NAME}",
            color = contentColor.copy(alpha = 0.3f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Light
        )
    }

    @Composable
    fun LoginInput(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        isPassword: Boolean
    ) {
        var passwordVisible by remember { mutableStateOf(false) }
        val configuration = LocalConfiguration.current
        val isTabletLandscape = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE 
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val isDark = if (isTabletLandscape) true else isSystemInDarkTheme()
        val contentColor = if (isDark) ComposeColor.White else ComposeColor.Black

        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                textAlign = TextAlign.Start, 
                color = contentColor,
                fontSize = 16.sp
            ),
            placeholder = { 
                Text(
                    placeholder, 
                    color = contentColor.copy(alpha = 0.35f),
                    fontSize = 16.sp
                ) 
            },
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword) {
                {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    val description = if (passwordVisible) "隐藏密码" else "显示密码"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description, tint = contentColor.copy(alpha = 0.4f))
                    }
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ComposeColor.Transparent,
                unfocusedContainerColor = ComposeColor.Transparent,
                disabledContainerColor = ComposeColor.Transparent,
                focusedIndicatorColor = contentColor.copy(alpha = 0.4f),
                unfocusedIndicatorColor = contentColor.copy(alpha = 0.1f),
                focusedTextColor = contentColor,
                unfocusedTextColor = contentColor
            ),
            singleLine = true
        )
    }

    private fun Modifier.scaleOnFocus(isFocused: Boolean): Modifier = this.then(
        if (isFocused) Modifier.padding(horizontal = 4.dp).height(44.dp) else Modifier
    )

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

                    val authHeader = "MediaBrowser Client=\"Embysic\", Device=\"${MediaItemUtils.getDeviceName(this@MainActivity)}\", DeviceId=\"${MediaItemUtils.getDeviceId(this@MainActivity)}\", Version=\"${BuildConfig.VERSION_NAME}\""
                    Log.d("EmbyLogin", "Auth Header: $authHeader")
                    Log.d("EmbyLogin", "URL: $url")
                    Log.d("EmbyLogin", "Body: ${json.toString()}")

                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("X-Emby-Authorization", authHeader)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseCode = response.code
                    val responseMessage = response.message
                    val responseBody = response.body?.string()
                    
                    if (response.isSuccessful) {
                        Pair(true, responseBody)
                    } else {
                        Log.e("EmbyLogin", "HTTP Error: $responseCode $responseMessage - $responseBody")
                        val displayError = if (!responseBody.isNullOrBlank()) {
                            responseBody // 使用服务器返回的具体错误信息，例如“账户被锁定”
                        } else {
                            "错误码: $responseCode $responseMessage"
                        }
                        Pair(false, displayError)
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
                    @OptIn(UnstableApi::class)
                    val intent = android.content.Intent(this@MainActivity, HomeActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
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