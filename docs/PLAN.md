# tokens — Android 云端 Token 用量查看器

一个只做一件事的安卓 App：用 `vbu_` API Key 读 vibefriends.com 的云端用量，展示金额 / tokens / 趋势，并提供一个桌面小组件。

## 1. 目标与非目标

**目标**

- 输入 API Key 后，看「今天 / 24H / 7D / 30D / 90D」的费用、总 tokens、缓存读、趋势、按工具 / 按模型的排行。
- 桌面小组件（4×2）：今日费用、7 天费用、tokens、迷你趋势、数据时间与刷新入口。
- 断网 / 失败时显示上次缓存的数据 + 明确的时间戳，绝不显示假数字。

**非目标（第一版不做）**

- 不做上传 / 同步（这是 vibe-usage CLI 和桌面端的活）。
- 不做订阅配额（Cursor / Kimi / ZCode 那套是另一条数据链）。
- 不做设备码登录（要起浏览器 + 轮询，v2 再说），只支持手动粘贴 key。
- 不做登录 / 多账号 / 服务端。（多账号 = 多存几个 key，v2）

## 2. 数据源契约（已对真实账号验证）

```
GET {apiUrl}/api/usage?days=7&tz=Asia/Shanghai
Authorization: Bearer vbu_xxxx
```

- 时间参数三选一：`days=1|7|30|90`、`from=<ISO8601>`（「今天」用本机零点）、`from=YYYY-MM-DD&to=YYYY-MM-DD`。
- 必须带 `tz=<IANA 时区>`，安卓侧用 `TimeZone.getDefault().id`。
- 200 → JSON；401 → key 无效；其它 → 展示 HTTP 状态。无分页。

响应（实测字段）：

```jsonc
{
  "account": { "handle": "ziyuhaokun", "name": null },
  "buckets": [{
    "source": "claude-code", "model": "claude-opus-5-5", "project": "unknown",
    "hostname": "LAPTOP-...", "bucketStart": "2026-11-13T16:00:00.000Z",
    "inputTokens": 0, "outputTokens": 0, "cachedInputTokens": 0,
    "reasoningOutputTokens": 0, "cacheCreation5mTokens": 0, "cacheCreation1hTokens": 0,
    "totalTokens": 0, "estimatedCost": 0.0
  }],
  "sessions": [{ "source": "", "project": "", "hostname": "",
                 "firstMessageAt": "", "lastMessageAt": "",
                 "durationSeconds": 0, "activeSeconds": 0,
                 "messageCount": 0, "userMessageCount": 0 }],
  "hasAnyData": true,
  "names": { "sources": {}, "models": {}, "modelFamilies": {}, "families": [] }
}
```

口径（照搬 Windows 端，不要用 `totalTokens`）：

```
tokens = input + output + cachedInput + reasoning + cacheCreation5m + cacheCreation1h
费用   = 服务端 estimatedCost 求和（本地不做定价）
```

实测体量：7 天 30KB / 81 buckets，30 天 460KB，均 < 1s，**不需要分页、不需要 Room**。

## 3. 技术路线（已落地：当前最新稳定版 + AGP 9 内置 Kotlin）

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 / UI | Kotlin（AGP 9 内置编译器）+ Jetpack Compose（BOM 2026.11.01）+ Material 3 | 小组件必须原生；AGP 9 起 `org.jetbrains.kotlin.android` 不再需要（加了会报错），只加 `org.jetbrains.kotlin.plugin.compose` |
| 构建 | Gradle 9.9.2 + AGP 9.5.1 + JDK 21 | AGP 9.5 最低要求 Gradle 9.6 / JDK 17，最高支持 API 37；实测用 `C:\Program Files\Android\openjdk\jdk-21.0.8` |
| compileSdk / target | 37 + `compileSdkMinor = 2`、targetSdk 37 | Compose 1.13 要求 compileSdk ≥ 37.1，实测必须装 platform android-37.2 才能编过 |
| minSdk | 26 | java.time 可用、无需脱糖；本机 AVD（API 34）可验证 |
| 网络 | OkHttp 5.5.1 | 一个 GET 不引 Retrofit；超时 / 401 / 解析错误都有独立异常类型 |
| JSON | 系统 `org.json` | 响应结构固定；AGP 9 内置 Kotlin 下不引 kotlinx.serialization 插件，少一层兼容风险 |
| 存储 | DataStore Preferences 1.3.0（设置与缓存两个独立 store）+ Android Keystore AES-GCM | 只有 key + 一份 JSON 缓存，不值得上 Room；两个 store 是因为缓存写入曾触发设置流导致刷新死循环 |
| 小组件 | Glance 1.2.0（`GlanceAppWidget`） | 官方新一代组件 API；4×2 布局、点击、刷新按钮全用 Glance 原生能力 |
| 定时刷新 | WorkManager 2.12.0 periodic 30 分钟 + 联网约束 | 系统托管、跨重启存活；App 内另有手动刷新与缓存兜底 |
| 图表 | Compose 自绘柱状 | 一个柱状图不需要 Vico |

