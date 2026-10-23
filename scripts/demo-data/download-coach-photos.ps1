# Sportify Center - download the six demo coach photos into the project (run once, then commit the images).
# Existing photos are always preserved. Explicitly approve demo assets before downloading missing files.
# Usage: powershell -File scripts\demo-data\download-coach-photos.ps1 -ApprovedDemoAssets
# Photos: Unsplash License (free to use, no attribution required), cropped to 640 x 640 around the face. Demo only.
param([switch]$ApprovedDemoAssets)
$ErrorActionPreference = 'Stop'
if (-not $ApprovedDemoAssets) { throw 'Explicit approval is required for demo asset downloads.' }
$target = Join-Path $PSScriptRoot '..\..\frontend\public\images\coaches'
New-Item -ItemType Directory -Force -Path $target | Out-Null
$photos = [ordered]@{
    'khoa-nguyen.jpg' = 'https://images.unsplash.com/photo-1681097561932-36d0df02b379?auto=format&fm=jpg&fit=crop&crop=faces&w=640&h=640&q=80'
    'ha-tran.jpg' = 'https://images.unsplash.com/photo-1683848644087-c3cb69b77d4f?auto=format&fm=jpg&fit=crop&crop=faces&w=640&h=640&q=80'
    'bao-le.jpg' = 'https://images.unsplash.com/photo-1676378385638-135ca6ef4619?auto=format&fm=jpg&fit=crop&crop=faces&w=640&h=640&q=80'
    'long-pham.jpg' = 'https://images.unsplash.com/photo-1750698545009-679820502908?auto=format&fm=jpg&fit=crop&crop=faces&w=640&h=640&q=80'
    'tram-vo.jpg' = 'https://images.unsplash.com/photo-1744725845508-054bf3b6d80c?auto=format&fm=jpg&fit=crop&crop=faces&w=640&h=640&q=80'
    'huy-dang.jpg' = 'https://images.unsplash.com/photo-1612338458140-298a970a9ba5?auto=format&fm=jpg&fit=crop&crop=faces&w=640&h=640&q=80'
}
foreach ($name in $photos.Keys) {
    $file = Join-Path $target $name
    if (Test-Path -LiteralPath $file) {
        Write-Host ("Preserved existing photo: {0}" -f $name)
        continue
    }
    $download = Invoke-WebRequest -Uri $photos[$name] -UseBasicParsing
    # CreateNew also refuses a file created by another process after the existence check.
    $output = [IO.File]::Open($file, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $download.RawContentStream.CopyTo($output) } finally { $output.Dispose() }
    $size = [math]::Round((Get-Item $file).Length / 1KB)
    Write-Host ("Saved {0} ({1} KB)" -f $name, $size)
}
Write-Host 'Done: 6 photos in frontend\public\images\coaches'
