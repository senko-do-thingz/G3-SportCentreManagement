# Installation into the user's already-created task folder

Target directory:

`D:\G3-SportCentreManagement\SWP\tasks\Flow1-4 - Release 1.1 (i)`

The ZIP stores package contents at the archive root, without an extra nesting directory.

If you also download the provided `Install_Flow1-4_Release_1.1.ps1`, run it from PowerShell to extract safely. It refuses to overwrite a non-empty destination.

Or use:

```powershell
$zip = Join-Path $env:USERPROFILE 'Downloads\Sportify_Flow1-4_Release_1.1_Tasks.zip'
$dest = 'D:\G3-SportCentreManagement\SWP\tasks\Flow1-4 - Release 1.1 (i)'
if (-not (Test-Path -LiteralPath $zip)) { throw "ZIP not found: $zip" }
if (-not (Test-Path -LiteralPath $dest)) { throw "Missing target directory: $dest" }
if (Get-ChildItem -LiteralPath $dest -Force | Select-Object -First 1) { throw 'Target is not empty. Do not overwrite.' }
Expand-Archive -LiteralPath $zip -DestinationPath $dest -ErrorAction Stop
Get-ChildItem -LiteralPath $dest | Select-Object Name
```

No source code or runtime database is changed by extraction.