**为什么不 Flutter / RN**：小组件在两种方案里都得写原生，而 App 本身只有一个页面 + 一个 GET，跨端框架的收益（代码复用）在这里等于零，代价是多一层 bridge、多一套构建链、模拟器验证更麻烦。

## 4. 模块结构

```
tokens/
├─ settings.gradle.kts / build.gradle.kts / gradle.properties
├─ gradle/wrapper/…                     # 指向本机已缓存的 8.14.3
└─ app/src/main/
   ├─ java/com/ziyuhaokun/tokens/
   │  ├─ MainActivity.kt                # 单 Activity，路由：未配 key → 引导页，否则 → 仪表盘
   │  ├─ data/ApiClient.kt              # GET /api/usage，拼 tz，超时 20s
   │  ├─ data/UsageModels.kt            # UsageResponse / Bucket / Session / Names
   │  ├─ data/UsageRepository.kt        # 拉取 + 缓存 + 失败回退
   │  ├─ data/SecureKeyStore.kt         # Keystore AES-GCM 包装 SharedPreferences
   │  ├─ data/UsageCache.kt             # 原样存 JSON + fetchedAt
   │  ├─ ui/DashboardScreen.kt          # 概览卡 / 范围切换 / 排行
   │  ├─ ui/TrendChart.kt               # Canvas 柱状图
   │  ├─ ui/SettingsScreen.kt           # key、apiUrl、默认范围、刷新间隔
   │  ├─ ui/theme/…                     # 深色优先
   │  └─ widget/UsageWidgetProvider.kt  # RemoteViews + Bitmap 迷你趋势
   └─ res/…
```

单模块、手动注入（`AppContainer` 单例），不上 Hilt。

## 5. 关键设计

**刷新策略**

- App 在前台：进入 / 切范围时拉一次；下拉或按钮手动刷新。
- 后台：`JobScheduler` 每 30 分钟拉一次并写缓存、通知小组件重绘。仅在「已配置 key」且「小组件存在于桌面」时调度，省电。
- 小组件：读缓存立即渲染，点击刷新按钮触发一次立即抓取。

**错误与空态**

| 情况 | App | 小组件 |
|---|---|---|
| 没配 key | 引导页 | 「点此配置」 |
| 401 | 「API Key 无效」+ 去设置 | 同上，红点 |
| 网络 / 5xx | 显示缓存 + 「更新于 xx:xx」 | 缓存 + 置灰时间 |
| `hasAnyData=false` | 「账号还没有上传记录」 | 「无数据」 |

**隐私**

- key 加密存储，绝不写日志、不进 `adb logcat`、不出现在崩溃信息里。
- 只发一个 GET，不上传任何本地数据。

**时区**

- 请求带 `tz=TimeZone.getDefault().id`；按天分组用本机时区，与网页端一致。

## 6. 里程碑与验收

