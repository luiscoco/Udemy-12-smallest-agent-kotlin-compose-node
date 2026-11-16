param([ValidateSet('Build','Test','Lint','Install')][string]$Task='Build')
$ErrorActionPreference='Stop'
$env:DEBUG=''
$appRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $candidates=@('C:\Program Files\Android\Android Studio\jbr','C:\Program Files (x86)\Android\openjdk\jdk-21.0.8','C:\Program Files\Android\openjdk\jdk-21.0.8')
    foreach($candidate in $candidates){if(Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe')){$env:JAVA_HOME=$candidate;break}}
}
if (-not $env:JAVA_HOME){throw 'Set JAVA_HOME to JDK 17 or newer, or install Android Studio.'}
if (-not $env:ANDROID_HOME) {
    $candidates=@((Join-Path $env:LOCALAPPDATA 'Android\Sdk'),'C:\Program Files (x86)\Android\android-sdk')
    foreach($candidate in $candidates){if(Test-Path -LiteralPath (Join-Path $candidate 'platforms')){$env:ANDROID_HOME=$candidate;break}}
}
if (-not $env:ANDROID_HOME){throw 'Set ANDROID_HOME to an Android SDK containing platform 36 and build-tools 36.0.0.'}
$env:GRADLE_USER_HOME=Join-Path $appRoot '.tools\gradle-user-home'
# AAPT2 cannot start from the long Gradle transform path in this workspace.
# Keep the exact AGP compiler at a shorter path, verified before extraction.
$aaptDirectory = Join-Path $appRoot '.tools/aapt2'
$aaptExecutable = Join-Path $aaptDirectory 'aapt2.exe'
if (-not (Test-Path -LiteralPath $aaptExecutable)) {
    New-Item -ItemType Directory -Path $aaptDirectory -Force | Out-Null
    $archive = Join-Path $appRoot '.tools/aapt2-9.3.3-windows.jar'
    $expectedHash = 'B1006ECEC7E5936257E95E97F3EBA7EF439D3E44178967CC048F86C9119FB231'
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/9.3.3-15703166/aapt2-9.3.3-15703166-windows.jar' -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expectedHash) { throw 'The Android resource compiler checksum does not match.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($archive)
    try {
        $entry = $zip.GetEntry('aapt2.exe')
        if (-not $entry) { throw 'The verified compiler archive has no aapt2.exe.' }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $aaptExecutable, $true)
    } finally { $zip.Dispose() }
}
$gradleTask=@{Build=':app:assembleDebug';Test=':app:testDebugUnitTest';Lint=':app:lintDebug';Install=':app:installDebug'}[$Task]
Push-Location -LiteralPath (Join-Path $appRoot 'android')
try { & '.\gradlew.bat' $gradleTask '--no-daemon' "-Pandroid.aapt2FromMavenOverride=$aaptExecutable"; exit $LASTEXITCODE } finally { Pop-Location }
