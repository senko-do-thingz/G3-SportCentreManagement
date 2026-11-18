param(
    [Parameter(Mandatory = $true)]
    [string] $WebhookUrl
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$envFile = Join-Path $repoRoot 'backend/.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'backend/.env was not found. Copy backend/.env.example, then set PayOS values locally.'
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and $line -notmatch '^\s*#') {
        $value = $Matches[2]
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$Matches[1]] = $value
    }
}

if ($values['PAYOS_ENABLED'] -ne 'true') { throw 'Set PAYOS_ENABLED=true in backend/.env and restart the backend first.' }
$clientId = $values['PAYOS_CLIENT_ID']
$apiKey = $values['PAYOS_API_KEY']
if ([string]::IsNullOrWhiteSpace($clientId) -or [string]::IsNullOrWhiteSpace($apiKey)) {
    throw 'PAYOS_CLIENT_ID and PAYOS_API_KEY must be set in backend/.env.'
}

$webhookUri = $null
$validUri = [Uri]::TryCreate($WebhookUrl, [UriKind]::Absolute, [ref] $webhookUri)
$invalidUrl = (-not $validUri) -or ($webhookUri.Scheme -ne 'https') `
    -or ($webhookUri.AbsolutePath -ne '/api/v1/payments/payos/webhook') `
    -or ($webhookUri.Host -eq 'api.trycloudflare.com') `
    -or [bool]$webhookUri.Query -or [bool]$webhookUri.Fragment -or [bool]$webhookUri.UserInfo
if ($invalidUrl) {
    throw 'Use an HTTPS webhook URL ending exactly in /api/v1/payments/payos/webhook.'
}

$headers = @{ 'x-client-id' = $clientId; 'x-api-key' = $apiKey }
$body = @{ webhookUrl = $webhookUri.AbsoluteUri } | ConvertTo-Json -Compress
try {
    $result = Invoke-RestMethod -Method Post `
        -Uri 'https://api-merchant.payos.vn/confirm-webhook' `
        -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 20
} catch {
    $failure = $_
    $statusCode = $null
    $responseBody = $failure.ErrorDetails.Message
    try {
        if ($failure.Exception.Response) {
            $statusCode = [int]$failure.Exception.Response.StatusCode
            if ([string]::IsNullOrWhiteSpace($responseBody)) {
                $responseStream = $failure.Exception.Response.GetResponseStream()
                if ($responseStream) {
                    $reader = [System.IO.StreamReader]::new($responseStream)
                    try { $responseBody = $reader.ReadToEnd() } finally { $reader.Dispose() }
                }
            }
        }
    } catch { }

    $details = $null
    if (-not [string]::IsNullOrWhiteSpace($responseBody)) {
        try {
            $providerError = $responseBody | ConvertFrom-Json
            $parts = @()
            if ($providerError.code) { $parts += "code $($providerError.code)" }
            if ($providerError.desc) { $parts += [string]$providerError.desc }
            if ($parts.Count -gt 0) { $details = $parts -join ': ' }
        } catch { }
    }
    if ([string]::IsNullOrWhiteSpace($details)) {
        $details = $failure.Exception.Message
    }
    if ($details.Length -gt 300) { $details = $details.Substring(0, 300) }

    $statusText = if ($statusCode) { "HTTP $statusCode" } else { 'request error' }
    throw "PayOS confirm-webhook failed ($statusText): $details"
}

if ($result.code -ne '00' -or $result.data.webhookUrl -ne $webhookUri.AbsoluteUri) {
    $responseDetails = @()
    if ($result.code) { $responseDetails += "code $($result.code)" }
    if ($result.desc) { $responseDetails += [string]$result.desc }
    if ($result.data.webhookUrl -and $result.data.webhookUrl -ne $webhookUri.AbsoluteUri) {
        $responseDetails += 'PayOS returned a different webhook URL'
    }
    $details = if ($responseDetails.Count -gt 0) { $responseDetails -join ': ' } else { 'PayOS returned an incomplete response' }
    throw "PayOS did not confirm the configured webhook URL ($details)."
}
Write-Host 'PayOS confirmed the webhook endpoint. Its sample validation event is acknowledged without settling a payment.'
