# Design: weather-and-location

## Context

课程新增联网要求(见 proposal.md - Why)。这是项目第一个真正联网的功能:此前全部为本地 SQLite 模拟。已确认:规格不锁定具体天气 API(实现优先 Open-Meteo)、地名显示经纬度、展示今日 + 未来 7 天预报。

## Goals / Non-Goals

**Goals:**

- 定位 → 联网查询 → 存库 → 展示的完整链路
- 无网络/无权限/无 GPS 时均有兜底,保证课程演示不翻车

**Non-Goals:**

- 逆地理编码显示城市名(显示经纬度即可,已与用户确认)
- 天气图标美化、多城市管理、逐小时预报
- 新闻资讯等其他联网特性(天气一个特性即满足要求)

## Decisions

### D1: 数据模型 —— DB v4 → v5,单表存查询记录

```
  weather_queries(id, latitude REAL, longitude REAL, queried_at INTEGER, result TEXT)
      -- result 为预报 JSON 原文,解析与存储解耦
```

- 存 JSON 原文而非逐字段建表:预报结构以 API 为准,存原文保证历史记录可完整重现;展示历史时重新解析
- 归属:查询记录不绑定 user_id —— 天气是设备级数据,与账号无关,切换账号后缓存仍可用

### D2: 定位 —— LocationManager + 运行时权限 + 默认坐标兜底

- 用系统 `LocationManager`(GPS/网络定位),Manifest 声明 `ACCESS_FINE_LOCATION`,运行时申请
- 兜底链:权限被拒 → 默认坐标(121.44, 31.19);定位超时(约 10 秒)或无信号 → 默认坐标;两种情况都提示"当前使用默认位置"
- **替代方案**: 高德定位 SDK(更准更快,但需注册 key 且引入 SDK 依赖;系统定位 + 兜底已够,否决)

### D3: 天气 API —— 适配层隔离,Open-Meteo 优先

- 定义内部接口 `WeatherProvider`(输入经纬度,输出今日 + 7 天预报的结构化结果),实现类首选 `OpenMeteoProvider`(`api.open-meteo.com/v1/forecast`,免 key,参数 `daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=Asia%2FShanghai`)
- 若演示环境无法连通 Open-Meteo,则实现 `AmapProvider`(高德天气,需用户注册 key)替换;规格与界面层不受影响
- 天气状况码(weathercode)映射为中文文案(晴/多云/小雨等)的对照表内置在适配层

### D4: 网络层 —— 子线程 + 内置 JSON 解析

- Manifest 声明 `INTERNET` 权限;请求在 `Executor` 子线程执行,结果切回主线程更新 UI
- HTTP 用 `HttpURLConnection`(零依赖),JSON 解析用内置 `org.json`;请求必须走 HTTPS
- **替代方案**: OkHttp + Gson(更好用,但为单一接口引入两个第三方依赖,否决;若实现中 HttpURLConnection 遇到麻烦可再评估)

### D5: 界面结构

```
  MainActivity --天气入口--> WeatherActivity
                                |-- 顶部: 当前坐标(或"默认位置"提示) + 刷新按钮
                                |-- 今日天气卡片(状况 + 最高/最低温)
                                |-- 7 天预报列表(ListView)
                                |-- 查询历史入口 --> WeatherHistoryActivity(ListView,时间倒序)
                                |-- 请求失败时: 展示最近一次缓存(标注时间)或失败提示
```

- 进入 WeatherActivity 触发"定位 → 查询"流程;刷新按钮重新执行
- 两个界面均嵌入进度环(既有规格要求)

## Risks / Trade-offs

- [演示环境(课堂/机房)网络不通或 API 不可达] → 双层兜底:API 适配层可换实现;数据库缓存保证"查过一次之后,无网也能展示"
- [模拟器无 GPS 导致定位卡住] → 默认坐标兜底 + 超时控制,演示前先在目标设备走查一遍
- [Open-Meteo 国内连通性不确定] → D3 的适配层设计把切换成本降到最低(换一个实现类)
- [天气状况码映射不全出现空白] → 未知码兜底显示"未知",不崩溃

## Open Questions

- ~~Open-Meteo 与高德在具体演示设备上的实际连通性 —— 实现任务中安排连通性验证,结果决定最终用哪个 Provider~~
  - **已解决(任务 3.2)**:在目标模拟器(Medium_Phone, API 37)上实测 `api.open-meteo.com` 可达,连续多次请求均返回今日 + 7 天数据并成功入库,故最终实现采用 `OpenMeteoProvider`,无需 `AmapProvider`。若演示环境网络受限,后续可按 D3 的适配层设计替换实现类。
