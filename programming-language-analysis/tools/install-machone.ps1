param(
    [ValidateSet('user','global')]
    [string]$Mode = 'user'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "JDK 21+ não encontrado no PATH. Instale um JDK completo antes de instalar MachOne."
}

$JavaVersionRaw = & java -version 2>&1 | Select-Object -First 1
if ($JavaVersionRaw -notmatch 'version\s+"?([0-9]+)') {
    throw "Não foi possível detectar a versão do Java."
}
$JavaMajor = [int]$Matches[1]
if ($JavaMajor -lt 21) {
    throw "MachOne requer JDK 21 ou superior. O Java atual é: $JavaVersionRaw"
}

& (Join-Path $RepoRoot 'gradlew.bat') :cli:installDist --console=plain | Out-Null

if ($Mode -eq 'user') {
    $TargetDir = Join-Path $HOME '.local\bin'
    $InstallRoot = Join-Path $HOME '.local\share\machone'
} else {
    $TargetDir = 'C:\Program Files\MachOne\bin'
    $InstallRoot = 'C:\Program Files\MachOne'
}

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null
if (Test-Path $InstallRoot) { Remove-Item $InstallRoot -Recurse -Force }
Copy-Item (Join-Path $RepoRoot 'cli\build\install\mach') $InstallRoot -Recurse -Force
Copy-Item (Join-Path $InstallRoot 'bin\mach.bat') (Join-Path $TargetDir 'machone.cmd') -Force
Copy-Item (Join-Path $InstallRoot 'bin\mach.bat') (Join-Path $TargetDir 'mach.cmd') -Force

Write-Host "MachOne instalado em: $InstallRoot"
Write-Host "Use: machone --version"
Write-Host "Use: machone run caminho\do\programa.mach"

$UserPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if (-not (($UserPath -split ';') -contains $TargetDir)) {
    Write-Host "Adicione ao PATH do usuário: $TargetDir"
}
