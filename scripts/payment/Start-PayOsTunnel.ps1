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

$proxyPort = 8082
$tcp = [System.Net.Sockets.TcpClient]::new()
try {
    $tcp.Connect('127.0.0.1', $proxyPort)
    throw "Port $proxyPort is already in use. Stop the existing local process before starting this tunnel."
} catch [System.Net.Sockets.SocketException] {
    # The expected result means the proxy port is free.
} finally {
    $tcp.Dispose()
}

$proxy = Start-Process -FilePath $node.Source `
    -ArgumentList @('scripts/payment/payos-webhook-proxy.mjs') `
    -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru

try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Milliseconds 250
        $probe = [System.Net.Sockets.TcpClient]::new()
        try { $probe.Connect('127.0.0.1', $proxyPort); $ready = $true; break }
        catch [System.Net.Sockets.SocketException] { }
        finally { $probe.Dispose() }
    }
    if (-not $ready) { throw 'The PayOS webhook proxy did not start.' }

    Write-Host 'Only POST /api/v1/payments/payos/webhook is forwarded to the backend.'
    Write-Host 'Copy the https://*.trycloudflare.com URL shown below, then register the complete webhook URL in a second PowerShell window.'
    Write-Host "Register-PayOsWebhook.ps1 -WebhookUrl 'https://<quick-tunnel-host>/api/v1/payments/payos/webhook'"
    Write-Host 'Keep this terminal open while testing. Ctrl+C stops the tunnel and its local proxy.'
    & $cloudflaredPath tunnel --url "http://127.0.0.1:$proxyPort"
} finally {
    if ($proxy -and -not $proxy.HasExited) {
        Stop-Process -Id $proxy.Id -Force -ErrorAction SilentlyContinue
    }
}
