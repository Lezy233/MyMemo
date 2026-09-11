package com.example.mymemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.mymemo.db.Friend

/** 好友列表适配器:每行展示头像、用户名与好友今日已背单词数。 */
class FriendAdapter(context: Context, friends: List<Friend>) : BaseAdapter() {

    private val appContext = context.applicationContext
    private val inflater = LayoutInflater.from(context)
    private var friends: List<Friend> = friends

    override fun getCount(): Int = friends.size

    override fun getItem(position: Int): Friend = friends[position]

    override fun getItemId(position: Int): Long = friends[position].userId

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_friend, parent, false)
        val friend = friends[position]

        val avatarResId = appContext.resources.getIdentifier(
            friend.avatar,
            "drawable",
            appContext.packageName
        )
        view.findViewById<ImageView>(R.id.ivFriendAvatar).apply {
            if (avatarResId != 0) setImageResource(avatarResId)
        }
        view.findViewById<TextView>(R.id.tvFriendName).text = friend.username
        view.findViewById<TextView>(R.id.tvFriendLearned).text =
            appContext.getString(R.string.friend_today_learned_format, friend.todayLearnedCount)
        return view
    }

    /** 替换数据集并刷新列表。 */
    fun submit(newFriends: List<Friend>) {
        friends = newFriends
        notifyDataSetChanged()
    }
}
