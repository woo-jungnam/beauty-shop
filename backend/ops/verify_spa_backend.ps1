param([string]$Tests)
$ErrorActionPreference = 'Stop'
$spaBackendRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$spaPomPath = Join-Path $spaBackendRoot 'pom.xml'
$spaVerificationPom = Join-Path $spaBackendRoot '.spa-validation-pom.xml'
$spaWrapper = Join-Path $spaBackendRoot 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $spaPomPath) -or -not (Test-Path -LiteralPath $spaWrapper)) {
    throw 'Backend pom.xml and Maven wrapper are required.'
}
$spaPom = [System.IO.File]::ReadAllText($spaPomPath)
if ($spaPom -notmatch '<build>') { throw 'Expected Maven build element was not found.' }
$spaPom = $spaPom.Replace('<build>', '<build><directory>${project.basedir}/target-spa-validation</directory>')
[System.IO.File]::WriteAllText($spaVerificationPom, $spaPom, (New-Object System.Text.UTF8Encoding($false)))
$spaArguments = @('-f', $spaVerificationPom)
if ($Tests) { $spaArguments += "-Dtest=$Tests" }
$spaArguments += 'test'
Push-Location -LiteralPath $spaBackendRoot
try {
    # Windows PowerShell emits NativeCommandError for nonfatal Maven stderr warnings.
    # Maven's exit code remains the authoritative verification result.
    $ErrorActionPreference = 'Continue'
    & $spaWrapper @spaArguments
    $spaVerifyExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = 'Stop'
    Pop-Location
    Remove-Item -LiteralPath $spaVerificationPom -ErrorAction SilentlyContinue
}
exit $spaVerifyExit
