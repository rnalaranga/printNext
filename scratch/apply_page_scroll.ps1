$cssPath = "rr-secure-print-web\css\admin.css"
$css = [System.IO.File]::ReadAllText($cssPath, [System.Text.Encoding]::UTF8)

# 1. Update lines 113-126 (.admin-body scroll container)
$patternAdminBody = '(?s)\.admin-body\s*\{[^}]+?overflow:\s*hidden\s*!important;\s*overflow-x:\s*hidden\s*!important;[^}]+?\}'
$replacementAdminBody = @'
.admin-body {
  padding: 16px 24px 60px 24px !important;
  flex: 1 1 auto;
  display: block !important;
  height: calc(100vh - 56px) !important;
  max-height: calc(100vh - 56px) !important;
  min-height: 0 !important;
  overflow-y: auto !important; /* Dedicated page-level vertical scrollbar */
  overflow-x: hidden !important;
  box-sizing: border-box !important;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
}
'@
$css = [regex]::new($patternAdminBody).Replace($css, $replacementAdminBody, 1)

# 2. Update lines 195-201 (.table-wrap)
$patternTableWrap1 = '(?s)\.table-wrap\s*\{[^}]+?overflow-x:\s*auto\s*!important;\s*overflow-y:\s*auto\s*!important;[^}]+?\}'
$replacementTableWrap1 = @'
.table-wrap {
  width: 100% !important;
  height: auto !important;
  max-height: none !important;
  overflow-x: auto !important; /* Horizontal scroll for wide tables */
  overflow-y: visible !important; /* Expand table vertically - page scrolls */
  border: 1px solid var(--border);
  box-sizing: border-box;
}
'@
$css = [regex]::new($patternTableWrap1).Replace($css, $replacementTableWrap1, 1)

# 3. Update the bottom block lines 2170-2230
$patternBottomBlock = '(?s)/\*\s*==========================================================================\s*\r?\n\s*ZERO WHOLE-SCREEN VERTICAL SCROLL.*?(?=\r?\n\s*\.brand-mark|\r?\n\s*\[data-theme=)'
$replacementBottomBlock = @'
/* ==========================================================================
   PAGE-LEVEL NATURAL SCROLLING ARCHITECTURE (TABLES EXPAND, PAGE SCROLLS)
   ========================================================================== */
.page-view {
  width: 100% !important;
  height: auto !important;
  max-height: none !important;
  min-height: min-content;
  display: none;
  overflow: visible !important;
  box-sizing: border-box !important;
  padding-bottom: 40px !important;
}

.page-view[style*="display: block"],
.page-view.active {
  display: block !important;
}

#usersListContainer,
#printersListContainer {
  width: 100% !important;
  height: auto !important;
  max-height: none !important;
  overflow-y: visible !important;
  overflow-x: visible !important;
  display: block !important;
}

.card.table-card,
.page-view > .card {
  width: 100% !important;
  height: auto !important;
  max-height: none !important;
  overflow: visible !important;
  margin-bottom: 24px !important;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.table-wrap {
  width: 100% !important;
  height: auto !important;
  max-height: none !important;
  flex: none !important;
  overflow-x: auto !important; /* Horizontal scroll if table exceeds width */
  overflow-y: visible !important; /* Tables expand fully to let the PAGE scroll vertically */
  border: 1px solid var(--border);
  border-radius: 8px;
  box-sizing: border-box;
}

#page-dashboard,
#page-virtual-queue,
#page-printers,
#page-users,
#page-directory,
#page-queue,
#page-copy-logs,
#page-scan-logs,
#page-reports,
#page-audit,
#page-settings {
  width: 100% !important;
  height: auto !important;
  max-height: none !important;
  overflow: visible !important;
}

#page-settings .card {
  overflow: visible !important;
}
'@

if ([regex]::new($patternBottomBlock).IsMatch($css)) {
    $css = [regex]::new($patternBottomBlock).Replace($css, $replacementBottomBlock, 1)
    Write-Host "Replaced bottom scroll override block successfully."
} else {
    Write-Host "Pattern for bottom block did not match directly, checking fallback replacement."
    $altPattern = '(?s)\.page-view\s*\{[^}]+?height:\s*100%;[^}]+?overflow:\s*hidden;.*?padding-bottom:\s*24px;\s*\}'
    if ([regex]::new($altPattern).IsMatch($css)) {
        $css = [regex]::new($altPattern).Replace($css, $replacementBottomBlock, 1)
        Write-Host "Replaced via altPattern successfully."
    } else {
        Write-Host "WARNING: Could not match bottom block."
    }
}

[System.IO.File]::WriteAllText($cssPath, $css, [System.Text.Encoding]::UTF8)
Write-Host "admin.css written successfully!"

# 4. Bump version in admin.html
$htmlPath = "rr-secure-print-web\admin.html"
$html = [System.IO.File]::ReadAllText($htmlPath, [System.Text.Encoding]::UTF8)
$html = $html.Replace('admin.css?v=20261026_107', 'admin.css?v=20261026_108')
[System.IO.File]::WriteAllText($htmlPath, $html, [System.Text.Encoding]::UTF8)
Write-Host "admin.html updated with admin.css?v=20261026_108!"
