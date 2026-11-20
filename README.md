# Tokens

安卓上看 vibecafe.ai 云端 token 用量的小工具：一个页面 + 一个桌面小组件，只读，不上传任何东西。

## 现在能做什么

- 输入 `vbu_` API Key（存进 Android Keystore 加密后落盘），直接读 `GET /api/usage`
- 今天 / 24H / 7D / 30D / 90D 五个范围：费用、tokens、缓存读、活跃时长、按天/按小时趋势、按工具、按模型、按项目排行
- 桌面小组件 4×2：今日费用、7 天费用、7 天 tokens、7 天迷你柱状图、更新时间；点右上角刷新，点卡片进 App
- 后台每 30 分钟（WorkManager，需联网）刷新一次并重绘小组件
- 断网 / 401 / 服务端报错时显示上次缓存 + 「更新于 xx:xx」，不给假数字

## 构建

需要 JDK 21 与 Android SDK（platform 37.2）。本机已验证的路径：

```bash
export JAVA_HOME="C:\Program Files\Android\openjdk\jdk-21.0.8"
./gradlew.bat assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` 里写 `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk`（用正斜杠，别用反斜杠转义）。

## 安装 / 跑起来

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.ziyuhaokun.tokens/.ui.MainActivity
```

小组件：长按桌面 → Widgets → Tokens → 拖到桌面。

## 技术栈

| 层 | 选型 |
|---|---|
| 构建 | Gradle 9.9.2 + AGP 9.5.1（内置 Kotlin，无需 `kotlin-android` 插件）+ JDK 21 |
| SDK | compileSdk 37 / `compileSdkMinor = 2`、targetSdk 37、minSdk 26 |
| UI | Compose（BOM 2026.11.01）+ Material 3 |
| 小组件 | Glance 1.2.0（`GlanceAppWidget` + `RemoteViews` 后端） |
| 后台 | WorkManager 2.12.0（30 分钟 periodic，联网约束） |
| 存储 | DataStore Preferences 1.3.0（设置与缓存分开两个 store）+ Keystore AES-GCM |
| 网络 | OkHttp 5.5.1；JSON 用系统 `org.json`（AGP 9 内置 Kotlin 下不引序列化插件） |

## 数据口径

```
tokens = input + output + cachedInput + reasoning + cacheCreation5m + cacheCreation1h
费用   = Σ estimatedCost（服务端算好的，本地不建定价表）
```

「今天」用 `from=<本机零点 ISO>`，其余用 `days=N`，请求一律带 `tz=<本机 IANA 时区>`。

## 已知边界

- 只读，不做上传/同步（那是 vibe-usage CLI 与桌面端的活）
- 项目名大多是 `unknown`：服务端默认不收集（上传端 `uploadProject` 关闭）
- `sessions` 可能为空数组，此时「活跃」显示 0
- 还没有自定义日期区间、多账号、设备码登录
