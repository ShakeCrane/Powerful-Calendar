param(
    [string]$Serial = "emulator-5554",
    [switch]$Status
)

$ErrorActionPreference = "Stop"
$package = "dev.powerfulcalendar"
$prefs = "shared_prefs/powercal-ai.xml"
$pending = "shared_prefs/powercal-ai.xml.pending"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$adb = Join-Path $repo ".local\android-test\sdk\platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $adb)) { throw "Repository-local ADB not found: $adb" }
$deviceState = (& $adb -s $Serial get-state 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne "device") { throw "Emulator $Serial is not connected." }

function Read-AppPrefs {
    $raw = (& $adb -s $Serial exec-out run-as $package cat $prefs 2>$null | Out-String)
    if ($LASTEXITCODE -ne 0 -or -not $raw.Contains("<map")) {
        throw "Cannot read debug app preferences using run-as. Check the installed debug APK."
    }
    $xml = New-Object System.Xml.XmlDocument
    $xml.PreserveWhitespace = $true
    $xml.LoadXml($raw)
    if ($xml.DocumentElement.Name -ne "map") { throw "Unexpected preferences document." }
    return $xml
}

function Put-String([System.Xml.XmlDocument]$doc, [string]$name, [string]$value) {
    $node = $doc.SelectSingleNode("/map/string[@name='$name']")
    if ($null -eq $node) {
        $node = $doc.CreateElement("string")
        $node.SetAttribute("name", $name)
        [void]$doc.DocumentElement.AppendChild($node)
    }
    $node.InnerText = $value
}

function Put-Boolean([System.Xml.XmlDocument]$doc, [string]$name, [bool]$value) {
    $node = $doc.SelectSingleNode("/map/boolean[@name='$name']")
    if ($null -eq $node) {
        $node = $doc.CreateElement("boolean")
        $node.SetAttribute("name", $name)
        [void]$doc.DocumentElement.AppendChild($node)
    }
    $node.SetAttribute("value", $value.ToString().ToLowerInvariant())
}

$doc = Read-AppPrefs
if ($Status) {
    Write-Output ("endpoint=" + $doc.SelectSingleNode("/map/string[@name='endpoint']").InnerText)
    Write-Output ("model=" + $doc.SelectSingleNode("/map/string[@name='model']").InnerText)
    $keyNode = $doc.SelectSingleNode("/map/string[@name='api_key']")
    Write-Output ("key_present=" + ($null -ne $keyNode -and -not [string]::IsNullOrWhiteSpace($keyNode.InnerText)))
    $enabledNode = $doc.SelectSingleNode("/map/boolean[@name='enabled']")
    Write-Output ("enabled=" + ($null -ne $enabledNode -and $enabledNode.GetAttribute('value') -eq 'true'))
    exit 0
}

Write-Output "Configuring PowerCal AI directly on $Serial (no Android GUI required)."
Write-Output "Key is read locally with hidden input. It is never placed in the command line or repository."
$secureKey = Read-Host "Paste a NEW CommandCode API key (hidden)" -AsSecureString
$handle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($handle)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle)
}
if ([string]::IsNullOrWhiteSpace($plainKey)) { throw "No key entered; preferences unchanged." }
if ($plainKey -ne $plainKey.Trim()) { throw "Key contains surrounding whitespace; preferences unchanged." }
if ($plainKey -match '[^\x21-\x7E]') { throw "Key has non-ASCII characters; preferences unchanged." }

Put-String $doc "endpoint" "https://api.commandcode.ai/provider/v1"
Put-String $doc "model" "stealth/glyph-cluster:free"
Put-String $doc "api_key" $plainKey
Put-Boolean $doc "enabled" $true
Put-Boolean $doc "share_context" $false
$payload = $doc.OuterXml
$plainKey = $null

# Stop the process before writing so in-memory SharedPreferences cannot overwrite our change.
& $adb -s $Serial shell am force-stop $package | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not stop the app safely; preferences unchanged." }

# Pipe the XML through stdin. The API key is NOT a command argument, file on the host,
# ADB shell command, Git-tracked asset, or terminal output.
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $adb
$psi.Arguments = "-s $Serial shell -T run-as $package dd of=$pending"
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$p = New-Object System.Diagnostics.Process
$p.StartInfo = $psi
try {
    if (-not $p.Start()) { throw "Cannot start ADB." }
    $p.StandardInput.Write($payload)
    $p.StandardInput.Close()
    $null = $p.StandardError.ReadToEnd()
    $null = $p.StandardOutput.ReadToEnd()
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw "ADB transfer failed; existing preferences unchanged." }
} finally {
    $p.Dispose()
    $payload = $null
}
& $adb -s $Serial shell run-as $package chmod 600 $pending | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not protect staged preferences; existing preferences unchanged." }
& $adb -s $Serial shell run-as $package mv $pending $prefs | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not activate staged preferences." }

$verified = Read-AppPrefs
$keyPresent = $verified.SelectSingleNode("/map/string[@name='api_key']")
if ($null -eq $keyPresent -or [string]::IsNullOrWhiteSpace($keyPresent.InnerText)) {
    throw "Verification failed: API key not present."
}
Write-Output "SUCCESS: AI config saved in the emulator app's private preferences."
Write-Output "Endpoint: https://api.commandcode.ai/provider/v1"
Write-Output "Model: stealth/glyph-cluster:free"
Write-Output "Key present: True (value never displayed)"
Write-Output "Calendar context sharing: disabled"
Write-Output "Next: ask ChatGPT to run the free minimal network connectivity check."
