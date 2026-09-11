package com.example.mymemo

import android.os.Bundle
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.FriendRequest
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 好友申请界面:展示待处理申请,同意后双方成为好友,拒绝后删除申请记录。
 * 列表为空时给出提示;顶部嵌入背词进度环。
 */
class FriendRequestActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var progressRing: StudyProgressRingView
    private lateinit var adapter: FriendRequestAdapter
    private lateinit var tvEmptyRequests: TextView

    private var userId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_request)

        db = AppDatabaseHelper.getInstance(this)
        progressRing = findViewById(R.id.progressRing)
        userId = db.findUserByUsername(
            intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()
        )?.id ?: -1L

        tvEmptyRequests = findViewById(R.id.tvEmptyRequests)
        adapter = FriendRequestAdapter(
            this,
            emptyList(),
            onAccept = { accept(it) },
            onReject = { reject(it) }
        )
        findViewById<ListView>(R.id.listRequests).adapter = adapter

        reload()
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
        reload()
    }

    private fun reload() {
        if (userId <= 0) {
            adapter.submit(emptyList())
            tvEmptyRequests.visibility = TextView.VISIBLE
            return
        }
        val requests = db.getPendingRequests(userId)
        adapter.submit(requests)
        tvEmptyRequests.visibility =
            if (requests.isEmpty()) TextView.VISIBLE else TextView.GONE
    }

    private fun accept(request: FriendRequest) {
        if (db.acceptFriendRequest(request.id)) {
            toast(getString(R.string.friend_request_accepted))
            reload()
        }
    }

    private fun reject(request: FriendRequest) {
        if (db.rejectFriendRequest(request.id)) {
            toast(getString(R.string.friend_request_rejected))
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
