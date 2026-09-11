package com.example.mymemo.game

import com.example.mymemo.game.SnakeGame.Cell
import com.example.mymemo.game.SnakeGame.Direction
import com.example.mymemo.game.SnakeGame.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 贪吃蛇规则单元测试(任务 4.1 / 4.2 / 4.3 / 4.4):
 * 初始布局、转向与禁止掉头、吃果子生长、撞墙/撞身/铺满胜负判定、额度奖励计算。
 */
class SnakeGameTest {

    @Test
    fun startsAtEleventhRowEleventhColWithLengthOne() {
        val game = SnakeGame()
        assertEquals(Cell(10, 10), game.head)
        assertEquals(1, game.length)
        assertEquals(1, game.body().size)
        assertEquals(Status.RUNNING, game.status)
        // 果子落在棋盘内且不与蛇头重叠
        assertTrue(game.fruit.row in 0 until game.rows)
        assertTrue(game.fruit.col in 0 until game.columns)
        assertTrue(game.fruit != game.head)
    }

    @Test
    fun stepMovesOneCellAlongCurrentDirection() {
        val game = SnakeGame()
        game.step()
        assertEquals(Cell(10, 11), game.head)
        assertFalse(game.body().contains(Cell(10, 10)))
    }

    @Test
    fun turnTakesEffectOnNextStep() {
        val game = SnakeGame()
        assertTrue(game.turn(Direction.DOWN))
        game.step()
        assertEquals(Cell(11, 10), game.head)
    }

    @Test
    fun reverseInputIgnoredWhenLengthGreaterThanOne() {
        // 3×3 棋盘,固定果子在 (0,0);吃到后身长 2,此时禁止掉头
        val game = SnakeGame(
            3,
            3,
            listOf(Cell(0, 0), Cell(0, 1)),
            Direction.LEFT,
            Cell(2, 2)
        )
        assertEquals(2, game.length)
        assertFalse("向左移动时点击右应被忽略", game.turn(Direction.RIGHT))
        assertEquals(Direction.LEFT, game.nextDirection)
    }

    @Test
    fun reverseInputAcceptedWhenLengthIsOne() {
        val game = SnakeGame(5, 5, listOf(Cell(2, 2)), Direction.RIGHT, Cell(0, 0))
        assertTrue(game.turn(Direction.LEFT))
        assertEquals(Direction.LEFT, game.nextDirection)
    }

    @Test
    fun eatingFruitGrowsLengthAndScore() {
        // 蛇头 (0,1) 向右一步到果子 (0,2)
        val game = SnakeGame(5, 5, listOf(Cell(0, 1)), Direction.RIGHT, Cell(0, 2))
        game.step()
        assertEquals(1, game.score)
        assertEquals(2, game.length)
        assertEquals(Cell(0, 2), game.head)
        assertEquals(Cell(0, 1), game.body()[1])
    }

    @Test
    fun hittingWallLoses() {
        val game = SnakeGame(5, 5, listOf(Cell(0, 0)), Direction.UP, Cell(4, 4))
        assertEquals(Status.LOST, game.step())
    }

    @Test
    fun hittingOwnBodyLoses() {
        // 蛇头 (0,0) 向右进入颈部 (0,1) → 撞身
        val game = SnakeGame(
            4,
            4,
            listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(1, 0)),
            Direction.RIGHT,
            Cell(3, 3)
        )
        assertEquals(Status.LOST, game.step())
    }

    @Test
    fun fillingBoardWins() {
        // 2×2 棋盘,蛇身 3 格,吃到最后一格后铺满
        val game = SnakeGame(
            2,
            2,
            listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1)),
            Direction.DOWN,
            Cell(1, 0)
        )
        assertEquals(Status.WON, game.step())
        assertEquals(4, game.length)
    }

    @Test
    fun rewardIsScoreWithWinBonus() {
        val losing = SnakeGame(5, 5, listOf(Cell(0, 1)), Direction.RIGHT, Cell(0, 2))
        losing.step() // 吃 1 颗
        assertEquals(1, losing.score)
        assertEquals(1, losing.reward)

        val winning = SnakeGame(
            2,
            2,
            listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1)),
            Direction.DOWN,
            Cell(1, 0)
        )
        winning.step()
        assertEquals(Status.WON, winning.status)
        assertEquals(1, winning.score)
        assertEquals(101, winning.reward)
    }

    @Test
    fun stepAfterGameOverIsNoop() {
        val game = SnakeGame(5, 5, listOf(Cell(0, 0)), Direction.UP, Cell(4, 4))
        game.step()
        assertEquals(Status.LOST, game.status)
        assertEquals(Status.LOST, game.step())
    }
}
