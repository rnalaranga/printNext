$lines = Get-Content 'C:\Users\Administrator\.gemini\antigravity\brain\18ae7954-1e9b-4c69-9965-e5c722a0a1e2\.system_generated\logs\transcript.jsonl'
foreach ($line in $lines) {
    if ($line -match 'scheduleResetToEwb|targetScreen|revert|schedule') {
        [regex]::Matches($line, '.{0,100}(scheduleResetToEwb|revert|executor|DefaultScreen).{0,100}') | ForEach-Object { Write-Host $_.Value }
    }
}
