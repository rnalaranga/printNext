$pc = Get-Content -Path PanelCustomize.js
$pc | Select-String -Pattern "div_print_tab|Print" -Context 3, 3 | Select-Object -First 30
