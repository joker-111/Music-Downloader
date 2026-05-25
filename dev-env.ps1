$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$LocalJava = Join-Path $ProjectRoot ".tools\jdk-17.0.19+10"
$LocalMaven = Join-Path $ProjectRoot ".tools\apache-maven-3.9.16"

function Test-JavaHome {
    param([string]$Path)

    return -not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path (Join-Path $Path "bin\java.exe"))
}

function Test-MavenHome {
    param([string]$Path)

    return -not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path (Join-Path $Path "bin\mvn.cmd"))
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

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "MAVEN_HOME=$env:MAVEN_HOME"
