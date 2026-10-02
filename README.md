# 问候小组件（Greeting Widget）

一个 Android 桌面小组件：随机显示一句**牧濑红莉栖口吻**的预设语句，**点一下换一句**，
每小时兜底刷新一次。语句按**时段 / 季节 / 天气**自动挑选。

面向 Pixel 5 / Android 14（`minSdk 26`、`targetSdk 34`，其它 Android 设备同样可用）。

**[⬇ 下载 APK](https://github.com/apotofcat-bit/kurisu-greeting-widget/releases/latest)** ·
debug 签名，装上即用，详见 [Releases](https://github.com/apotofcat-bit/kurisu-greeting-widget/releases)。

---

## 1. 这个仓库是两部分工作

| | 第一部分：小组件本身 | 第二部分：语句与角色语气 |
|---|---|---|
| **做了什么** | AppWidget：RemoteViews 渲染、点击换句、WorkManager 定时、定位 + Open-Meteo 天气、按场景抽取 | 学原作里红莉栖的说话方式，产出 **189 条原创中文语句**，分 18 组 |
| **在哪** | `app/src/main/java/.../`、`app/src/main/res/` | `kurisu_greetings.txt`（源头）、`kurisu_voice_notes.md`（语气笔记） |
| **怎么改** | [§4](#4-改外观) | [§3](#3-改语句)、[§6](#6-角色语气的来路) |

两部分解耦：**句子池可以整体替换**（换角色、换主题），抽取逻辑一行都不用改。

## 2. 基本功能

- **随机显示 + 点击换句**是主要交互；WorkManager 每小时兜底刷新一次。
- **按场景挑选**：先判断时段（早/午/晚/深夜）、季节、天气，再按权重抽组、组内抽句。
- **天气自动获取**：授权定位后后台拉 Open-Meteo，映射成晴/阴/雾/雨/雪/雷暴/高温/低温。
- **断网/无授权不崩**：天气组自动消失，权重让给其它组，只是"少一类句子"。
- **小组件永不联网**：联网全在 WorkManager 线程（`onUpdate` 超 10 秒就 ANR）。
- **跟随系统配色**：浅色 / 深色 / Android 12+ 壁纸取色；图标和气泡全是矢量，没有 PNG。

语句长这样：

```
greetings_lab（实验室日常）
  该吃午饭了。泡面…啊，你有看到我的叉子吗？
  嘶，外面真是冷死了。幸好实验室的暖气够足。   ← 冬天组
greetings_anytime（通用）
  需要帮忙的话就说，我不会主动问第二次。
```

风格是「傲娇 + 科学家」：关心先否定再给，说完就收回；爱用生理学和技术名词包装；
不卖萌，也不写成养生小贴士。依据见 [§6](#6-角色语气的来路)。

## 3. 改语句

语句**只有一个源头**：根目录的 `kurisu_greetings.txt`。一行 = 一句。

```
============================================================
greetings_lab  |  实验室日常
============================================================

该吃午饭了。泡面…啊，你有看到我的叉子吗？
```

改完跑同步脚本（自动转义 `%` → `%%`、`&` → `&amp;`）：

```powershell
python tools\sync_strings.py          # 生成 strings.xml
python tools\sync_strings.py --check  # 校验两边一致
```

> 别直接改 `strings.xml`：189 句、18 个数组，两份副本手改必然漂移。

**长度**：按 2×2 格设计（约 4 行、每行 ~9 字），**12–30 字舒适，上限 34 字**，超了被省略号截断。

**权重**：时段 25% / 日常 `lab` 25% / 天气 20% / 通用 `anytime` 20% / 季节 10%。
「通用 + 日常」占 45% 是主力；天气拿不到时这一组消失，权重让给其它组。
（季节按北半球月份划分，南半球改 `GreetingRepository.season()`。）

新增**一组**要改三处：草稿加分类、`tools/sync_strings.py` 的 `KEY_ORDER`/`TITLES`、
`GreetingRepository` 的权重和 `arrayRes()`。

## 4. 改外观

| 想改什么 | 改哪 |
|---|---|
| 气泡形状 | `drawable/widget_bubble.xml`（layer-list：圆角矩形 + 尾巴） |
| 尾巴角度位置 | `drawable/widget_bubble_tail.xml` 的 `pathData` 三个顶点 |
| 颜色 | 四个文件都要改：`values/`、`values-night/`、`values-v31/`、`values-night-v31/` |
| 字号 / 对齐 / 内边距 | `layout/widget_greeting.xml` |
| 尺寸（占几格） | `xml/greeting_widget_info.xml`（`minWidth = 70n - 30` dp，或 Android 12+ 的 `targetCellWidth`） |

> ⚠️ 目录名 `values-night-v31` 顺序不能反，限定符里 `night` 必须在版本号前面。
> 布局只能用 RemoteViews 支持的控件（`TextView`/`LinearLayout`/`FrameLayout` 等）；
> 用 `ConstraintLayout`、`RecyclerView` 会崩，**而且崩在桌面进程，按你的包名过滤日志看不到**。

## 5. 构建、安装、调试

```powershell
# 首次：装 JDK/SDK/Gradle 并编译（需联网，别在受限沙箱里跑）
& 'D:\sth_interesting\greeting_widget\tools\setup-toolchain.ps1'
# 日常：只编译
& 'D:\sth_interesting\greeting_widget\tools\build.ps1'
# 装到手机
& 'D:\sth_interesting\greeting_widget\tools\install-to-phone.ps1'
```

产物 `app\build\outputs\apk\debug\app-debug.apk`。
脚本里的 `D:\Android` 是本机路径，clone 到别处请自行改 `tools/*.ps1` 的 `$Root`。

手机端：开发者选项 → 打开 **USB 调试** → 连电脑 → 手机上允许调试 →
**长按桌面 → 小组件 → 「问候」→ 拖到桌面**。

```powershell
# 看日志（Provider 跑在 App 进程里）
adb logcat -v time GreetingWidget:D '*:S'
# 手动触发一次刷新（id 用 dumpsys appwidget | Select-String greeting 查）
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE `
  --eia appWidgetIds 12 -n com.example.greetingwidget/.GreetingWidgetProvider
```

| 改了什么 | 要做什么 |
|---|---|
| Kotlin 逻辑 / 句子 | 直接 Run，点一下就见效 |
| 布局、尺寸、名称 | **删掉桌面 widget 重新添加**（旧的 RemoteViews 缓存在桌面） |

Android Studio 的 **Apply Changes 对小组件无效**，必须完整 Run。

## 6. 角色语气的来路

第二部分不是拍脑袋写的：**先读原作 → 提取语气 → 批量重写 → 四轮迭代**。
完整笔记见 [`kurisu_voice_notes.md`](kurisu_voice_notes.md)。

**读了什么**：[The Let's Play Archive 的 Steins;Gate 文本](https://lparchive.org/SteinsGate/)
（作者 ProfessorProf），共 40 个 part（chapter 1–5、10B、final，以及标题含 Makise Kurisu 的全部），
提取 400+ 条台词。该文本用**立绘文件名**标注说话人，所以台词归属可硬判定。

**学到了什么**：

- **她没有固定口头禅。** 招牌是「一句冷水 + 一段技术长句 + 一个短收束」，
  `whatever` / `Stupidhead` 只在彻底破防时出现，滥用就失真。
- **关心从不直说，且说完立刻收回。** 表达在意靠**追问参数**（不问你没事吧，而问"放电真的只有 2 秒？"），
  说漏嘴马上否认（`…someone was worried enough to come searching for you...` 紧跟 `Ah, no, I wasn't worried.`）。
- **傲娇有硬模板**：先付出，再用 `not like ... or anything` 撤销。
- **情绪靠医学化解说**：把烦躁说成"你在让我的脑产生过量去甲肾上腺素"——
  「视网膜不会因为你凝视就充电」就是这个逻辑。

**四轮迭代**（历史版本见 `git log`，工作区里不留备份文件）：

| 版本 | 做了什么 |
|---|---|
| v1 | 手写 83 句，通用问候为主 |
| v2 | 按原作重写，扩到 150 句，新增 `lab` 分组 |
| v3 | 去 AI 味：补语气词、加感叹自问、省略号表示犹豫、拆掉同构句式 |
| v4 | 提示改场景，删掉 21 条最泛的养生小贴士 |

v4 的例子：`v3「手冷就戴手套。」` → `v4「嘶，外面真是冷死了。幸好实验室的暖气够足。」`

**借鉴了什么**：

- **[Code-Amadeus/Amadeus](https://github.com/Code-Amadeus/Amadeus)**（AGPL-3.0）把红莉栖做成桌面 Agent 的项目。
  它**没做任何微调**——人格是 `llm/prompts.py` 里一段 system prompt，事实靠默认关闭的 RAG 语料。
  我们没用它的语料（那是设定资料不是对话），但抄了它的[评测方法](https://github.com/Code-Amadeus/Amadeus/blob/main/docs/character_rag_curation.md)：
  用对照实验统计角色一致性，以及它记录的坑（问候语是检索误召回重灾区、口吻漂移、主语归属搞错）。
- **[ChatHaruhi-Suzumiya](https://github.com/LC1332/Chat-Haruhi-Suzumiya)**：从剧本抽取角色对话的思路，
  也是 HF 上那些红莉栖数据集（如 [`showchen/Kurisu`](https://hf-mirror.com/datasets/showchen/Kurisu)）的来源，只作旁证。
- **[RewrZ/RWKV6-Amadeus](https://huggingface.co/RewrZ/RWKV6-Amadeus)**（[博客](https://rewrz.com/archive/my-rwkv6-amadeus-project)）：
  真正做了微调的那条路线（RWKV6 1B6/3B）。评估后没采用——189 条静态文本用提示词生成 + 人工筛选更可控。

> **版权**：仓库不含任何原作台词、剧本、语音、模型权重或角色素材。
> 189 条是针对角色性格**原创撰写**的中文短句，不是台词的翻译或改写；
> 学习只提取**说话方式**，不搬运句子。

## 7. 致谢与许可

| 用途 | 来源 | 许可 |
|---|---|---|
| 天气数据 | [Open-Meteo](https://open-meteo.com/) | CC BY 4.0，非商业免费 |
| 语气学习（主语料） | [LP Archive · Steins;Gate](https://lparchive.org/SteinsGate/)（ProfessorProf） | 版权归原作者；本项目只提取说话方式，未收录台词 |
| 评测方法论 | [Code-Amadeus/Amadeus](https://github.com/Code-Amadeus/Amadeus) | AGPL-3.0（未复制其代码或语料） |
| 对话抽取思路 | [ChatHaruhi-Suzumiya](https://github.com/LC1332/Chat-Haruhi-Suzumiya) | 仅参考 |

《STEINS;GATE》及角色「牧濑红莉栖」著作权属 **5pb. / Nitroplus / MAGES.** 等权利方。
本项目是**非商业粉丝作品**，与原作权利方无关联，未获授权或认可，也不含任何原作素材。

**关于作者**：小组件代码与最终取舍由项目作者负责；语句生成与角色语气整理由 AI 助手
（**DeepSeek Harness** 中的 `deepseek-flash`）协助完成——读了 40 个 part、整理了语气笔记、
起草 189 条语句并跑完四轮迭代，把关和删改由人做。

**许可证**：尚未添加 `LICENSE`。要让人自由使用推荐 MIT；以后可能商业化就先用保留所有权利。

> `El Psy Kongroo.`
