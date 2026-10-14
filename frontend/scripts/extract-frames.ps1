# Extract an evenly spaced WebP frame sequence from a video for the canvas scrubber.
#
#   ./scripts/extract-frames.ps1 -Video video.mp4 [-Count 150] [-Width 1920]
#
# Output: public/frames/frame_0001.webp ... frame_NNNN.webp
# Requires ffmpeg + ffprobe on PATH (e.g. `winget install Gyan.FFmpeg`).
param(
  [Parameter(Mandatory = $true)][string]$Video,
  [int]$Count = 150,
  [int]$Width = 1920
)
$ErrorActionPreference = "Stop"

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) { throw "ffmpeg not found on PATH" }
if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) { throw "ffprobe not found on PATH" }

$outDir = Join-Path (Split-Path $PSScriptRoot -Parent) "public/frames"
New-Item -ItemType Directory -Force $outDir | Out-Null
Get-ChildItem $outDir -Filter "frame_*.webp" | Remove-Item -Force

# Duration in seconds -> fps that yields exactly $Count frames over the clip.
$duration = [double]::Parse((& ffprobe -v error -show_entries format=duration -of csv=p=0 $Video), [cultureinfo]::InvariantCulture)
$fps = ($Count / $duration).ToString("0.000000", [cultureinfo]::InvariantCulture)

& ffmpeg -hide_banner -loglevel error -i $Video `
  -vf "fps=$fps,scale=${Width}:-2:flags=lanczos" `
  -frames:v $Count `
  -c:v libwebp -quality 60 -compression_level 6 -preset photo `
  (Join-Path $outDir "frame_%04d.webp")
if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed" }

$n = (Get-ChildItem $outDir -Filter "frame_*.webp").Count
Write-Host "Wrote $n frames to $outDir"
Write-Host "Set scene.frames.enabled = true and scene.frames.count = $n in src/config/site.config.ts"
