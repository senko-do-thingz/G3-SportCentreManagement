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

$proxyPort = 8082
if (Test-LocalPort -Port $proxyPort) {
    throw "Port $proxyPort is already in use. Stop the existing local process before starting this tunnel."
}

$proxy = Start-Process -FilePath $node.Source `
    -ArgumentList @('scripts/payment/payos-webhook-proxy.mjs') `
    -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru

try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Milliseconds 250
        if ($proxy.HasExited) { throw 'The PayOS webhook proxy stopped before it became ready.' }
        if (Test-LocalPort -Port $proxyPort) { $ready = $true; break }
    }
    if (-not $ready) { throw 'The PayOS webhook proxy did not start.' }

    Write-Host 'Only POST /api/v1/payments/payos/webhook is forwarded to the backend.'
    Write-Host 'Cloudflare will create a temporary HTTPS URL. This script registers its PayOS webhook automatically.'
    Write-Host 'Keep this terminal open while testing. Press Ctrl+C to stop the tunnel and its local proxy.'

    $webhookRegistered = $false
    $registerScript = Join-Path $PSScriptRoot 'Register-PayOsWebhook.ps1'
    $scriptErrorActionPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 turns native stderr into PowerShell errors. cloudflared
        # writes normal INFO logs to stderr, so Stop would abort before the URL appears.
        $ErrorActionPreference = 'Continue'
        & $cloudflaredPath tunnel --url "http://127.0.0.1:$proxyPort" 2>&1 | ForEach-Object {
            $line = [string]$_
            Write-Host $line

            if (-not $webhookRegistered -and $line -match '(?i)https://(?<host>(?!api\.)[a-z0-9-]+\.trycloudflare\.com)(?=[/:?#\s]|$)') {
                $origin = "https://$($Matches['host'])"
                $webhookUrl = "$origin/api/v1/payments/payos/webhook"
                Write-Host "Registering PayOS webhook at $webhookUrl"

                $registered = $false
                for ($registrationAttempt = 1; $registrationAttempt -le 4; $registrationAttempt++) {
                    try {
                        & $registerScript -WebhookUrl $webhookUrl
                        $registered = $true
                        break
                    } catch {
                        if ($registrationAttempt -eq 4) { break }
                        Write-Warning 'PayOS has not confirmed the temporary URL yet; retrying in 3 seconds.'
                        Start-Sleep -Seconds 3
                    }
                }

                if (-not $registered) {
                    throw 'PayOS webhook registration failed after four attempts. Check the PayOS credentials and backend logs.'
                }

                $webhookRegistered = $true
            }
        }
        $cloudflaredExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $scriptErrorActionPreference
    }

    if (-not $webhookRegistered) {
        if ($cloudflaredExitCode -ne 0) {
            throw "cloudflared exited with code $cloudflaredExitCode before creating a public URL. Quick Tunnels need outbound HTTPS access to api.trycloudflare.com:443; a VPN/WARP, proxy, firewall, or network may be resetting the connection."
        }
        throw 'cloudflared exited before publishing a trycloudflare.com URL, so PayOS could not be configured.'
    }
    if ($cloudflaredExitCode -ne 0) {
        throw "cloudflared exited with code $cloudflaredExitCode. Restart the tunnel and register the new URL."
    }
} finally {
    if ($proxy -and -not $proxy.HasExited) {
        Stop-Process -Id $proxy.Id -Force -ErrorAction SilentlyContinue
    }
}
