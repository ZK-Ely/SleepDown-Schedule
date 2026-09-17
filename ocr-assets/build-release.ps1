# SleepDown Release build script (key never persisted to disk)
# Usage: powershell -ExecutionPolicy Bypass -File .\build-release.ps1
# Flow: enter a signing password -> temp PKCS12 is created from D:\Ely-key.pem
#       -> Gradle builds the Release APK -> temp signing files are deleted.
$ErrorActionPreference = "Continue"

$KeyPath     = "D:\Ely-key.pem"
$ProjectRoot = Join-Path $PSScriptRoot "..\SleepDown-Schedule-main"
$OpenSsl     = "C:\Program Files\Git\usr\bin\openssl.exe"
$JavaHome    = "C:\Users\26291\.cache\tb-build\jdk\jdk-21.0.12.1+1"
$Keytool     = "$JavaHome\bin\keytool.exe"

if (-not (Test-Path $KeyPath)) { throw "Cannot find private key: $KeyPath" }

$secure = Read-Host "Enter signing password (temp PKCS12 only, never stored)" -AsSecureString
$signPwd = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
if ([string]::IsNullOrWhiteSpace($signPwd)) { throw "Password must not be empty" }

$tmp = Join-Path $env:TEMP "sd-sign"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
Remove-Item "$tmp\keystore.p12", "$tmp\cert.pem" -Force -ErrorAction SilentlyContinue

Write-Host "Generating temp certificate and PKCS12 container..."
& $OpenSsl req -new -x509 -key $KeyPath -out "$tmp\cert.pem" -days 10950 -subj "/CN=SleepDown Release" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Certificate generation failed (exit $LASTEXITCODE)" }
& $OpenSsl pkcs12 -export -inkey $KeyPath -in "$tmp\cert.pem" -out "$tmp\keystore.p12" -name sleepdown -passout "pass:$signPwd" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path "$tmp\keystore.p12")) { throw "PKCS12 generation failed (exit $LASTEXITCODE)" }

# Verify the container immediately so a long build is never wasted
& $Keytool -list -keystore "$tmp\keystore.p12" -storepass "$signPwd" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "PKCS12 verification failed (bad password or container), please retry" }
Write-Host "Signing container verified. Building (R8 minify takes a few minutes)..."

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
$exitCode = 0
Push-Location $ProjectRoot
try {
    & .\gradlew.bat :app:assembleGithubRelease --no-daemon --max-workers=2 `
        "-Psleepdown.releaseStoreFile=$tmp\keystore.p12" `
        "-Psleepdown.releaseStorePassword=$signPwd" `
        "-Psleepdown.releaseKeyAlias=sleepdown" `
        "-Psleepdown.releaseKeyPassword=$signPwd"
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
    Remove-Item "$tmp\keystore.p12", "$tmp\cert.pem" -Force -ErrorAction SilentlyContinue
}

if ($exitCode -ne 0) {
    Write-Host ""
    Write-Host "Build FAILED (temp signing files deleted)" -ForegroundColor Red
    exit $exitCode
}

Write-Host ""
Write-Host "Build SUCCESS, temp signing files deleted. Artifacts:" -ForegroundColor Green
Get-ChildItem (Join-Path $ProjectRoot "app\build\outputs\apk\github\release\*.apk") |
    ForEach-Object { "{0}  {1:N1} MB" -f $_.Name, ($_.Length / 1MB) }