| # | 内容 | 完成标准 |
|---|---|---|
| M0 | Gradle 骨架 | `gradlew assembleDebug` 通过，模拟器装得上、能启动空页面 |
| M1 | 数据层 | 填入真实 key 能拿到 7 天数据；401 / 超时 / 断网三种错误都有明确提示；缓存回退生效 |
| M2 | 仪表盘 | 五个范围切换正确；概览卡与 `curl` 结果一致（金额、tokens）；柱状图与排行可读 |
| M3 | 小组件 | 4×2 上桌面，数量正确；30 分钟自动刷新；点击刷新即时生效；点击进入 App |
| M4 | 收尾 | 设置页完整；深色主题；`assembleDebug` 出可侧载 APK；README 写清安装与配置 |
| M5 | 年度热力图 | 一次拉 365 天（`from`/`to`），按「年-月」分行日历格；颜色分档随数据分位；点格子看当天明细；可切「费用 / Tokens」 |
| M6 | 设置与主题 | 设置独立成页；主题深色/浅色/跟随系统；强调色、动态效果、文字大小可调；深浅两套色值都走同一套角色，不另做一套 UI |

## 7. 风险

| 风险 | 对策 |
|---|---|
| 接口无版本号，字段可能变 | 解析全部防御式（缺字段给默认值），未知字段忽略；`names` 缺失时回退原始 id |
| 响应体积（90 天可能上 MB） | 后台线程解析 + 只保留聚合所需字段的缓存副本；必要时把 90 天拆成两次请求 |
| 小组件刷新被系统压制 | 内容带「更新于」时间戳，用户能看到是否新鲜；提供手动刷新 |
| 桌面端/网页端口径差异 | 明示采用 `computedTotal` 口径，README 里写清 |
| 无真机 | 先用本机 API 24 模拟器验证 UI 与后台任务 |

## 8. 待确认

1. 是否做 v2 设备码登录（浏览器授权，免手动粘贴 key）？
2. 是否需要自定义日期区间 / 多账号？
3. 应用 id 与 App 名维持 `com.ziyuhaokun.tokens` / `Tokens`？

## 9. 实施状态（截至首次构建）

| 里程碑 | 状态 |
|---|---|
| M0 骨架 | 完成：`assembleDebug` 通过 |
| M1 数据层 | 完成：真实 key 拉到 7 天数据；401/网络/解析错误有提示；缓存回退；CancellationException 正确透传 |
| M2 仪表盘 | 完成：模拟器实拍验证（7D、今天两种粒度） |
| M3 小组件 | 完成：真机桌面实拍验证（4×2，当前为单色白灰、金额拆整数/小数）；provider 已注册、WorkManager 已排任务 |
| M4 收尾 | 部分：设置页、可侧载 APK 已有；自定义区间、多账号未做 |

UI 于第二轮按 Vibe Usage 桌面端视觉重做：单色系（去除蓝/金主色）、圆角 24 卡片、
大号细体金额拆整数/小数、平滑面积曲线、刻度环、1dp 发丝分隔；文字型时间范围与入口。

第三轮（M5）参照 TOKNIX 补年度热力图：顶部加「概览 / 热力」一级切换，
热力图按 13 行（跨年的 11 月各占一行）× 31 列排布，空槽压到接近卡片底色，
色阶取非零日的 40/70/90 分位，避免不同账号量级差导致一片死黑。

第四轮（M6）把设置从弹窗改成独立页，并把颜色从写死的 `Palette` 常量改成
`CompositionLocal` 主题注入（保留 `Palette.X` 的调用写法）：
- 主题：深色 / 浅色 / 跟随系统；浅色是同一套角色值的白底版本，不是另一套 UI
- 强调色：默认单色（= 正文色），可选蓝/紫/青/橙；只作用于大金额、选中标签、
  图表主线、热力图最亮档
- 文字大小：85 / 100 / 115 / 130%，走 `density.fontScale`；大字号时多列自动改单列，
  金额字号不再跟着放大，避免一行放不下
- 动态效果：完整 / 精简，控制页面淡入与图表入场动画
真机（Xiaomi / Android 17，1220x2656）实拍验证。

图标（第三轮）：白底黑标（三根递进胶囊 + 四角星），用于 adaptive icon 的
background/foreground/monochrome 三层；对齐参考的 Toknix 风格——浅色底 + 粗黑标记。
