param([Parameter(Mandatory = $true)][string]$SourceSheet)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectDirectory = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectDirectory 'src/main/resources/assets/skyglint/textures/gui/sprites/ranks'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$source = [System.Drawing.Bitmap]::new((Resolve-Path -LiteralPath $SourceSheet).Path)
try {
    if ($source.Width % 3 -ne 0 -or $source.Height % 2 -ne 0 -or $source.Width / 3 -ne $source.Height / 2) {
        throw 'Expected a three-column, two-row sheet of equal square cells.'
    }
    $cell = $source.Width / 3
    $names = @('d', 'c', 'b', 'a', 's', 's_plus')
    for ($index = 0; $index -lt $names.Length; $index++) {
        # Technical asset export only: nearest grid samples, binary alpha, no runtime resampling.
        # Original artwork and creative edits are produced with image generation; source stays intact.
        $sprite = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            for ($y = 0; $y -lt 32; $y++) {
                for ($x = 0; $x -lt 32; $x++) {
                    $sx = [int]([math]::Floor(($index % 3) * $cell + ($x + 0.5) * $cell / 32))
                    $sy = [int]([math]::Floor([math]::Floor($index / 3) * $cell + ($y + 0.5) * $cell / 32))
                    $pixel = $source.GetPixel($sx, $sy)
                    $color = if ($pixel.A -ge 128) { [System.Drawing.Color]::FromArgb(255, $pixel.R, $pixel.G, $pixel.B) }
                        else { [System.Drawing.Color]::FromArgb(0, 0, 0, 0) }
                    $sprite.SetPixel($x, $y, $color)
                }
            }
            $path = Join-Path $outputDirectory ($names[$index] + '.png')
            $sprite.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output "$($names[$index]): 32 x 32, $((Get-Item -LiteralPath $path).Length) bytes"
        } finally { $sprite.Dispose() }
    }
} finally { $source.Dispose() }
