# Design: account-login

## Context

项目为 Android Studio 生成的 Kotlin + Jetpack Compose 模板,尚无任何业务代码(见 proposal.md - Why)。课程评分点明确要求多个活动、`ListView` 控件、继承 View 的自定义控件,因此 UI 技术栈转向传统 View 体系。

## Goals / Non-Goals

**Goals:**

- 用 Kotlin + 传统 View 体系(XML 布局、多 Activity)实现登录/注册/主界面
- 建立 SQLite 数据库基础(单一 `SQLiteOpenHelper`,后续变更在同库中扩展表)
- 实现背词进度环自定义 View,预留数据接入口

**Non-Goals:**

- 密码加密存储(课程项目,明文存储;如需可在后续变更中加哈希)
- 联网注册/登录、找回密码
- 背词进度环接入真实背词数据(属 word-library 变更)
- 自动登录/记住登录态(每次启动均显示登录界面)

## Decisions

### D1: UI 技术栈 —— Kotlin + 传统 View 体系,弃用 Compose

- 课程评分点("至少两个活动"、"必须使用 ListView"、"自定义控件")均指传统 View 体系概念;Compose 中无对应物,存在不得分风险
- 保留 Kotlin 语言(模板已是 Kotlin,View 体系完全兼容)
- 删除模板自带的 Compose 版 `MainActivity.kt` 与 `ui/theme/` 包,从 `build.gradle.kts` 移除 Compose 相关插件与依赖(compose plugin、compose BOM、activity-compose、material3、ui-tooling 等)及 `buildFeatures.compose`
- **替代方案**: 全 Compose(评分风险,否决);Java + View 体系(无端失去 Kotlin 简洁性,否决)

### D2: 界面结构 —— 三个 Activity

```
  LoginActivity (LAUNCHER 入口)
      |-- 注册入口 --> RegisterActivity
      |-- 登录成功 --> MainActivity (显示用户名、头像、进度环)
```

- 注册独立成 `RegisterActivity` 而非塞进登录页:登录页保持简洁,且天然满足"至少两个活动"
- Manifest 入口由模板 MainActivity 改为 LoginActivity;模板 MainActivity 重写为传统布局版

### D3: 头像方案 —— 内置预设头像

- 提供 6~8 个内置 drawable 头像,注册时以网格点选;数据库只存头像的资源名(字符串)
- **替代方案**: 相册选图(需处理运行时权限、URI 持久化、分区存储,复杂度远超课程要求,否决)

### D4: 数据持久化 —— 单一 SQLiteOpenHelper + users 表

- 新建 `AppDatabaseHelper extends SQLiteOpenHelper`(单例),本变更建 `users` 表:

```
  users(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    avatar TEXT NOT NULL          -- 内置头像资源名
  )
```

- 后续变更(word-library、friends-and-chat)在同一 Helper 的 `onCreate/onUpgrade` 中扩展新表,数据库版本号随之递增

### D5: 用户信息传递 —— Intent extras

- 登录成功后 `Intent.putExtra("username", ...)`、`putExtra("avatar", ...)` 启动 MainActivity;主界面按资源名解析 drawable 显示
- 不引入全局单例保存登录用户 —— 课程要求明确考察"活动间传参",Intent 是标准做法

### D6: 背词进度环 —— 自定义 View

- `StudyProgressRingView extends View`,`onDraw` 中用 `Paint` + `drawArc` 画底环与进度弧,中央绘制 "N/M" 文本
- 暴露 `setProgress(current: Int, max: Int)` 方法;自定义属性(环宽、颜色)通过 `attrs.xml` 声明
- 本变更中各 Activity 以占位数据(0 / 默认上限 30)调用;D6 的解耦设计保证 word-library 变更只需替换取值来源
- 复用方式:抽取公共布局片段 `<include>` 或在各布局中直接声明该自定义 View

## Risks / Trade-offs

- [移除 Compose 依赖后模板残留引用导致编译失败] → 移除依赖的同时删除 `MainActivity.kt` 与 `ui/theme/` 包,全量构建验证
- [进度环在极小屏幕上文字溢出] → 文本字号按控件尺寸比例计算,课程演示设备固定,风险可接受
- [数据库表结构后续变更频繁改动] → 从一开始就走 `onUpgrade` 版本管理机制,不依赖卸载重装

## Open Questions

- 主界面除用户名/头像/进度环外的具体内容(背词入口、好友入口等占位按钮的排布)—— 可在实现时按常识布局,不影响规格与任务拆分
