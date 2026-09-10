# Tasks: account-login

## 1. 技术栈切换:移除 Compose

- [ ] 1.1 删除 Compose 版 `MainActivity.kt` 与 `ui/theme/` 包,从 `app/build.gradle.kts` 移除 Compose 插件、依赖与 `buildFeatures.compose`;执行 `./gradlew assembleDebug` 确认无 Compose 残留引用、构建通过
- [ ] 1.2 补充 View 体系所需依赖(如 `androidx.appcompat`、`material`、`constraintlayout`,按 layouts 实际需要);`./gradlew assembleDebug` 通过

## 2. 数据库基础

- [ ] 2.1 新建 `AppDatabaseHelper`(单例 SQLiteOpenHelper),`onCreate` 创建 users 表(id / username UNIQUE / password / avatar);编写后可实例化并调用 `writableDatabase` 不抛异常
- [ ] 2.2 实现用户数据访问方法:插入用户(捕获用户名冲突)、按用户名查询、校验用户名密码;用临时测试代码或日志验证增查正确

## 3. 自定义控件:背词进度环

- [ ] 3.1 新建 `StudyProgressRingView extends View`,声明 attrs(环宽、颜色),`onDraw` 绘制底环 + 进度弧 + 中央 "N/M" 文本,提供 `setProgress(current, max)`;在测试布局中设置多组数值,肉眼验证比例与文本正确(含 0、超上限满环两种情况)

## 4. 注册界面(RegisterActivity)

- [ ] 4.1 实现注册布局:用户名、密码输入框,6~8 个内置头像网格点选(有选中态),提交按钮;布局预览正常
- [ ] 4.2 实现注册逻辑:空值/未选头像/用户名冲突分别提示,成功则写入数据库并提示;真机或模拟器验证规格中三个注册场景

## 5. 登录界面(LoginActivity)

- [ ] 5.1 实现登录布局(用户名、密码、登录按钮、注册入口),并将 LoginActivity 设为 Manifest LAUNCHER 入口;冷启动应用首先显示登录界面
- [ ] 5.2 实现登录逻辑:校验失败(密码错误、用户名不存在)提示并停留,成功则 Intent 携带 username + avatar 跳转 MainActivity;真机或模拟器验证规格中三个登录场景

## 6. 主界面(MainActivity)

- [ ] 6.1 重写主界面布局:显示 Intent 传入的用户名与头像(按资源名解析 drawable),顶部嵌入进度环(占位数据 0/30);登录后进入主界面可见正确的用户名、头像与进度环

## 7. 集成验证

- [ ] 7.1 完整走查:启动 → 注册(含各失败场景)→ 登录(含各失败场景)→ 主界面展示;旋转屏幕或返回重进验证进度环在各界面显示一致;确认进度环已应用于登录后的所有 Activity
