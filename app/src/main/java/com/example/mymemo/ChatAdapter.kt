package com.example.mymemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.mymemo.db.Message

/**
 * 聊天消息适配器:两种视图类型 —— 本人发送的靠右气泡、对方发送的靠左气泡。
 */
class ChatAdapter(
    context: Context,
    private val currentUserId: Long,
    messages: List<Message>
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private var messages: List<Message> = messages

    override fun getCount(): Int = messages.size

    override fun getItem(position: Int): Message = messages[position]

    override fun getItemId(position: Int): Long = messages[position].id

    override fun getViewTypeCount(): Int = TYPE_COUNT

    override fun getItemViewType(position: Int): Int =
        if (messages[position].senderId == currentUserId) TYPE_MINE else TYPE_OTHER

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val layoutRes = if (getItemViewType(position) == TYPE_MINE) {
            R.layout.item_message_mine
        } else {
            R.layout.item_message_other
        }
        val view = convertView ?: inflater.inflate(layoutRes, parent, false)
        view.findViewById<TextView>(R.id.tvMessage).text = messages[position].content
        return view
    }

    /** 替换数据集并刷新列表。 */
    fun submit(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    private companion object {
        const val TYPE_MINE = 0
        const val TYPE_OTHER = 1
        const val TYPE_COUNT = 2
    }
}
