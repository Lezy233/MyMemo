# Design: stats-and-game

## Context

已归档:user-auth(账号)、word-library / word-learning(词库、学习队列、每日上限)、study-progress-widget(进度环)、friend-system / chat(好友与聊天)。本变更完成最后两块课程要求:画图展示数据库内容、自制小游戏(见 proposal.md - Why)。

## Goals / Non-Goals

**Goals:**

- 失败次数持久化(推翻此前"点击数据仅存内存"的决定 —— 饼图必须展示数据库内容)
- 饼图、贪吃蛇均用手写自定义 View,不引入第三方库
- 额度钱包与兑换,打通"学习-游戏-提额"闭环

**Non-Goals:**

- 历史日期/周月维度的统计图表(仅今日饼图)
- 贪吃蛇难度递增、加速、最高分排行、触屏滑动操控(仅四个虚拟按键)
- 中途退出对局的进度保存(未结束不结算)

## Decisions

### D1: 数据模型 —— DB v3 → v4

```
  users 增加列: quota_credit INTEGER DEFAULT 0
  study_records 增加列: fail_count INTEGER DEFAULT 0
  新表 word_daily_fails(id, user_id, word_id, fail_date TEXT, fail_count INTEGER)
      -- fail_date 格式 'yyyy-MM-dd',按 (user_id, word_id, fail_date) 逻辑唯一
```

- 点"不认识":对 (user, word, 今天) 执行 upsert,fail_count+1 —— 即使最终没学会,失败记录也在库中,支撑红色分类
- 学会时:读当日 fail_count 写入 study_records.fail_count
- 额度余额放 users 表:与账号绑定、查询方便,无需独立钱包表

### D2: 饼图数据 —— 四条查询,纯数据库计算

- 绿 = 今日 study_records 中 fail_count = 0 的条数
- 黄 = 今日 study_records 中 fail_count = 1 的条数
- 红 = 今日 word_daily_fails 中 fail_count ≥ 2 的去重单词数(含后来学会的 —— 已与用户确认)
- 灰 = max(0, min(剩余额度, 未学会单词总数) − 红色中未学会的数量)
  - 待背数用公式而非读内存队列:重启 App 后内存队列不存在,但饼图必须随时可画;公式结果与"今日计划要背还没背完"的直觉一致

### D3: 饼图渲染 —— 自定义 View

- `StudyPieChartView extends View`:按四类数量计算扇形角,`drawArc` 绘制,旁配图例(色块 + 类别名 + 数量)
- **替代方案**: MPAndroidChart(功能强但引入第三方依赖,课程"画图"更看重 Canvas 手绘能力,否决)

### D4: 贪吃蛇 —— 自定义棋盘 View + Handler 定时步进

- `SnakeBoardView extends View` 绘制 21×21 网格、蛇身、果子;游戏状态(蛇身坐标队列、方向、果子位置、得分)由 Activity 持有
- 游戏循环:`Handler.postDelayed` 每 ~300ms 步进一次;`onPause` 停表防后台空跑
- 四个方向按键更新"待生效方向";身长 >1 时忽略反向输入;方向在下一步移动时生效(避免一步内连按两键直接撞身)
- 按键样式:绘制一个等腰直角三角形 drawable,四个按键复用并分别旋转 0°/90°/180°/270° 指向各自方向;布局用 ConstraintLayout 按十字方位(小键盘 8/4/6/2)在界面下方均匀排布
- 果子在空格中随机生成;失败/胜利后弹结果对话框显示得分并重开
- **替代方案**: 触屏滑动操控(需求明确四个虚拟按键,不扩展);SurfaceView/线程循环(课程级复杂度,Handler 足够,否决)

### D5: 额度结算与兑换

- 结算时机:对局结束(失败/胜利)时一次性结算 —— 得分(果子数)+ 胜利 100;中途退出不结算,防止刷分
- 兑换入口:主界面显示额度余额,旁设"兑换上限"入口,对话框输入数量 → 校验(正整数、≤ 余额)→ `quota_credit -= n`、`daily_limit += n` → 立即生效,队列补充走 word-learning 既有规则

### D6: 界面接入

- 主界面新增两个入口:统计(饼图)、游戏(贪吃蛇);统计界面与游戏界面均嵌入进度环(既有规格要求);游戏界面的进度环用小号尺寸置于角落,不遮挡棋盘

## Risks / Trade-offs

- [灰色"待背"的公式与内存队列实际内容可能不一致(随机打乱)] → 规格只约束数量比例,不约束具体单词;饼图看的是分布,可接受
- [铺满 441 格判胜几乎不可达] → 保留作为理论胜利条件;正常对局以失败结算为主,不影响奖励逻辑验证
- [onUpgrade 迁移再增两列一表,链式升级(v1→v4)易出错] → 实现后按 v1 老包 → 覆盖安装最新包的路径验证全链路升级
- [Handler 泄漏导致 Activity 销毁后仍步进] → onDestroy 移除回调

## Open Questions

- 蛇的步进速度是否需要随得分加快 —— 当前定为恒速 300ms,后续体验不佳可在 polish 类变更中调整
