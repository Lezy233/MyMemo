package com.example.mymemo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper

/**
 * 登录界面,应用启动后的第一个活动(LAUNCHER 入口)。
 * 校验本地数据库中的凭据,成功后携带用户名与头像跳转 [MainActivity]。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        db = AppDatabaseHelper.getInstance(this)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)

        findViewById<Button>(R.id.btnLogin).setOnClickListener { login() }
        val goRegister = { startActivity(Intent(this, RegisterActivity::class.java)) }
        findViewById<TextView>(R.id.tvGoRegister).setOnClickListener { goRegister() }
        findViewById<ImageView>(R.id.ivAvatarEntry).setOnClickListener { goRegister() }
    }

    private fun login() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            toast(getString(R.string.error_empty_credentials))
            return
        }

        if (!db.checkCredentials(username, password)) {
            toast(getString(R.string.error_bad_credentials))
            return
        }

        val avatar = db.findUserByUsername(username)?.avatar.orEmpty()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_USERNAME, username)
                putExtra(MainActivity.EXTRA_AVATAR, avatar)
            }
        )
        finish()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
