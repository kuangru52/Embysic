package com.kuangru52.embysic

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var etServer: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查是否已经登录
        val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
        val savedServer = prefs.getString("server_url", null)
        val savedToken = prefs.getString("access_token", null)
        val savedUserId = prefs.getString("user_id", null)

        if (!savedServer.isNullOrEmpty() && !savedToken.isNullOrEmpty() && !savedUserId.isNullOrEmpty()) {
            // 已有登录信息，直接进入主界面
            val intent = android.content.Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // 初始化视图
        etServer = findViewById(R.id.etServer)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        // 预填调试信息
        etServer.setText("https://www.kuangru52.eu.org:8097/")
        etUsername.setText("kuangru52")
        etPassword.setText("yhwk1747")

        btnLogin.setOnClickListener {
            val server = etServer.text?.toString()?.trim()
            val username = etUsername.text?.toString()?.trim()
            val password = etPassword.text?.toString()?.trim()

            if (server.isNullOrEmpty() || username.isNullOrEmpty()) {
                Toast.makeText(this, "请输入服务器地址和用户名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginToEmby(server, username, password ?: "")
        }
    }

    private fun loginToEmby(server: String, username: String, password: String) {
        val cleanServer = if (server.endsWith("/")) server.removeSuffix("/") else server
        val url = "$cleanServer/emby/Users/AuthenticateByName"

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    
                    val json = JSONObject().apply {
                        put("Username", username)
                        put("Pw", password)
                    }

                    val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                    // Emby 要求在头部提供客户端信息
                    val authHeader = "MediaBrowser Client=\"Android\", Device=\"Android Phone\", DeviceId=\"123456\", Version=\"1.0.0\""

                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("X-Emby-Authorization", authHeader)
                        .build()

                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    val accessToken = jsonResponse.optString("AccessToken")
                    val userObj = jsonResponse.optJSONObject("User")
                    val userId = userObj?.optString("Id") ?: ""
                    val serverUrl = cleanServer

                    // 保存登录信息
                    val prefs = getSharedPreferences("embysic_prefs", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("server_url", serverUrl)
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
                    val errorMsg = when(response.code) {
                        401 -> "用户名或密码错误"
                        else -> "服务器返回错误: ${response.code}"
                    }
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    Log.e("EmbyLogin", "Failed: ${response.code} ${response.message}")
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EmbyLogin", "Error", e)
            }
        }
    }
}