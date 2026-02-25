param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidHome = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }),
    [switch]$SkipSdkInstall,
    [switch]$BuildOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Add-ToPath {
    param([Parameter(Mandatory = $true)][string]$Dir)
    if (-not (Test-Path $Dir)) {
        return
    }
    $parts = $env:Path -split ";"
    if ($parts -notcontains $Dir) {
        $env:Path = "$Dir;$env:Path"
    }
}

Write-Host "== Widget Calendar build/install =="

if (-not $JavaHome) {
    throw "JAVA_HOME is not set. Pass -JavaHome 'C:\path\to\jdk-17'."
}
if (-not (Test-Path (Join-Path $JavaHome "bin\java.exe"))) {
    throw "java.exe not found under '$JavaHome'. Use a valid JDK 17 path."
}

$env:JAVA_HOME = $JavaHome
Add-ToPath (Join-Path $JavaHome "bin")
Write-Host "JAVA_HOME: $env:JAVA_HOME"

if (-not $AndroidHome) {
    throw "Android SDK path not found. Pass -AndroidHome 'C:\path\to\Android\Sdk'."
}
if (-not (Test-Path $AndroidHome)) {
    throw "Android SDK path does not exist: '$AndroidHome'"
}

$env:ANDROID_HOME = $AndroidHome
$env:ANDROID_SDK_ROOT = $AndroidHome
Add-ToPath (Join-Path $AndroidHome "platform-tools")
Add-ToPath (Join-Path $AndroidHome "cmdline-tools\latest\bin")
Add-ToPath (Join-Path $AndroidHome "cmdline-tools\latest\cmdline-tools\bin")
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"

if (-not $SkipSdkInstall) {
    $sdkManagerCmd = Get-Command sdkmanager -ErrorAction SilentlyContinue
    if (-not $sdkManagerCmd) {
        $fallbackSdkManager = Join-Path $AndroidHome "cmdline-tools\latest\cmdline-tools\bin\sdkmanager.bat"
        if (Test-Path $fallbackSdkManager) {
            Add-ToPath (Split-Path $fallbackSdkManager -Parent)
            $sdkManagerCmd = Get-Command sdkmanager -ErrorAction SilentlyContinue
        }
    }
    if (-not $sdkManagerCmd) {
        throw "sdkmanager not found. Install Android command-line tools, then ensure cmdline-tools\latest\bin is under your SDK path."
    }

    Write-Host "Accepting SDK licenses..."
    $yes = @()
    1..200 | ForEach-Object { $yes += "y" }
    $yes | & sdkmanager --licenses

    Write-Host "Installing required SDK packages..."
    & sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
}

$localPropertiesPath = Join-Path $PSScriptRoot "local.properties"
$escapedSdkPath = $AndroidHome.Replace("\", "\\")
"sdk.dir=$escapedSdkPath" | Out-File -FilePath $localPropertiesPath -Encoding ascii -Force
Write-Host "Wrote local.properties"

$gradlew = Join-Path $PSScriptRoot "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat not found in repo root."
}

Write-Host "Building debug APK..."
& $gradlew --no-daemon assembleDebug

$apkPath = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    throw "APK not found after build: $apkPath"
}
Write-Host "APK built: $apkPath"

if ($BuildOnly) {
    Write-Host "Build-only mode enabled. Skipping adb install."
    exit 0
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb not found. Ensure platform-tools are installed."
}

Write-Host "Checking connected devices..."
$devicesOutput = & adb devices
$connectedLines = @($devicesOutput | Where-Object { $_ -match "^\S+\s+device$" })
$connectedCount = $connectedLines.Count
if ($connectedCount -lt 1) {
    throw "No Android device detected. Enable USB debugging and verify with 'adb devices'."
}

Write-Host "Installing APK to device..."
& adb install -r $apkPath

Write-Host ""
Write-Host "Install complete."
Write-Host "Now add the 'Widget Calendar' widget on your home screen."
