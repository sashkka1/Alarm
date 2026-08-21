# Одна команда: собрать APK. Готовый файл кладётся в папку builds\ в корне проекта.
#
# Проще всего звать через npm:
#   npm run dev        — debug-сборка
#   npm run release    — release-сборка
#   npm run nobump     — собрать, не увеличивая номер сборки
#   npm test           — тесты ядра, APK не собирается
#
# Напрямую: .\build.ps1 [-Release] [-NoBump] [-Test]

param(
    [switch]$Release,
    [switch]$NoBump,
    [switch]$Test
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Host "Не найден JDK: $env:JAVA_HOME" -ForegroundColor Red
    exit 1
}

if ($Test) {
    Write-Host ""
    Write-Host "=== Тесты ядра ===" -ForegroundColor Cyan
    & .\gradlew.bat :core:test --console=plain
    exit $LASTEXITCODE
}

$variant = if ($Release) { "Release" } else { "Debug" }
$gradleArgs = @(":app:assemble$variant", "--console=plain")
if ($NoBump) { $gradleArgs += "-PnoVersionBump" }

& .\gradlew.bat @gradleArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Сборка упала." -ForegroundColor Red
    exit $LASTEXITCODE
}

$folder = Join-Path "app\build\outputs\apk" $variant.ToLower()
$apk = Get-ChildItem $folder -Filter "alarm-*.apk" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime | Select-Object -Last 1
if (-not $apk) {
    Write-Host "APK не найден в $folder" -ForegroundColor Red
    exit 1
}

$dest = Join-Path $PSScriptRoot "builds"
New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item $apk.FullName $dest -Force

Write-Host ""
Write-Host "Готово: builds\$($apk.Name)" -ForegroundColor Green
