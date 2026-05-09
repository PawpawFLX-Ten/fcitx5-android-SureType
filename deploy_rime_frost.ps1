# Full deployment script for fcitx5-android Suretype + Rime
# Installs both APKs, deploys rime-frost dictionary, and restarts the app.
#
# Prerequisites:
#   1. Device connected via USB with USB debugging enabled
#   2. Both APKs already built (assembleDebug)
#
# Usage:
#   .\deploy_rime_frost.ps1

$ErrorActionPreference = "Stop"

# Find ADB
$ADB = Get-ChildItem "E:\Android" -Recurse -Filter "adb.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $ADB) { Write-Error "ADB not found in E:\Android"; exit 1 }
$ADB = $ADB.FullName
Write-Host "ADB: $ADB"

# Check device
$devices = & $ADB devices | Select-String -Pattern "device$" | Measure-Object
if ($devices.Count -eq 0) { Write-Error "No device connected"; exit 1 }
Write-Host "Device: connected" -ForegroundColor Green

$PACKAGE = "org.fcitx.fcitx5.android"
$PROJECT = "E:\Development Work\HandJump V2\fcitx5-android-SureType"
$ASSETS_DIR = "$PROJECT\assets\rime-frost"
$BUILD_DIR = "$PROJECT\app\build\outputs\apk\debug"
$RIME_BUILD_DIR = "$PROJECT\plugin\rime\build\outputs\apk\debug"

# Step 1: Install APKs
Write-Host "`n[1/7] Installing main APK..." -ForegroundColor Yellow
$mainApk = Get-ChildItem $BUILD_DIR -Filter "*.apk" | Select-Object -First 1
& $ADB install -r $mainApk.FullName

Write-Host "`n[2/7] Installing Rime plugin APK..." -ForegroundColor Yellow
$rimeApk = Get-ChildItem $RIME_BUILD_DIR -Filter "*.apk" | Select-Object -First 1
& $ADB install -r $rimeApk.FullName

# Step 3: Create tarball (exclude git, github CI, and script sources)
$TARBALL = "$env:TEMP\rime-frost-deploy.tar"
Write-Host "`n[3/7] Creating tarball from $ASSETS_DIR..." -ForegroundColor Yellow
Push-Location $ASSETS_DIR
try {
    & tar -cf $TARBALL --exclude=".git" --exclude=".github" --exclude="others" .
    Write-Host "  Size: $([math]::Round((Get-Item $TARBALL).Length/1MB,1)) MB"
} finally { Pop-Location }

# Step 4: Push to device
Write-Host "`n[4/7] Pushing to device..." -ForegroundColor Yellow
& $ADB push $TARBALL /sdcard/rime-frost-deploy.tar

# Step 5: Extract to Rime user data directory
Write-Host "`n[5/7] Extracting on device..." -ForegroundColor Yellow
$RIME_DIR = "/data/data/$PACKAGE/files/rime"
& $ADB shell "run-as $PACKAGE mkdir -p $RIME_DIR"
& $ADB shell "run-as $PACKAGE tar -xf /sdcard/rime-frost-deploy.tar -C $RIME_DIR"

# Step 6: Cleanup
Write-Host "`n[6/7] Cleaning up..." -ForegroundColor Yellow
& $ADB shell "rm /sdcard/rime-frost-deploy.tar"
Remove-Item $TARBALL -Force

# Step 7: Restart app
Write-Host "`n[7/7] Restarting fcitx5-android..." -ForegroundColor Yellow
& $ADB shell "am force-stop $PACKAGE"
Start-Sleep -Seconds 2
& $ADB shell "am start -n $PACKAGE/.ui.main.MainActivity"

Write-Host "`n=== Done ===" -ForegroundColor Green
Write-Host "Rime will deploy schemas (build prism) on first input method activation."
Write-Host "To use Suretype disambiguation:"
Write-Host "  1. Open fcitx5-android Settings"
Write-Host "  2. Select input method: 白霜双键 (rime_frost_suretype)"
Write-Host "  3. Switch to Suretype keyboard layout"
Write-Host "  4. Start typing — xlit disambiguation is active!"
