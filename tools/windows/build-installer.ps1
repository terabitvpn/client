# Builds build/release/Terabit-Windows.exe (Inno Setup installer) and
# build/release/Terabit-Windows-Portable.zip (unzip & run, data stays in the folder).
param([string]$Iscc = "$env:ISCC")
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\.."
Push-Location $root
try {
    & .\gradlew.bat :desktopApp:createDistributable --console=plain
    if (-not $Iscc) { $Iscc = (Get-Command iscc -ErrorAction SilentlyContinue).Source }
    if (-not $Iscc) { $Iscc = "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe" }
    $version = (Select-String -Path desktopApp\build.gradle.kts -Pattern 'val terabitVersion = "(.+)"').Matches[0].Groups[1].Value
    & $Iscc "/DAppVersion=$version" tools\windows\terabit.iss

    # Portable: same app image + a "portable" marker (switches data to .\data, no registry/autostart/updates)
    $app = "desktopApp\build\compose\binaries\main\app\Terabit"
    $stage = "build\portable\Terabit"
    if (Test-Path "build\portable") { Remove-Item "build\portable" -Recurse -Force }
    New-Item -ItemType Directory -Force $stage | Out-Null
    Copy-Item "$app\*" $stage -Recurse
    Set-Content -Path "$stage\portable" -Value "Terabit portable $version" -Encoding ascii
    Set-Content -Path "$stage\README.txt" -Encoding utf8 -Value @"
Terabit VPN $version — портативная версия

Запуск: Terabit.exe. Установка не нужна, права администратора не нужны
(кроме режима TUN). Подписки и настройки хранятся рядом, в папке data —
папку можно носить на флешке. Новую версию скачивайте с сайта
terabitvpn.ru
"@
    $zip = "build\release\Terabit-Windows-Portable.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    # tar (bsdtar, built into Windows 10+) writes proper "/" paths; Compress-Archive in PS 5 writes "\\".
    & "$env:SystemRoot\System32\tar.exe" -a -c -f $zip -C build\portable Terabit
    if ($LASTEXITCODE -ne 0) { throw "zip failed" }
} finally { Pop-Location }

