# 把 APK 装到 Pixel 5 并做基本验证
#
# 前置条件：
#   1. 手机已用 USB 连上电脑
#   2. 手机已开启「开发者选项 -> USB 调试」，并在手机上点了「允许此电脑调试」
#   3. 已经跑过 tools\setup-toolchain.ps1，且 APK 构建成功

$Root    = 'D:\Android'
$Sdk     = "$Root\sdk"
$Project = 'D:\sth_interesting\greeting_widget'
$Adb     = "$Sdk\platform-tools\adb.exe"
$Apk     = "$Project\app\build\outputs\apk\debug\app-debug.apk"
$Pkg     = 'com.example.greetingwidget'

function Log([string]$m) { Write-Host "[install] $m" }

if (-not (Test-Path $Adb)) { throw "找不到 adb: $Adb" }
if (-not (Test-Path $Apk)) { throw "找不到 APK，请先跑 setup-toolchain.ps1: $Apk" }

Log '已连接的设备：'
& $Adb devices -l
Log ''

$count = (& $Adb devices | Select-String -Pattern "`tdevice$" | Measure-Object).Count
if ($count -eq 0) {
    throw '没有检测到已授权设备。检查 USB 线、USB 调试开关，以及手机上的「允许调试」弹窗。'
}

Log "安装 $Apk"
& $Adb install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "安装失败 ($LASTEXITCODE)" }

Log '启动说明页'
& $Adb shell am start -n "$Pkg/.MainActivity"

Log ''
Log '当前系统里注册的小组件（应该能看到 com.example.greetingwidget/.GreetingWidgetProvider）：'
& $Adb shell dumpsys appwidget | Select-String -Pattern $Pkg

Log ''
Log '完成。接下来在手机上：长按桌面空白处 -> 小组件 -> 找到「问候」-> 拖到桌面。'
Log ''
Log '实时看日志（AppWidgetProvider 跑在你的 App 进程里，按 tag 过滤就行）：'
Log "  & '$Adb' logcat -v time GreetingWidget:D '*:S'"
Log ''
Log '注意：如果 RemoteViews 渲染出错，崩的是桌面进程，上面的 tag 过滤看不到。'
Log '那种情况要用全量日志再搜关键字：'
Log "  & '$Adb' logcat -v time | Select-String -Pattern 'RemoteViews|Error inflating|InflateException'"
