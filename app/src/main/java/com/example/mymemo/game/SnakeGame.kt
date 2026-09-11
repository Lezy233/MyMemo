package com.example.mymemo.game

import kotlin.random.Random

/**
 * 贪吃蛇游戏状态与规则(纯逻辑,不含 Android 依赖,便于单元测试)。
 *
 * - 棋盘 21×21,蛇起点为第 11 行第 11 列(0 基坐标 (10,10)),初始身长 1。
 * - 蛇身长 > 1 时忽略与「待生效方向」相反的输入(禁止直接掉头)。
 * - 撞墙或撞身判负;蛇身铺满全部格子判胜。
 * - 每吃一颗果子得分 +1;胜利额外奖励 [WIN_BONUS] 额度(见 [reward])。
 *
 * 游戏状态由 Activity 持有并通过 Handler 定时调用 [step] 推进,View 只负责渲染。
 */
class SnakeGame(
    val columns: Int = DEFAULT_SIZE,
    val rows: Int = DEFAULT_SIZE,
    private val random: Random = Random.Default
) {

    enum class Direction(val dRow: Int, val dCol: Int) {
        UP(-1, 0),
        DOWN(1, 0),
        LEFT(0, -1),
        RIGHT(0, 1);

        fun isOpposite(other: Direction): Boolean =
            dRow + other.dRow == 0 && dCol + other.dCol == 0
    }

    enum class Status { RUNNING, LOST, WON }

    data class Cell(val row: Int, val col: Int)

    private val snake = ArrayDeque<Cell>()
    private var direction: Direction = Direction.RIGHT
    private var pendingDirection: Direction = Direction.RIGHT
    private var fruitCell: Cell

    /** 本局已吃果子数。 */
    var score: Int = 0
        private set

    /** 当前对局状态。 */
    var status: Status = Status.RUNNING
        private set

    init {
        require(columns > 0 && rows > 0) { "棋盘尺寸必须为正" }
        // 21×21 时 (10,10) 即第 11 行第 11 列
        snake.addFirst(Cell(rows / 2, columns / 2))
        fruitCell = spawnFruit()
    }

    /** 仅供测试:直接构造指定蛇身、方向与果子,便于验证撞身/铺满等边界场景。 */
    internal constructor(
        columns: Int,
        rows: Int,
        body: List<Cell>,
        direction: Direction,
        fruit: Cell
    ) : this(columns, rows, Random(0)) {
        require(body.isNotEmpty()) { "蛇身不得为空" }
        snake.clear()
        body.forEach { snake.addLast(it) }
        this.direction = direction
        this.pendingDirection = direction
        this.fruitCell = fruit
    }

    /** 果子所在格。 */
    val fruit: Cell get() = fruitCell

    /** 当前蛇身长度(格数)。 */
    val length: Int get() = snake.size

    /** 蛇头所在格。 */
    val head: Cell get() = snake.first()

    /** 当前实际移动方向。 */
    val currentDirection: Direction get() = direction

    /** 下一步将生效的方向。 */
    val nextDirection: Direction get() = pendingDirection

    /** 蛇身从蛇头到蛇尾的坐标快照。 */
    fun body(): List<Cell> = snake.toList()

    /**
     * 请求转向。身长 > 1 时,与待生效方向相反的输入被忽略。
     *
     * @return 输入被接受返回 true;被忽略或对局已结束返回 false。
     */
    fun turn(newDirection: Direction): Boolean {
        if (status != Status.RUNNING) return false
        if (length > 1 && newDirection.isOpposite(pendingDirection)) return false
        pendingDirection = newDirection
        return true
    }

    /**
     * 推进一步:应用待生效方向、移动蛇头,处理吃果子与胜负判定。
     *
     * @return 推进后的对局状态。
     */
    fun step(): Status {
        if (status != Status.RUNNING) return status

        direction = pendingDirection
        val head = snake.first()
        val next = Cell(head.row + direction.dRow, head.col + direction.dCol)

        // 撞墙
        if (next.row !in 0 until rows || next.col !in 0 until columns) {
            status = Status.LOST
            return status
        }

        val eating = next == fruitCell
        // 不吃果子时蛇尾会让出格子,允许蛇头进入原蛇尾位置(标准规则)
        val occupied = if (eating) snake.toList() else snake.dropLast(1)
        if (occupied.any { it == next }) {
            status = Status.LOST
            return status
        }

        snake.addFirst(next)
        if (eating) {
            score += 1
            if (snake.size == rows * columns) {
                status = Status.WON
                return status
            }
            fruitCell = spawnFruit()
        } else {
            snake.removeLast()
        }
        return status
    }

    /** 本局结束时的额度奖励:果子数 + 胜利额外奖励(未结束/失败无额外奖励)。 */
    val reward: Int get() = score + if (status == Status.WON) WIN_BONUS else 0

    /** 在空格中随机生成一颗果子。 */
    private fun spawnFruit(): Cell {
        val occupied = snake.toHashSet()
        val empty = ArrayList<Cell>(rows * columns - snake.size)
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val cell = Cell(row, col)
                if (cell !in occupied) empty.add(cell)
            }
        }
        return empty[random.nextInt(empty.size)]
    }

    companion object {
        /** 默认棋盘边长(21×21)。 */
        const val DEFAULT_SIZE = 21

        /** 胜利额外奖励额度。 */
        const val WIN_BONUS = 100
    }
}
