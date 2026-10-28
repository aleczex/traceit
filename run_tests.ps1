<#
.SYNOPSIS
Orchestrates Android instrumentation testing where tests capture screenshots while the app is active.

.PARAMETER TestClass
Optional fully qualified test class name or class#method (e.g., com.aletchec.traceit.MainActivityEspressoTest).

.PARAMETER DeviceId
Required emulator or device serial ID or friendly name (e.g., emulator-5554 or Medium_Tablet) when multiple devices are running.

.PARAMETER Variant
Build variant to test (default: devDebug).

.PARAMETER Output
Directory to save captured screenshots (default: screenshots).

.EXAMPLE
.\run_tests.ps1 -DeviceId emulator-5554

.EXAMPLE
.\run_tests.ps1 -DeviceId Medium_Tablet

.EXAMPLE
.\run_tests.ps1 -TestClass com.aletchec.traceit.MainActivityEspressoTest -DeviceId Pixel_9_Pro
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

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " TraceIt: Running Instrumentation Tests & Screencaps" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

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

# Step 2: Query connected devices and build friendly name mapping
Write-Step "Querying connected Android devices and emulators..."
$adbOutput = & $adbCmd devices 2>&1
$deviceSerials = @()
if ($LASTEXITCODE -eq 0) {
$deviceSerials = $adbOutput | Select-String -Pattern "\bdevice$" | ForEach-Object { $_.Line.Split("`t")[0].Trim() }
}

if ($deviceSerials.Count -eq 0) {
Write-ErrorMsg "No connected devices or emulators found. Please start an emulator or connect a physical device."
exit 1
}

$deviceList = @()
foreach ($serial in $deviceSerials) {
$avd = (& $adbCmd -s $serial shell getprop ro.boot.qemu.avd_name).Trim()
$model = (& $adbCmd -s $serial shell getprop ro.product.model).Trim()
$friendly = if (-not [string]::IsNullOrEmpty($avd)) { $avd } elseif (-not [string]::IsNullOrEmpty($model)) { $model -replace '[^\w\-_]', '_' } else { $serial }
$deviceList += [PSCustomObject]@{
    Serial = $serial
    FriendlyName = $friendly
    Model = $model
    AvdName = $avd
}
}

Write-Host "--------------------------------------------------" -ForegroundColor Cyan
Write-Host "Connected devices/emulators detected ($($deviceList.Count)):" -ForegroundColor Cyan
foreach ($dev in $deviceList) {
$selected = ($dev.Serial -eq $DeviceId -or $dev.FriendlyName -eq $DeviceId)
$label = "$($dev.Serial) [Friendly: $($dev.FriendlyName)$(if($dev.Model){", Model: $($dev.Model)"})]"
Write-Host "  - $label $(if($selected){'[SELECTED]'}else{''})" -ForegroundColor $(if($selected){'Green'}else{'Gray'})
}
Write-Host "--------------------------------------------------" -ForegroundColor Cyan

# Step 3: Validate and resolve DeviceId / FriendlyName
Write-Step "Validating target device configuration..."
$targetDevice = $null

if ([string]::IsNullOrEmpty($DeviceId)) {
if ($deviceList.Count -gt 1) {
    Write-ErrorMsg "Multiple devices/emulators running ($($deviceList.Count)). Providing a device ID or friendly name (-DeviceId <name>) is mandatory."
    Write-Host "Available devices:" -ForegroundColor Yellow
    foreach ($dev in $deviceList) {
        Write-Host "  .\run_tests.ps1 -DeviceId $($dev.FriendlyName)  (or serial: $($dev.Serial))" -ForegroundColor Yellow
    }
    exit 1
} else {
    $targetDevice = $deviceList[0]
    Write-Info "Only 1 device connected. Automatically selected: $($targetDevice.Serial) (Friendly: $($targetDevice.FriendlyName))"
}
} else {
# Try matching by Serial or FriendlyName
$match = $deviceList | Where-Object { $_.Serial -eq $DeviceId -or $_.FriendlyName -eq $DeviceId }
if ($match) {
    $targetDevice = $match
    Write-Info "Target Device Resolved -> Serial: $($targetDevice.Serial), Friendly Name: $($targetDevice.FriendlyName)"
} else {
    Write-ErrorMsg "Specified device/friendly name '$DeviceId' not found in connected devices."
    Write-Host "Connected devices available:" -ForegroundColor Yellow
    foreach ($dev in $deviceList) {
        Write-Host "  - $($dev.FriendlyName) (serial: $($dev.Serial))" -ForegroundColor Yellow
    }
    exit 1
}
}

$activeSerial = $targetDevice.Serial
$activeFriendly = $targetDevice.FriendlyName

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
Write-Step "Installing APKs on target device: $activeFriendly ($activeSerial)..."
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
& $adbCmd -s $activeSerial install -r $appApk
if ($LASTEXITCODE -ne 0) {
Write-ErrorMsg "Failed to install app APK."
exit 1
}

Write-Info "Installing test APK: $testApk"
& $adbCmd -s $activeSerial install -r $testApk
if ($LASTEXITCODE -ne 0) {
Write-ErrorMsg "Failed to install test APK."
exit 1
}
Write-Success "APKs installed successfully."

# Clear existing remote screenshots
& $adbCmd -s $activeSerial shell rm -rf /sdcard/Pictures/screenshots
& $adbCmd -s $activeSerial shell mkdir -p /sdcard/Pictures/screenshots

# Step 6: Define Test to Execute
$testToRun = if (-not [string]::IsNullOrEmpty($TestClass)) { $TestClass } else { "com.aletchec.traceit.MainActivityEspressoTest" }

Write-Step "Executing test: $testToRun (app captures screenshots internally)..."
$overallSuccess = $true

Write-Host "--------------------------------------------------" -ForegroundColor Cyan
$instrumentCmd = @("-s", $activeSerial, "shell", "am", "instrument", "-w", "-e", "class", $testToRun, "com.aletchec.traceit.dev.test/androidx.test.runner.AndroidJUnitRunner")
& $adbCmd @instrumentCmd
$testResult = $LASTEXITCODE

if ($testResult -eq 0) {
Write-Success "Test(s) '$testToRun' passed."
} else {
Write-WarningMsg "Test(s) '$testToRun' failed with exit code $testResult."
$overallSuccess = $false
}

# Step 7: Pull Screenshots into target directory (screenshots/<FriendlyName>/)
$targetOutputDir = Join-Path $PSScriptRoot "$Output/$activeFriendly"
Write-Step "Pulling screenshots from device '$activeFriendly' ($activeSerial) to '$targetOutputDir'..."
if (-not (Test-Path $targetOutputDir)) {
New-Item -ItemType Directory -Force -Path $targetOutputDir | Out-Null
}

Write-Info "Pulling captured screenshots..."
& $adbCmd -s $activeSerial pull "/sdcard/Pictures/screenshots/." $targetOutputDir 2>&1 | Out-Null
& $adbCmd -s $activeSerial pull "/sdcard/Android/data/com.aletchec.traceit.dev/files/screenshots/." $targetOutputDir 2>&1 | Out-Null

Write-Success "Screenshot pull process completed into '$Output/$activeFriendly/'."

# Step 8: Final Summary
Write-Host "--------------------------------------------------" -ForegroundColor Cyan
if ($overallSuccess) {
Write-Success "All tests completed successfully and screenshots saved to '$Output/$activeFriendly/'."
exit 0
} else {
Write-WarningMsg "Some tests failed, but test execution finished and screenshots were saved to '$Output/$activeFriendly/'."
exit 1
}
