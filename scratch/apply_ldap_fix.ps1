# 1. Update css/admin.css
$cssPath = "rr-secure-print-web\css\admin.css"
$css = [System.IO.File]::ReadAllText($cssPath, [System.Text.Encoding]::UTF8)

$ldapCss = @'

/* ==========================================================================
   LDAP / ACTIVE DIRECTORY SYNC LOADING MODAL
   ========================================================================== */
@keyframes ldapSpin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes ldapProgressIndeterminate {
  0% { left: -40%; width: 40%; }
  50% { left: 20%; width: 60%; }
  100% { left: 100%; width: 40%; }
}

.ldap-sync-spinner {
  width: 64px;
  height: 64px;
  border: 4px solid #DBEAFE;
  border-top-color: #2563EB;
  border-right-color: #3B82F6;
  border-radius: 50% !important;
  animation: ldapSpin 1s cubic-bezier(0.55, 0.15, 0.45, 0.85) infinite;
}

.ldap-progress-bar {
  position: absolute;
  top: 0;
  bottom: 0;
  background: linear-gradient(90deg, #2563EB, #3B82F6, #60A5FA);
  border-radius: 4px;
  animation: ldapProgressIndeterminate 1.5s infinite ease-in-out;
}

[data-theme="dark"] #modalLdapLoading .modal-box {
  background: #1E293B !important;
  border-color: #334155 !important;
}
[data-theme="dark"] #ldapLoadingTitle {
  color: #F8FAFC !important;
}
[data-theme="dark"] #ldapLoadingDesc {
  color: #94A3B8 !important;
}
[data-theme="dark"] #ldapLoadingStatusText {
  background: #0F172A !important;
  color: #60A5FA !important;
  border-color: #1E3A8A !important;
}
[data-theme="dark"] .ldap-sync-spinner {
  border-color: #1E293B !important;
  border-top-color: #3B82F6 !important;
  border-right-color: #60A5FA !important;
}
'@

if (-not $css.Contains("LDAP / ACTIVE DIRECTORY SYNC LOADING MODAL")) {
    $css += "`r`n" + $ldapCss
    [System.IO.File]::WriteAllText($cssPath, $css, [System.Text.Encoding]::UTF8)
    Write-Host "Updated css/admin.css with LDAP loading styles."
} else {
    Write-Host "css/admin.css already contains LDAP loading styles."
}

# 2. Update admin.html
$htmlPath = "rr-secure-print-web\admin.html"
$html = [System.IO.File]::ReadAllText($htmlPath, [System.Text.Encoding]::UTF8)

# Bump CSS and JS version in admin.html
$html = $html.Replace('admin.css?v=20261026_106', 'admin.css?v=20261026_107')
$html = $html.Replace('admin-app.js?v=20261026_105', 'admin-app.js?v=20261026_107')

# Replace User Operations dropdown in Page 3: Users
$patternUserOps = '(?s)<div class="action-dropdown-menu" id="user-top-dropdown-menu">.*?</div>\s*</div>'
$replacementUserOps = @'
<div class="action-dropdown-menu" id="user-top-dropdown-menu">
              <button type="button" class="action-dropdown-item item-primary" onclick="window.syncLdapUsersWithLoading()">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                Sync with Active Directory / LDAP
              </button>
              <button type="button" class="action-dropdown-item" onclick="handleImportWindowsUsers()">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="8" height="8"/><rect x="13" y="3" width="8" height="8"/><rect x="3" y="13" width="8" height="8"/><rect x="13" y="13" width="8" height="8"/></svg>
                Import Windows Local Users
              </button>
              <button type="button" class="action-dropdown-item" onclick="loadUsers()">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
                Refresh User List
              </button>
              <button type="button" class="action-dropdown-item" onclick="navigatePage('directory')">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
                Configure LDAP Settings
              </button>
            </div>
          </div>
'@
$html = [regex]::new($patternUserOps).Replace($html, $replacementUserOps, 1)

# Replace Topbar quick action sync
$oldQuickSync = '<button type="button" class="action-dropdown-item" onclick="navigatePage(''directory'')"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/></svg> Sync Active Directory</button>'
$newQuickSync = '<button type="button" class="action-dropdown-item" onclick="window.syncLdapUsersWithLoading()"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/></svg> Sync Active Directory / LDAP</button>'
$html = $html.Replace($oldQuickSync, $newQuickSync)

