# ПУТЬ К ОТСТУПЛЕНИЮ. Освобождает телефон от жёсткой блокировки.
#
#   npm run escape          — снять блокировку и права владельца, приложение оставить
#   npm run escape -- -Wipe — снять всё и удалить приложение
#
# Работает в три захода, от мягкого к жёсткому, чтобы сработало даже если
# приложение зависло:
#   1. просим само приложение освободиться;
#   2. снимаем права владельца устройства через adb;
#   3. удаляем приложение (если попросили).

param(
    [switch]$Wipe
)

Set-Location $PSScriptRoot

$pkg = "com.sasha.alarm"
$admin = "$pkg/.AlarmDeviceAdminReceiver"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

function Test-DeviceOwner {
    # «Device Owner Type: -1» означает, что владельца устройства нет.
    $dump = & $adb shell "dumpsys device_policy" | Out-String
    $line = ($dump -split "`n") | Where-Object { $_ -match "Device Owner Type:" } | Select-Object -First 1
    if (-not $line) { return $null }
    return -not ($line -match "-1")
}

$devices = @()
try {
    $devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice\s*$" }
} catch {
    Write-Host "adb не отозвался: $_" -ForegroundColor Red
    exit 1
}
if (-not $devices) {
    Write-Host "Телефон не подключён или не разрешена отладка по USB." -ForegroundColor Red
    exit 1
}

$ownerBefore = Test-DeviceOwner
Write-Host ""
if ($ownerBefore -eq $true) {
    Write-Host "Жёсткая блокировка активна — снимаю." -ForegroundColor Yellow
} elseif ($ownerBefore -eq $false) {
    Write-Host "Владельца устройства нет. Всё равно прогоняю все шаги." -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "1. Прошу приложение освободиться..." -ForegroundColor Cyan
& $adb shell "am broadcast -n $pkg/.EscapeReceiver"

Write-Host ""
Write-Host "2. Снимаю права владельца устройства..." -ForegroundColor Cyan
Write-Host "   (ошибка 'non-test admin' здесь нормальна, если владелец и не был назначен)" -ForegroundColor DarkGray
& $adb shell "dpm remove-active-admin $admin"

if ($Wipe) {
    Write-Host ""
    Write-Host "3. Удаляю приложение..." -ForegroundColor Cyan
    & $adb uninstall $pkg
}

Write-Host ""
$ownerAfter = Test-DeviceOwner
if ($ownerAfter -eq $true) {
    Write-Host "ТЕЛЕФОН ВСЁ ЕЩЁ ЗАПЕРТ: приложение осталось владельцем устройства." -ForegroundColor Red
    Write-Host "Последнее средство — сброс к заводским настройкам." -ForegroundColor Red
    exit 1
} elseif ($ownerAfter -eq $false) {
    Write-Host "Телефон свободен: владельца устройства нет." -ForegroundColor Green
} else {
    Write-Host "Не удалось прочитать состояние владельца устройства." -ForegroundColor Yellow
}
