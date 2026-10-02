<#
  一键搭好 Android 命令行构建链，并编译出 debug APK。
  幂等：每一步都先检查是否已完成，重复运行只会补齐缺失的部分。
  日志同时写到控制台和 D:\Android\setup.log。

  注意（Windows PowerShell 5.1 的两个坑）：
  1. 本文件必须保存为 UTF-8 with BOM，否则 5.1 会按 GBK 读取，中文变乱码并撑破语法。
  2. 这里故意把 $ErrorActionPreference 设为 Continue。因为在 5.1 下设为 Stop 时，
     原生命令（java / sdkmanager / gradle / curl）往 stderr 写的任何内容都会被当成
     终止性错误抛出——而 java -version 恰恰是往 stderr 写正常输出的。
     所以凡原生命令都显式检查退出码，不用异常控制流程。
#>

$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'SilentlyContinue'

$Root    = 'D:\Android'
$Sdk     = "$Root\sdk"
$Dl      = "$Root\downloads"
$JdkHome = "$Root\jdk17"
$Project = 'D:\sth_interesting\greeting_widget'
$LogFile = "$Root\setup.log"

New-Item -ItemType Directory -Force -Path $Root, $Sdk, $Dl -ErrorAction Stop | Out-Null
Add-Content -Path $LogFile -Value "`n=== setup started $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ==="

function Log([string]$m) {
    $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

# 跑原生命令：stdout 和 stderr 都逐行记进日志，返回退出码。
# 返回值一定是一个整数，不会混入命令输出。
function Run-Logged {
    param(
        [string]   $File,
        [string[]] $Arguments,
        [string]   $Stdin,
        [string]   $Prefix = '  '
    )
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($Stdin) {
            $Stdin | & $File @Arguments 2>&1 |
                ForEach-Object { $s = "$_"; if ($s -match '\S') { Log "$Prefix$s" } }
        } else {
            & $File @Arguments 2>&1 |
                ForEach-Object { $s = "$_"; if ($s -match '\S') { Log "$Prefix$s" } }
        }
    } finally {
        $ErrorActionPreference = $prev
    }
    return $LASTEXITCODE
}

function Get-File([string]$url, [string]$out) {
    if ((Test-Path $out) -and ((Get-Item $out).Length -gt 0)) {
        Log "已下载，跳过: $(Split-Path $out -Leaf)"
        return
    }
    Log "下载 $url"
    $code = Run-Logged -File 'curl.exe' -Arguments @(
        '-L', '--fail', '-sS', '--retry', '3', '--retry-delay', '5',
        '--connect-timeout', '30', '-o', $out, $url
    )
    if ($code -ne 0) {
        # 清掉半截文件，否则换下一个源时会被"已下载，跳过"骗过去
        if (Test-Path $out) { Remove-Item $out -Force -ErrorAction SilentlyContinue }
        throw "下载失败 ($code): $url"
    }
    Log ("  完成 {0:N1} MB" -f ((Get-Item $out).Length / 1MB))
}

# ---------------------------------------------------------------- 1. JDK 17
if (-not (Test-Path "$JdkHome\bin\java.exe")) {
    $jdkUrls = @(
        'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip',
        'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
    )
    $zip = "$Dl\jdk17.zip"
    $ok = $false
    foreach ($u in $jdkUrls) {
        try { Get-File $u $zip; $ok = $true; break }
        catch { Log "  该源不可用，换下一个: $($_.Exception.Message)" }
    }
    if (-not $ok) { throw 'JDK 下载失败，所有源都不可用' }

    $tmp = "$Root\jdk-tmp"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Log '解压 JDK'
    Expand-Archive -Path $zip -DestinationPath $tmp -Force -ErrorAction Stop
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (-not $inner) { throw 'JDK 压缩包结构异常' }
    if (Test-Path $JdkHome) { Remove-Item $JdkHome -Recurse -Force }
    Move-Item $inner.FullName $JdkHome -ErrorAction Stop
    Remove-Item $tmp -Recurse -Force
}
Log "JDK 就绪: $JdkHome"

# ------------------------------------------------- 2. 环境变量（仅本进程内）
$env:JAVA_HOME        = $JdkHome
$env:ANDROID_HOME     = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:GRADLE_USER_HOME = "$Root\gradle-home"
$env:PATH = "$JdkHome\bin;$Sdk\platform-tools;$Sdk\cmdline-tools\latest\bin;$env:PATH"

