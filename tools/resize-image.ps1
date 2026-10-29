# Resizes an image to at most $Width pixels wide and saves it as JPEG (quality 82).
param([Parameter(Mandatory)][string]$In, [Parameter(Mandatory)][string]$Out, [int]$Width = 800)
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile((Resolve-Path $In))
$w = [Math]::Min($Width, $img.Width)
$h = [int]($img.Height * $w / $img.Width)
$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($img, 0, 0, $w, $h)
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 82L
$bmp.Save($Out, $codec, $params)
$g.Dispose(); $bmp.Dispose(); $img.Dispose()
