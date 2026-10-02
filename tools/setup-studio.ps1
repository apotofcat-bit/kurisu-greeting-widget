<#
  安装 Android Studio（免安装 ZIP 版）。

  为什么不用 .exe 安装包：
    .exe 是 NSIS 安装程序，清单里要求管理员权限，静默安装会触发 UAC。
    而从后台任务发起的提权请求到不了交互桌面，最终以
    1223 (ERROR_CANCELLED) 失败。ZIP 版解压即可用，完全不需要提权。

  为什么不能直接用官网链接：
    本机网络下 developer.android.com 不可达，拿不到页面里的下载地址，
    所以这里直接对 Google 的下载 CDN 逐个探测候选地址，取第一个可用的。

  注意：和 setup-toolchain.ps1 一样，本文件必须保存为 UTF-8 with BOM。
#>

$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'SilentlyContinue'

$Root      = 'D:\Android'
$Dl        = "$Root\downloads"
$InstallTo = "$Root\AndroidStudio"
$LogFile   = "$Root\studio.log"

New-Item -ItemType Directory -Force -Path $Root, $Dl -ErrorAction Stop | Out-Null
Add-Content -Path $LogFile -Value "`n=== studio setup (zip) started $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ==="

function Log([string]$m) {
    $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

function Run-Logged {
    param([string]$File, [string[]]$Arguments, [string]$Prefix = '  ')
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $File @Arguments 2>&1 | ForEach-Object { $s = "$_"; if ($s -match '\S') { Log "$Prefix$s" } }
    } finally {
        $ErrorActionPreference = $prev
    }
    return $LASTEXITCODE
}

# 2025.2.2 放最前面：上一轮已经探明这个版本在 dl.google.com 上确实存在。
$versionPrefixes = @(
    '2025.2.2',
    '2025.3.4',
    '2025.3.3',
    '2025.3.2',
    '2025.3.1',
    '2025.1.2',
    '2025.1.1',
    '2024.3.2',
    '2024.3.1',
    '2024.2.2',
    '2024.2.1'
)
$suffixes = 8..16

$zipHosts = @(
    'https://dl.google.com/android/studio/ide-zips/{0}/android-studio-{0}-windows.zip',
    'https://redirector.gvt1.com/edgedl/android/studio/ide-zips/{0}/android-studio-{0}-windows.zip'
)

$found = $null
Log '探测免安装 ZIP 版的下载地址...'
foreach ($pre in $versionPrefixes) {
    foreach ($suf in $suffixes) {
        $v = "$pre.$suf"
        foreach ($h in $zipHosts) {
            $u = $h -f $v
            $code = & curl.exe -sS -o NUL -w '%{http_code}' -I -L --max-time 10 $u 2>$null
            if ("$code" -eq '200') { $found = $u; Log "  命中 $v"; break }
        }
        if ($found) { break }
    }
    if ($found) { break }
    Log "  前缀 $pre 未命中"
}

if (-not $found) {
    throw '没找到可用的 ZIP 版地址。可改为手动安装：双击 D:\Android\downloads\android-studio-2025.2.2.8-windows.exe，一路点下一步即可。'
}
Log "选用下载地址: $found"

$zip = Join-Path $Dl (Split-Path $found -Leaf)
if (-not ((Test-Path $zip) -and ((Get-Item $zip).Length -gt 0))) {
    Log '开始下载（1GB 以上，期间没有进度条）'
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & curl.exe -L --fail -sS --retry 3 --retry-delay 5 --connect-timeout 30 -o $zip $found 2>&1 |
            ForEach-Object { $s = "$_"; if ($s -match '\S') { Log "  $s" } }
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($LASTEXITCODE -ne 0) {
        if (Test-Path $zip) { Remove-Item $zip -Force -ErrorAction SilentlyContinue }
        throw "下载失败 ($LASTEXITCODE)"
    }
}
Log ("下载完成 {0:N1} MB" -f ((Get-Item $zip).Length / 1MB))

# ---------------------------------------------------------------- 解压
$stage = "$Root\studio-tmp"
$stagedStudio = Get-ChildItem $stage -Recurse -Filter 'studio64.exe' -ErrorAction SilentlyContinue | Select-Object -First 1

if ($stagedStudio) {
    Log "暂存目录里已有解压好的 Studio，跳过下载和解压"
} else {
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $stage | Out-Null

    # tar.exe (bsdtar) 解 zip 比 PowerShell 的 Expand-Archive 快得多
    $tar = "$env:SystemRoot\System32\tar.exe"
    if (Test-Path $tar) {
        Log '用 tar.exe 解压（较快）'
        $code = Run-Logged -File $tar -Arguments @('-xf', $zip, '-C', $stage)
        Log "  tar 退出码 = $code"
    } else {
        Log '用 Expand-Archive 解压（较慢）'
        Expand-Archive -Path $zip -DestinationPath $stage -Force -ErrorAction Stop
    }
    $stagedStudio = Get-ChildItem $stage -Recurse -Filter 'studio64.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
}

if (-not $stagedStudio) { throw "找不到 studio64.exe。临时目录保留在 $stage" }

# ！！变量名不能叫 $home ！！
# HOME 是 PowerShell 的只读自动变量（变量名不区分大小写），赋值会失败并保留原值
# ——也就是用户主目录，于是 Move-Item 会试图把整个用户目录搬走。这个坑真的踩过。
$studioHome = $stagedStudio.Directory.Parent.FullName
Log "解压出的主目录: $studioHome"

if (Test-Path $InstallTo) { Remove-Item $InstallTo -Recurse -Force }
Move-Item $studioHome $InstallTo -ErrorAction Stop
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue

# ---------------------------------------------------------------- 校验
$bad = 0
foreach ($f in @("$InstallTo\bin\studio64.exe", "$InstallTo\jbr\bin\java.exe", "$InstallTo\jbr\bin\javac.exe")) {
    if (Test-Path $f) { Log "  OK  $f" } else { Log "  缺失 $f"; $bad++ }
}

if ($bad -eq 0) {
    Log "Android Studio 自带的 JDK: $InstallTo\jbr"
    Log ("占用空间 {0:N0} MB" -f ((Get-ChildItem $InstallTo -Recurse -File -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum / 1MB))
}

Log '=== Android Studio 安装结束 ==='
if ($bad -gt 0) { exit 1 }
