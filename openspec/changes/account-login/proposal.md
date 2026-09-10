# Proposal: account-login

## Why

本 APP(背单词软件，含账号、好友、聊天、每日背词上限与游戏提额)尚无任何功能代码。课程小作业要求以登录界面为第一个活动，包含用户名、密码、头像选择，并将用户名与头像传递到其他活动展示，同时要求设计一个自定义控件并在所有活动中应用。本变更建立整个 APP 的入口与账号基础。

## What Changes

- 新增登录/注册界面作为 APP 启动后的第一个活动：用户名 + 密码 + 头像选择
- 用户凭据存储在本地 SQLite 数据库中(单机模拟，无联网)
- 登录成功后跳转到主界面活动，通过 Intent 传递用户名与头像并展示
- 新增自定义控件「背词进度环」(圆形进度 View，显示今日已背 N / 上限 M),应用在登录后的所有活动中;本变更中进度数据为占位值(0 / 默认上限),数据源将在 word-library 变更中接入

## Capabilities

### New Capabilities

- `user-auth`: 用户注册与登录(用户名、密码、头像选择),凭据本地持久化,登录成功后向主界面传递用户名与头像
- `study-progress-widget`: 背词进度环自定义控件的定义与在所有活动中的应用

### Modified Capabilities

(无 —— 项目尚无既有规格)

## Impact

- 新建 Android 活动:LoginActivity(启动入口)、MainActivity(主界面)
- 新建自定义 View:进度环控件
- 新建本地数据库帮助类与用户表(SQLite)
- AndroidManifest 入口由模板 MainActivity 调整为 LoginActivity
- 后续变更(word-library、friends-and-chat、stats-and-game)均依赖本变更建立的账号体系与数据库基础
