$LocalJava = Join-Path $PSScriptRoot ".tools\jdk-17.0.19+10"
$LocalMaven = Join-Path $PSScriptRoot ".tools\apache-maven-3.9.16"

function Use-UserEnvironmentVariable {
    param([string]$Name)

    $CurrentValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (-not [string]::IsNullOrWhiteSpace($CurrentValue)) {
        return
    }

    $UserValue = [Environment]::GetEnvironmentVariable($Name, "User")
    if (-not [string]::IsNullOrWhiteSpace($UserValue)) {
        [Environment]::SetEnvironmentVariable($Name, $UserValue, "Process")
    }
}

Use-UserEnvironmentVariable "JAVA_HOME"
Use-UserEnvironmentVariable "MAVEN_HOME"
Use-UserEnvironmentVariable "MUSIC_GATEWAY_API_KEY"

if (-not $env:JAVA_HOME -and (Test-Path $LocalJava)) {
    $env:JAVA_HOME = $LocalJava
}

if (-not $env:MAVEN_HOME -and (Test-Path $LocalMaven)) {
    $env:MAVEN_HOME = $LocalMaven
}

if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

if ($env:MAVEN_HOME) {
    $env:Path = "$env:MAVEN_HOME\bin;$env:Path"
}

$AppJar = Join-Path $PSScriptRoot "target\music-downloader-0.0.1-SNAPSHOT.jar"

try {
    Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        Where-Object { $_ -and $_ -ne $PID } |
        ForEach-Object {
            Write-Host "Stopping existing process $($_) on port 8080..."
            Stop-Process -Id $_ -Force
        }
} catch {
    Write-Warning "Could not check port 8080 before startup: $($_.Exception.Message)"
}

if (-not $env:MUSIC_GATEWAY_API_KEY) {
    Write-Warning "MUSIC_GATEWAY_API_KEY is not configured. Gateway requests may return the login page."
}

mvn -DskipTests package
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

java -jar $AppJar
