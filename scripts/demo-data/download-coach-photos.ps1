# Sportify Center - download the six demo coach photos into the project (run once, then commit the images).
# Run again after a photo is changed here (it overwrites the files), then commit the images.
# Usage from the repository root:  powershell -ExecutionPolicy Bypass -File scripts\demo-data\download-coach-photos.ps1
# Photos: Unsplash License (free to use, no attribution required), cropped to 640 x 640 around the face. Demo only.
$ErrorActionPreference = 'Stop'
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
    Invoke-WebRequest -Uri $photos[$name] -OutFile $file -UseBasicParsing
    $size = [math]::Round((Get-Item $file).Length / 1KB)
    Write-Host ("Saved {0} ({1} KB)" -f $name, $size)
}
Write-Host 'Done: 6 photos in frontend\public\images\coaches'
