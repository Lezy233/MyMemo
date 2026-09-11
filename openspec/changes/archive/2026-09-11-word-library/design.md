# Design: word-library

## Context

account-login 已归档:`AppDatabaseHelper`(v1,users 表)、登录/注册/主界面、进度环占位数据均已就绪。本变更在此地基上加入核心业务(见 proposal.md - Why)。

## Goals / Non-Goals

**Goals:**

- words / study_records 两张新表 + users 表加列,走 `onUpgrade` 迁移(v1 → v2)
- 内存态学习队列,实现"不认识插回、连续两次认识计学会"的判定逻辑
- 词库管理界面承载课程要求的"增删查改"演示

**Non-Goals:**

- 艾宾浩斯复习曲线、记忆熟练度分级(留作未来扩展)
- 学习队列的跨进程持久化(中途退出丢失队列进度,已学会单词不受影响)
- 额度提升的实现(属 stats-and-game;本变更只保证上限存取与执行)
- 统计图表(属 stats-and-game)

## Decisions

### D1: 数据模型 —— 两张新表 + users 加列,DB 版本升 v2

```
  words(id, word TEXT UNIQUE, meaning TEXT, is_preset INTEGER)
  study_records(id, user_id, word_id, learned_at INTEGER)   -- 毫秒时间戳
  users 增加列: daily_limit INTEGER DEFAULT 30
```

- `AppDatabaseHelper` 版本 1→2:`onUpgrade` 中执行 `CREATE TABLE` × 2 + `ALTER TABLE users ADD COLUMN`
- 手动添加的单词与预制词同表(`is_preset` 区分来源),**全账号共享** —— 单机演示中"全班共用一本词书"的直觉模型,省去按账号过滤
- study_records 只记"学会"事件;队列中间态(连续认识计数)不进库

### D2: 预制词表初始化 —— 内置数据 + 幂等播种

- 词表以代码内置数组(或 assets 文本文件)形式打包,约 150 个常用四级词,仅单词 + 释义
- 播种时机:数据库 `onCreate`/`onUpgrade` 后检查 words 表为空则批量插入(单事务);空表判断保证幂等

### D3: 学习队列 —— 内存队列 + 单词状态机(修订)

- 会话开始:查询词库中无学习记录的单词,随机打乱,按**当日剩余额度**(每日上限 - 今日已背数)截取,构建 `Deque<QueueItem>`;`QueueItem = (wordId, failedToday, streak, tapHistory)`
- 点"认识":若 `failedToday == false`(初见)→ 直接写 study_records、出队;否则 streak+1,streak==2 → 写库出队,不足则留队
- 点"不认识":`failedToday = true`、streak=0,插入到距队首第 3 位(剩余不足 3 张则队尾)
- 每次点击向 `tapHistory` 追加一条记录(认识/不认识),卡片据此在释义下方渲染蓝/红色块;历史仅存内存,会话结束清空(已与用户确认为会话级)
- 上限提升(游戏奖励或手动设置)后:按新剩余额度从未学会词中补充队列
- 每次判定后检查:已背数达到当日上限 → 终止会话并提示;队列空 → 提示完成
- **替代方案**: 队列与点击历史持久化到数据库(增加会话表/点击记录表,复杂度不成比例,否决);初见单词也需两次认识(违背"初见即熟知"的直觉,经用户指出后修正)

### D4: 今日已背数 —— 按日期查询,零重置逻辑

- `今日已背 = SELECT COUNT(*) FROM study_records WHERE user_id=? AND 日期(learned_at)=今天`
- 跨天自动归零;历史记录天然留存,直接服务 stats-and-game 的图表

### D5: 上限设置 —— 主界面弹窗编辑

- 主界面提供"每日上限"设置入口(对话框输入数值),写入 users.daily_limit,立即生效
- 额度提升(游戏奖励)届时走同一写入路径,只改数值来源

### D6: 进度环接入 —— 各活动 onResume 刷新

- 各 Activity `onResume` 时查询 D4 的已背数与 users.daily_limit,调用 `setProgress()`;从背词界面返回主界面时进度即时更新
- 控件绘制逻辑零改动 —— 验证"数据来源可替换"这条规格确实成立

## Risks / Trade-offs

- [预制词表数据质量:手写 150 个单词易出错] → 用常见四级高频词,实现时抽查;释义保持简短
- [onUpgrade 迁移写错导致老用户崩溃] → 迁移只加表加列,不动既有数据;实现后先装 v1 再覆盖安装 v2 验证
- [队列随机打乱后同一词短期内重复出现体验差] → "隔 3 张插回"规则已缓解;词库量级下可接受
- [用户把上限设为 0 或负数] → 设置入口校验为正整数,非法值拒绝并提示

## Open Questions

- 词库管理界面用 ListView 还是 RecyclerView —— 实现时按布局需要定,不影响规格与任务拆分(好友列表的 ListView 硬性要求由 friends-and-chat 满足)
