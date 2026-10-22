param(
    [switch] $CreateOnly,
    [switch] $WipeData
)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$local = Join-Path $repo ".local\android-test"
$sdk = Join-Path $local "sdk"
$avdRoot = Join-Path $local "avd"
$homeDir = Join-Path $local "home"
$reports = Join-Path $local "reports"
$image = Join-Path $sdk "system-images\android-35\google_apis\x86_64"
$emulator = Join-Path $sdk "emulator\emulator.exe"
$avdName = "PowerCal_API35"
$avdDir = Join-Path $avdRoot "$avdName.avd"
$avdIni = Join-Path $avdRoot "$avdName.ini"
foreach ($dir in @($sdk,$avdRoot,$homeDir,$reports,$avdDir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
if (!(Test-Path (Join-Path $image "system.img"))) {
    throw "System image missing; verify and extract the official ZIP in $image"
}
if (!(Test-Path $emulator)) { throw "Android emulator missing: $emulator" }

# Isolate virtual device configuration/data/cache and logs within this repo.
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_USER_HOME = $homeDir
$env:ANDROID_EMULATOR_HOME = $homeDir
$env:ANDROID_AVD_HOME = $avdRoot

$path = $avdDir.Replace("\","/")
$sys = $image.Replace("\","/") + "/"
@"
avd.ini.encoding=UTF-8
path=$path
target=android-35
"@ | Set-Content -Path $avdIni -Encoding Ascii
$config = Join-Path $avdDir "config.ini"
if (!(Test-Path $config)) {
@"
AvdId=$avdName
PlayStore.enabled=false
abi.type=x86_64
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
fastboot.forceColdBoot=yes
hw.accelerometer=yes
hw.audioInput=no
hw.battery=yes
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.device.manufacturer=Google
hw.device.name=pixel_6
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.initialOrientation=Portrait
hw.keyboard=yes
hw.lcd.density=420
hw.lcd.height=2400
hw.lcd.width=1080
hw.ramSize=4096
hw.sdCard=no
image.sysdir.1=$sys
runtime.network.latency=none
runtime.network.speed=full
skin.dynamic=yes
skin.name=1080x2400
tag.display=Google APIs
tag.id=google_apis
vm.heapSize=512
"@ | Set-Content -Path $config -Encoding Ascii
}
Write-Output "AVD directory: $avdDir"
& $emulator -list-avds
if ($LASTEXITCODE -ne 0) { throw "Emulator could not read local AVD registry" }
if ($CreateOnly) { exit 0 }
$args = @("-avd",$avdName,"-port","5554","-no-window","-no-audio",
          "-no-snapshot","-no-boot-anim","-gpu","swiftshader_indirect","-accel","on",
          "-memory","4096","-partition-size","2047")
if ($WipeData) { $args += "-wipe-data" }
$stdout = Join-Path $reports "emulator.stdout.log"
$stderr = Join-Path $reports "emulator.stderr.log"
$proc = Start-Process -FilePath $emulator -WorkingDirectory (Split-Path $emulator) -ArgumentList $args -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
Write-Output "Started Android emulator PID $($proc.Id)"
Write-Output "Logs: $stdout / $stderr"
