<#
  只编译，不做任何下载或安装。
  前提：tools\setup-toolchain.ps1 已经成功跑过一次（JDK / SDK / Gradle 都在 D:\Android 下）。

  用法：
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
    & 'D:\sth_interesting\greeting_widget\tools\build.ps1'

  注意：沙箱会挡住 TLS，而 Gradle 需要读写 D:\Android\gradle-home 这个缓存目录，
  所以这个脚本要在放开沙箱的权限下跑。
  另外本文件必须保存为 UTF-8 with BOM（PowerShell 5.1 否则按 GBK 误读中文）。
#>

$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'SilentlyContinue'

$Root    = 'D:\Android'
$Sdk     = "$Root\sdk"
$JdkHome = "$Root\jdk17"
$Project = 'D:\sth_interesting\greeting_widget'
$LogFile = "$Root\build.log"

function Log([string]$m) {
    $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

Add-Content -Path $LogFile -Value "`n=== build started $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ==="

$GradleBin = "$Root\gradle-8.7\bin\gradle.bat"
foreach ($req in @("$JdkHome\bin\java.exe", "$Sdk\platforms\android-34\android.jar", $GradleBin)) {
    if (-not (Test-Path $req)) { throw "缺少 $req，请先跑 tools\setup-toolchain.ps1" }
}

$env:JAVA_HOME        = $JdkHome
$env:ANDROID_HOME     = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:GRADLE_USER_HOME = "$Root\gradle-home"
$env:PATH = "$JdkHome\bin;$Sdk\platform-tools;$Sdk\cmdline-tools\latest\bin;$env:PATH"

Log '开始编译 assembleDebug'
$prev = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & $GradleBin --no-daemon --console=plain -p $Project assembleDebug 2>&1 |
        ForEach-Object { $s = "$_"; if ($s -match '\S') { Log "  $s" } }
} finally {
    $ErrorActionPreference = $prev
}
$code = $LASTEXITCODE
Log "assembleDebug 退出码 = $code"

$apk = "$Project\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Log ("APK: {0} ({1:N2} MB, 生成于 {2})" -f $apk, ((Get-Item $apk).Length / 1MB), (Get-Item $apk).LastWriteTime.ToString('HH:mm:ss'))
} else {
    Log 'APK 未生成，见上面输出'
}
Log '=== 结束 ==='
exit $code
