# Local twin of check-conventions.sh. Usage: powershell -File scripts\ci\check-conventions.ps1 origin/main
param([string]$Base = "origin/main")
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$symbols = '[\u2013\u2014\u2190-\u21FF\u2600-\u27BF]|[\uD83C-\uDBFF][\uDC00-\uDFFF]'
$diacritics = '[\u00C0-\u1EF9]'
$status = 0

$added = git diff -U0 --diff-filter=AM $Base HEAD -- '*.java' '*.sql' '*.yml' '*.yaml' '*.properties' '*.md' '*.xml' '*.sh' '*.ps1' |
  Where-Object { $_ -match '^\+' -and $_ -notmatch '^\+\+\+' }
$bad = $added | Select-String -Pattern $symbols -CaseSensitive
if ($bad) { Write-Host "Forbidden symbol characters:"; $bad | ForEach-Object { $_.Line }; $status = 1 }

$addedJava = git diff -U0 --diff-filter=AM $Base HEAD -- '*.java' |
  Where-Object { $_ -match '^\+' -and $_ -notmatch '^\+\+\+' }
$bad = $addedJava | Select-String -Pattern $diacritics -CaseSensitive
if ($bad) { Write-Host "Non English characters in Java:"; $bad | ForEach-Object { $_.Line }; $status = 1 }

exit $status
