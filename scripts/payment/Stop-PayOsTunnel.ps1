$ErrorActionPreference = 'Stop'

$stateFiles = @(Get-ChildItem -LiteralPath $env:TEMP -Filter 'sportify-payos-tunnel-*.json' -File -ErrorAction SilentlyContinue)
if ($stateFiles.Count -eq 0) {
    Write-Host 'No Sportify PayOS tunnel or webhook proxy process is running.'
    exit 0
}

$stopped = 0
foreach ($stateFile in $stateFiles) {
    try {
        $state = Get-Content -LiteralPath $stateFile.FullName -Raw | ConvertFrom-Json
        $proxyPort = [int]$state.ProxyPort
        $proxyProcessId = [int]$state.ProxyProcessId
        if ($proxyPort -le 0 -or $proxyPort -gt 65535 -or $proxyProcessId -le 0) {
            throw 'Invalid tunnel state.'
        }

        # Match only this run's random local port, so other Cloudflare tunnels are untouched.
        $tunnelPattern = '(?i)\btunnel\s+--url\s+.*127\.0\.0\.1:' + $proxyPort + '(?!\d)'
        $cloudflared = @(Get-CimInstance Win32_Process -Filter "name='cloudflared.exe'" | Where-Object {
            $_.CommandLine -match $tunnelPattern
        })
        foreach ($process in $cloudflared) {
            Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
            $stopped++
        }

        $proxyProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$proxyProcessId" -ErrorAction SilentlyContinue
        if ($proxyProcess -and $proxyProcess.Name -eq 'node.exe' -and $proxyProcess.CommandLine -match '(?i)scripts[\\/]payment[\\/]payos-webhook-proxy\.mjs') {
            Stop-Process -Id $proxyProcessId -Force -ErrorAction SilentlyContinue
            $stopped++
        }
    } finally {
        Remove-Item -LiteralPath $stateFile.FullName -Force -ErrorAction SilentlyContinue
    }
}

if ($stopped -gt 0) {
    Write-Host 'Stopped the Sportify PayOS tunnel and webhook proxy. The temporary PayOS webhook URL is no longer active.'
} else {
    Write-Host 'No processes from the recorded Sportify PayOS tunnel run were found; cleared its stale state file.'
}