# Add modalLdapLoading markup if not already present
$loadingModalMarkup = @'
<!-- =========================================================================
     MODAL: LDAP / ACTIVE DIRECTORY SYNCHRONIZATION LOADING OVERLAY
     ========================================================================= -->
<div id="modalLdapLoading" class="modal-backdrop" style="display:none; z-index:999999;">
  <div class="modal-box" style="max-width:440px; text-align:center; padding:32px 28px; border-radius:12px; box-shadow:0 20px 45px rgba(15,23,42,0.3); background:#FFFFFF; border:1.5px solid #CBD5E1;">
    <!-- Animated Spinner / Syncer Graphic -->
    <div style="position:relative; width:64px; height:64px; margin:0 auto 18px auto;">
      <div class="ldap-sync-spinner"></div>
      <div style="position:absolute; top:0; left:0; width:64px; height:64px; display:flex; align-items:center; justify-content:center;">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#2563EB" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
          <circle cx="9" cy="7" r="4"></circle>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
          <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
        </svg>
      </div>
    </div>
    
    <h3 id="ldapLoadingTitle" style="font-size:16.5px; font-weight:800; color:#0F172A; margin-bottom:8px; letter-spacing:-0.2px;">Synchronizing Active Directory / LDAP</h3>
    <p id="ldapLoadingDesc" style="font-size:12.5px; color:#64748B; margin-bottom:18px; line-height:1.5;">
      Querying Domain Controller, synchronizing employee accounts, and mapping external attributes...
    </p>

    <!-- Animated indeterminate progress bar -->
    <div style="width:100%; height:6px; background:#E2E8F0; border-radius:4px; overflow:hidden; position:relative; margin-bottom:14px;">
      <div class="ldap-progress-bar"></div>
    </div>

    <div id="ldapLoadingStatusText" style="font-size:11.5px; font-weight:700; color:#2563EB; display:inline-block; background:#EFF6FF; padding:4px 14px; border-radius:20px; border:1px solid #BFDBFE;">
      Connecting to LDAP Domain Controller...
    </div>
  </div>
</div>
'@

if (-not $html.Contains("modalLdapLoading")) {
    $html = $html.Replace('<div id="modalDirectorySync"', $loadingModalMarkup + "`r`n<div id=""modalDirectorySync""")
    [System.IO.File]::WriteAllText($htmlPath, $html, [System.Text.Encoding]::UTF8)
    Write-Host "Updated admin.html with modalLdapLoading."
} else {
    [System.IO.File]::WriteAllText($htmlPath, $html, [System.Text.Encoding]::UTF8)
    Write-Host "admin.html already contains modalLdapLoading."
}

# 3. Update js/admin-app.js
$jsPath = "rr-secure-print-web\js\admin-app.js"
$js = [System.IO.File]::ReadAllText($jsPath, [System.Text.Encoding]::UTF8)

