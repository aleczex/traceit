<#
.SYNOPSIS
Orchestrates Android instrumentation testing and screenshot capture via PowerShell and ADB.
.PARAMETER TestClass
Optional fully qualified test class name (e.g., com.aletchec.traceit.MainActivityEspressoTest).
.PARAMETER DeviceId
Required emulator or device serial ID when multiple devices are running.
.PARAMETER Variant
Build variant to test (default: devDebug).
.PARAMETER Output
Directory to save captured screenshots (default: screenshots).
.EXAMPLE
.\run_tests.ps1 -DeviceId emulator-5554
.EXAMPLE
.\run_tests.ps1 -TestClass com.aletchec.traceit.MainActivityEspressoTest -DeviceId emulator-5554
#>

param(
[string]$TestClass = "",
[string]$DeviceId = "",
[string]$Variant = "devDebug",
[string]$Output = "screenshots"
)

function Write-Step {
    param([string]$Message)
    $time = Get-Date -Format "HH:mm:ss"
    Write-Host "[$time] [STEP] $Message" -ForegroundColor Cyan
}

function Write-Info {
    param([string]$Message)
    Write-Host "  -> $Message" -ForegroundColor Gray
}

function Write-Success {
    param([string]$Message)
    $time = Get-Date -Format "HH:mm:ss"
    Write-Host "[$time] [SUCCESS] $Message" -ForegroundColor Green
}

function Write-WarningMsg {
    param([string]$Message)
    $time = Get-Date -Format "HH:mm:ss"
    Write-Host "[$time] [WARNING] $Message" -ForegroundColor Yellow
}

