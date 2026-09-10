package com.example.mymemo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import kotlin.math.roundToInt

/**
 * 注册界面:用户名 + 密码 + 内置头像网格点选。
 * 注册成功后写入本地数据库并返回登录界面。
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var gridAvatars: GridLayout

    private val avatarNames = (1..AVATAR_COUNT).map { "avatar_$it" }
    private val avatarViews = mutableListOf<ImageView>()
    private var selectedAvatarIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        db = AppDatabaseHelper.getInstance(this)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        gridAvatars = findViewById(R.id.gridAvatars)

        bindAvatarGrid()

        findViewById<Button>(R.id.btnRegister).setOnClickListener { submit() }
        findViewById<TextView>(R.id.tvGoLogin).setOnClickListener { finish() }
    }

    /** 动态生成头像网格,便于维护选中态。 */
    private fun bindAvatarGrid() {
        val size = dp(64)
        val margin = dp(6)
        avatarNames.forEachIndexed { index, name ->
            val imageView = ImageView(this).apply {
                val resId = resources.getIdentifier(name, "drawable", packageName)
                setImageResource(resId)
                setBackgroundResource(R.drawable.bg_avatar_normal)
                val padding = dp(6)
                setPadding(padding, padding, padding, padding)
                contentDescription = name
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(margin, margin, margin, margin)
                }
                setOnClickListener { selectAvatar(index) }
            }
            avatarViews += imageView
            gridAvatars.addView(imageView)
        }
    }

    private fun selectAvatar(index: Int) {
        selectedAvatarIndex = index
        avatarViews.forEachIndexed { i, view ->
            view.setBackgroundResource(
                if (i == index) R.drawable.bg_avatar_selected else R.drawable.bg_avatar_normal
            )
            view.isSelected = i == index
        }
    }

    private fun submit() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        when {
            username.isEmpty() -> {
                toast(getString(R.string.error_empty_username))
                return
            }

            password.isEmpty() -> {
                toast(getString(R.string.error_empty_password))
                return
            }

            selectedAvatarIndex < 0 -> {
                toast(getString(R.string.error_no_avatar))
                return
            }
        }

        val avatar = avatarNames[selectedAvatarIndex]
        if (db.insertUser(username, password, avatar)) {
            toast(getString(R.string.register_success))
            finish()
        } else {
            toast(getString(R.string.error_username_taken))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        /** 内置头像数量(avatar_1 .. avatar_8)。 */
        private const val AVATAR_COUNT = 8
    }
}
