$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $projectRoot '.tools\jdk25'
if (Get-ChildItem -LiteralPath $destination -Directory -ErrorAction SilentlyContinue | Where-Object {
    Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe')
}) { Write-Host 'Local JDK 25 is ready.'; return }
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot '.tools') | Out-Null
Write-Host 'Downloading Eclipse Temurin JDK 25 for this project only...'
$assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
$package = $assets[0].binary.package
if (-not $package.link -or -not $package.checksum) { throw 'No verified JDK 25 package found.' }
$archive = Join-Path $projectRoot '.tools\jdk25.zip'
Invoke-WebRequest -Uri $package.link -OutFile $archive
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.checksum) {
    throw 'JDK checksum mismatch. Archive was not extracted.'
}
Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
Write-Host 'JDK 25 is ready in .tools/jdk25. System Java settings were not changed.'