$syncFnCode = @'

  /* =========================================================================
     GLOBAL LDAP / ACTIVE DIRECTORY SYNCHRONIZATION WITH LOADING SCREEN
     ========================================================================= */
  window.syncLdapUsersWithLoading = function () {
    // Dismiss any open dropdowns
    var openDropdowns = document.querySelectorAll('.action-dropdown-menu.show');
    for (var i = 0; i < openDropdowns.length; i++) {
      openDropdowns[i].classList.remove('show');
    }

    var modal = document.getElementById('modalLdapLoading');
    var titleEl = document.getElementById('ldapLoadingTitle');
    var descEl = document.getElementById('ldapLoadingDesc');
    var statusEl = document.getElementById('ldapLoadingStatusText');

    if (titleEl) titleEl.textContent = 'Synchronizing Active Directory / LDAP';
    if (descEl) descEl.textContent = 'Querying Domain Controller, synchronizing employee accounts, and mapping external attributes...';
    if (statusEl) statusEl.textContent = 'Connecting to LDAP Domain Controller...';

    if (modal) {
      modal.style.display = 'flex';
    }

    var logBoxDir = document.getElementById('dirPageLogBox');
    var logBoxModal = document.getElementById('dirSyncLogBox');
    var logMsg = '[' + new Date().toLocaleTimeString() + '] Initiating Active Directory / LDAP synchronization...';
    if (logBoxDir) logBoxDir.textContent = logMsg;
    if (logBoxModal) logBoxModal.textContent = logMsg;

    var startTime = Date.now();
    var isTimedOut = false;
    var timer = setTimeout(function () {
      isTimedOut = true;
      if (modal) modal.style.display = 'none';
      showToast('LDAP sync took longer than expected. Please check Domain Controller connection.', 'warning');
    }, 25000);

    fetch('/api/admin/directory/sync-ad', { method: 'POST' })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (res) {
        if (isTimedOut) return;
        clearTimeout(timer);

        var elapsed = Date.now() - startTime;
        var delay = Math.max(0, 750 - elapsed);

        setTimeout(function () {
          if (modal) modal.style.display = 'none';

          if (res.success) {
            var msg = 'LDAP Sync Complete! ' + (res.importedCount || 0) + ' imported, ' + (res.updatedCount || 0) + ' updated (Found ' + (res.totalFoundCount || 0) + ' accounts in directory).';
            showToast(msg, 'success');

            var detailLog = '[' + new Date().toLocaleTimeString() + '] ' + res.message + '\nImported: ' + (res.importedCount || 0) + ' | Updated: ' + (res.updatedCount || 0);
            if (logBoxDir) logBoxDir.textContent = detailLog;
            if (logBoxModal) logBoxModal.textContent = detailLog;

            if (typeof loadUsers === 'function') loadUsers();
            if (typeof loadDirectoryPageConfig === 'function') loadDirectoryPageConfig();
            if (typeof loadDirectoryConfig === 'function') loadDirectoryConfig();
            if (typeof loadStats === 'function') loadStats();
            if (typeof loadLdapDeptSettings === 'function') loadLdapDeptSettings();
          } else {
            showToast('LDAP Sync failed: ' + (res.message || 'Unknown error'), 'error');
            var errLog = '[' + new Date().toLocaleTimeString() + '] AD Sync Error: ' + res.message;
            if (logBoxDir) logBoxDir.textContent = errLog;
            if (logBoxModal) logBoxModal.textContent = errLog;
          }
        }, delay);
      })
      .catch(function (err) {
        if (isTimedOut) return;
        clearTimeout(timer);

        var elapsed = Date.now() - startTime;
        var delay = Math.max(0, 600 - elapsed);

        setTimeout(function () {
          if (modal) modal.style.display = 'none';
          showToast('Failed to sync LDAP users: ' + err.message, 'error');
          var netErrLog = '[' + new Date().toLocaleTimeString() + '] AD Sync Error: ' + err.message;
          if (logBoxDir) logBoxDir.textContent = netErrLog;
          if (logBoxModal) logBoxModal.textContent = netErrLog;
        }, delay);
      });
  };
'@

if (-not $js.Contains("window.syncLdapUsersWithLoading")) {
    # Replace syncActiveDirectoryFromPage to use syncLdapUsersWithLoading
    $patternSyncPage = '(?s)window\.syncActiveDirectoryFromPage\s*=\s*function\s*\(\)\s*\{.*?\}\;'
    $newSyncPage = @'
window.syncActiveDirectoryFromPage = function () {
    window.syncLdapUsersWithLoading();
  };
'@
    $js = [regex]::new($patternSyncPage).Replace($js, $newSyncPage, 1)

    # Replace handleSyncActiveDirectory to use syncLdapUsersWithLoading
    $patternHandleSync = '(?s)window\.handleSyncActiveDirectory\s*=\s*function\s*\(\)\s*\{.*?\}\;'
    $newHandleSync = @'
window.handleSyncActiveDirectory = function () {
    window.syncLdapUsersWithLoading();
  };
'@
    $js = [regex]::new($patternHandleSync).Replace($js, $newHandleSync, 1)

    # Append syncLdapUsersWithLoading before the closing IIFE
    $js = $js.TrimEnd()
    if ($js.EndsWith("})();")) {
        $js = $js.Substring(0, $js.Length - 5) + "`r`n" + $syncFnCode + "`r`n})();"
    } else {
        $js += "`r`n" + $syncFnCode
    }
    [System.IO.File]::WriteAllText($jsPath, $js, [System.Text.Encoding]::UTF8)
    Write-Host "Updated js/admin-app.js with syncLdapUsersWithLoading."
} else {
    Write-Host "js/admin-app.js already contains syncLdapUsersWithLoading."
}

Write-Host "All files updated successfully!"
