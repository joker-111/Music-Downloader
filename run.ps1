$env:JAVA_HOME = Join-Path $PSScriptRoot ".tools\jdk-17.0.19+10"
$env:MAVEN_HOME = Join-Path $PSScriptRoot ".tools\apache-maven-3.9.16"
$env:Path = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"

& "$env:MAVEN_HOME\bin\mvn.cmd" spring-boot:run
