param(
    [string] $Serial = "emulator-5554",
    [string] $Adb = "",
    [switch] $SkipInstall
)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if (!$Adb) { $Adb = Join-Path $repo ".local\android-test\sdk\platform-tools\adb.exe" }
$root = Join-Path $repo ".local\android-test\reports"
$session = Join-Path $root ("smoke-"+(Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $session | Out-Null
$apk = Join-Path $repo "app\build\outputs\apk\debug\PowerCal-0.9.5-rc.1-debug.apk"
$pkg = "dev.powerfulcalendar"
if (!(Test-Path $Adb)) { throw "adb not found: $Adb" }
if (!(Test-Path $apk)) { throw "APK not found: $apk" }
$report = [ordered]@{
    device = $Serial
    package = $pkg
    apk = $apk
    timestamp = (Get-Date).ToUniversalTime().ToString("o")
    boot = "unknown"
    install = "not-run"
    launch = "not-run"
    restart = "not-run"
}
function Capture($name) {
    $remotePng = "/sdcard/powercal_$($name).png"
    $remoteXml = "/sdcard/powercal_$($name).xml"
    & $Adb -s $Serial shell screencap -p $remotePng | Out-Null
    if($LASTEXITCODE -ne 0){throw "screencap failed"}
    & $Adb -s $Serial pull $remotePng (Join-Path $session "$name.png") | Out-Null
    if($LASTEXITCODE -ne 0){throw "screenshot download failed"}
    & $Adb -s $Serial shell uiautomator dump $remoteXml | Out-Null
    if($LASTEXITCODE -ne 0){throw "uiautomator dump failed"}
    & $Adb -s $Serial pull $remoteXml (Join-Path $session "$name.xml") | Out-Null
    if($LASTEXITCODE -ne 0){throw "XML download failed"}
}
try {
    $booted=$false
    for($n=0;$n -lt 120;$n++) {
        $prop=(& $Adb -s $Serial shell getprop sys.boot_completed 2>$null)
        if($LASTEXITCODE -eq 0 -and "$prop".Trim() -eq "1") {
            $booted=$true; break
        }
        Start-Sleep -Seconds 2
    }
    if(!$booted){throw "Emulator did not complete Android boot"}
    $report.boot="PASS"
    $version=(& $Adb -s $Serial shell getprop ro.build.version.release)
    $report.androidVersion="$version".Trim()
    if(!$SkipInstall){
        $out=& $Adb -s $Serial install -r $apk
        if($LASTEXITCODE -ne 0){throw "Install failed: $out"}
        $report.install="PASS"
    } else {$report.install="SKIPPED"}
    $result=& $Adb -s $Serial shell am start -n "$pkg/.MainActivity"
    if($LASTEXITCODE -ne 0){throw "Activity start failed: $result"}
    Start-Sleep -Seconds 8
    $pidText=& $Adb -s $Serial shell pidof $pkg
    if($LASTEXITCODE -ne 0 -or !("$pidText".Trim() -match '^\d+$')){
        throw "App process not running after initial launch"
    }
    $report.launch="PASS"
    $report.initialPid="$pidText".Trim()
    Capture "initial"
    & $Adb -s $Serial shell am force-stop $pkg | Out-Null
    $again=& $Adb -s $Serial shell am start -n "$pkg/.MainActivity"
    Start-Sleep -Seconds 6
    $restartPid=& $Adb -s $Serial shell pidof $pkg
    if($LASTEXITCODE -ne 0 -or !("$restartPid".Trim() -match '^\d+$')){
        throw "App process not running after force-stop/restart"
    }
    $report.restart="PASS"
    $report.restartedPid="$restartPid".Trim()
    Capture "restarted"
    (& $Adb -s $Serial logcat -d -b crash -v threadtime) |
        Set-Content (Join-Path $session "crash-buffer.log") -Encoding UTF8
    Write-Output "SMOKE_PASS Android=$($report.androidVersion) PID=$($report.initialPid) RestartPID=$($report.restartedPid)"
}
catch {
    $report.error=$_.Exception.Message
    Write-Output "SMOKE_FAIL $($report.error)"
    throw
}
finally {
    ($report | ConvertTo-Json -Depth 6) | Set-Content (Join-Path $session "result.json") -Encoding UTF8
    Write-Output "Test evidence: $session"
}
