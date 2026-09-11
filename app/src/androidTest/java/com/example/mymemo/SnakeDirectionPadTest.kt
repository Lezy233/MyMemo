package com.example.mymemo

import android.content.Intent
import android.widget.ImageButton
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mymemo.db.AppDatabaseHelper
import com.example.mymemo.game.SnakeGame
import com.example.mymemo.widget.SnakeBoardView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 贪吃蛇方向键测试(任务 4.2):
 * - 四个按键为无文字标签的 ImageButton,复用同一三角形 drawable 并旋转指向各自方向;
 * - 按十字方位均匀分布(上居上、左居左、右居右、下居下);
 * - 点击按键使下一步方向生效(转向);禁止掉头规则由 [com.example.mymemo.game.SnakeGameTest] 覆盖。
 */
@RunWith(AndroidJUnit4::class)
class SnakeDirectionPadTest {

    private lateinit var db: AppDatabaseHelper

    private val username = "snake_ui"

    @Before
    fun setUp() {
        db = AppDatabaseHelper.getInstance(ApplicationProvider.getApplicationContext())
        cleanup()
        db.insertUser(username, "pw", "avatar_1")
    }

    @After
    fun tearDown() {
        cleanup()
    }

    private fun cleanup() {
        val database = db.writableDatabase
        database.delete(AppDatabaseHelper.TABLE_WORD_DAILY_FAILS, null, null)
        database.delete(AppDatabaseHelper.TABLE_STUDY_RECORDS, null, null)
        database.delete(
            AppDatabaseHelper.TABLE_USERS,
            "${AppDatabaseHelper.COLUMN_USERNAME} = ?",
            arrayOf(username)
        )
    }

    @Test
    fun directionButtonsUseRotatedTriangleInCrossLayout() {
        launchGame().use { scenario ->
            scenario.onActivity { activity ->
                val up = activity.findViewById<ImageButton>(R.id.btnUp)
                val left = activity.findViewById<ImageButton>(R.id.btnLeft)
                val right = activity.findViewById<ImageButton>(R.id.btnRight)
                val down = activity.findViewById<ImageButton>(R.id.btnDown)
                val pad = activity.findViewById<android.view.View>(R.id.dirPad)

                // 无文字标签、复用同一三角形 drawable、按方向旋转
                assertEquals(0f, up.rotation, 0.01f)
                assertEquals(90f, right.rotation, 0.01f)
                assertEquals(180f, down.rotation, 0.01f)
                assertEquals(270f, left.rotation, 0.01f)
                listOf(up, left, right, down).forEach { button ->
                    assertNotNull(button.drawable)
                    assertNotNull(button.contentDescription)
                }
                assertEquals(up.drawable.intrinsicWidth, right.drawable.intrinsicWidth)
                assertEquals(up.drawable.intrinsicHeight, down.drawable.intrinsicHeight)
                assertEquals(right.drawable.intrinsicWidth, left.drawable.intrinsicWidth)

                // 十字方位:上居中于顶部、下居中于底部、左/右居中于两侧
                assertEquals((pad.width / 2).toDouble(), ((up.left + up.right) / 2).toDouble(), 1.0)
                assertEquals((pad.width / 2).toDouble(), ((down.left + down.right) / 2).toDouble(), 1.0)
                assertEquals((pad.height / 2).toDouble(), ((left.top + left.bottom) / 2).toDouble(), 1.0)
                assertEquals((pad.height / 2).toDouble(), ((right.top + right.bottom) / 2).toDouble(), 1.0)

                assertTrue("左键应在上键左侧", left.right <= up.left)
                assertTrue("右键应在上键右侧", right.left >= up.right)
                assertTrue("上键应在左键上方", up.bottom <= left.top)
                assertTrue("下键应在左键下方", down.top >= left.bottom)
            }
        }
    }

    @Test
    fun clickingDirectionButtonTurnsSnakeOnNextStep() {
        launchGame().use { scenario ->
            scenario.onActivity { activity ->
                val board = activity.findViewById<SnakeBoardView>(R.id.snakeBoard)
                val game = board.gameValue
                assertNotNull("棋盘应已注入对局状态", game)

                activity.findViewById<ImageButton>(R.id.btnUp).performClick()
                assertEquals(SnakeGame.Direction.UP, game!!.nextDirection)

                activity.findViewById<ImageButton>(R.id.btnLeft).performClick()
                assertEquals(SnakeGame.Direction.LEFT, game.nextDirection)
            }
        }
    }

    private fun launchGame(): ActivityScenario<SnakeGameActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            SnakeGameActivity::class.java
        ).putExtra(MainActivity.EXTRA_USERNAME, username)
        return ActivityScenario.launch(intent)
    }
}
