param(
    [Parameter(Mandatory=$true)][string]$Database,
    [Parameter(Mandatory=$true)][string]$OutputFile,
    [ValidateRange(1,65535)][int]$Port = 1433,
    [switch]$ProtectedReadAuthorized,
    [switch]$DisposableDemoHistory
)
$ErrorActionPreference = 'Stop'
if ($Database -notmatch '^(sportify|sportify_demo|sportify_qr_demo|sportify_unify_test_[0-9]{14}(?:_[a-z0-9]+)?)$') {
    throw 'Unsupported database name'
}
$disposable = $Database -match '^sportify_unify_test_'
if (-not $disposable -and -not $ProtectedReadAuthorized) { throw 'Target-specific read-only authorization required' }
if ($DisposableDemoHistory -and -not $disposable) { throw 'Demo history is restricted to disposable fixtures' }
if (-not $env:UNIFY_PREFLIGHT_DB_USER -or -not $env:UNIFY_PREFLIGHT_DB_PASSWORD) { throw 'Set explicit preflight credential environment variables' }
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$output = [System.IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $output) { throw 'Refusing to overwrite existing evidence' }
$names = @('UNIFY_PREFLIGHT_DB_URL','UNIFY_PREFLIGHT_SCHEMA_FILE','UNIFY_PREFLIGHT_OUTPUT','UNIFY_PREFLIGHT_PROTECTED_READ_AUTHORIZED','UNIFY_PREFLIGHT_DISPOSABLE_DEMO')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:UNIFY_PREFLIGHT_DB_URL = "jdbc:sqlserver://127.0.0.1:$Port;databaseName=$Database;encrypt=false;trustServerCertificate=true"
    $env:UNIFY_PREFLIGHT_SCHEMA_FILE = Join-Path $repo 'docs/db-unify/schema-fingerprint.txt'
    $env:UNIFY_PREFLIGHT_OUTPUT = $output
    $env:UNIFY_PREFLIGHT_PROTECTED_READ_AUTHORIZED = $ProtectedReadAuthorized.IsPresent.ToString().ToLowerInvariant()
    $env:UNIFY_PREFLIGHT_DISPOSABLE_DEMO = $DisposableDemoHistory.IsPresent.ToString().ToLowerInvariant()
    Push-Location (Join-Path $repo 'backend')
    try {
        & mvn -B -ntp compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/db-unify-classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'Preflight tooling build failed' }
        $dependencies = (Get-Content -LiteralPath 'target/db-unify-classpath.txt' -Raw).Trim()
        $classes = (Resolve-Path -LiteralPath 'target/classes').Path
        & java -cp ($classes + [IO.Path]::PathSeparator + $dependencies) com.sportify.tools.UnifyPreflight
        if ($LASTEXITCODE -ne 0) { throw 'Read-only preflight rejected the target; no import was attempted' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
