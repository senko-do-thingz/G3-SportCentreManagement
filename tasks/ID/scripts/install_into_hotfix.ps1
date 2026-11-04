param(
  [string]$TaskRoot = "D:\G3-SportCentreManagement\SWP-HOTFIX\tasks\ID"
)

$ErrorActionPreference = "Stop"
$sourceRoot = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path -LiteralPath (Join-Path $sourceRoot "00_MASTER_CODEX_PROMPT.md"))) {
  throw "Task source is missing its master prompt: $sourceRoot"
}
if (Test-Path -LiteralPath $TaskRoot) {
  throw "Destination already exists. Inspect it first; never overwrite existing task reports: $TaskRoot"
}

$parent = Split-Path -Parent $TaskRoot
if (-not (Test-Path -LiteralPath $parent)) {
  New-Item -Path $parent -ItemType Directory -Force | Out-Null
}

Copy-Item -LiteralPath $sourceRoot -Destination $TaskRoot -Recurse -ErrorAction Stop
foreach ($number in 1..7) {
  $task = "{0:D2}" -f $number
  $reports = Join-Path $TaskRoot (Join-Path $task "reports")
  New-Item -Path $reports -ItemType Directory -Force | Out-Null
}
Write-Host "Task root installed safely: $TaskRoot"
Write-Host "Next step: read 00_MASTER_CODEX_PROMPT.md then START_CODEX_MESSAGE.md."
Write-Host "Each task uses scripts\new_round.ps1 -Task 01 (through 07)."
