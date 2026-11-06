$lines = Get-Content 'C:\Users\Administrator\.gemini\antigravity\brain\fdce555f-28e2-4d60-9721-1288c51b42ea\.system_generated\tasks\task-1584.log'
foreach ($line in $lines) {
    if ($line -match '15:47:4[0-9]|15:47:5[0-9]') {
        Write-Output $line
    }
}
