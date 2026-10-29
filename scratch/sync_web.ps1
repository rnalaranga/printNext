$src = "C:\Users\Administrator\Documents\GitHub\printNext\rr-secure-print-web"
$dst = "C:\Users\Administrator\Documents\GitHub\printNext\rr-secure-print-api\src\main\resources\static"
$dstTarget = "C:\Users\Administrator\Documents\GitHub\printNext\rr-secure-print-api\target\classes\static"

Copy-Item -Path "$src\index.html" -Destination "$dst\index.html" -Force
Copy-Item -Path "$src\admin.html" -Destination "$dst\admin.html" -Force
Copy-Item -Path "$src\js\ewb-app.js" -Destination "$dst\js\ewb-app.js" -Force
Copy-Item -Path "$src\js\admin-app.js" -Destination "$dst\js\admin-app.js" -Force
Copy-Item -Path "$src\css\*" -Destination "$dst\css\" -Force -Recurse
Copy-Item -Path "$src\img\*" -Destination "$dst\img\" -Force -Recurse

if (Test-Path $dstTarget) {
    Copy-Item -Path "$src\index.html" -Destination "$dstTarget\index.html" -Force
    Copy-Item -Path "$src\admin.html" -Destination "$dstTarget\admin.html" -Force
    Copy-Item -Path "$src\js\ewb-app.js" -Destination "$dstTarget\js\ewb-app.js" -Force
    Copy-Item -Path "$src\js\admin-app.js" -Destination "$dstTarget\js\admin-app.js" -Force
    Copy-Item -Path "$src\css\*" -Destination "$dstTarget\css\" -Force -Recurse
    Copy-Item -Path "$src\img\*" -Destination "$dstTarget\img\" -Force -Recurse
}

Write-Output "Synced web assets (including css/* and js/*) to API static resources and target successfully."
