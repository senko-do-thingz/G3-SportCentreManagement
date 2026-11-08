# PowerShell placement and Claude start - Truc's machine

Truc's source path from owner-provided context:
`D:\SWP391\G3-SportCentreManagement-main\G3-SportCentreManagement`

Truc's download location:
`D:\SWP391`

Save the supplied ZIP file under `D:\SWP391` as:
`Sportify_Member_Wallet_Truc_Claude_2026-11-08.zip`

In PowerShell (run in your user account; no force delete):

```powershell
$zip = 'D:\SWP391\Sportify_Member_Wallet_Truc_Claude_2026-11-08.zip'
$repo = 'D:\SWP391\G3-SportCentreManagement-main\G3-SportCentreManagement'
$stage = 'D:\SWP391\Sportify_Wallet_Assignment_Extracted'
$source = Join-Path $stage 'Sportify_Member_Wallet_Truc_Claude_2026-11-08\Sportify-Member-Wallet-Release'
$dest = Join-Path $repo 'tasks\Member-Wallet-Release (i)'

if (-not (Test-Path -LiteralPath $zip)) { throw "ZIP not found: $zip" }
if (-not (Test-Path -LiteralPath (Join-Path $repo '.git'))) { throw "Repo .git not found: $repo" }
New-Item -ItemType Directory -Path $stage -Force | Out-Null
Expand-Archive -LiteralPath $zip -DestinationPath $stage -Force
if (-not (Test-Path -LiteralPath (Join-Path $source '00_MASTER_PROMPT_CLAUDE.md'))) { throw "Bundle not found: $source" }
if (Test-Path -LiteralPath $dest) { throw "Destination already exists; inspect and preserve it before copying: $dest" }
New-Item -ItemType Directory -Path $dest -Force | Out-Null
Copy-Item -Path (Join-Path $source '*') -Destination $dest -Recurse
Set-Location -LiteralPath $repo
git status --short
git fetch origin --prune
git rev-parse origin/main

# Launch Claude in project root using your normal Claude CLI configuration.
claude
```

Once in Claude, paste the complete contents of `tasks\Member-Wallet-Release (i)\00_MASTER_PROMPT_CLAUDE.md` as ONE prompt. If CLI supports file referencing, reference that file and instruct Claude to read all other bundled files. Do not use flags that implicitly grant destructive Git/SQL operations. GitHub auth, screenshots/Playwright and SQL environment need proper setup by Truc; these are not automatically installed by this assignment.

**Important:** Extracting the bundle is not itself a feature implementation. The agent must fetch latest main before work. No forced checkout, no automatic Flyway migration against shared DB, and no PR self-merge.
