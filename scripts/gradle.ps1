param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArguments)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $projectRoot '.tools\jdk25'
$javaCandidate = Get-ChildItem -LiteralPath $localJdk -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
if ($javaCandidate) {
    $env:JAVA_HOME = $javaCandidate.FullName
} elseif (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    & (Join-Path $PSScriptRoot 'setup-jdk.ps1')
    $javaCandidate = Get-ChildItem -LiteralPath $localJdk -Directory | Select-Object -First 1
    $env:JAVA_HOME = $javaCandidate.FullName
}
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
Push-Location -LiteralPath $projectRoot
try {
    if (-not $GradleArguments) { $GradleArguments = @('build') }
    & '.\gradlew.bat' @GradleArguments
    $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result
