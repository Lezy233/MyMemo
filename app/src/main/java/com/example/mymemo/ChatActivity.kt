package com.example.mymemo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 单聊界面:ListView 左右气泡展示与某好友的历史消息,底部输入框发送新消息。
 * 消息持久化到本地数据库,双账号切换登录后可读取对方发送的消息。
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var adapter: ChatAdapter
    private lateinit var listMessages: ListView
    private lateinit var tvEmptyChat: TextView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    private var userId: Long = -1L
    private var friendId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        db = AppDatabaseHelper.getInstance(this)
        progressRing = findViewById(R.id.progressRing)
        userId = db.findUserByUsername(
            intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()
        )?.id ?: -1L
        friendId = intent.getLongExtra(EXTRA_FRIEND_ID, -1L)
        val friendName = intent.getStringExtra(EXTRA_FRIEND_USERNAME).orEmpty()

        findViewById<TextView>(R.id.tvChatTitle).text =
            getString(R.string.title_chat_format, friendName)

        tvEmptyChat = findViewById(R.id.tvEmptyChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        listMessages = findViewById(R.id.listMessages)
        adapter = ChatAdapter(this, userId, emptyList())
        listMessages.adapter = adapter

        btnSend.setOnClickListener { sendMessage() }

        reload()
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
        reload()
    }

    /** 重查双方全部消息,按时间升序展示;无消息时提示。 */
    private fun reload() {
        if (userId <= 0 || friendId <= 0) {
            adapter.submit(emptyList())
            tvEmptyChat.visibility = TextView.VISIBLE
            return
        }
        val messages = db.getMessages(userId, friendId)
        adapter.submit(messages)
        tvEmptyChat.visibility = if (messages.isEmpty()) TextView.VISIBLE else TextView.GONE
        if (messages.isNotEmpty()) {
            listMessages.setSelection(messages.size - 1)
        }
    }

    /** 发送消息:空内容不发送;成功后写入数据库并刷新列表、滚动到底部。 */
    private fun sendMessage() {
        if (userId <= 0 || friendId <= 0) return
        if (db.sendMessage(userId, friendId, etMessage.text.toString())) {
            etMessage.setText("")
            reload()
        }
    }

    private fun refreshProgress() {
        if (userId <= 0) {
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            return
        }
        progressRing.setProgress(db.getTodayLearnedCount(userId), db.getDailyLimit(userId))
    }

    companion object {
        const val EXTRA_FRIEND_ID = "friend_id"
        const val EXTRA_FRIEND_USERNAME = "friend_username"
        const val EXTRA_FRIEND_AVATAR = "friend_avatar"
    }
}
