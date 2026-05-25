$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom

$LocalJava = Join-Path $PSScriptRoot ".tools\jdk-17.0.19+10"
$LocalMaven = Join-Path $PSScriptRoot ".tools\apache-maven-3.9.16"
$LocalEnvFile = Join-Path $PSScriptRoot ".env"

function Import-LocalEnvFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    Get-Content -Path $Path | ForEach-Object {
        $Line = $_.Trim()
        if (-not $Line -or $Line.StartsWith("#") -or -not $Line.Contains("=")) {
            return
        }

        $Parts = $Line.Split("=", 2)
        $Name = $Parts[0].Trim()
        $Value = $Parts[1].Trim().Trim('"').Trim("'")
        if ($Name) {
            [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
        }
    }
}

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

function Test-JavaHome {
    param([string]$Path)

    return -not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path (Join-Path $Path "bin\java.exe"))
}

function Test-MavenHome {
    param([string]$Path)

    return -not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path (Join-Path $Path "bin\mvn.cmd"))
}

Import-LocalEnvFile $LocalEnvFile
Use-UserEnvironmentVariable "JAVA_HOME"
Use-UserEnvironmentVariable "MAVEN_HOME"
@(
    "MUSIC_GATEWAY_BASE_URL",
    "MUSIC_GATEWAY_SEARCH_PATH",
    "MUSIC_GATEWAY_SONG_PATH",
    "MUSIC_GATEWAY_DOWNLOAD_PATH",
    "MUSIC_GATEWAY_LYRIC_PATH",
    "MUSIC_GATEWAY_ARTIST_PATH",
    "MUSIC_GATEWAY_PLAYLIST_PATH",
    "MUSIC_GATEWAY_ALBUM_PATH",
    "MUSIC_GATEWAY_API_KEY"
) | ForEach-Object {
    Use-UserEnvironmentVariable $_
}

if (-not (Test-JavaHome $env:JAVA_HOME) -and (Test-Path $LocalJava)) {
    $env:JAVA_HOME = $LocalJava
}

if (-not (Test-MavenHome $env:MAVEN_HOME) -and (Test-Path $LocalMaven)) {
    $env:MAVEN_HOME = $LocalMaven
}

if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

if ($env:MAVEN_HOME) {
    $env:Path = "$env:MAVEN_HOME\bin;$env:Path"
}

$MavenCommand = "mvn"
if ($env:MAVEN_HOME) {
    $MavenCommand = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
}

$AppJar = Join-Path $PSScriptRoot "target\music-downloader-0.0.1-SNAPSHOT.jar"
$ServerPort = 8080

function Test-LocalPortOpen {
    param([int]$Port)

    $Client = [System.Net.Sockets.TcpClient]::new()
    try {
        $Connect = $Client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $Connect.AsyncWaitHandle.WaitOne(250)) {
            return $false
        }
        $Client.EndConnect($Connect)
        return $true
    } catch {
        return $false
    } finally {
        $Client.Close()
    }
}

function Find-FreePort {
    param([int]$StartPort)

    for ($Port = $StartPort; $Port -le ($StartPort + 10); $Port++) {
        if (-not (Test-LocalPortOpen $Port)) {
            return $Port
        }
    }
    return $null
}

if (Test-LocalPortOpen 8080) {
    $ServerPort = Find-FreePort 8081
    if (-not $ServerPort) {
        Write-Error "Ports 8080-8091 are already in use. Stop one of those services or set a free port before starting."
        exit 1
    }
    Write-Warning "Port 8080 is already in use. Keeping the existing process running and starting on http://localhost:$ServerPort instead."
}

if (-not $env:MUSIC_GATEWAY_API_KEY) {
    Write-Warning "MUSIC_GATEWAY_API_KEY is not configured. Gateway requests may return the login page."
}

& $MavenCommand clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Starting music downloader on http://localhost:$ServerPort"
java -jar $AppJar "--server.port=$ServerPort"
exit $LASTEXITCODE
