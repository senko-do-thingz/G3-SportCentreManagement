$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$node = Get-Command node -ErrorAction SilentlyContinue
$cloudflaredCommand = Get-Command cloudflared -ErrorAction SilentlyContinue
$cloudflaredPath = if ($cloudflaredCommand) { $cloudflaredCommand.Source } else { $null }
if (-not $cloudflaredPath) {
    $userInstall = Join-Path $env:USERPROFILE 'AppData\Local\Programs\cloudflared\cloudflared.exe'
    if (Test-Path -LiteralPath $userInstall) { $cloudflaredPath = $userInstall }
}
if (-not $node) { throw 'Node.js was not found on PATH. Install Node.js before starting the PayOS webhook tunnel.' }
if (-not $cloudflaredPath) { throw 'cloudflared was not found. Install Cloudflare Tunnel before starting the PayOS webhook tunnel.' }

$envFile = Join-Path $repoRoot 'backend/.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'backend/.env was not found. Copy backend/.env.example and set the PayOS values before starting the tunnel.'
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

if (-not [string]::Equals($values['PAYOS_ENABLED'], 'true', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Set PAYOS_ENABLED=true in backend/.env and restart the backend before starting the tunnel.'
}
if ([string]::IsNullOrWhiteSpace($values['PAYOS_CLIENT_ID']) -or [string]::IsNullOrWhiteSpace($values['PAYOS_API_KEY'])) {
    throw 'PAYOS_CLIENT_ID and PAYOS_API_KEY must be set in backend/.env before starting the tunnel.'
}

function Test-LocalPort {
    param([int] $Port)

    $probe = [System.Net.Sockets.TcpClient]::new()
    try {
        $probe.Connect('127.0.0.1', $Port)
        return $true
    } catch [System.Net.Sockets.SocketException] {
        return $false
    } finally {
        $probe.Dispose()
    }
}

$backendPort = 8080
if (-not (Test-LocalPort -Port $backendPort)) {
    throw 'The backend is not listening on port 8080. Start the backend, then run this script again.'
}

$proxyStdoutLog = Join-Path $env:TEMP "sportify-payos-proxy-$PID.log"
$proxyStderrLog = Join-Path $env:TEMP "sportify-payos-proxy-$PID-error.log"
$proxy = Start-Process -FilePath $node.Source `
    -ArgumentList @('scripts/payment/payos-webhook-proxy.mjs') `
    -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $proxyStdoutLog -RedirectStandardError $proxyStderrLog

try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Milliseconds 250
        if ($proxy.HasExited) { throw 'The PayOS webhook proxy stopped before it became ready.' }
        if (Test-Path -LiteralPath $proxyStdoutLog) {
            $proxyOutput = Get-Content -LiteralPath $proxyStdoutLog -Raw -ErrorAction SilentlyContinue
            if ($proxyOutput -match 'PayOS webhook proxy listening on http://127\.0\.0\.1:(?<port>\d+)/api/v1/payments/payos/webhook') {
                $proxyPort = [int]$Matches['port']
                $ready = $true
                break
            }
        }
    }
    if (-not $ready) { throw 'The PayOS webhook proxy did not start.' }

    $tunnelStatePath = Join-Path $env:TEMP "sportify-payos-tunnel-$PID.json"
    [ordered]@{
        ProxyPort = $proxyPort
        ProxyProcessId = $proxy.Id
        StartedUtc = [DateTime]::UtcNow.ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath $tunnelStatePath -Encoding UTF8

    Write-Host "Local webhook proxy selected available port $proxyPort."
    Write-Host 'Only POST /api/v1/payments/payos/webhook is forwarded to the backend.'
    Write-Host 'Cloudflare will create a temporary HTTPS URL. This script registers its PayOS webhook automatically.'
    Write-Host 'Keep this terminal open while testing. Press Ctrl+C to stop the tunnel and its local proxy.'

    $registerScript = Join-Path $PSScriptRoot 'Register-PayOsWebhook.ps1'
    $maxTunnelAttempts = 3
    for ($tunnelAttempt = 1; $tunnelAttempt -le $maxTunnelAttempts; $tunnelAttempt++) {
        $webhookRegistered = $false
        $webhookRegistrationAttempted = $false
        $tunnelUrlCreated = $false
        $tunnelConnectionReady = $false
        $scriptErrorActionPreference = $ErrorActionPreference
        try {
            # Windows PowerShell 5.1 turns native stderr into PowerShell errors. cloudflared
            # writes normal INFO logs to stderr, so Stop would abort before the URL appears.
            $ErrorActionPreference = 'Continue'
            & $cloudflaredPath tunnel --url "http://127.0.0.1:$proxyPort" 2>&1 | ForEach-Object {
            $line = [string]$_
            Write-Host $line

            if ($line -match '(?i)Registered tunnel connection') {
                $tunnelConnectionReady = $true
            }

            if (-not $tunnelUrlCreated -and $line -match '(?i)https://(?<host>(?!api\.)[a-z0-9-]+\.trycloudflare\.com)(?=[/:?#\s]|$)') {
                $tunnelUrlCreated = $true
                $origin = "https://$($Matches['host'])"
                $webhookUrl = "$origin/api/v1/payments/payos/webhook"
                Write-Host "Tunnel URL created: $origin"
                Write-Host 'Waiting for cloudflared to establish its tunnel connection before registering the PayOS webhook.'
            }

            if (-not $webhookRegistrationAttempted -and $tunnelUrlCreated -and $tunnelConnectionReady) {
                $webhookRegistrationAttempted = $true
                Write-Host 'Cloudflare tunnel connection is ready. Waiting 5 seconds for the public URL to become reachable.'
                Start-Sleep -Seconds 5
                Write-Host "Registering PayOS webhook at $webhookUrl"

                $registered = $false
                $maxRegistrationAttempts = 10
                for ($registrationAttempt = 1; $registrationAttempt -le $maxRegistrationAttempts; $registrationAttempt++) {
                    try {
                        & $registerScript -WebhookUrl $webhookUrl
                        $registered = $true
                        break
                    } catch {
                        $registrationError = $_.Exception.Message
                        # A 4xx URL/auth rejection is deterministic; repeating confirm-webhook
                        # cannot repair it. Network and provider errors still get a warm-up retry.
                        if ($registrationError -match '\(HTTP 4\d\d\)|PayOS did not confirm the configured webhook URL') { break }
                        if ($registrationAttempt -eq $maxRegistrationAttempts) { break }
                        Write-Warning "PayOS webhook confirmation attempt $registrationAttempt/$maxRegistrationAttempts failed: $registrationError. Retrying in 5 seconds."
                        Start-Sleep -Seconds 5
                    }
                }

                if (-not $registered) {
                    Write-Warning "PayOS webhook registration failed. The tunnel will stay running so you can inspect the callback and retry. Last error: $registrationError"
                    Start-Sleep -Milliseconds 300
                    $proxyDiagnostics = @()
                    foreach ($logPath in @($proxyStdoutLog, $proxyStderrLog)) {
                        if (Test-Path -LiteralPath $logPath) {
                            $proxyDiagnostics += Get-Content -LiteralPath $logPath -Tail 20
                        }
                    }
                    if ($proxyDiagnostics.Count -gt 0) {
                        Write-Host 'Local callback proxy diagnostics (no payment data or credentials):'
                        $proxyDiagnostics | ForEach-Object { Write-Host $_ }
                    } else {
                        Write-Host 'No callback reached the local proxy during PayOS validation.'
                    }
                    Write-Host 'After correcting the callback or PayOS channel settings, run this in a second PowerShell window:'
                    Write-Host "powershell -NoProfile -ExecutionPolicy Bypass -File `"$registerScript`" -WebhookUrl '$webhookUrl'"
                } else {
                    $webhookRegistered = $true
                }
            }
            }
            $cloudflaredExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $scriptErrorActionPreference
        }

        if ($tunnelUrlCreated -or $cloudflaredExitCode -eq 0) { break }
        if ($tunnelAttempt -lt $maxTunnelAttempts) {
            Write-Warning "Quick Tunnel startup attempt $tunnelAttempt/$maxTunnelAttempts failed before producing a public URL (exit $cloudflaredExitCode). Retrying in 4 seconds."
            Start-Sleep -Seconds 4
        }
    }

    if (-not $tunnelUrlCreated) {
        if ($cloudflaredExitCode -ne 0) {
            throw "cloudflared exited with code $cloudflaredExitCode before creating a public URL. Quick Tunnels need outbound HTTPS access to api.trycloudflare.com:443; a VPN/WARP, proxy, firewall, or network may be resetting the connection."
        }
        throw 'cloudflared exited before publishing a trycloudflare.com URL, so PayOS could not be configured.'
    }
    if (-not $webhookRegistered) {
        Write-Warning 'The tunnel stopped before PayOS webhook confirmation succeeded. Its temporary URL is no longer active.'
    }
    if ($cloudflaredExitCode -ne 0) {
        throw "cloudflared exited with code $cloudflaredExitCode. Restart the tunnel and register the new URL."
    }
} finally {
    if ($proxy -and -not $proxy.HasExited) {
        Stop-Process -Id $proxy.Id -Force -ErrorAction SilentlyContinue
    }
    if ($tunnelStatePath -and (Test-Path -LiteralPath $tunnelStatePath)) {
        Remove-Item -LiteralPath $tunnelStatePath -Force -ErrorAction SilentlyContinue
    }
}
