package com.example.mymemo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 主界面:展示登录用户传入的用户名与头像,并嵌入背词进度环。
 *
 * 本变更中进度环使用占位数据(0 / 默认上限),真实数据将在 word-library 变更中接入。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        val avatar = intent.getStringExtra(EXTRA_AVATAR).orEmpty()

        findViewById<TextView>(R.id.tvUsername).text =
            getString(R.string.main_greeting_format, username)

        if (avatar.isNotEmpty()) {
            val avatarResId = resources.getIdentifier(avatar, "drawable", packageName)
            if (avatarResId != 0) {
                findViewById<ImageView>(R.id.ivAvatar).setImageResource(avatarResId)
            }
        }

        // 占位数据:今日已背 0 / 默认上限
        findViewById<StudyProgressRingView>(R.id.progressRing)
            .setProgress(0, ProgressRing.DEFAULT_MAX)

        findViewById<Button>(R.id.btnLogout).setOnClickListener { logout() }
    }

    /**
     * 退出登录:清空返回栈并回到登录界面,退出后按返回键无法回到主界面。
     */
    private fun logout() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_AVATAR = "avatar"
    }
}
