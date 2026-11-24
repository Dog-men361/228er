param([string]$Output = (Join-Path $PSScriptRoot 'LexRF.apk'))
$ErrorActionPreference = 'Stop'
$sdk = $env:ANDROID_SDK_ROOT
if (!$sdk) { $sdk = $env:ANDROID_HOME }
if (!$sdk -and $env:LOCALAPPDATA) { $sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (!(Test-Path $sdk)) { throw 'Install Android SDK and set ANDROID_SDK_ROOT.' }
$jdk = $env:JAVA_HOME
if (!$jdk -and (Test-Path 'C:/Program Files/Java/jdk-17/bin/java.exe')) { $jdk = 'C:/Program Files/Java/jdk-17' }
if (!$jdk) { throw 'Set JAVA_HOME to a JDK (17 or newer).' }
$ext = if ($IsLinux -or $IsMacOS) { '' } else { '.exe' }
$java = Join-Path $jdk "bin/java$ext"
$javac = Join-Path $jdk "bin/javac$ext"
$jar = Join-Path $jdk "bin/jar$ext"
$bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'lib/d8.jar') } | Sort-Object Name -Descending | Select-Object -First 1
$platform = Get-ChildItem (Join-Path $sdk 'platforms') -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'android.jar') } | Sort-Object Name -Descending | Select-Object -First 1
if (!$bt -or !$platform) { throw 'Android platform and build-tools are required.' }
$android = Join-Path $platform.FullName 'android.jar'
$aapt = Join-Path $bt.FullName "aapt$ext"
$zipalign = Join-Path $bt.FullName "zipalign$ext"
$d8 = Join-Path $bt.FullName 'lib/d8.jar'
$signer = Join-Path $bt.FullName 'lib/apksigner.jar'
if (!$env:LEXRF_KEYSTORE -or !$env:LEXRF_KEY_PASSWORD) { throw 'Configure LEXRF_KEYSTORE and LEXRF_KEY_PASSWORD with your private signing key.' }
$key = (Resolve-Path -LiteralPath $env:LEXRF_KEYSTORE).Path
$alias = if($env:LEXRF_KEY_ALIAS){$env:LEXRF_KEY_ALIAS}else{'lexrf'}
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('lexrf-build-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
New-Item -ItemType Directory -Path "$tmp/classes", "$tmp/dex" | Out-Null
function Run([string]$exe,[string[]]$arguments) { & $exe @arguments; if($LASTEXITCODE -ne 0){throw "Build failed: $exe ($LASTEXITCODE)"} }
$sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
Run $javac (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$android,'-d',"$tmp/classes")+$sources)
Run $jar @('cf',"$tmp/classes.jar",'-C',"$tmp/classes",'.')
Run $java @('-cp',$d8,'com.android.tools.r8.D8','--min-api','23','--lib',$android,'--output',"$tmp/dex","$tmp/classes.jar")
Run $aapt @('package','-f','-M',"$PSScriptRoot/AndroidManifest.xml",'-S',"$PSScriptRoot/res",'-A',"$PSScriptRoot/assets",'-I',$android,'-F',"$tmp/unsigned.apk")
Run $jar @('uf',"$tmp/unsigned.apk",'-C',"$tmp/dex",'classes.dex')
Run $zipalign @('-f','4',"$tmp/unsigned.apk","$tmp/aligned.apk")
Run $java @('-jar',$signer,'sign','--ks',$key,'--ks-key-alias',$alias,'--ks-pass','env:LEXRF_KEY_PASSWORD','--key-pass','env:LEXRF_KEY_PASSWORD','--out',$Output,"$tmp/aligned.apk")
Run $java @('-jar',$signer,'verify','--verbose',$Output)
Run $aapt @('dump','badging',$Output)
Write-Host "APK: $Output"
