#!/usr/bin/env pwsh
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $Root 'mach') @args
exit $LASTEXITCODE
