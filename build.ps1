$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom

$LocalJava = Join-Path $PSScriptRoot ".tools\jdk-17.0.19+10"
$LocalMaven = Join-Path $PSScriptRoot ".tools\apache-maven-3.9.16"

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

$MavenCommand = "mvn"
if ($env:MAVEN_HOME) {
    $env:Path = "$env:MAVEN_HOME\bin;$env:Path"
    $MavenCommand = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
}

& $MavenCommand clean package
exit $LASTEXITCODE
