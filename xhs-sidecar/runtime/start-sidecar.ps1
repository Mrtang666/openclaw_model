$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$mainEnvPath = Join-Path $projectRoot ".env"
$sidecarEnvPath = Join-Path $PSScriptRoot "sidecar.env"

function Import-SelectedEnv {
    param(
        [string]$Path,
        [string[]]$AllowedNames
    )

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $separator = $trimmed.IndexOf("=")
        if ($separator -le 0) {
            continue
        }

        $name = $trimmed.Substring(0, $separator).Trim()
        if ($AllowedNames.Count -gt 0 -and $name -notin $AllowedNames) {
            continue
        }

        $value = $trimmed.Substring($separator + 1).Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

Import-SelectedEnv -Path $mainEnvPath -AllowedNames @(
    "XHS_COLLECTOR_API_KEY",
    "XHS_AUTHOR_HASH_KEY"
)
Import-SelectedEnv -Path $sidecarEnvPath -AllowedNames @()

$python = $env:SPIDER_XHS_PYTHON
if (-not (Test-Path -LiteralPath $python)) {
    throw "Sidecar Python interpreter does not exist: $python"
}
if ([string]::IsNullOrWhiteSpace($env:XHS_COOKIES)) {
    Write-Warning "XHS_COOKIES is empty. Health checks will work, but collection jobs will fail with AUTH_MISSING."
}

Set-Location (Join-Path $projectRoot "xhs-sidecar")
& $python -m xhs_sidecar.server