# java -version 往 stderr 写版本号。走 cmd /c 让 cmd 内部完成重定向，
# 否则 PowerShell 5.1 会把 stderr 包装成 ErrorRecord，日志里全是无用的报错装饰。
$javaOut = (cmd /c "`"$JdkHome\bin\java.exe`" -version 2>&1" | Out-String).Trim()
$javaOut = $javaOut -replace "`r?`n", ' | '
Log "java: $javaOut"

# ------------------------------------------------------------ 3. cmdline-tools
$SdkManager = "$Sdk\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $SdkManager)) {
    Get-File 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' "$Dl\cmdline-tools.zip"
    $tmp = "$Root\cmdtools-tmp"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Log '解压 cmdline-tools'
    Expand-Archive -Path "$Dl\cmdline-tools.zip" -DestinationPath $tmp -Force -ErrorAction Stop
    New-Item -ItemType Directory -Force -Path "$Sdk\cmdline-tools" | Out-Null
    if (Test-Path "$Sdk\cmdline-tools\latest") { Remove-Item "$Sdk\cmdline-tools\latest" -Recurse -Force }
    Move-Item "$tmp\cmdline-tools" "$Sdk\cmdline-tools\latest" -ErrorAction Stop
    Remove-Item $tmp -Recurse -Force
}
Log 'cmdline-tools 就绪'

# ---------------------------------------------------------- 4. SDK 许可 + 组件
Log '接受 SDK 许可协议'
$yes = ("y`r`n" * 300)
$licCode = Run-Logged -File $SdkManager -Arguments @("--sdk_root=$Sdk", '--licenses') -Stdin $yes
Log "  许可协议步骤退出码 = $licCode"

Log '安装 SDK 组件: platform-tools / platforms;android-34 / build-tools;34.0.0'
$pkgCode = Run-Logged -File $SdkManager -Arguments @("--sdk_root=$Sdk", 'platform-tools', 'platforms;android-34', 'build-tools;34.0.0')
Log "  SDK 组件步骤退出码 = $pkgCode"

$missing = 0
foreach ($p in @("$Sdk\platform-tools\adb.exe", "$Sdk\platforms\android-34\android.jar", "$Sdk\build-tools\34.0.0\aapt2.exe")) {
    if (Test-Path $p) { Log "  OK  $p" } else { Log "  缺失 $p"; $missing++ }
}
if ($missing -gt 0) { throw "有 $missing 个 SDK 组件没装上，编译会失败" }

# ------------------------------------------------------------------ 5. Gradle
# services.gradle.org 会 302 到 github.com，本机访问不了；腾讯镜像已验证可直连。
$GradleBin = "$Root\gradle-8.7\bin\gradle.bat"
$gradleUrls = @(
    'https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip',
    'https://downloads.gradle.org/distributions/gradle-8.7-bin.zip',
    'https://services.gradle.org/distributions/gradle-8.7-bin.zip'
)
if (-not (Test-Path $GradleBin)) {
    $gzip = "$Dl\gradle-8.7-bin.zip"
    $ok = $false
    foreach ($u in $gradleUrls) {
        try { Get-File $u $gzip; $ok = $true; break }
        catch { Log "  该源不可用，换下一个: $($_.Exception.Message)" }
    }
    if (-not $ok) { throw 'Gradle 下载失败，所有源都不可用' }
    Log '解压 Gradle'
    Expand-Archive -Path $gzip -DestinationPath $Root -Force -ErrorAction Stop
}
Log "Gradle 就绪: $GradleBin"

# --------------------------------------------------------- 6. local.properties
$sdkProp = $Sdk -replace '\\', '/'
Set-Content -Path "$Project\local.properties" -Value "sdk.dir=$sdkProp" -Encoding ASCII -ErrorAction Stop
Log "local.properties 已写入: sdk.dir=$sdkProp"

# --------------------------------------------------------------- 7. 编译 APK
Log '开始编译 assembleDebug（首次会下载 AGP / Kotlin / androidx 依赖，比较慢）'
$buildCode = Run-Logged -File $GradleBin -Arguments @('--no-daemon', '--console=plain', 'assembleDebug')
Log "assembleDebug 退出码 = $buildCode"

$apk = "$Project\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Log ("APK 构建成功: {0} ({1:N2} MB)" -f $apk, ((Get-Item $apk).Length / 1MB))
} else {
    Log 'APK 未生成，见上面的编译输出'
}

# --------------------------------------------------------- 8. gradle wrapper
if (Test-Path $apk) {
    Log '生成 gradle wrapper（方便以后用 Android Studio 打开）'
    $wrapCode = Run-Logged -File $GradleBin -Arguments @(
        '--no-daemon', '--console=plain', 'wrapper',
        '--gradle-version', '8.7',
        '--distribution-type', 'bin',
        # 让 wrapper 也指向本机可达的镜像，否则以后 ./gradlew 会去 github 拿包而失败
        '--gradle-distribution-url', 'https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip'
    )
    Log "wrapper 步骤退出码 = $wrapCode"
    foreach ($f in @("$Project\gradlew", "$Project\gradlew.bat", "$Project\gradle\wrapper\gradle-wrapper.jar")) {
        Log ("  {0} {1}" -f $(if (Test-Path $f) { 'OK ' } else { '缺失' }), $f)
    }
}

Log '=== 全部完成 ==='
if (-not (Test-Path $apk)) { exit 1 }
