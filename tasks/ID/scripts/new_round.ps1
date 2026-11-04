param(
  [Parameter(Mandatory = $true)]
  [ValidatePattern('^0[1-7]$')]
  [string]$Task
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root (Join-Path $Task "reports")

if (-not (Test-Path -LiteralPath (Join-Path $root "00_MASTER_CODEX_PROMPT.md"))) {
  throw "Not in a valid ID task root."
}
if (-not (Test-Path -LiteralPath $path)) {
  New-Item -Path $path -ItemType Directory -Force | Out-Null
}

$next = 1
$existing = Get-ChildItem -LiteralPath $path -Directory -Filter "round-*" -ErrorAction Stop
foreach ($entry in $existing) {
  if ($entry.Name -match '^round-([0-9]+)$') {
    $number = [int]$Matches[1]
    if ($number -ge $next) {
      $next = $number + 1
    }
  }
}

$roundName = "round-{0:D2}" -f $next
$newPath = Join-Path $path $roundName
if (Test-Path -LiteralPath $newPath) {
  throw "The next report round already exists: $newPath"
}
New-Item -Path $newPath -ItemType Directory -ErrorAction Stop | Out-Null
Write-Output $newPath
