param(
    [string]$McDir = $env:MINECRAFT_DIR,
    [string]$JdkBin = $env:JDK_BIN
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 构建脚本（无需 Gradle）
# 依赖：JDK 25+、Minecraft 26.3 客户端 jar、Fabric Loader 与依赖库（.minecraft/libraries）
# 参数：-McDir .minecraft 目录；-JdkBin JDK 的 bin 目录（默认取 PATH 中的 javac/jar）

if (-not $McDir) { $McDir = Join-Path $env:APPDATA ".minecraft" }
if (-not (Test-Path $McDir)) {
    Write-Host "找不到 .minecraft 目录，请用 -McDir 指定" -ForegroundColor Red
    exit 1
}

# Minecraft 26.3 客户端 jar
$clientJar = Join-Path $McDir "versions\26.3\26.3.jar"
if (-not (Test-Path $clientJar)) {
    $found = Get-ChildItem (Join-Path $McDir "versions") -Recurse -Filter "26.3*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "sources|javadoc" } | Select-Object -First 1
    if ($found) { $clientJar = $found.FullName }
}
if (-not (Test-Path $clientJar)) {
    Write-Host "找不到 Minecraft 26.3 客户端 jar" -ForegroundColor Red
    exit 1
}

# 依赖版本（与 Minecraft 26.3 自带版本一致；如你的实例不同，脚本会自动回退到已安装的最新版本）
$deps = [ordered]@{
    "fabric-loader" = "0.20.1"
    "sponge-mixin"  = "0.17.5+mixin.0.8.7"
    "gson"          = "2.14.0"
    "slf4j-api"     = "2.0.17"
    "brigadier"     = "1.3.11"
    "authlib"       = "10.0.77"
    "jspecify"      = "1.0.0"
    "fastutil"      = "8.5.18"
    "joml"          = "1.10.9"
}

function Find-Jar([string]$name, [string]$preferred) {
    $root = Join-Path $McDir "libraries"
    if ($preferred) {
        $exact = Get-ChildItem $root -Recurse -Filter "$name-$preferred.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($exact) { return $exact.FullName }
    }
    $hits = Get-ChildItem $root -Recurse -Filter "$name-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "sources|javadoc" }
    $best = $null; $bestVer = $null; $bestName = ""
    foreach ($h in $hits) {
        $v = $null
        try { $v = [version](($h.Directory.Name -split "\+")[0]) } catch { }
        if ($null -eq $best) { $best = $h; $bestVer = $v; $bestName = $h.Directory.Name }
        elseif ($v -and $bestVer -and $v -gt $bestVer) { $best = $h; $bestVer = $v; $bestName = $h.Directory.Name }
        elseif (-not $v -and -not $bestVer -and $h.Directory.Name -gt $bestName) { $best = $h; $bestName = $h.Directory.Name }
    }
    if ($best) { return $best.FullName }
    return $null
}

$cpItems = @($clientJar, (Join-Path $PSScriptRoot "libs\fabric-stubs.jar"))
foreach ($key in $deps.Keys) {
    $path = Find-Jar $key $deps[$key]
    if ($path) { $cpItems += $path } else { Write-Host "警告：未找到依赖 $key" -ForegroundColor Yellow }
}
$CP = $cpItems -join ";"

$src = "lingua-franca\src"
$res = "lingua-franca\resources"
$out = "lingua-franca\build\classes"
$ver = "0.0.2"

Remove-Item -Recurse -Force "lingua-franca\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item -Recurse -Force "$res\*" $out
New-Item -ItemType Directory -Force "$out\assets\lingua-franca" | Out-Null
Copy-Item -Force "data\vanilla-zh.tsv" "$out\assets\lingua-franca\vanilla-zh.tsv"
Copy-Item -Force "data\server-zh.tsv" "$out\assets\lingua-franca\server-zh.tsv"
Copy-Item -Force "data\orqol-zh.tsv" "$out\assets\lingua-franca\orqol-zh.tsv"
New-Item -ItemType Directory -Force "$out\assets\lingua-franca\servers" | Out-Null
if (Test-Path "data\servers") { Copy-Item -Force "data\servers\*.tsv" "$out\assets\lingua-franca\servers\" }

$javac = if ($JdkBin) { Join-Path $JdkBin "javac.exe" } else { "javac" }
$files = Get-ChildItem -Recurse $src -Filter *.java | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 --release 25 -nowarn -proc:none -cp $CP -d $out @files
if ($LASTEXITCODE -ne 0) { Write-Host "COMPILE FAILED" -ForegroundColor Red; exit 1 }

New-Item -ItemType Directory -Force "dist" | Out-Null
$jar = "dist\lingua-franca-$ver.jar"
if (Test-Path $jar) { Remove-Item -Force $jar }
$jarTool = if ($JdkBin) { Join-Path $JdkBin "jar.exe" } else { "jar" }
& $jarTool --create --file $jar -C $out .
if ($LASTEXITCODE -ne 0) { Write-Host "JAR FAILED" -ForegroundColor Red; exit 1 }

Write-Host ("JAR OK " + (Get-Item $jar).Length) -ForegroundColor Green