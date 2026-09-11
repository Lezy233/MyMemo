# Design: friends-and-chat

## Context

已归档的能力:账号体系(user-auth)、词库与学习记录(word-library / word-learning,含"按用户统计当日学习记录"的查询)、进度环(study-progress-widget)。本变更全部基于本地模拟:好友即本机注册的其他账号,聊天通过双账号互发演示(见 proposal.md - Why)。

## Goals / Non-Goals

**Goals:**

- friendships / messages 两张新表,数据库版本 v2 → v3
- 好友列表用 ListView + BaseAdapter(课程硬性要求在此落地)
- 聊天界面左右气泡,消息本地存取

**Non-Goals:**

- 实时推送、未读消息角标、消息已读状态(本地模拟无推送通道)
- 图片/表情消息(仅文字)
- 删除好友时级联删除聊天记录(明确保留)

## Decisions

### D1: 数据模型 —— 单向申请、双向生效,DB 升 v3

```
  friendships(id, user_id, friend_id, status TEXT, created_at INTEGER)
      status: 'pending' | 'accepted'
  messages(id, sender_id, receiver_id, content TEXT, sent_at INTEGER)
```

- 申请存一条记录(user_id=发起人,friend_id=接收人,pending);同意后 status 改 accepted,查询好友时按"user_id=我 OR friend_id=我"双向匹配 —— 不需要存两条镜像记录
- 拒绝:直接 DELETE 该 pending 记录,天然支持"拒绝后可再次申请"
- 删除好友:DELETE accepted 记录,messages 不动
- 防重约束由代码校验(自己/重复申请/已是好友),不建复合唯一索引,保持表结构简单

### D2: 好友今日背词数 —— 复用既有查询

- 好友列表每项调用 word-library 已有的"按用户统计当日学习记录"逻辑,传入好友 id
- 列表 onResume 刷新,保证数字最新

### D3: 界面结构

```
  MainActivity
      |-- 好友入口 --> FriendListActivity (ListView: 头像/用户名/今日已背数)
      |                    |-- 添加好友按钮 --> 搜索对话框发起申请
      |                    |-- 申请入口 --> FriendRequestActivity (待处理申请,同意/拒绝)
      |                    |-- 点击好友项 --> ChatActivity
      |                    |-- 长按好友项 --> 删除确认对话框
      |
  ChatActivity: ListView + 双布局适配器(左/右气泡),底部输入框 + 发送按钮
```

- 删除好友用长按触发 —— 避免列表项上再放按钮造成误触;申请入口上显示待处理数量(简单文本即可)
- 所有新界面嵌入进度环(既有规格要求),onResume 刷新

### D4: 聊天刷新 —— 进入界面与发送后重查

- ChatActivity onCreate/onResume 查询双方全部消息按 sent_at 排序;发送成功后重查并滚动到底部
- **替代方案**: 轮询/Handler 定时刷新(模拟"对方正在输入"的实时感,但本地双账号演示时对方永远不在线,无意义,否决)

## Risks / Trade-offs

- [双向匹配查询("user_id=我 OR friend_id=我")写错导致好友关系错乱] → 数据访问层封装统一方法,UI 不直接拼 SQL;实现后用两个账号交叉验证
- [演示时需要频繁切换账号] → 这正是退出登录按钮的用武之地;走查脚本包含双账号互发全流程
- [消息多时 ListView 性能] → 课程演示数据量小,不引入 ViewHolder 之外的优化

## Open Questions

- 好友列表与聊天界面的具体视觉细节(气泡颜色、间距)—— 实现时按常识处理,不影响规格
