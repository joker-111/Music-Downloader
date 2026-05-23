$LocalJava = Join-Path $PSScriptRoot ".tools\jdk-17.0.19+10"
$LocalMaven = Join-Path $PSScriptRoot ".tools\apache-maven-3.9.16"

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
$AppJarName = Split-Path $AppJar -Leaf

Get-CimInstance Win32_Process -Filter "name = 'java.exe'" |
    Where-Object { $_.CommandLine -and $_.CommandLine.Contains($AppJarName) } |
    ForEach-Object {
        Write-Host "Stopping existing music downloader process $($_.ProcessId)..."
        Stop-Process -Id $_.ProcessId -Force
    }

mvn -DskipTests clean package
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

java -jar $AppJar
