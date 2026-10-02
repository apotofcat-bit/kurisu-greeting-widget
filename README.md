# 问候小组件（Greeting Widget）

一个 Android 桌面小组件：随机显示一句《命运石之门》里**牧濑红莉栖口吻**的预设语句，
**点一下换一句**，另外每小时自动换一次。语句会按**时段、季节、天气**自动挑选。

面向设备：Pixel 5 / Android 14（`minSdk 26`、`targetSdk 34`，其它 Android 设备同样可用）。

---

## 0. 这个仓库是两部分工作

| | 第一部分：小组件本身 | 第二部分：语句与角色语气 |
|---|---|---|
| **做了什么** | Android AppWidget：RemoteViews 渲染、点击换句、WorkManager 定时、定位 + Open-Meteo 天气、按场景分组的抽取逻辑 | 学了原作里红莉栖的说话方式，产出 **189 条中文语句**、分成 18 组，并写成可维护的格式 |
| **主要文件** | `app/src/main/java/com/example/greetingwidget/`、`app/src/main/res/` 下的布局与配色 | `kurisu_greetings.txt`（源头）、`kurisu_voice_notes.md`（语气笔记）、`tools/sync_strings.py`（同步脚本） |
| **依赖关系** | 不依赖第二部分，换掉句子池就是另一个小组件 | 依赖第一部分定义的**分组 key** 和**长度约束** |
| **怎么改** | 见 [§4 改外观](#4-改外观) | 见 [§3 改语句](#3-改语句) 和 [§9 角色语气的来路](#9-角色语气的来路) |

两部分是解耦的：**句子池可以整体替换**（换角色、换成别的主题），
而**抽取逻辑一行都不用改** —— 只要新句子沿用时段的 key 就行。

---

## 1. 基本功能

- **随机显示预设语句**，点一下小组件立刻换一句（主要交互）。
- **按场景挑选**：先判断当前是哪个时段（早/午/晚/深夜）、哪个季节、什么天气，
  再按权重从对应分组里抽一句。
- **每小时兜底刷新一次**（WorkManager），保证你不点它也会变。
- **天气自动获取**：授权定位后，后台拉 Open-Meteo 的当前天气，映射成晴/阴/雾/雨/雪/雷暴/高温/低温。
- **断网/没授权也不崩**：天气这一组自动消失，权重让给其它组，只是"少一类句子"。
- **跟随系统配色**：浅色/深色/Android 12+ 壁纸取色，四个资源文件自动切换。
- **没有一张 PNG**：图标、气泡背景、尾巴全是矢量/XML。

### 语句长什么样

```
greetings_lab（实验室日常）
  该吃午饭了。泡面…啊，你有看到我的叉子吗？
  嘶，外面真是冷死了。幸好实验室的暖气够足。      ← 冬天分组
  你没带伞吧。…毛巾在架子上，自己拿。              ← 雨天分组

greetings_anytime（通用）
  有证据再下结论，这是基本素养吧。
  需要帮忙的话就说，我不会主动问第二次。
```

风格是"傲娇 + 科学家"：关心先否定再给，说完就收回去；爱用生理学和技术名词包装；
不刻意卖萌，也不写成养生小贴士。详细依据见 [§9](#9-角色语气的来路)。

---

## 2. 目录结构

```
greeting_widget/
├── settings.gradle.kts
├── build.gradle.kts                    插件版本（AGP 8.5.2 / Kotlin 1.9.24）
├── gradle.properties
├── local.properties                    构建时生成，指向 SDK 位置（.gitignore 已忽略）
│
├── kurisu_greetings.txt                ★ 语句源头（189 句 / 18 组 / 带长度约束）
├── kurisu_voice_notes.md               ★ 语气笔记：从原作台词整理的角色说话方式
├── kurisu_greetings.v3-you-edited.txt  历史版本备份（165 句）
├── kurisu_greetings.v2-150.txt         历史版本备份（150 句）
│
├── app/
│   ├── build.gradle.kts                模块配置：compileSdk 34 / minSdk 26 / targetSdk 34
│   └── src/main/
│       ├── AndroidManifest.xml          注册 Provider + 绑定组件配置
│       ├── java/com/example/greetingwidget/
│       │   ├── GreetingWidgetProvider.kt    ★ 核心：接收刷新事件、渲染 RemoteViews
│       │   ├── GreetingRepository.kt        ★ 按 时段/季节/天气/通用/日常 分组挑句子
│       │   ├── WeatherRepository.kt         ★ Open-Meteo 请求 + 定位 + 缓存
│       │   ├── GreetingRefreshWorker.kt     后台刷新（WorkManager，联网在这里发生）
│       │   └── MainActivity.kt              说明页 + 授权定位 + 「立刻换一句」
│       └── res/
│           ├── layout/widget_greeting.xml       ★ 小组件布局
│           ├── layout/activity_main.xml
│           ├── xml/greeting_widget_info.xml     ★ 小组件尺寸 / 预览 / 刷新周期
│           ├── drawable/widget_bubble.xml       ★ 对话气泡背景（圆角矩形主体）
│           ├── drawable/widget_bubble_tail.xml  ★ 气泡右下角的小尾巴（矢量三角）
│           ├── drawable/ic_launcher_foreground.xml
│           ├── mipmap-anydpi-v26/ic_launcher.xml  纯 XML 自适应图标
│           ├── values/colors.xml               颜色兜底值（浅色模式）
│           ├── values-night/colors.xml         深色模式配色
│           ├── values-v31/colors.xml           Android 12+ 跟随壁纸取色
│           ├── values-night-v31/colors.xml     Android 12+ 且深色模式
│           └── values/{strings,themes}.xml     strings.xml 由同步脚本生成，别手改
└── tools/
    ├── setup-toolchain.ps1             一键装构建链并编译 APK
    ├── build.ps1                       只编译（日常改完代码用这个）
    ├── sync_strings.py                 ★ 从 kurisu_greetings.txt 生成 strings.xml
    ├── setup-studio.ps1                安装 Android Studio（免安装 ZIP 版）
    ├── strings.v2-150.xml.bak          历史版本 strings.xml 备份
    └── install-to-phone.ps1            装到手机 + 输出调试命令
```

---

## 3. 改语句

语句**只有一个源头**：根目录的 `kurisu_greetings.txt`。
一行 = 一句，往对应分类下面加一行就行：

```
============================================================
greetings_lab  |  实验室日常（同属一个 lab 的日常对话 / 自言自语）
============================================================

该吃午饭了。泡面…啊，你有看到我的叉子吗？
咖啡又要没了啊…这次轮到你买了吧？
```

改完跑同步脚本，它会重新生成 `strings.xml` 并自动转义 `%` → `%%`、`&` → `&amp;`：

```powershell
python tools\sync_strings.py          # 生成
python tools\sync_strings.py --check  # 只校验两边是否一致
```

> **为什么不直接改 `strings.xml`**：189 句、18 个数组，两份副本手改一定会漂移。
> 草稿文件还带分类说明和长度约束，比 XML 好读得多。

**长度约束**：小组件按 2×2 格设计（约 4 行、每行 ~9 字）。
12–30 字是舒适区，硬上限 36 字（4 行整满），实际按 **34 字** 写。超了会被省略号截断。

新增**一组**（而不是往现有组里加句子）要改三处：
草稿里加分类、`tools/sync_strings.py` 的 `KEY_ORDER`/`TITLES`、
以及 `GreetingRepository` 的权重和 `arrayRes()` 映射。

### 抽取权重

`GreetingRepository` 每次先按权重抽一个组，再从组里随机抽一句（并避开上一句）：

| 组 | 权重 |
|---|---:|
| 时段（morning / afternoon / evening / night） | 25% |
| 日常（lab） | 25% |
| 天气（8 种） | 20% |
| 通用（anytime） | 20% |
| 季节（4 种） | 10% |

「通用 + 日常」合计 45%，是主力；天气只占 20%，所以断网也不会让语句变单调。
天气拿不到时，"天气"这一组自动从候选里消失，权重让给其它组。

（季节按北半球、气象学常用的月份划分。如果你会去南半球，改 `GreetingRepository.season()` 即可。）

---

## 4. 改外观

**形状**（现在是"对话气泡"）：

- 气泡形状 → `drawable/widget_bubble.xml`
  这是一个 layer-list：一层是圆角矩形主体，一层是左下角的尾巴。
  调 `android:radius` 改圆角大小，调主体那层的 `android:bottom` 改尾巴露多高。
- 尾巴的角度和位置 → `drawable/widget_bubble_tail.xml`
  一个矢量三角，`pathData="M8,0 L38,0 L38,20 Z"` 里的三个点就是三个顶点。
  顶点放在右边（右下那个点）就是朝右下；放到左边就是朝左下，改这一行即可。

**颜色**（跟随系统，不需要写任何代码，Android 按限定符自动挑一个）：

| 文件 | 什么时候生效 |
|---|---|
| `values/colors.xml` | Android 11 及以下，或浅色模式的兜底 |
| `values-night/colors.xml` | 系统开了深色模式 |
| `values-v31/colors.xml` | Android 12+，跟随壁纸取色（Material You） |
| `values-night-v31/colors.xml` | Android 12+ 且深色模式 |

改颜色时**四个文件都要改**，否则某些场景下会串味：只改 `values/colors.xml` 的话，
浅色模式变了，一开深色模式又变回旧颜色。

> ⚠️ 目录名 `values-night-v31` 的顺序不能反。Android 的资源限定符有固定书写顺序，
> `night` 必须写在版本号前面，写成 `values-v31-night` 不会被识别。

**字号 / 对齐 / 内边距** → `layout/widget_greeting.xml` 里 `tv_greeting` 和 `FrameLayout` 的属性

**尺寸（占几格）** → `xml/greeting_widget_info.xml`。
`minWidth` 的换算公式是 `70 * n - 30` dp 对应 n 个格子（110dp ≈ 2 格）。
Android 12+ 优先用 `targetCellWidth` / `targetCellHeight` 按格子数指定，更准。

---

## 5. 它是怎么工作的

Android 的小组件不能像普通界面那样直接操作 View。它是一个**跨进程**的东西：

```
你的 App 进程                                  桌面（Launcher）进程
──────────────                                ──────────────────
GreetingWidgetProvider
   │ 选一句问候语
   │ 组装 RemoteViews（一份"布局说明书"）
   └──────── AppWidgetManager.updateAppWidget() ────────► 渲染成桌面上的控件
```

所以：

- 小组件布局**只能用 RemoteViews 支持的控件**（`TextView` / `ImageView` / `Button` /
  `LinearLayout` / `FrameLayout` / `RelativeLayout` / `GridLayout` / `ListView`）。
  用 `ConstraintLayout`、`RecyclerView`、自定义 View 会直接崩，**而且崩在桌面进程里**，
  按你的包名过滤日志是看不到的。
- `GreetingWidgetProvider` 是一个 `BroadcastReceiver`，跑在**你自己的 App 进程**里。

### 刷新时机的选择

| 机制 | 说明 |
|---|---|
| `android:updatePeriodMillis` | **本项目设为 0 禁用了。** 它的最小值就是 30 分钟，而且系统会把多个小组件的刷新合并唤醒，时间完全不可控。 |
| WorkManager 定时 | 每 1 小时兜底刷一次。最小周期 15 分钟，省电模式下会被推迟——"尽力而为"。 |
| **点击小组件** | **主要机制。** 即时、可控、不依赖后台调度，在国产 ROM 的省电策略下也不会失效。 |

结论：「点一下换一句」才是用户真正能感知到的随机，定时只是兜底。

### 天气数据：Open-Meteo

用的是 [Open-Meteo](https://open-meteo.com/)：**免密钥、免注册**，一个 GET 就出数据。
免费额度每天 10,000 次（我们每小时才 1 次），**仅限非商业用途**，数据本身是 CC BY 4.0 许可、需要署名。

> ⚠️ **Google 的天气 API 用不了。** 实测 `weather.googleapis.com` 和 `maps.googleapis.com`
> 在本机网络下直接超时（HTTP `000`），不是费用或权限问题；而且它还要 Google Cloud 项目 +
> 结算账号 + API Key。`api.openweathermap.org` 同样不通。

### 数据流（这条最重要）

```
MainActivity（你点一次「授权定位」）
    └─ 取系统里已有的最近定位  →  存进 SharedPreferences
GreetingRefreshWorker（WorkManager：每小时一次 + 按需一次性）
    ├─ 联网拉 Open-Meteo      →  存进 SharedPreferences
    └─ 通知小组件重画
GreetingWidgetProvider.render()
    └─ 只读缓存，绝不联网
```

**小组件永远不直接联网。** `AppWidgetProvider.onUpdate` 是广播回调，超过约 10 秒不返回就 ANR，
所以联网全部转移给 WorkManager 的后台线程。

天气超过 **6 小时**就算过期，宁可不用也不显示错的。

---

## 6. 构建

> 下面几条命令里的路径是本机（`D:\...`）的写法。你 clone 到别处的话，
> 把 `D:\sth_interesting\greeting_widget` 换成你自己的路径即可；
> `tools/*.ps1` 里的 `$Root = 'D:\Android'` 也可以改，或者把 JDK/SDK/Gradle 装到那儿。

### 用命令行

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
& 'D:\sth_interesting\greeting_widget\tools\setup-toolchain.ps1'
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

工具链装好之后，日常改完代码重新编译用更轻的这个（不做任何下载/安装，只跑 `assembleDebug`）：

```powershell
& 'D:\sth_interesting\greeting_widget\tools\build.ps1'
```

> ⚠️ 这两个脚本都必须在**放开沙箱**（或普通 PowerShell 窗口）下跑：前者要联网下载，
> 后者要读写 `D:\Android\gradle-home` 里的依赖缓存（在工作目录之外）。
> 如果在受限沙箱里跑，进程拿不到 TLS 凭证，任何 HTTPS 请求都会以 `SEC_NO_CREDENTIALS` 失败，
> Gradle 也会报 `Could not initialize native services`。

### 用 Android Studio

用官网版本即可；然后 **Open** 本目录，等 Gradle Sync 完成，点绿色三角 Run。

如果 Studio 提示找不到 SDK：`File → Settings → Languages & Frameworks → Android SDK`，
把 SDK 位置指到你自己的 Android SDK 目录。

---

## 7. 装到手机 + 开启 USB 调试

**手机端（以 Pixel 5 / Android 14 为例）：**

1. 设置 → 关于手机 → 连续点「版本号」7 次，开启开发者选项
2. 设置 → 系统 → 开发者选项 → 打开 **USB 调试**
3. USB 连上电脑，手机上弹出「允许 USB 调试吗？」→ 勾选「一律允许」→ 允许
4. 数据线要能传数据（很多充电线只有电源线，这是最常见的坑）

**电脑端：**

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
& 'D:\sth_interesting\greeting_widget\tools\install-to-phone.ps1'
```

装完后在手机上：**长按桌面空白处 → 小组件 → 找到「问候」→ 拖到桌面**。

> 如果 `adb devices` 显示 `unauthorized`：解锁手机、重新插拔数据线，
> 或者把开发者选项里的「USB 调试」关掉再打开 / 点「撤销 USB 调试授权」后重试。

---

## 8. 调试

### 看日志

`AppWidgetProvider` 跑在你的 App 进程里，所以按 tag 过滤就能看到：

```powershell
& 'D:\Android\sdk\platform-tools\adb.exe' logcat -v time GreetingWidget:D '*:S'
```

会看到这些行：

```
onUpdate ids=12
render id=12 -> 这不是安慰。我只是在陈述一个事实。
widget clicked -> shuffle
refreshAll ids=12
```

### ⚠️ RemoteViews 渲染错误看不到

如果布局里有不支持的控件或属性，**崩的是桌面进程**，日志里的进程名是 launcher，
按你的包名或 tag 过滤**永远看不到**。排查这类问题必须全量看，然后搜：

```powershell
& 'D:\Android\sdk\platform-tools\adb.exe' logcat -v time | Select-String 'RemoteViews|Error inflating|InflateException'
```

### 手动触发一次更新

```powershell
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE `
  --eia appWidgetIds 12 -n com.example.greetingwidget/.GreetingWidgetProvider
```

（`--eia` 是 int 数组，对应 `EXTRA_APPWIDGET_IDS`。id 可以从下面这条查。）

```powershell
adb shell dumpsys appwidget | Select-String greeting
```

### 改完代码后要做什么

| 改了什么 | 要做什么 |
|---|---|
| Kotlin 逻辑 | 直接 Run 覆盖安装，widget 不用动，点一下就见效 |
| `widget_greeting.xml` 布局 | Run 后**删掉桌面的 widget 重新添加**（旧的 RemoteViews 缓存在桌面） |
| `greeting_widget_info.xml`（尺寸/名称） | 删掉重新添加，必要时卸载重装 |
| 句子（`kurisu_greetings.txt` → `strings.xml`） | 跑同步脚本 + Run 即可；已存在的 widget 建议删掉重加一次 |

Android Studio 的 **Apply Changes 对小组件无效**，必须完整 Run。

---

## 9. 角色语气的来路

第二部分工作（189 条语句）不是拍脑袋写的，是**先读原作、再提取语气、最后批量重写**。
过程和依据全部记录在 [`kurisu_voice_notes.md`](kurisu_voice_notes.md)，这里说结论。

### 9.1 读了什么

以 [The Let's Play Archive 的 Steins;Gate 文本](https://lparchive.org/SteinsGate/)（作者 ProfessorProf）
为主要语料，读了 **40 个 part**：chapter 1–5 全部、chapter 10B、final chapter，
以及标题里出现 Makise Kurisu 的所有 part，共提取 400 多条她的台词。

> 这个文本用**立绘文件名**（`kurisu*.png` / `daru*.png` …）标注说话人，
> 冈部的台词是斜体引号，其余是旁白。所以台词归属可以硬判定。
> 三类容易搞错的坑也记在笔记里：共同台词（两人同时说）、冈部模仿她的话、图内没转录的文字。

### 9.2 学到了什么

- **她没有固定口头禅。** 招牌节奏是「一句冷水 + 一段技术长句 + 一个短收束」
  （`No it isn't.` / `Nobody cares.` 这种 3–5 词的判定句，配一整段 Kerr 黑洞推导）。
  `whatever` / `Stupidhead` 只在彻底破防时出现，滥用就失真。
- **关心从不直说，而且说完立刻收回。** 她表达在意的方式是**追问参数**
  （不问你没事吧，而问"放电真的只有 2 秒？"），或者用命令句；
  说漏嘴之后立刻否认（`Even though someone was worried enough to come searching for you...`
  紧跟 `Ah, no, I wasn't worried.`）。
- **傲娇有硬模板**：先付出，再用 `not like ... or anything` 撤销
  （"又不是因为喜欢你才缝的"）。
- **情绪靠医学化解说**：把烦躁说成"你在让我的脑产生过量去甲肾上腺素"。
  这条正是"视网膜不会因为你凝视就充电"的出处逻辑。
- **@channel 是她的软肋**（网名"栗悟饭和龟派气功"是她自己起的），
  说话会冒出生造的御宅用语，她真的说过 `Japanimation`。

### 9.3 语句是怎么迭代出来的

四轮，每轮都留了备份（`kurisu_greetings.v2-150.txt`、`kurisu_greetings.v3-you-edited.txt`）：

| 版本 | 做了什么 |
|---|---|
| v1 | 手写 83 句，通用问候语为主 |
| v2 | 按原作重写，扩到 150 句；新增 `lab`（实验室日常）分组 |
| v3 | 针对"AI 味"精修：补语气词、加感叹与自问、用省略号表示犹豫和自我修正、拆掉同构句式 |
| v4 | 针对"提示性语句太多"：把养生建议改写成**场景**（她在实验室里经历了这个天气），并删掉 21 条最泛的健康小贴士 |

v4 的例子——同一条信息，两种写法：

```
v3（提示）  手冷就戴手套。
v4（场景）  嘶，外面真是冷死了。幸好实验室的暖气够足。
```

现在时段/季节/天气这三类里，场景/观察句占一半以上，纯建议句只剩个位数。

### 9.4 借鉴了哪些项目的做法

- **[Code-Amadeus/Amadeus](https://github.com/Code-Amadeus/Amadeus)**（AGPL-3.0）
  是一个把红莉栖做成桌面 Agent 的项目。它的**角色没有经过任何微调** ——
  人格是 `llm/prompts.py` 里的一段 system prompt，事实靠一份默认关闭的 RAG 语料
  （`examples/character-rag/`，中日各 26 主题，公开可下载）。
  我们**没有**用它的语料（那是设定资料，不是对话），但抄了它两样东西：
  1. **评测方法**：[`docs/character_rag_curation.md`](https://github.com/Code-Amadeus/Amadeus/blob/main/docs/character_rag_curation.md)
     里记录了它怎么用对照实验验证角色一致性（同一批问题跑多次统计命中率，而不是凭感觉说"好多了"），
     以及它踩过的坑——问候语是检索误召回重灾区、性别/口吻漂移、主语归属搞错。
  2. **事实核查的态度**：它把旧语料 148 条逐条分成"已有覆盖 / 修正后并入 / 暂缓"，
     暂缓不等于断言为假。我们写句子时也照这个标准处理不确定的角色设定。
- **[ChatHaruhi-Suzumiya](https://github.com/LC1332/Chat-Haruhi-Suzumiya)**（论文 [arXiv:2308.09597](https://arxiv.org/abs/2308.09597)）
  提出的"从剧本抽取角色对话再让模型学"的思路，是 Hugging Face 上那些
  红莉栖对话数据集（如 [`showchen/Kurisu`](https://hf-mirror.com/datasets/showchen/Kurisu)）的来源。
  我们只把它当作**语气参照的旁证**，没有直接引用台词。
- **[RewrZ/RWKV6-Amadeus](https://huggingface.co/RewrZ/RWKV6-Amadeus)**（作者 [博客](https://rewrz.com/archive/my-rwkv6-amadeus-project)）
  是**真正做了微调**的那条路线（RWKV6 1B6/3B，LoRA/PISSA + 自造自我认知语料）。
  它和上面那个 Amadeus 项目没有关系，训练数据也没公开。
  我们评估后没有选择微调：189 条静态文本用提示词生成 + 人工筛选，语气质量更高也更可控。

> **关于版权**：本项目**不包含**任何原作台词、剧本、语音、模型权重或角色素材。
> 189 条语句是针对角色性格**原创撰写**的中文短句，不是对台词的翻译或改写。
> 学习过程只提取**说话方式**（语用习惯、句法节奏），不搬运句子。

---

## 10. 踩过的坑

- **`android:exported="false"`**：系统（uid 1000）发的广播可以送达非导出组件，所以这个值是够的。
  万一在小组件列表里找不到它，第一件事就是改成 `true` 再试——不同 ROM 行为有差异。
- **`PendingIntent` 必须加 `FLAG_IMMUTABLE`**（targetSdk 31+ 强制）。
- **多个小组件实例必须用不同的 `requestCode` 和 `data`**，否则它们共用同一个 `PendingIntent`，
  点其中一个只有它自己会变。本项目用 `data = greeting://widget/<id>` 来区分。
- **`onUpdate` 里不要做耗时操作**：广播接收器约 10 秒超时，超时就是 ANR。
- **Android 12+ 系统会强制给小组件加圆角和内边距**，内容贴边会被裁掉。
- **省电模式下定时刷新不会执行**，这是正常的，所以别把"随机"寄托在后台任务上。
- **数据不一致**：语句存了两份（`kurisu_greetings.txt` 和 `strings.xml`），
  动手改两份必然漂移。所以加了 `tools/sync_strings.py`，让 txt 成为唯一源头。
- **校验脚本会漏数**：早期用"以中文字符开头"来识别语句行，结果
  `@channel 上又在传没根据的东西。` 和 `…算了，当我没说。` 这两句被漏掉了
  （`@` 和 `…` 都不在 `\u4e00-\u9fff` 里）。现在按"非空、非分隔线、非说明行"来判定。
- **PowerShell 的 `\t` 陷阱**：用 `-replace` 往 README 里写 Windows 路径时，
  `\t` 会被解释成制表符，把 `tools/...` 变成 `ools/...`。替换串里要用反引号转义或改用字面量。
- **PowerShell 脚本编码**：Windows PowerShell 5.1 按 GBK 读取无 BOM 的 `.ps1`。
  `tools/` 下的脚本都带 UTF-8 BOM；如果你用编辑器改过它们，记得**保留 BOM**，
  否则中文会变乱码并撑破语法。

---

## 11. 致谢与许可

### 数据与内容来源

| 用途 | 来源 | 许可 |
|---|---|---|
| 天气数据 | [Open-Meteo](https://open-meteo.com/) | CC BY 4.0，非商业免费 |
| 角色语气学习（主语料） | [The Let's Play Archive · Steins;Gate](https://lparchive.org/SteinsGate/)，作者 ProfessorProf | 站点内容版权归原作者；本项目**只提取说话方式，未收录台词** |
| 方法论与评测思路 | [Code-Amadeus/Amadeus](https://github.com/Code-Amadeus/Amadeus) | AGPL-3.0（本项目未复制其代码或语料） |
| 角色对话抽取思路 | [ChatHaruhi-Suzumiya](https://github.com/LC1332/Chat-Haruhi-Suzumiya) | 仅作参考 |
| 微调路线的对照 | [RewrZ/RWKV6-Amadeus](https://huggingface.co/RewrZ/RWKV6-Amadeus) | 仅作参考，未采用 |

### 角色版权声明

《STEINS;GATE》及其角色「牧濑红莉栖」的著作权属于 **5pb. / Nitroplus / MAGES.** 等权利方。
本项目是**非商业的粉丝作品**，与原作权利方无关联，也未获得其授权或认可。
仓库中不包含任何原作素材（台词文本、剧本、图像、语音、模型权重）。

### 关于作者

- 小组件代码、语句撰写方向的把关：**项目作者**
- 语句生成与角色语气整理：由 AI 助手（**DeepSeek Harness** 中的 `deepseek-flash`）协助完成 ——
  它读了 40 个 part 的原作文本、整理了 [`kurisu_voice_notes.md`](kurisu_voice_notes.md)、
  起草了 189 条语句并跑完四轮迭代。语气把关、删改和最终取舍由人来做。
- 感谢 [DeepSeek Harness](https://github.com/deepseek-ai) 提供的编码代理环境。

### 许可证

**尚未添加 `LICENSE` 文件。** 如果打算让别人自由使用，推荐 MIT（宽松、对 Android 项目友好）；
如果以后可能商业化，先用保留所有权利、等确定方案再补。
加许可证之前，默认著作权归作者所有。

> `El Psy Kongroo.`
