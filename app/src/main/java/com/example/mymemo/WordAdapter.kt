package com.example.mymemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.mymemo.db.Word

/** 词库列表适配器:每行展示单词与其释义。 */
class WordAdapter(context: Context, words: List<Word>) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private var words: List<Word> = words

    override fun getCount(): Int = words.size

    override fun getItem(position: Int): Word = words[position]

    override fun getItemId(position: Int): Long = words[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_word, parent, false)
        val word = words[position]
        view.findViewById<TextView>(R.id.tvWord).text = word.word
        view.findViewById<TextView>(R.id.tvMeaning).text = word.meaning
        return view
    }

    /** 替换数据集并刷新列表。 */
    fun submit(newWords: List<Word>) {
        words = newWords
        notifyDataSetChanged()
    }
}
