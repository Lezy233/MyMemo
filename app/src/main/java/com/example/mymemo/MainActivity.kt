package com.example.mymemo

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 主界面:展示登录用户信息、真实背词进度环,并提供词库管理、开始背词与每日上限设置入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView

    private var username: String = ""
    private var userId: Long = -1L
    private var dailyLimit: Int = AppDatabaseHelper.DEFAULT_DAILY_LIMIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabaseHelper.getInstance(this)
        progressRing = findViewById(R.id.progressRing)

        username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        val avatar = intent.getStringExtra(EXTRA_AVATAR).orEmpty()

        findViewById<TextView>(R.id.tvUsername).text =
            getString(R.string.main_greeting_format, username)

        if (avatar.isNotEmpty()) {
            val avatarResId = resources.getIdentifier(avatar, "drawable", packageName)
            if (avatarResId != 0) {
                findViewById<ImageView>(R.id.ivAvatar).setImageResource(avatarResId)
            }
        }

        findViewById<Button>(R.id.btnStartStudy).setOnClickListener {
            startActivity(
                Intent(this, StudyActivity::class.java)
                    .putExtra(EXTRA_USERNAME, username)
            )
        }
        findViewById<Button>(R.id.btnWordLibrary).setOnClickListener {
            startActivity(
                Intent(this, WordLibraryActivity::class.java)
                    .putExtra(EXTRA_USERNAME, username)
            )
        }
        findViewById<Button>(R.id.btnTodayQueue).setOnClickListener {
            startActivity(
                Intent(this, WordQueueActivity::class.java)
                    .putExtra(EXTRA_USERNAME, username)
            )
        }
        findViewById<Button>(R.id.btnDailyLimit).setOnClickListener { showDailyLimitDialog() }
        findViewById<Button>(R.id.btnLogout).setOnClickListener { logout() }

        refreshProgress()
    }

    override fun onResume() {
        super.onResume()
        // 从背词/词库界面返回时,进度环即时更新为真实数据
        refreshProgress()
    }

    /** 查询当前用户今日已背数与每日上限,刷新进度环。 */
    private fun refreshProgress() {
        val user = db.findUserByUsername(username)
        if (user == null) {
            // 数据库中没有该用户(理论上不会发生);退回默认显示,避免崩溃
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            return
        }
        userId = user.id
        dailyLimit = user.dailyLimit
        progressRing.setProgress(db.getTodayLearnedCount(userId), dailyLimit)
    }

    /** 每日上限设置弹窗:仅接受正整数,保存后立即生效。 */
    private fun showDailyLimitDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.hint_daily_limit)
            setText(dailyLimit.toString())
            setSelection(text.length)
        }
        val container = FrameLayout(this).apply {
            setPadding(24.dp(), 8.dp(), 24.dp(), 0)
            addView(
                input,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_daily_limit)
            .setView(container)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().toIntOrNull()
                if (value == null || value <= 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.error_invalid_limit),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (userId > 0) {
                    db.setDailyLimit(userId, value)
                    dailyLimit = value
                    refreshProgress()
                    Toast.makeText(
                        this,
                        getString(R.string.daily_limit_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_AVATAR = "avatar"
    }
}
