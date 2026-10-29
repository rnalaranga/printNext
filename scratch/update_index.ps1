$path = "rr-secure-print-web\index.html"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# 1. Bump version
$content = $content.Replace('/css/ewb-touch.css?v=37', '/css/ewb-touch.css?v=38')

# 2. pc-menu-icon
$patternIcon = '(?s)\.pc-menu-icon\s*\{[^}]+?\}'
$replacementIcon = @'
.pc-menu-icon {
      width: 60px;
      height: 60px;
      margin: 14px auto 0 auto;
      display: block;
      border-radius: 10px;
      object-fit: contain;
    }
'@
$content = [regex]::new($patternIcon).Replace($content, $replacementIcon, 1)

# 3. Simplify RFID Hero
$patternRfid = '(?s)<div class="pc-rfid-title">TAP CARD</div>\s*<div class="pc-rfid-sub">RFID / NFC Contactless</div>\s*<div class="pc-rfid-status"[^>]*>.*?</div>'
$replacementRfid = '<div class="pc-rfid-title">TAP CARD</div>'
$content = [regex]::new($patternRfid).Replace($content, $replacementRfid, 1)

# 4. Simplify Menu Title Bar
$patternMenuTitle = '(?s)<div class="pc-menu-title-bar">\s*<h2>Select an Application</h2>\s*<p>Choose an action below to proceed with your authenticated session</p>\s*</div>'
$replacementMenuTitle = @'
<div class="pc-menu-title-bar">
        <h2>Select an Application</h2>
      </div>
'@
$content = [regex]::new($patternMenuTitle).Replace($content, $replacementMenuTitle, 1)

# 5. Simplify 3 Cards in Screen 2
# Tile 1: Print Release
$patternCard1 = '(?s)(<div class="pc-menu-card pc-menu-card-print".*?<div class="pc-menu-card-title">Print Release</div>).*?(</div>\s*</td>)'
$content = [regex]::new($patternCard1).Replace($content, '$1' + "`r`n            " + '$2', 1)

# Tile 2: Copy
$patternCard2 = '(?s)(<div class="pc-menu-card pc-menu-card-copy" onclick="window\.openCopyFunction\(\)" role="button" tabindex="0">\s*)<div class="pc-menu-card-badge pc-badge-ready">Ready</div>\s*(.*?<div class="pc-menu-card-title">Copy</div>).*?(</div>\s*</td>)'
$content = [regex]::new($patternCard2).Replace($content, '$1$2' + "`r`n            " + '$3', 1)

# Tile 3: Scan
$patternCard3 = '(?s)(<div class="pc-menu-card pc-menu-card-scan" onclick="window\.openScanFunction\(\)" role="button" tabindex="0">\s*)<div class="pc-menu-card-badge pc-badge-ready">Ready</div>\s*(.*?<div class="pc-menu-card-title">Scan</div>).*?(</div>\s*</td>)'
$content = [regex]::new($patternCard3).Replace($content, '$1$2' + "`r`n            " + '$3', 1)

# 6. Simplify Screen 4 (Photocopy Walk-Up)
$patternCopy = '(?s)(<div id="screenCopy" class="pc-screen pc-copy-screen">\s*<div class="pc-copy-wrapper"[^>]*>).*?(<div style="display:flex; justify-content:space-between;)'
$replacementCopy = @'
$1
      <div style="background:#EFF6FF; border:2px solid #2563EB; border-radius:16px; padding:32px 24px; text-align:center; box-shadow:0 6px 20px rgba(37,99,235,0.08);">
        <div style="font-size:48px; margin-bottom:12px; line-height:1;">&#128424;</div>
        <div style="font-size:24px; font-weight:900; color:#1E40AF; margin-bottom:12px; letter-spacing:0.2px;">
          Photocopier Ready
        </div>
        <div style="font-size:16px; font-weight:700; color:#334155;">
          Press <span style="background:#FEF08A; padding:4px 12px; border:1.5px solid #CA8A04; border-radius:6px; color:#854D0E; font-size:17px; font-weight:800;">[ HOME ]</span> on physical panel
        </div>
      </div>

      $2
'@
$content = [regex]::new($patternCopy).Replace($content, $replacementCopy, 1)

# 7. Simplify Screen 5 (Document Scan Walk-Up)
$patternScan = '(?s)(<div id="screenScan" class="pc-screen pc-scan-screen">\s*<div class="pc-copy-wrapper"[^>]*>).*?(<div style="display:flex; justify-content:space-between;)'
$replacementScan = @'
$1
      <div style="background:#F0FDF4; border:2px solid #16A34A; border-radius:16px; padding:32px 24px; text-align:center; box-shadow:0 6px 20px rgba(22,163,74,0.08);">
        <div style="font-size:48px; margin-bottom:12px; line-height:1;">&#128224;</div>
        <div style="font-size:24px; font-weight:900; color:#15803D; margin-bottom:12px; letter-spacing:0.2px;">
          Scanner Ready
        </div>
        <div style="font-size:16px; font-weight:700; color:#334155;">
          Press <span style="background:#FEF08A; padding:4px 12px; border:1.5px solid #CA8A04; border-radius:6px; color:#854D0E; font-size:17px; font-weight:800;">[ HOME ]</span> on physical panel
        </div>
      </div>

      $2
'@
$content = [regex]::new($patternScan).Replace($content, $replacementScan, 1)

[System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::UTF8)
Write-Host "Successfully updated index.html via PowerShell!"
