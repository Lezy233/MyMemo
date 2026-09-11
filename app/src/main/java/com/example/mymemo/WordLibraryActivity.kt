package com.example.mymemo

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.db.Word
import com.example.mymemo.widget.ProgressRing
import com.example.mymemo.widget.StudyProgressRingView

/**
 * 词库管理界面:浏览词库、添加单词、编辑释义、删除单词(数据库增删查改的展示点)。
 * 词库对全账号共享;顶部嵌入背词进度环。
 */
class WordLibraryActivity : AppCompatActivity() {

    private lateinit var db: AppDatabaseHelper
    private lateinit var adapter: WordAdapter
    private lateinit var progressRing: StudyProgressRingView

    private var userId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_library)

        db = AppDatabaseHelper.getInstance(this)
        progressRing = findViewById(R.id.progressRing)
        userId = db.findUserByUsername(currentUsername())?.id ?: -1L

        adapter = WordAdapter(this, emptyList())
        findViewById<ListView>(R.id.listWords).apply {
            adapter = this@WordLibraryActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                showEditDialog(this@WordLibraryActivity.adapter.getItem(position))
            }
        }
        findViewById<Button>(R.id.btnAddWord).setOnClickListener { showAddDialog() }

        reload()
    }

    override fun onResume() {
        super.onResume()
        refreshProgress()
    }

    private fun currentUsername(): String =
        intent.getStringExtra(MainActivity.EXTRA_USERNAME).orEmpty()

    private fun reload() {
        adapter.submit(db.getAllWords())
    }

    private fun refreshProgress() {
        if (userId <= 0) {
            progressRing.setProgress(0, ProgressRing.DEFAULT_MAX)
            return
        }
        progressRing.setProgress(db.getTodayLearnedCount(userId), db.getDailyLimit(userId))
    }

    /** 添加单词:单词 + 释义均必填,单词不可重复。 */
    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_word_edit, null)
        val etWord = view.findViewById<EditText>(R.id.etWord)
        val etMeaning = view.findViewById<EditText>(R.id.etMeaning)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_word)
            .setView(view)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val word = etWord.text.toString().trim()
                val meaning = etMeaning.text.toString().trim()
                when {
                    word.isEmpty() -> toast(getString(R.string.error_empty_word))
                    meaning.isEmpty() -> toast(getString(R.string.error_empty_meaning))
                    db.findWordByText(word) != null -> toast(getString(R.string.error_word_exists))
                    else -> {
                        db.insertWord(word, meaning)
                        reload()
                        toast(getString(R.string.word_added))
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    /** 编辑单词:仅可修改释义,另提供删除入口。 */
    private fun showEditDialog(word: Word) {
        val view = layoutInflater.inflate(R.layout.dialog_word_edit, null)
        val etWord = view.findViewById<EditText>(R.id.etWord).apply {
            setText(word.word)
            isEnabled = false
        }
        val etMeaning = view.findViewById<EditText>(R.id.etMeaning).apply {
            setText(word.meaning)
            setSelection(text.length)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_word)
            .setView(view)
            .setPositiveButton(R.string.btn_save, null)
            .setNeutralButton(R.string.btn_delete, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val meaning = etMeaning.text.toString().trim()
                if (meaning.isEmpty()) {
                    toast(getString(R.string.error_empty_meaning))
                } else {
                    db.updateWordMeaning(word.id, meaning)
                    reload()
                    toast(getString(R.string.word_updated))
                    dialog.dismiss()
                }
            }
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                confirmDelete(word, dialog)
            }
        }
        dialog.show()
    }

    /** 删除单词:需二次确认。 */
    private fun confirmDelete(word: Word, editDialog: AlertDialog) {
        AlertDialog.Builder(this)
            .setTitle(word.word)
            .setMessage(R.string.word_delete_confirm)
            .setPositiveButton(R.string.btn_confirm_delete) { _, _ ->
                db.deleteWord(word.id)
                reload()
                toast(getString(R.string.word_deleted))
                editDialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
