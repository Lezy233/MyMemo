package com.example.mymemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.example.mymemo.db.FriendRequest

/**
 * 好友申请列表适配器:每行展示申请人头像/用户名,并提供同意、拒绝按钮。
 * 按钮点击通过回调交给 Activity 处理(保持适配器无数据库依赖)。
 */
class FriendRequestAdapter(
    context: Context,
    requests: List<FriendRequest>,
    private val onAccept: (FriendRequest) -> Unit,
    private val onReject: (FriendRequest) -> Unit
) : BaseAdapter() {

    private val appContext = context.applicationContext
    private val inflater = LayoutInflater.from(context)
    private var requests: List<FriendRequest> = requests

    override fun getCount(): Int = requests.size

    override fun getItem(position: Int): FriendRequest = requests[position]

    override fun getItemId(position: Int): Long = requests[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_friend_request, parent, false)
        val request = requests[position]

        val avatarResId = appContext.resources.getIdentifier(
            request.fromAvatar,
            "drawable",
            appContext.packageName
        )
        view.findViewById<ImageView>(R.id.ivRequestAvatar).apply {
            if (avatarResId != 0) setImageResource(avatarResId)
        }
        view.findViewById<TextView>(R.id.tvRequestName).text = request.fromUsername
        view.findViewById<Button>(R.id.btnAccept).setOnClickListener { onAccept(request) }
        view.findViewById<Button>(R.id.btnReject).setOnClickListener { onReject(request) }
        return view
    }

    /** 替换数据集并刷新列表。 */
    fun submit(newRequests: List<FriendRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
