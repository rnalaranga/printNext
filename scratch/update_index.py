import re

path = 'rr-secure-print-web/index.html'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# 1. bump css version
text = text.replace('/css/ewb-touch.css?v=37', '/css/ewb-touch.css?v=38')

# 2. pc-menu-icon
pattern_icon = r'(\.pc-menu-icon\s*\{[^}]*?margin-top:\s*13px;[^}]*?display:\s*inline-block;[^}]*?\})'
replacement_icon = '''.pc-menu-icon {
      width: 60px;
      height: 60px;
      margin: 14px auto 0 auto;
      display: block;
      border-radius: 10px;
      object-fit: contain;
    }'''
text = re.sub(r'\.pc-menu-icon\s*\{[^}]+?\}', replacement_icon, text, count=1)

# 3. Simplify RFID Hero
pattern_rfid = r'<div class="pc-rfid-title">TAP CARD</div>\s*<div class="pc-rfid-sub">RFID / NFC Contactless</div>\s*<div class="pc-rfid-status"[^>]*>[\s\S]*?</div>'
replacement_rfid = '<div class="pc-rfid-title">TAP CARD</div>'
text = re.sub(pattern_rfid, replacement_rfid, text, count=1)

# 4. Simplify Menu Title Bar
pattern_menu_title = r'<div class="pc-menu-title-bar">\s*<h2>Select an Application</h2>\s*<p>Choose an action below to proceed with your authenticated session</p>\s*</div>'
replacement_menu_title = '''<div class="pc-menu-title-bar">
        <h2>Select an Application</h2>
      </div>'''
text = re.sub(pattern_menu_title, replacement_menu_title, text, count=1)

# 5. Simplify 3 Cards in Screen 2
# Tile 1: Print Release
pattern_card1 = r'(<div class="pc-menu-card pc-menu-card-print"[\s\S]*?<div class="pc-menu-card-title">Print Release</div>)[\s\S]*?(</div>\s*</td>)'
replacement_card1 = r'\1\n            \2'
text = re.sub(pattern_card1, replacement_card1, text, count=1)

# Tile 2: Copy
# Remove badge and subtitles/action
pattern_card2 = r'(<div class="pc-menu-card pc-menu-card-copy" onclick="window\.openCopyFunction\(\)" role="button" tabindex="0">\s*)<div class="pc-menu-card-badge pc-badge-ready">Ready</div>\s*([\s\S]*?<div class="pc-menu-card-title">Copy</div>)[\s\S]*?(</div>\s*</td>)'
replacement_card2 = r'\1\2\n            \3'
text = re.sub(pattern_card2, replacement_card2, text, count=1)

# Tile 3: Scan
pattern_card3 = r'(<div class="pc-menu-card pc-menu-card-scan" onclick="window\.openScanFunction\(\)" role="button" tabindex="0">\s*)<div class="pc-menu-card-badge pc-badge-ready">Ready</div>\s*([\s\S]*?<div class="pc-menu-card-title">Scan</div>)[\s\S]*?(</div>\s*</td>)'
replacement_card3 = r'\1\2\n            \3'
text = re.sub(pattern_card3, replacement_card3, text, count=1)

# 6. Simplify Screen 4 (Photocopy Walk-Up)
old_screen_copy = '''  <!-- ============================================================
       SCREEN 4: PHOTOCOPY WALK-UP STATION
       ============================================================ -->
  <div id="screenCopy" class="pc-screen pc-copy-screen">
    <div class="pc-copy-wrapper" style="max-width: 680px; margin: 30px auto 0 auto;">
      <div style="background:#EFF6FF; border:2.5px solid #2563EB; border-radius:14px; padding:32px 24px; text-align:center; box-shadow:0 6px 20px rgba(37,99,235,0.12);">
        <div style="font-size:46px; margin-bottom:10px;">&#128424;</div>
        <div style="font-size:24px; font-weight:900; color:#1E40AF; margin-bottom:14px; letter-spacing:0.3px;">
          Photocopier Ready!
        </div>
        <div style="font-size:18px; font-weight:800; color:#0F172A; line-height:1.6; margin-bottom:12px;">
          Press <span style="background:#FEF08A; padding:4px 14px; border:2px solid #CA8A04; border-radius:6px; color:#854D0E; font-size:20px; font-weight:900;">[ HOME ]</span> or <span style="background:#FEF08A; padding:4px 14px; border:2px solid #CA8A04; border-radius:6px; color:#854D0E; font-size:20px; font-weight:900;">[ CLEAR ]</span> on panel
        </div>
        <div style="font-size:13px; color:#1E40AF; font-weight:700; background:#DBEAFE; padding:8px 16px; border-radius:8px; display:inline-block; margin-bottom:10px;">
          &#9889; Press physical button next to screen for INSTANT copy window (or wait for auto-switch)
        </div>
        <div style="font-size:14px; color:#475569; font-weight:600;">
          Place pages on ADF or glass and press green [ START ]
        </div>
      </div>'''

