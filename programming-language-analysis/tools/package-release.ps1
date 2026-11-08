param(
    [ValidateSet('portable','msi')]
    [string]$Mode = 'portable'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$DistDir = Join-Path $Root 'dist'
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

& (Join-Path $Root 'gradlew.bat') :cli:installDist --console=plain | Out-Null

$InstallRoot = Join-Path $DistDir 'machone-windows-x64'
if (Test-Path $InstallRoot) { Remove-Item $InstallRoot -Recurse -Force }
Copy-Item (Join-Path $Root 'cli\build\install\mach') $InstallRoot -Recurse -Force
Copy-Item (Join-Path $Root 'machone.bat') (Join-Path $InstallRoot 'bin\machone.bat') -Force
Copy-Item (Join-Path $Root 'machone.ps1') (Join-Path $InstallRoot 'bin\machone.ps1') -Force

Compress-Archive -Path (Join-Path $InstallRoot '*') -DestinationPath (Join-Path $DistDir 'machone-windows-x64.zip') -Force

if ($Mode -eq 'msi') {
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
        Write-Warning 'WiX (candle.exe) não está no PATH; somente o pacote zip portátil foi gerado.'
        return
    }
    $Wix = Join-Path $Root 'tools\machone.wxs'
    $WixObj = Join-Path $DistDir 'machone.wixobj'
    $Msi = Join-Path $DistDir 'machone-windows-x64.msi'
    & candle.exe $Wix -out $WixObj
    & light.exe $WixObj -out $Msi
    Write-Host "MSI gerado em: $Msi"
}

Write-Host "Pacote Windows gerado em: $DistDir"
