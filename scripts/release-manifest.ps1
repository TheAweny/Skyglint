param(
    [string]$Version,
    [string]$OutputDirectory,
    [ValidateSet('normal', 'important', 'critical')][string]$Importance = 'normal'
)
$ErrorActionPreference = 'Stop'
$releaseRoot = Split-Path -Parent $PSScriptRoot
$releaseProperties = Get-Content -LiteralPath (Join-Path $releaseRoot 'gradle.properties')
if (-not $Version) { $Version = ($releaseProperties | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', '' }
if ($Version -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') { throw 'Use a stable major.minor.patch version.' }
$releaseBase = ($releaseProperties | Where-Object { $_ -match '^archives_base_name=' }) -replace '^archives_base_name=', ''
$releaseJar = Join-Path $releaseRoot "build/libs/$releaseBase-$Version.jar"
if (-not (Test-Path -LiteralPath $releaseJar -PathType Leaf)) { throw "Build the release JAR first: $releaseJar" }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $releaseRoot 'build/release' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$releaseDigest = (Get-FileHash -LiteralPath $releaseJar -Algorithm SHA256).Hash.ToLowerInvariant()
$releaseManifest = [ordered]@{
    schema = 1
    version = $Version
    importance = $Importance.ToLowerInvariant()
    minecraft = '26.2'
    java = 25
    fabricLoader = '0.19.3'
    file = [System.IO.Path]::GetFileName($releaseJar)
    sha256 = $releaseDigest
    size = (Get-Item -LiteralPath $releaseJar).Length
}
$releaseJson = ($releaseManifest | ConvertTo-Json) + "`n"
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'update-manifest.json'), $releaseJson, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText((Join-Path $OutputDirectory 'SHA256SUMS.txt'), "$releaseDigest  $($releaseManifest.file)`n", [System.Text.UTF8Encoding]::new($false))
Write-Output "Manifest and SHA256SUMS ready in $OutputDirectory"
