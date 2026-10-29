$candidate = Get-ChildItem -Path "C:\tools" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if ($candidate) {
    & $candidate.FullName "scratch\TestSmtpJava.java"
} else {
    & java "scratch\TestSmtpJava.java"
}