new_screen_copy = '''  <!-- ============================================================
       SCREEN 4: PHOTOCOPY WALK-UP STATION
       ============================================================ -->
  <div id="screenCopy" class="pc-screen pc-copy-screen">
    <div class="pc-copy-wrapper" style="max-width: 580px; margin: 30px auto 0 auto;">
      <div style="background:#EFF6FF; border:2px solid #2563EB; border-radius:16px; padding:32px 24px; text-align:center; box-shadow:0 6px 20px rgba(37,99,235,0.08);">
        <div style="font-size:48px; margin-bottom:12px; line-height:1;">&#128424;</div>
        <div style="font-size:24px; font-weight:900; color:#1E40AF; margin-bottom:12px; letter-spacing:0.2px;">
          Photocopier Ready
        </div>
        <div style="font-size:16px; font-weight:700; color:#334155;">
          Press <span style="background:#FEF08A; padding:4px 12px; border:1.5px solid #CA8A04; border-radius:6px; color:#854D0E; font-size:17px; font-weight:800;">[ HOME ]</span> on physical panel
        </div>
      </div>'''

# Handle CRLF and LF
if old_screen_copy in text:
    text = text.replace(old_screen_copy, new_screen_copy)
elif old_screen_copy.replace('\n', '\r\n') in text:
    text = text.replace(old_screen_copy.replace('\n', '\r\n'), new_screen_copy.replace('\n', '\r\n'))
else:
    print('Warning: old_screen_copy not matched directly')

# 7. Simplify Screen 5 (Document Scan Walk-Up)
old_screen_scan = '''  <!-- ============================================================
       SCREEN 5: DOCUMENT SCAN WALK-UP STATION
       ============================================================ -->
  <div id="screenScan" class="pc-screen pc-scan-screen">
    <div class="pc-copy-wrapper" style="max-width: 680px; margin: 30px auto 0 auto;">
      <div style="background:#F0FDF4; border:2.5px solid #22C55E; border-radius:14px; padding:32px 24px; text-align:center; box-shadow:0 6px 20px rgba(34,197,94,0.12);">
        <div style="font-size:46px; margin-bottom:10px;">&#128224;</div>
        <div style="font-size:24px; font-weight:900; color:#15803D; margin-bottom:14px; letter-spacing:0.3px;">
          Scanner Ready!
        </div>
        <div style="font-size:18px; font-weight:800; color:#0F172A; line-height:1.6; margin-bottom:12px;">
          Press <span style="background:#FEF08A; padding:4px 14px; border:2px solid #CA8A04; border-radius:6px; color:#854D0E; font-size:20px; font-weight:900;">[ HOME ]</span> or <span style="background:#FEF08A; padding:4px 14px; border:2px solid #CA8A04; border-radius:6px; color:#854D0E; font-size:20px; font-weight:900;">[ CLEAR ]</span> on panel
        </div>
        <div style="font-size:13px; color:#15803D; font-weight:700; background:#DCFCE7; padding:8px 16px; border-radius:8px; display:inline-block; margin-bottom:10px;">
          &#9889; Press physical button next to screen for INSTANT scan window (or wait for auto-switch)
        </div>
        <div style="font-size:14px; color:#475569; font-weight:600;">
          Destination email is ready in the Address Book
        </div>
      </div>'''

new_screen_scan = '''  <!-- ============================================================
       SCREEN 5: DOCUMENT SCAN WALK-UP STATION
       ============================================================ -->
  <div id="screenScan" class="pc-screen pc-scan-screen">
    <div class="pc-copy-wrapper" style="max-width: 580px; margin: 30px auto 0 auto;">
      <div style="background:#F0FDF4; border:2px solid #16A34A; border-radius:16px; padding:32px 24px; text-align:center; box-shadow:0 6px 20px rgba(22,163,74,0.08);">
        <div style="font-size:48px; margin-bottom:12px; line-height:1;">&#128224;</div>
        <div style="font-size:24px; font-weight:900; color:#15803D; margin-bottom:12px; letter-spacing:0.2px;">
          Scanner Ready
        </div>
        <div style="font-size:16px; font-weight:700; color:#334155;">
          Press <span style="background:#FEF08A; padding:4px 12px; border:1.5px solid #CA8A04; border-radius:6px; color:#854D0E; font-size:17px; font-weight:800;">[ HOME ]</span> on physical panel
        </div>
      </div>'''

if old_screen_scan in text:
    text = text.replace(old_screen_scan, new_screen_scan)
elif old_screen_scan.replace('\n', '\r\n') in text:
    text = text.replace(old_screen_scan.replace('\n', '\r\n'), new_screen_scan.replace('\n', '\r\n'))
else:
    print('Warning: old_screen_scan not matched directly')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

print('Update successfully executed!')