function Write-ErrorMsg {
    param([string]$Message)
    $time = Get-Date -Format "HH:mm:ss"
    Write-Host "[$time] [ERROR] $Message" -ForegroundColor Red
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " TraceIt: Running Instrumentation Tests & Screenshots" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# Step 1: Locate ADB
Write-Step "Locating ADB (Android Debug Bridge)..."
$androidHome = $env:ANDROID_HOME
if (-not $androidHome) { $androidHome = $env:ANDROID_SDK_ROOT }
$adbCmd = "adb"
if ($androidHome) {
    $adbWin = Join-Path $androidHome "platform-tools/adb.exe"
    $adbPath = Join-Path $androidHome "platform-tools/adb"
    if (Test-Path $adbWin) { $adbCmd = $adbWin }
    elseif (Test-Path $adbPath) { $adbCmd = $adbPath }
}
Write-Info "Using ADB: $adbCmd"

# Step 2: Query connected devices
Write-Step "Querying connected Android devices and emulators..."
$adbOutput = & $adbCmd devices 2>&1
$devices = @()
if ($LASTEXITCODE -eq 0) {
    $devices = $adbOutput | Select-String -Pattern "\bdevice$" | ForEach-Object { $_.Line.Split("`t")[0].Trim() }
}

Write-Host "--------------------------------------------------" -ForegroundColor Cyan
Write-Host "Connected devices/emulators detected ($($devices.Count)):" -ForegroundColor Cyan
if ($devices.Count -eq 0) {
    Write-ErrorMsg "No connected devices or emulators found. Please start an emulator or connect a physical device."
    exit 1
}
foreach ($dev in $devices) {
    $selected = ($dev -eq $DeviceId)
    Write-Host "  - $dev $(if($selected){'[SELECTED]'}else{''})" -ForegroundColor $(if($selected){'Green'}else{'Gray'})
}
Write-Host "--------------------------------------------------" -ForegroundColor Cyan

# Step 3: Validate and resolve DeviceId
Write-Step "Validating target device configuration..."
if ([string]::IsNullOrEmpty($DeviceId)) {
    if ($devices.Count -gt 1) {
        Write-ErrorMsg "Multiple devices/emulators running ($($devices.Count)). Providing an emulator ID (-DeviceId) is mandatory when multiple devices are running."
        Write-Host "Connected devices available:" -ForegroundColor Yellow
        foreach ($dev in $devices) {
            Write-Host "  .\run_tests.ps1 -DeviceId $dev" -ForegroundColor Yellow
        }
        exit 1
    } else {
        $DeviceId = $devices[0]
        Write-Info "Only 1 device connected. Automatically selected device ID: $DeviceId"
    }
} else {
    Write-Info "Target Device ID: $DeviceId"
    if (-not ($devices -contains $DeviceId)) {
        Write-WarningMsg "Specified device '$DeviceId' not found in active devices list: $($devices -join ', ')"
    }
}

# Step 4: Build APKs via Gradle
Write-Step "Building app and test APKs for variant '$Variant'..."
$gradlewFilename = if ($PSVersionTable.Platform -eq "Unix") { "./gradlew" } else { ".\gradlew.bat" }
$capitalizeVariant = $Variant.Substring(0,1).ToUpper() + $Variant.Substring(1)

$gradleBuildArgs = @(":app:assemble$capitalizeVariant", ":app:assemble${capitalizeVariant}AndroidTest")
Write-Info "Executing: $gradlewFilename $($gradleBuildArgs -join ' ')"
& $gradlewFilename @gradleBuildArgs
if ($LASTEXITCODE -ne 0) {
    Write-ErrorMsg "Gradle build failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}
Write-Success "APKs built successfully."

# Step 5: Install APKs on target device
Write-Step "Installing APKs on target device: $DeviceId..."
$appApk = Join-Path $PSScriptRoot "app/build/outputs/apk/dev/debug/app-dev-debug.apk"
$testApk = Join-Path $PSScriptRoot "app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk"

if (-not (Test-Path $appApk)) {
    Write-ErrorMsg "Could not find app APK at expected path: $appApk"
    exit 1
}
if (-not (Test-Path $testApk)) {
    Write-ErrorMsg "Could not find test APK at expected path: $testApk"
    exit 1
}

Write-Info "Installing app APK: $appApk"
& $adbCmd -s $DeviceId install -r $appApk
if ($LASTEXITCODE -ne 0) {
    Write-ErrorMsg "Failed to install app APK."
    exit 1
}

Write-Info "Installing test APK: $testApk"
& $adbCmd -s $DeviceId install -r $testApk
if ($LASTEXITCODE -ne 0) {
    Write-ErrorMsg "Failed to install test APK."
    exit 1
}
Write-Success "APKs installed successfully."

# Step 6: Run Instrumentation Tests via ADB
Write-Step "Running instrumentation tests on device '$DeviceId'..."
$instrumentCmd = @("-s", $DeviceId, "shell", "am", "instrument", "-w")
if (-not [string]::IsNullOrEmpty($TestClass)) {
    Write-Info "Target Test Class: $TestClass"
    $instrumentCmd += @("-e", "class", $TestClass)
} else {
    Write-Info "Running all instrumentation tests"
}
$instrumentCmd += "com.aletchec.traceit.dev.test/androidx.test.runner.AndroidJUnitRunner"

Write-Info "Executing: $adbCmd $($instrumentCmd -join ' ')"
& $adbCmd @instrumentCmd
$testExitCode = $LASTEXITCODE

# Step 7: Pull Screenshots
Write-Step "Pulling screenshots from device '$DeviceId' to '$Output'..."
$targetOutputDir = Join-Path $PSScriptRoot $Output
if (-not (Test-Path $targetOutputDir)) {
    New-Item -ItemType Directory -Force -Path $targetOutputDir | Out-Null
}

Write-Info "Pulling public pictures screenshots..."
& $adbCmd -s $DeviceId pull "/sdcard/Pictures/screenshots/." $targetOutputDir 2>&1 | Out-Null

Write-Info "Pulling app-specific screenshots..."
& $adbCmd -s $DeviceId pull "/sdcard/Android/data/com.aletchec.traceit.dev/files/screenshots/." $targetOutputDir 2>&1 | Out-Null

Write-Success "Screenshot pull process completed."

# Step 8: Final Summary
Write-Host "--------------------------------------------------" -ForegroundColor Cyan
if ($testExitCode -eq 0) {
    Write-Success "Tests completed successfully and screenshots saved to '$Output'."
    exit 0
} else {
    Write-ErrorMsg "Tests completed with failures (exit code $testExitCode), but screenshots were pulled."
    exit $testExitCode
}
