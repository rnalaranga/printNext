$src = "C:\Users\Administrator\Documents\GitHub\printNext\rr-secure-print-web"
$dst = "C:\Users\Administrator\Documents\GitHub\printNext\rr-secure-print-api\src\main\resources\static"

Copy-Item -Path "$src\index.html" -Destination "$dst\index.html" -Force
Copy-Item -Path "$src\admin.html" -Destination "$dst\admin.html" -Force
Copy-Item -Path "$src\js\ewb-app.js" -Destination "$dst\js\ewb-app.js" -Force
Copy-Item -Path "$src\js\admin-app.js" -Destination "$dst\js\admin-app.js" -Force
Copy-Item -Path "$src\css\ewb-touch.css" -Destination "$dst\css\ewb-touch.css" -Force
Copy-Item -Path "$src\img\*" -Destination "$dst\img\" -Force -Recurse

Write-Output "Synced web assets to API static resources (v=54)."
