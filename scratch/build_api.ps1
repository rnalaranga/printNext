if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $candidate = Get-ChildItem -Path "C:\tools" -Filter "mvn.cmd" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) {
        $env:M2_HOME = $candidate.Directory.Parent.FullName
        $env:Path = "$($candidate.Directory.FullName);$env:Path"
    }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $candidate = Get-ChildItem -Path "C:\tools" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) {
        $env:JAVA_HOME = $candidate.Directory.Parent.FullName
        $env:Path = "$($candidate.Directory.FullName);$env:Path"
    }
}

mvn package -pl rr-secure-print-api -DskipTests
