[CmdletBinding()]
param(
    [int]$BackendPort = 8080,
    [int]$ProxyPort = 8081
)

$ErrorActionPreference = 'Stop'
$cloudflaredCommand = Get-Command cloudflared -ErrorAction SilentlyContinue
$cloudflaredPath = if ($cloudflaredCommand) { $cloudflaredCommand.Source } else { $null }
if (-not $cloudflaredPath) {
    $userInstall = Join-Path $env:USERPROFILE 'AppData\Local\Programs\cloudflared\cloudflared.exe'
    if (Test-Path -LiteralPath $userInstall) { $cloudflaredPath = $userInstall }
}
if (-not $cloudflaredPath) {
    throw 'cloudflared is not installed. Install the Windows client from https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/ and reopen PowerShell.'
}

$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) {
    throw 'Node.js is required to run the VietQR callback allowlist proxy.'
}

if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port $BackendPort -InformationLevel Quiet)) {
    throw "The backend is not listening on 127.0.0.1:$BackendPort. Start it first with mvn spring-boot:run from backend/."
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$proxyScript = Join-Path $PSScriptRoot 'vietqr-callback-proxy.mjs'
$previousBackendPort = $env:VIETQR_BACKEND_PORT
$previousProxyPort = $env:VIETQR_PROXY_PORT
$env:VIETQR_BACKEND_PORT = [string]$BackendPort
$env:VIETQR_PROXY_PORT = [string]$ProxyPort
$proxyProcess = $null

try {
    $proxyProcess = Start-Process -FilePath $node.Source `
        -ArgumentList @('"' + $proxyScript + '"') `
        -WorkingDirectory $projectRoot `
        -WindowStyle Hidden `
        -PassThru

    $proxyReady = $false
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
        if ($proxyProcess.HasExited) {
            throw 'The callback allowlist proxy exited before it started.'
        }
        if (Test-NetConnection -ComputerName 127.0.0.1 -Port $ProxyPort -InformationLevel Quiet) {
            $proxyReady = $true
            break
        }
        Start-Sleep -Milliseconds 200
    }
    if (-not $proxyReady) {
        throw "The callback allowlist proxy did not start on 127.0.0.1:$ProxyPort."
    }

    Write-Host 'Starting a temporary Cloudflare Quick Tunnel.'
    Write-Host 'Only VietQR Get Token and Transaction Sync POST routes are forwarded.'
    Write-Host 'Copy the https://*.trycloudflare.com origin from the cloudflared output into VietQR; keep this window open.'
    Write-Host 'Press Ctrl+C here to stop the tunnel and its local proxy.'
    & $cloudflaredPath tunnel --url "http://127.0.0.1:$ProxyPort"
}
finally {
    if ($proxyProcess -and -not $proxyProcess.HasExited) {
        Stop-Process -Id $proxyProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($null -eq $previousBackendPort) { Remove-Item Env:VIETQR_BACKEND_PORT -ErrorAction SilentlyContinue }
    else { $env:VIETQR_BACKEND_PORT = $previousBackendPort }
    if ($null -eq $previousProxyPort) { Remove-Item Env:VIETQR_PROXY_PORT -ErrorAction SilentlyContinue }
    else { $env:VIETQR_PROXY_PORT = $previousProxyPort }
}
