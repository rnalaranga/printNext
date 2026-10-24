$src = "C:\Users\Administrator\Documents\GitHub\printNext\rr-secure-print-web"
$target = "C:\Users\Administrator\Documents\GitHub\printNext\rr-secure-print-api\target\classes\static"
Copy-Item "$src\index.html" "$target\index.html" -Force
Copy-Item "$src\admin.html" "$target\admin.html" -Force
Copy-Item "$src\js\ewb-app.js" "$target\js\ewb-app.js" -Force
Copy-Item "$src\js\admin-app.js" "$target\js\admin-app.js" -Force
Copy-Item "$src\css\ewb-touch.css" "$target\css\ewb-touch.css" -Force
Copy-Item "$src\img\*" "$target\img\" -Force -Recurse
Write-Output "Hot copied index.html, ewb-app.js, ewb-touch.css to target classes static"
