package com.example.mymemo

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AddFriendResult
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.Friend
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 好友列表界面:ListView 展示好友(头像/用户名/今日已背数),
 * 提供添加好友、查看好友申请、点击进入聊天、长按删除好友。
 */
class FriendListActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var adapter: FriendAdapter
    private lateinit var btnFriendRequests: Button
    private lateinit var tvEmptyFriends: TextView

    private var userId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_list)

        db = AppDatabaseHelper.getInstance(this)
        progressRing = findViewById(R.id.progressRing)
        userId = db.findUserByUsername(
            intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()
        )?.id ?: -1L

        tvEmptyFriends = findViewById(R.id.tvEmptyFriends)
        btnFriendRequests = findViewById(R.id.btnFriendRequests)
        adapter = FriendAdapter(this, emptyList())
        findViewById<ListView>(R.id.listFriends).apply {
            adapter = this@FriendListActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                openChat(this@FriendListActivity.adapter.getItem(position))
            }
            setOnItemLongClickListener { _, _, position, _ ->
                confirmDeleteFriend(this@FriendListActivity.adapter.getItem(position))
                true
            }
        }
        findViewById<Button>(R.id.btnAddFriend).setOnClickListener { showAddFriendDialog() }
        btnFriendRequests.setOnClickListener {
            startActivity(
                Intent(this, FriendRequestActivity::class.java)
                    .putExtra(MainActivity.EXTRA_USERNAME, currentUsername())
            )
        }

        reload()
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
        // 从聊天或申请界面返回时,好友今日背词数与申请数量保持最新
        reload()
    }

    private fun currentUsername(): String =
        intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()

    private fun reload() {
        if (userId <= 0) {
            adapter.submit(emptyList())
            tvEmptyFriends.visibility = TextView.VISIBLE
            return
        }
        val friends = db.getFriends(userId)
        adapter.submit(friends)
        tvEmptyFriends.visibility = if (friends.isEmpty()) TextView.VISIBLE else TextView.GONE

        val pending = db.getPendingRequestCount(userId)
        btnFriendRequests.text = getString(R.string.btn_friend_requests_format, pending)
    }

    private fun refreshProgress() {
        if (userId <= 0) {
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            return
        }
        progressRing.setProgress(db.getTodayLearnedCount(userId), db.getDailyLimit(userId))
    }

    /** 添加好友:按用户名搜索本机账号发起申请,失败时给出明确原因。 */
    private fun showAddFriendDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_search_username) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_friend)
            .setView(input)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val target = input.text.toString().trim()
                if (target.isEmpty()) {
                    toast(getString(R.string.error_empty_username_search))
                } else {
                    when (db.sendFriendRequest(userId, target)) {
                        AddFriendResult.SUCCESS -> {
                            toast(getString(R.string.friend_request_sent))
                            dialog.dismiss()
                        }
                        AddFriendResult.SELF -> toast(getString(R.string.error_friend_self))
                        AddFriendResult.USER_NOT_FOUND ->
                            toast(getString(R.string.error_friend_not_found))
                        AddFriendResult.ALREADY_FRIEND ->
                            toast(getString(R.string.error_friend_already))
                        AddFriendResult.REQUEST_PENDING ->
                            toast(getString(R.string.error_friend_pending))
                    }
                }
            }
        }
        dialog.show()
    }

    /** 长按好友项:二次确认后双向解除关系(聊天记录保留)。 */
    private fun confirmDeleteFriend(friend: Friend) {
        AlertDialog.Builder(this)
            .setTitle(R.string.friend_delete_title)
            .setMessage(R.string.friend_delete_confirm)
            .setPositiveButton(R.string.btn_confirm_delete_friend) { _, _ ->
                db.deleteFriend(userId, friend.userId)
                reload()
                toast(getString(R.string.friend_deleted))
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** 点击好友项进入聊天界面,传入好友信息。 */
    private fun openChat(friend: Friend) {
        startActivity(
            Intent(this, ChatActivity::class.java)
                .putExtra(MainActivity.EXTRA_USERNAME, currentUsername())
                .putExtra(ChatActivity.EXTRA_FRIEND_ID, friend.userId)
                .putExtra(ChatActivity.EXTRA_FRIEND_USERNAME, friend.username)
                .putExtra(ChatActivity.EXTRA_FRIEND_AVATAR, friend.avatar)
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
