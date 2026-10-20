# Build script with auto-detected tools
$ErrorActionPreference = "Stop"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $candidate = Get-ChildItem -Path "C:\tools" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) {
        $env:JAVA_HOME = $candidate.Directory.Parent.FullName
        $env:Path = "$($candidate.Directory.FullName);$env:Path"
    }
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $candidate = Get-ChildItem -Path "C:\tools" -Filter "mvn.cmd" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) {
        $env:M2_HOME = $candidate.Directory.Parent.FullName
        $env:Path = "$($candidate.Directory.FullName);$env:Path"
    }
}

Write-Host "Running Maven package..."
& mvn package -DskipTests
