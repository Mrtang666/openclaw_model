param(
    [switch]$SidecarOnly
)

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$sidecarRoot = Join-Path $projectRoot "xhs-sidecar"
$composeFile = Join-Path $sidecarRoot "compose.yaml"
$sidecarHealth = "http://127.0.0.1:18081/health"

function Test-SidecarHealth {
    try {
        $response = Invoke-RestMethod -Uri $sidecarHealth -TimeoutSec 2
        return $response.status -eq "UP"
    } catch {
        return $false
    }
}

function Test-DockerEngine {
    docker info *> $null
    return $LASTEXITCODE -eq 0
}

function Start-DockerEngine {
    if (Test-DockerEngine) {
        return
    }
    $desktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path -LiteralPath $desktop)) {
        throw "Docker Desktop is not installed or its executable was not found."
    }
    Write-Host "Starting Docker Desktop..."
    Start-Process -FilePath $desktop -WindowStyle Hidden | Out-Null
    foreach ($attempt in 1..45) {
        if (Test-DockerEngine) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Docker Desktop did not become ready within 90 seconds."
}

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Sidecar Compose configuration is missing: $composeFile"
}

if (-not (Test-SidecarHealth)) {
    Start-DockerEngine
    Write-Host "Starting Xiaohongshu collection Sidecar with Docker Compose..."
    docker compose --project-directory $sidecarRoot -f $composeFile up -d --no-build
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed to start the Sidecar."
    }
    $ready = $false
    foreach ($attempt in 1..40) {
        if (Test-SidecarHealth) {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw "Sidecar failed to become healthy. Run docker compose logs sidecar in xhs-sidecar."
    }
}

if ($SidecarOnly) {
    Write-Host "Sidecar is UP at http://127.0.0.1:18081."
    exit 0
}

Write-Host "Sidecar is UP. Starting OpenClaw on port 8080..."
Set-Location $projectRoot
& mvn spring-boot:run
