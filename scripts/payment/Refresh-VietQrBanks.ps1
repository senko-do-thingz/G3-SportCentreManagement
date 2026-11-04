param([string]$OutputPath = (Join-Path $PSScriptRoot '../../backend/src/main/resources/payment/vietqr-banks.json'))
$ErrorActionPreference = 'Stop'
$source = 'https://api.vietqr.io/v2/banks'
$response = Invoke-RestMethod -Uri $source -Method Get -TimeoutSec 30
if ($response.code -ne '00' -or -not $response.data) { throw 'The official bank directory was not returned' }
$banks = @($response.data | ForEach-Object {
    [ordered]@{ bin = [string]$_.bin; code = [string]$_.code; shortName = [string]$_.shortName; transferSupported = [int]$_.transferSupported }
})
$snapshot = [ordered]@{ source = $source; capturedAt = [DateTime]::UtcNow.ToString('o'); data = $banks }
$json = $snapshot | ConvertTo-Json -Depth 8
$ascii = [regex]::Replace($json, '[^\x00-\x7F]', { param($match) '\u{0:x4}' -f [int][char]$match.Value })
$target = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
[IO.File]::WriteAllText($target, $ascii, [Text.Encoding]::ASCII)
Write-Output "Saved $($banks.Count) bank directory entries from the official public API. No payment was created or confirmed."
