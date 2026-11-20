/* ==========================================================================
   nexPrint - Administration Console Application JS v20
   Clean Modern Architecture
   ========================================================================== */

(function () {
  'use strict';

  var state = {
    currentPage: 'dashboard',
    printers: [],
    users: [],
    jobs: [],
    copyJobs: [],
    auditLogs: [],
    stats: {
      totalJobs: 0,
      heldJobs: 0,
      completedJobs: 0,
      printerCount: 0,
      userCount: 0
    },
    serverTerminalUrl: 'http://192.168.1.100:8080/'
  };
  window.state = state;

  /* =========================================================================
     PAGE INITIALIZATION & AUTHENTICATION
     ========================================================================= */

  window.onload = function () {
    // Fetch server LAN IP & health silently
    fetch('/api/health')
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (d && d.defaultTerminalUrl) {
          state.serverTerminalUrl = d.defaultTerminalUrl;
        }
      })
      .catch(function () {});

    // Authenticate admin session silently in background
    fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', pin: '9999' })
    }).finally(function () {
      // Load corporate branding & logo for left-corner sidebar
      loadSystemBranding();

      // Determine page from URL hash (e.g. #printers, #users, #virtual-queue)
      var hash = (window.location.hash || '').replace('#', '').toLowerCase();
      var validPages = ['dashboard', 'virtual-queue', 'printers', 'monitoring', 'maintenance', 'users', 'directory', 'queue', 'copy-logs', 'scan-logs', 'reports', 'audit', 'settings'];
      var targetPage = validPages.indexOf(hash) !== -1 ? hash : 'dashboard';
      window.navigatePage(targetPage);

      // Listen for browser back / forward navigation
      window.addEventListener('hashchange', function () {
        var newHash = (window.location.hash || '').replace('#', '').toLowerCase();
        if (validPages.indexOf(newHash) !== -1 && newHash !== state.currentPage) {
          window.navigatePage(newHash, false);
        }
      });
    });
  };

  /* =========================================================================
     PAGE NAVIGATION CONTROLLER
     ========================================================================= */

  window.navigatePage = function (pageId, updateHash) {
    if (updateHash === undefined) updateHash = true;
    state.currentPage = pageId;

    if (updateHash) {
      window.location.hash = pageId;
    }

    // Toggle active sidebar item
    var navItems = document.querySelectorAll('.sidebar-item[data-page]');
    for (var i = 0; i < navItems.length; i++) {
      if (navItems[i].getAttribute('data-page') === pageId) {
        navItems[i].classList.add('active');
      } else {
        navItems[i].classList.remove('active');
      }
    }

    // Toggle visible page view
    var views = document.querySelectorAll('.page-view');
    for (var j = 0; j < views.length; j++) {
      views[j].style.display = 'none';
    }

    var activeView = document.getElementById('page-' + pageId);
    if (activeView) {
      activeView.style.display = 'block';
    }

    var adminBody = document.querySelector('.admin-body');
    if (adminBody) {
      adminBody.scrollTop = 0;
    }

    // Update Topbar Title & Subtitle
    var topbarTitle = document.getElementById('topbarTitle');
    var topbarSub   = document.getElementById('topbarSub');

    if (pageId === 'dashboard') {
      topbarTitle.textContent = 'System Administration Overview';
      topbarSub.textContent   = 'Enterprise Pull-Print Server & Hardware Monitor';
      loadStats();
      loadJobs();
    } else if (pageId === 'virtual-queue') {
      topbarTitle.textContent = 'Virtual Print Queue (PaperCut Find-Me)';
      topbarSub.textContent   = 'Server Spool Quarantine, SMB Network Share & Release Stations Pool';
      loadVirtualQueueConfig();
    } else if (pageId === 'printers') {
      topbarTitle.textContent = 'Physical Printer Device Management';
      topbarSub.textContent   = 'Toshiba Multifunction Printers & Network Port 9100 Terminals';
      loadPrinters();
    } else if (pageId === 'monitoring') {
      topbarTitle.textContent = 'Printer Hardware Monitoring';
      topbarSub.textContent   = 'Real-Time RFC 3805 SNMP Telemetry • Active Hardware Fleet';
      if (typeof window.initNocMonitoring === 'function') {
        window.initNocMonitoring();
      }
    } else if (pageId === 'maintenance') {
      topbarTitle.textContent = 'Printer Maintenance';
      topbarSub.textContent   = 'Toner Replacements, Hardware Repairs & Service Cost Logbook';
      if (typeof window.loadMaintenance === 'function') {
        window.loadMaintenance();
      }
    } else if (pageId === 'directory') {
      topbarTitle.textContent = 'Active Directory & LDAP Management';
      topbarSub.textContent   = 'Enterprise Directory Synchronization, Attribute Mapping & Toshiba Fleet LDAP Lockdown';
      loadDirectoryPageConfig();
    } else if (pageId === 'users') {
      topbarTitle.textContent = 'User Identity Management';
      topbarSub.textContent   = 'Security Credentials, Touchscreen PINs, and Windows SIDs';
      loadUsers();
      loadDirectoryConfig();
    } else if (pageId === 'queue') {
      topbarTitle.textContent = 'Global Held Print Queue';
      topbarSub.textContent   = 'Active Print Jobs Quarantined Awaiting Walk-up Release';
      loadJobs();
    } else if (pageId === 'copy-logs') {
      topbarTitle.textContent = 'Walk-Up Photocopy Logs';
      topbarSub.textContent   = 'MFP Hardware Copy Tracking & User Accounting';
      loadCopyJobs();
    } else if (pageId === 'reports') {
      topbarTitle.textContent = 'Enterprise Reports & Analytics';
      topbarSub.textContent   = 'Department & Cost Center (extensionAttribute3) Print/Copy Accounting';
      window.loadReports();
    } else if (pageId === 'scan-logs') {
      topbarTitle.textContent = 'Scan to Email Activity';
      topbarSub.textContent   = 'Document Scan-to-Email Records & Delivery Audit';
      window.loadScanActivity();
    } else if (pageId === 'audit') {
      topbarTitle.textContent = 'Security Audit Trail';
      topbarSub.textContent   = 'Tamper-Evident Security Log & Device Release Events';
      loadAudit();
    } else if (pageId === 'settings') {
      topbarTitle.textContent = 'System Settings & Corporate Branding';
      topbarSub.textContent   = 'Organization Branding, Custom Logo, and System Preferences';
      window.loadSettings();
    }
  };

  window.refreshCurrentPage = function () {
    window.navigatePage(state.currentPage, false);
  };

  /* =========================================================================
     STATISTICS & DASHBOARD
     ========================================================================= */

  function loadStats() {
    fetch('/api/admin/stats')
      .then(function (res) { return res.json(); })
      .then(function (data) {
        state.stats = data;
        updateStatsUI();
      })
      .catch(function (err) {
        console.error('Stats error:', err);
      });
  }

  function updateStatsUI() {
    var elTotalJobs     = document.getElementById('statTotalJobs');
    var elHeldJobs      = document.getElementById('statHeldJobs');
    var elCompletedJobs = document.getElementById('statCompletedJobs');
    var elPrintersCount = document.getElementById('statPrintersCount');
    var elUsersCount    = document.getElementById('statUsersCount');

    if (elTotalJobs)     elTotalJobs.textContent     = state.stats.totalJobs !== undefined ? state.stats.totalJobs : '-';
    if (elHeldJobs)      elHeldJobs.textContent      = state.stats.heldJobs !== undefined ? state.stats.heldJobs : '-';
    if (elCompletedJobs) elCompletedJobs.textContent = state.stats.completedJobs !== undefined ? state.stats.completedJobs : '-';
    if (elPrintersCount) elPrintersCount.textContent = state.stats.printerCount !== undefined ? state.stats.printerCount : '-';
    if (elUsersCount)    elUsersCount.textContent    = state.stats.userCount !== undefined ? state.stats.userCount : '-';
  }

  /* =========================================================================
     PRINTER MANAGEMENT
     ========================================================================= */

  window.loadPrinters = function () {
    fetch('/api/admin/printers')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        state.printers = data || [];
        renderPrinters();
        updateSidebarPrinterInfo();
        try { if (typeof window.populateDirPrintersDropdown === 'function') { window.populateDirPrintersDropdown(); } } catch (e) { console.warn('Dropdown populate error:', e); }
      })
      .catch(function (err) {
        console.error('Printers error:', err);
        var tbody = document.getElementById('printersTableBody');
        if (tbody) {
          tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:#ef4444;">Failed to load printers: ' + esc(err.message) + '</td></tr>';
        }
      });
  };

  function updateSidebarPrinterInfo() {
    if (state.printers.length > 0) {
      var p = state.printers[0];
      var ipEl = document.getElementById('sidebarPrinterIp');
      var modelEl = document.getElementById('sidebarPrinterModel');
      if (ipEl) ipEl.textContent = p.ipAddress + ':' + p.rawPort;
      if (modelEl) modelEl.textContent = p.name;
    }
  }

  function getDefaultTerminalUrl() {
    if (state.serverTerminalUrl) {
      return state.serverTerminalUrl;
    }
    var host = window.location.hostname;
    if (!host || host === 'localhost' || host === '127.0.0.1') {
      return 'http://192.168.1.100:8080/';
    }
    return 'http://' + host + ':8080/';
  }

  function sanitizeTerminalUrl(url, printerIp) {
    if (!url || url.indexOf('localhost') !== -1 || url.indexOf('127.0.0.1') !== -1 || (printerIp && url.indexOf(printerIp) !== -1)) {
      return getDefaultTerminalUrl();
    }
    return url;
  }

  window.togglePassVisibility = function (inputId) {
    var inp = document.getElementById(inputId);
    if (!inp) return;
    inp.type = inp.type === 'password' ? 'text' : 'password';
  };

    /* =========================================================================
     DEPARTMENT CATEGORIZATION & PRINTER VIEW CONTROLLERS
     ========================================================================= */
  state.printerViewMode = 'grouped';
  state.selectedPrinterDept = 'ALL';

  window.setPrinterViewMode = function (mode) {
    state.printerViewMode = mode;
    var btnGrp = document.getElementById('btnPrnViewGroup');
    var btnFlt = document.getElementById('btnPrnViewFlat');
    if (btnGrp && btnFlt) {
      if (mode === 'grouped') {
        btnGrp.classList.add('active');
        btnFlt.classList.remove('active');
      } else {
        btnFlt.classList.add('active');
        btnGrp.classList.remove('active');
      }
    }
    renderPrinters();
  };

  window.selectPrinterDeptPill = function (dept) {
    state.selectedPrinterDept = dept;
    renderPrinters();
  };

  function resolvePrinterSeries(p) {
    if (!p) return { code: 'TOSHIBA_18', label: '18 Series', browser: 'Chromium 56', color: '#7C3AED', bg: '#EDE9FE', border: '#DDD6FE' };
    var gen = (p.deviceGeneration || '').toUpperCase();
    if (gen === 'TOSHIBA_08') return { code: 'TOSHIBA_08', label: '08 Series', browser: 'WebKit Safari', color: '#0284C7', bg: '#E0F2FE', border: '#BAE6FD' };
    if (gen === 'TOSHIBA_18') return { code: 'TOSHIBA_18', label: '18 Series', browser: 'Chromium 56', color: '#7C3AED', bg: '#EDE9FE', border: '#DDD6FE' };
    if (gen === 'TOSHIBA_28') return { code: 'TOSHIBA_28', label: '28 Series', browser: 'Chromium 56 (Gen 3)', color: '#16A34A', bg: '#DCFCE7', border: '#BBF7D0' };
    if (gen === 'TOSHIBA_OLD') return { code: 'TOSHIBA_OLD', label: 'Toshiba Old', browser: 'No EWB', color: '#92400E', bg: '#FEF3C7', border: '#FDE68A' };

    // Fallback: inspect model
    var m = (p.model || '').toLowerCase();
    if (m.indexOf('306') !== -1 || m.indexOf('256') !== -1 || m.indexOf('356') !== -1 || m.indexOf('456') !== -1 || m.indexOf('506') !== -1 || m.indexOf('305') !== -1) {
      return { code: 'TOSHIBA_OLD', label: 'Toshiba Old', browser: 'No EWB', color: '#92400E', bg: '#FEF3C7', border: '#FDE68A' };
    }
    if (m.indexOf('08') !== -1 || m.indexOf('05ac') !== -1) {
      return { code: 'TOSHIBA_08', label: '08 Series', browser: 'WebKit Safari', color: '#0284C7', bg: '#E0F2FE', border: '#BAE6FD' };
    }
    if (m.indexOf('28') !== -1 || m.indexOf('25ac') !== -1 || m.indexOf('20ac') !== -1) {
      return { code: 'TOSHIBA_28', label: '28 Series', browser: 'Chromium 56 (Gen 3)', color: '#16A34A', bg: '#DCFCE7', border: '#BBF7D0' };
    }
    return { code: 'TOSHIBA_18', label: '18 Series', browser: 'Chromium 56', color: '#7C3AED', bg: '#EDE9FE', border: '#DDD6FE' };
  }
  window.resolvePrinterSeries = resolvePrinterSeries;

  function buildSinglePrinterRow(p, showDeptCol) {
    var sInfo = resolvePrinterSeries(p);
    var isOld = (sInfo.code === 'TOSHIBA_OLD');

    var genBadge = '<span class="badge" style="background:' + sInfo.bg + '; color:' + sInfo.color + '; border:1px solid ' + sInfo.border + '; font-weight:600;" title="' + sInfo.browser + '"><span style="color:' + sInfo.color + ';">●</span> ' + sInfo.label + '</span>';

    var statusBadge = p.status === 'ONLINE'
      ? '<span class="badge badge-online">ONLINE</span>'
      : '<span class="badge badge-offline">' + esc(p.status || 'OFFLINE') + '</span>';

    var ewbBadge;
    if (isOld) {
      ewbBadge =
        '<div class="ewb-status-lights">' +
          '<span class="status-pill pill-dim" title="Legacy device releases prints via RAW Port 9100 / Web Station">' +
            '<span class="light-dot light-dim"></span> Port 9100' +
          '</span>' +
        '</div>';
    } else {
      var ewbLight = p.ewbConfigured
        ? '<span class="status-pill pill-green" title="EWB Terminal: Configured & Ready (' + esc(p.ewbUrl || '') + ')"><span class="light-dot light-green"></span> EWB</span>'
        : '<span class="status-pill pill-dim" title="EWB Terminal: Not pushed to device yet"><span class="light-dot light-dim"></span> No EWB</span>';

      var lockLight = p.functionsLocked
        ? '<span class="status-pill pill-green" title="HOME Screen Icons: LOCKED (Photo Copy & Native Functions Restricted)"><span class="light-dot light-green"></span> Lock</span>'
        : '<span class="status-pill pill-amber" title="HOME Screen Icons: UNLOCKED (Walk-Up Copy Open)"><span class="light-dot light-amber"></span> Open</span>';

      ewbBadge =
        '<div class="ewb-status-lights">' +
          ewbLight +
          lockLight +
        '</div>';
    }

    var roleBadge = '<span class="badge" style="background:#EFF6FF; color:#1D4ED8; font-weight:600; border:1px solid #BFDBFE;">Follow-Me Station</span>';

    var actionsDropdown =
      '<div class="action-dropdown-wrap" id="action-wrap-' + p.id + '">' +
        '<button type="button" class="btn-action-dropdown" onclick="window.togglePrinterDropdown(event, ' + p.id + ')" id="dropdown-btn-' + p.id + '">' +
          'Actions ' +
        '</button>' +
        '<div class="action-dropdown-menu" id="dropdown-menu-' + p.id + '">' +
          (!isOld ? '<button type="button" class="action-dropdown-item item-primary" onclick="openQuickEwbModal(' + p.id + ')"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg> Push EWB Settings</button>' : '') +
          (!isOld ? '<button type="button" class="action-dropdown-item" style="color:#2563EB; font-weight:700;" onclick="window.pushLdapAndLockSinglePrinter(' + p.id + ')"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg> Push LDAP &amp; Card Lock</button>' : '') +
          (!isOld ? '<button type="button" class="action-dropdown-item item-warning" onclick="window.lockSinglePrinter(' + p.id + ', true)"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg> Lock HOME Icons</button>' : '') +
          (!isOld ? '<button type="button" class="action-dropdown-item" onclick="window.lockSinglePrinter(' + p.id + ', false)"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 9.9-1"/></svg> Unlock Walk-Up Copy</button>' : '') +
          (!isOld ? '<div class="action-dropdown-divider"></div>' : '') +
          '<button type="button" class="action-dropdown-item" onclick="testPrinter(' + p.id + ')"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> Test Connection</button>' +
          '<button type="button" class="action-dropdown-item" onclick="window.open(\'http://' + esc(p.ipAddress) + '/\', \'_blank\')"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg> Open TopAccess</button>' +
          (!isOld ? '<button type="button" class="action-dropdown-item" onclick="window.rebootSinglePrinter(' + p.id + ')"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> Remote Restart MFP</button>' : '') +
          '<div class="action-dropdown-divider"></div>' +
          '<button type="button" class="action-dropdown-item" onclick="openEditPrinterModal(' + p.id + ')"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg> Edit Printer</button>' +
          '<button type="button" class="action-dropdown-item item-danger" onclick="deletePrinter(' + p.id + ', \'' + esc(p.name) + '\')"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg> Delete Printer</button>' +
        '</div>' +
      '</div>';

    var deptCell = showDeptCol ? '<td><span class="badge bg-primary-subtle text-primary border border-primary-subtle fw-bold">' + esc(p.department || 'Operations') + '</span></td>' : '';

    return '<tr>' +
      '<td><strong>' + esc(p.name) + '</strong></td>' +
      '<td><code>' + esc(p.printerCode) + '</code></td>' +
      deptCell +
      '<td>' + genBadge + '</td>' +
      '<td>' + esc(p.ipAddress) + ':' + p.rawPort + '</td>' +
      '<td>' + esc(p.location || 'Office') + '</td>' +
      '<td>' + esc(p.model || (isOld ? 'Toshiba e-STUDIO 306SE' : 'Toshiba MFP')) + '</td>' +
      '<td>' + roleBadge + '</td>' +
      '<td>' + ewbBadge + '</td>' +
      '<td id="printer-status-' + p.id + '">' + statusBadge + '</td>' +
      '<td style="text-align:right; white-space:nowrap; position:relative;">' + actionsDropdown + '</td>' +
    '</tr>';
  }

  function renderPrinters() {
    var container = document.getElementById('printersListContainer');
    var fallbackTbody = document.getElementById('printersTableBody');
    if (!container && !fallbackTbody) return;

    if (state.printers.length === 0) {
      if (container) {
        container.innerHTML = '<div class="card p-4 text-center text-muted">No physical printers registered in the Follow-Me pool yet. Click "+ Register New Printer" to add one.</div>';
      } else if (fallbackTbody) {
        fallbackTbody.innerHTML = '<tr><td colspan="11" style="text-align:center;padding:30px;color:#6B7299;">No physical printers registered yet.</td></tr>';
      }
      return;
    }

    // 1. Build Department Pills Strip
    var deptCounts = {};
    for (var i = 0; i < state.printers.length; i++) {
      var d = state.printers[i].department || 'Operations';
      deptCounts[d] = (deptCounts[d] || 0) + 1;
    }

    var pillsContainer = document.getElementById('printerDeptPillsContainer');
    if (pillsContainer) {
      var pillsHtml = '<button type="button" class="dept-pill-btn' + (state.selectedPrinterDept === 'ALL' ? ' active' : '') + '" onclick="window.selectPrinterDeptPill(\'ALL\')">All Departments <span class="pill-count">' + state.printers.length + '</span></button>';
      var depts = Object.keys(deptCounts).sort();
      for (var k = 0; k < depts.length; k++) {
        var dName = depts[k];
        var count = deptCounts[dName];
        pillsHtml += '<button type="button" class="dept-pill-btn' + (state.selectedPrinterDept === dName ? ' active' : '') + '" onclick="window.selectPrinterDeptPill(\'' + esc(dName) + '\')">' + esc(dName) + ' <span class="pill-count">' + count + '</span></button>';
      }
      pillsContainer.innerHTML = pillsHtml;
    }

    // 2. Filter printers by search and selected department
    var searchInput = document.getElementById('printerSearchInput');
    var searchVal = (searchInput ? searchInput.value : '').toLowerCase().trim();

    var filtered = state.printers.filter(function (p) {
      var pDept = p.department || 'Operations';
      if (state.selectedPrinterDept !== 'ALL' && pDept !== state.selectedPrinterDept) return false;
      if (!searchVal) return true;
      return (p.name || '').toLowerCase().indexOf(searchVal) !== -1 ||
             (p.printerCode || '').toLowerCase().indexOf(searchVal) !== -1 ||
             (p.ipAddress || '').toLowerCase().indexOf(searchVal) !== -1 ||
             (p.model || '').toLowerCase().indexOf(searchVal) !== -1 ||
             pDept.toLowerCase().indexOf(searchVal) !== -1 ||
             (p.location || '').toLowerCase().indexOf(searchVal) !== -1;
    });

    if (filtered.length === 0) {
      if (container) {
        container.innerHTML = '<div class="card p-4 text-center text-muted">No printers matching "' + esc(searchVal || state.selectedPrinterDept) + '".</div>';
      }
      return;
    }

    // 3. Render Grouped vs Flat
    if (!container || state.printerViewMode === 'flat') {
      var tableHtml =
        '<div class="card">' +
          '<div class="table-wrap">' +
            '<table>' +
              '<thead>' +
                '<tr>' +
                  '<th>MFP Device Name</th>' +
                  '<th>Device Code</th>' +
                  '<th>Assigned Department</th>' +
                  '<th>Architecture</th>' +
                  '<th>Network IP : Port</th>' +
                  '<th>Location</th>' +
                  '<th>Hardware Model</th>' +
                  '<th>Release Role</th>' +
                  '<th>EWB Terminal</th>' +
                  '<th>Status</th>' +
                  '<th style="text-align:right;">Actions</th>' +
                '</tr>' +
              '</thead>' +
              '<tbody>' +
                filtered.map(function (p) { return buildSinglePrinterRow(p, true); }).join('') +
              '</tbody>' +
            '</table>' +
          '</div>' +
        '</div>';
      if (container) container.innerHTML = tableHtml;
      return;
    }

    // Grouped by Department Mode
    var grouped = {};
    for (var j = 0; j < filtered.length; j++) {
      var prn = filtered[j];
      var prnDept = prn.department || 'Operations';
      if (!grouped[prnDept]) grouped[prnDept] = [];
      grouped[prnDept].push(prn);
    }

    var groupKeys = Object.keys(grouped).sort();
    var outHtml = '';

    for (var g = 0; g < groupKeys.length; g++) {
      var grpDept = groupKeys[g];
      var list = grouped[grpDept];
      var ips = list.map(function (x) { return esc(x.ipAddress); }).join(', ');

      var isPrnCollapsed = (state.collapsedDepts && state.collapsedDepts['prn_' + grpDept]) ? ' is-collapsed' : '';

      outHtml +=
        '<div class="card dept-group-card shadow-sm' + isPrnCollapsed + '" id="prn-dept-card-' + esc(grpDept).replace(/[^a-zA-Z0-9]/g, '_') + '">' +
          '<div class="dept-group-head" onclick="window.toggleDeptGroupCollapse(this)" data-dept-key="prn_' + esc(grpDept) + '" title="Click to collapse / expand ' + esc(grpDept) + ' department printers">' +
            '<div class="d-flex align-items-center gap-2">' +
              '<span class="dept-collapse-toggle"><svg class="dept-collapse-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg></span>' +
              '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--primary); flex-shrink:0;"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>' +
              '<strong style="font-size:13.5px; color:var(--text-dark);">' + esc(grpDept) + ' Department</strong>' +
              '<span class="badge bg-primary-subtle text-primary border border-primary-subtle ms-2">' + list.length + ' ' + (list.length === 1 ? 'Printer' : 'Printers') + '</span>' +
            '</div>' +
            '<div class="d-flex align-items-center gap-3">' +
              '<div class="text-secondary small">Network IPs: <code>' + ips + '</code></div>' +
              '<span class="text-muted small" style="font-size:11px; user-select:none;">(click to collapse)</span>' +
            '</div>' +
          '</div>' +
          '<div class="table-wrap" style="max-height:360px;">' +
            '<table>' +
              '<thead>' +
                '<tr>' +
                  '<th>MFP Device Name</th>' +
                  '<th>Device Code</th>' +
                  '<th>Architecture</th>' +
                  '<th>Network IP : Port</th>' +
                  '<th>Location</th>' +
                  '<th>Hardware Model</th>' +
                  '<th>Release Role</th>' +
                  '<th>EWB Terminal</th>' +
                  '<th>Status</th>' +
                  '<th style="text-align:right;">Actions</th>' +
                '</tr>' +
              '</thead>' +
              '<tbody>' +
                list.map(function (p) { return buildSinglePrinterRow(p, false); }).join('') +
              '</tbody>' +
            '</table>' +
          '</div>' +
        '</div>';
    }

    container.innerHTML = outHtml;
  }

  window.handleModelInput = function (val) {
    if (!val) return;
    var m = val.toLowerCase();
    var genEl = document.getElementById('printerDeviceGeneration');
    if (!genEl) return;
    if (m.indexOf('306') !== -1 || m.indexOf('256') !== -1 || m.indexOf('356') !== -1 || m.indexOf('456') !== -1 || m.indexOf('506') !== -1 || m.indexOf('305') !== -1) {
      genEl.value = 'TOSHIBA_OLD';
    } else if (m.indexOf('08') !== -1 || m.indexOf('05ac') !== -1) {
      genEl.value = 'TOSHIBA_08';
    } else if (m.indexOf('28') !== -1 || m.indexOf('25ac') !== -1 || m.indexOf('20ac') !== -1) {
      genEl.value = 'TOSHIBA_28';
    } else if (m.indexOf('18') !== -1 || m.indexOf('15ac') !== -1 || m.indexOf('10ac') !== -1) {
      genEl.value = 'TOSHIBA_18';
    }
    window.handleDeviceGenerationChange();
  };

  window.handleDeviceGenerationChange = function () {
    var genEl = document.getElementById('printerDeviceGeneration');
    if (!genEl) return;
    var val = genEl.value;
    var isOld = (val === 'TOSHIBA_OLD');
    var is08 = (val === 'TOSHIBA_08');
    var is18 = (val === 'TOSHIBA_18');
    var is28 = (val === 'TOSHIBA_28');
    var modelInput = document.getElementById('printerModel');
    var ewbFields = document.getElementById('ewbConfigFields');
    var ewbNotice = document.getElementById('ewbLegacyNotice');
    var ewbBadge = document.getElementById('ewbArchBadge');
    var autoEwb = document.getElementById('printerAutoEwb');

    if (isOld) {
      if (modelInput && (!modelInput.value || modelInput.value.indexOf('3518A') !== -1 || modelInput.value.indexOf('4508A') !== -1 || modelInput.value.indexOf('2528A') !== -1)) {
        modelInput.value = 'Toshiba e-STUDIO 306SE';
      }
      if (ewbFields) {
        ewbFields.style.opacity = '0.35';
        ewbFields.style.pointerEvents = 'none';
      }
      if (ewbNotice) ewbNotice.style.display = 'block';
      if (ewbBadge) {
        ewbBadge.textContent = 'Toshiba Old (Legacy - RAW Port 9100)';
        ewbBadge.style.background = '#FEF3C7';
        ewbBadge.style.color = '#92400E';
        ewbBadge.style.border = '1px solid #FDE68A';
      }
      if (autoEwb) autoEwb.checked = false;
    } else {
      if (ewbFields) {
        ewbFields.style.opacity = '1';
        ewbFields.style.pointerEvents = 'auto';
      }
      if (ewbNotice) ewbNotice.style.display = 'none';

      if (is08) {
        if (modelInput && (modelInput.value === 'Toshiba e-STUDIO 306SE' || modelInput.value === 'Toshiba e-STUDIO 3518A' || modelInput.value === 'Toshiba e-STUDIO 2528A')) {
          modelInput.value = 'Toshiba e-STUDIO 4508A';
        }
        if (ewbBadge) {
          ewbBadge.textContent = '08 Series (WebKit Safari 538.1)';
          ewbBadge.style.background = '#E0F2FE';
          ewbBadge.style.color = '#0369A1';
          ewbBadge.style.border = '1px solid #BAE6FD';
        }
      } else if (is28) {
        if (modelInput && (modelInput.value === 'Toshiba e-STUDIO 306SE' || modelInput.value === 'Toshiba e-STUDIO 3518A' || modelInput.value === 'Toshiba e-STUDIO 4508A')) {
          modelInput.value = 'Toshiba e-STUDIO 2528A';
        }
        if (ewbBadge) {
          ewbBadge.textContent = '28 Series (Chromium 56 Gen 3)';
          ewbBadge.style.background = '#DCFCE7';
          ewbBadge.style.color = '#15803D';
          ewbBadge.style.border = '1px solid #BBF7D0';
        }
      } else { // 18 Series
        if (modelInput && (modelInput.value === 'Toshiba e-STUDIO 306SE' || modelInput.value === 'Toshiba e-STUDIO 4508A' || modelInput.value === 'Toshiba e-STUDIO 2528A')) {
          modelInput.value = 'Toshiba e-STUDIO 3518A';
        }
        if (ewbBadge) {
          ewbBadge.textContent = '18 Series (QtWebEngine Chromium 56)';
          ewbBadge.style.background = '#EDE9FE';
          ewbBadge.style.color = '#6D28D9';
          ewbBadge.style.border = '1px solid #DDD6FE';
        }
      }
    }
  };

  window.openAddPrinterModal = function () {
    document.getElementById('printerModalTitle').textContent = 'Register New Printer';
    document.getElementById('printerFormId').value = '';
    document.getElementById('printerName').value = '';

    // Auto-calculate next unique printer code (e.g. PCT0001, PCT0002, PCT0003 -> PCT0004)
    var nextNum = 1;
    if (state.printers && state.printers.length) {
      for (var i = 0; i < state.printers.length; i++) {
        var m = (state.printers[i].printerCode || '').match(/^PCT(\d+)$/i);
        if (m) {
          var val = parseInt(m[1], 10);
          if (val >= nextNum) nextNum = val + 1;
        }
      }
    }
    var padded = ('0000' + nextNum).slice(-4);
    document.getElementById('printerCode').value = 'PCT' + padded;
    document.getElementById('printerCode').disabled = false;
    document.getElementById('printerIp').value = '';
    document.getElementById('printerPort').value = '9100';
    document.getElementById('printerLocation').value = '';
    if (document.getElementById('printerDepartment')) document.getElementById('printerDepartment').value = 'Operations';
    document.getElementById('printerModel').value = 'Toshiba e-STUDIO 3518A';
    document.getElementById('printerDeviceGeneration').value = 'TOSHIBA_18';
    document.getElementById('printerDuplex').checked = true;
    document.getElementById('printerColor').checked = false;
    document.getElementById('printerActive').checked = true;

    // EWB fields
    document.getElementById('printerAdminUser').value = 'admin';
    document.getElementById('printerAdminPass').value = '123456';
    document.getElementById('printerTerminalUrl').value = getDefaultTerminalUrl();
    document.getElementById('printerAutoEwb').checked = true;

    window.handleDeviceGenerationChange();

    var logBox = document.getElementById('modalEwbLog');
    if (logBox) { logBox.style.display = 'none'; logBox.textContent = ''; }

    var submitBtn = document.getElementById('printerSubmitBtn');
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Register Printer';
    }
    document.getElementById('printerModal').style.display = 'flex';
  };

  window.openEditPrinterModal = function (printerId) {
    var p = null;
    for (var i = 0; i < state.printers.length; i++) {
      if (state.printers[i].id === printerId) {
        p = state.printers[i];
        break;
      }
    }
    if (!p) return;

    document.getElementById('printerModalTitle').textContent = 'Edit Printer: ' + p.name;
    document.getElementById('printerFormId').value = p.id;
    document.getElementById('printerName').value = p.name;
    document.getElementById('printerCode').value = p.printerCode;
    document.getElementById('printerCode').disabled = true; // Code is unique immutable key
    document.getElementById('printerIp').value = p.ipAddress;
    document.getElementById('printerPort').value = p.rawPort;
    document.getElementById('printerLocation').value = p.location || '';
    document.getElementById('printerModel').value = p.model || 'Toshiba e-STUDIO 3518A';

    var sInfo = resolvePrinterSeries(p);
    document.getElementById('printerDeviceGeneration').value = sInfo.code;

    document.getElementById('printerDuplex').checked = !!p.duplexSupported;
    document.getElementById('printerColor').checked = !!p.colorSupported;
    document.getElementById('printerActive').checked = p.isActive !== false;

    // EWB fields
    document.getElementById('printerAdminUser').value = p.adminUsername || 'admin';
    document.getElementById('printerAdminPass').value = p.adminPassword || '123456';
    document.getElementById('printerTerminalUrl').value = sanitizeTerminalUrl(p.ewbUrl, p.ipAddress);
    document.getElementById('printerAutoEwb').checked = false;

    window.handleDeviceGenerationChange();

    var logBox = document.getElementById('modalEwbLog');
    if (logBox) { logBox.style.display = 'none'; logBox.textContent = ''; }

    document.getElementById('printerSubmitBtn').textContent = 'Update Printer';
    document.getElementById('printerModal').style.display = 'flex';
  };

  window.closePrinterModal = function () {
    document.getElementById('printerModal').style.display = 'none';
  };

  window.testEwbInModal = function () {
    var ip = document.getElementById('printerIp').value.trim();
    var user = document.getElementById('printerAdminUser').value.trim();
    var pass = document.getElementById('printerAdminPass').value.trim();
    var url = document.getElementById('printerTerminalUrl').value.trim();
    var logBox = document.getElementById('modalEwbLog');
    var btn = document.getElementById('btnModalTestEwb');

    if (!ip) {
      showToast('Please enter a Printer IP address first.', 'error');
      return;
    }

    if (btn) btn.disabled = true;
    if (logBox) {
      logBox.style.display = 'block';
      logBox.textContent = '[1/3] Connecting to Toshiba TopAccess at http://' + ip + '/ ...\n';
    }

    fetch('/api/admin/printers/test-ewb-direct', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        printerIp: ip,
        adminUsername: user,
        adminPassword: pass,
        terminalUrl: url
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (btn) btn.disabled = false;
        if (logBox) {
          if (res.success) {
            logBox.textContent += '[2/3] Authenticated as "' + user + '"... STATUS_OK\n' +
                                  '[3/3] Set EWB HomePage & AutoScreenChange... Normal\n' +
                                  'SUCCESS: ' + res.message + '\n' +
                                  'Model: ' + (res.printerModel || 'Toshiba MFP');
            showToast('EWB settings applied to ' + ip, 'success');
          } else {
            logBox.textContent += 'FAILED: ' + res.message + '\n';
            showToast('EWB push failed: ' + res.message, 'error');
          }
        }
      })
      .catch(function (err) {
        if (btn) btn.disabled = false;
        if (logBox) logBox.textContent += 'ERROR: ' + err.message + '\n';
        showToast('EWB connection error: ' + err.message, 'error');
      });
  };

  /* Quick EWB Modal Handlers */
  window.openQuickEwbModal = function (printerId) {
    var p = null;
    for (var i = 0; i < state.printers.length; i++) {
      if (state.printers[i].id === printerId) { p = state.printers[i]; break; }
    }
    if (!p) return;

    document.getElementById('quickEwbPrinterId').value = p.id;
    document.getElementById('quickEwbTitle').textContent = 'Push EWB Settings: ' + p.name;
    document.getElementById('quickEwbIp').value = p.ipAddress;
    document.getElementById('quickEwbUser').value = p.adminUsername || 'admin';
    document.getElementById('quickEwbPass').value = p.adminPassword || '123456';
    document.getElementById('quickEwbUrl').value = sanitizeTerminalUrl(p.ewbUrl, p.ipAddress);
    document.getElementById('quickEwbTopAccessLink').href = 'http://' + p.ipAddress + '/';
    var secLink = document.getElementById('quickEwbSecLink');
    if (secLink) {
      secLink.href = 'http://' + p.ipAddress + '/Administration/Security/Authentication.html';
    }

    var logBox = document.getElementById('quickEwbLog');
    logBox.textContent = 'Device: ' + p.name + ' (' + p.ipAddress + ')\nReady to push EWB Home Page to printer. Click "Push EWB Now" or "Lock HOME Icons".';

    var badge = document.getElementById('quickEwbLockBadge');
    if (badge) {
      if (p.functionsLocked) {
        badge.textContent = 'Status: LOCKED ';
        badge.style.background = '#DCFCE7';
        badge.style.color = '#15803D';
      } else {
        badge.textContent = 'Status: UNLOCKED ';
        badge.style.background = '#FEF3C7';
        badge.style.color = '#92400E';
      }
    }

    var autoLockCb = document.getElementById('quickEwbAutoLock');
    if (autoLockCb) autoLockCb.checked = true;

    document.getElementById('quickEwbModal').style.display = 'flex';
  };

  window.closeQuickEwbModal = function () {
    document.getElementById('quickEwbModal').style.display = 'none';
  };

  window.handleQuickEwbLock = function (lock) {
    var id = document.getElementById('quickEwbPrinterId').value;
    if (!id) return;
    var btn = lock ? document.getElementById('btnQuickEwbLockCopy') : document.getElementById('btnQuickEwbUnlockCopy');
    var logBox = document.getElementById('quickEwbLog');
    var badge = document.getElementById('quickEwbLockBadge');

    if (btn) btn.disabled = true;
    if (logBox) {
      logBox.textContent = (lock
        ? ' [1/2] Connecting to Toshiba TopAccess CWS to LOCK HOME screen icons (Photo Copy)...\n'
        : ' [1/2] Connecting to Toshiba TopAccess CWS to UNLOCK HOME screen functions...\n');
    }

    fetch('/api/admin/printers/' + id + '/lock-functions?lock=' + lock, {
      method: 'POST'
    })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (btn) btn.disabled = false;
        if (res.success) {
          if (logBox) {
            logBox.textContent += '[2/2] Toshiba AuthenticatedTarget restriction updated... STATUS_OK\n' +
                                  'SUCCESS: ' + res.message + '\n' +
                                  (lock
                                    ? 'Toshiba HOME screen icons (Photo Copy & Native functions) are now LOCKED.\nWalk-up users cannot make copies without authenticating.'
                                    : 'Toshiba HOME screen functions are UNLOCKED for open walk-up copying.');
          }
          if (badge) {
            if (lock) {
              badge.textContent = 'Status: LOCKED ';
              badge.style.background = '#DCFCE7';
              badge.style.color = '#15803D';
            } else {
              badge.textContent = 'Status: UNLOCKED ';
              badge.style.background = '#FEF3C7';
              badge.style.color = '#92400E';
            }
          }
          showToast(res.message, 'success');
          loadPrinters();
        } else {
          if (logBox) logBox.textContent += 'FAILED: ' + res.message;
          showToast('Failed: ' + res.message, 'error');
        }
      })
      .catch(function (err) {
        if (btn) btn.disabled = false;
        if (logBox) logBox.textContent += 'ERROR: ' + err.message;
        showToast('Communication error: ' + err.message, 'error');
      });
  };

  window.handleQuickEwbSubmit = function (e) {
    e.preventDefault();
    var id = document.getElementById('quickEwbPrinterId').value;
    var user = document.getElementById('quickEwbUser').value.trim();
    var pass = document.getElementById('quickEwbPass').value.trim();
    var url = document.getElementById('quickEwbUrl').value.trim();
    var autoLock = document.getElementById('quickEwbAutoLock') ? document.getElementById('quickEwbAutoLock').checked : true;
    var btn = document.getElementById('btnQuickEwbSubmit');
    var logBox = document.getElementById('quickEwbLog');
    var badge = document.getElementById('quickEwbLockBadge');

    if (btn) { btn.disabled = true; btn.textContent = 'Pushing...'; }
    if (logBox) {
      logBox.textContent = '[1/4] Connecting to TopAccess XML endpoint...\n';
    }

    fetch('/api/admin/printers/' + id + '/configure-ewb', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        adminUsername: user,
        adminPassword: pass,
        terminalUrl: url,
        lockHomeFunctions: autoLock
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (btn) { btn.disabled = false; btn.textContent = 'Push EWB Now'; }
        if (res.success) {
          logBox.textContent += '[2/4] Administrator login... STATUS_OK\n' +
                                '[3/4] Updating EWBSettings (HomePage, MDS, AutoScreenChange)... Normal\n' +
                                (autoLock ? '[4/4] Restricting HOME Screen Icons (Photo Copy locked)... Normal\n' : '') +
                                'SUCCESS: ' + res.message + '\n' +
                                'Verified Model: ' + (res.printerModel || 'Toshiba MFP');
          if (badge && autoLock) {
            badge.textContent = 'Status: LOCKED ';
            badge.style.background = '#DCFCE7';
            badge.style.color = '#15803D';
          }
          showToast('EWB settings successfully updated on printer!', 'success');
          loadPrinters();
        } else {
          logBox.textContent += 'FAILED: ' + res.message;
          showToast('EWB configuration failed: ' + res.message, 'error');
        }
      })
      .catch(function (err) {
        if (btn) { btn.disabled = false; btn.textContent = 'Push EWB Now'; }
        logBox.textContent += 'ERROR: ' + err.message;
        showToast('Communication error: ' + err.message, 'error');
      });
  };

  window.handleQuickEwbReboot = function () {
    var id = document.getElementById('quickEwbPrinterId').value;
    if (!id) return;

    customConfirm(
      'Restart Toshiba MFP',
      'Reboot the Toshiba MFP controller to apply settings? The machine panel will restart and load the EWB screen.',
      'Reboot MFP',
      'btn-action-delete'
    ).then(function (confirmed) {
      if (!confirmed) return;

      var logBox = document.getElementById('quickEwbLog');
      var btn = document.getElementById('btnQuickEwbReboot');
      if (btn) btn.disabled = true;
      if (logBox) logBox.textContent += '\nSending remote restart signal to Toshiba controller...';

      fetch('/api/admin/printers/' + id + '/reboot', { method: 'POST' })
        .then(function (res) { return res.json(); })
        .then(function (res) {
          if (btn) btn.disabled = false;
          if (res.success) {
            if (logBox) logBox.textContent += '\nREBOOT SIGNAL ACCEPTED: Toshiba MFP controller is restarting.';
            showToast('Toshiba MFP controller restart command accepted!', 'success');
          } else {
            if (logBox) logBox.textContent += '\nREBOOT FAILED: ' + res.message;
            showToast('Failed to restart printer: ' + res.message, 'error');
          }
        })
        .catch(function (err) {
          if (btn) btn.disabled = false;
          if (logBox) logBox.textContent += '\nERROR: ' + err.message;
          showToast('Error sending reboot signal: ' + err.message, 'error');
        });
    });
  };

  window.handleQuickEwbReset = function () {
    var id = document.getElementById('quickEwbPrinterId').value;
    if (!id) return;

    customConfirm(
      'Reset EWB & Restore Home Screen',
      'This will clear Toshiba EWB settings, restore Default Screen back to "Home", and restart the MFP.\n\nProceed with reset?',
      'Reset to Home',
      'btn-action-delete'
    ).then(function (confirmed) {
      if (!confirmed) return;

      var logBox = document.getElementById('quickEwbLog');
      var btn = document.getElementById('btnQuickEwbReset');
      if (btn) btn.disabled = true;
      if (logBox) logBox.textContent += '\nResetting Toshiba EWB and restoring DefaultScreen to Home...';

      fetch('/api/admin/printers/' + id + '/reset-ewb?reboot=true', { method: 'POST' })
        .then(function (res) { return res.json(); })
        .then(function (res) {
          if (btn) btn.disabled = false;
          if (res.success) {
            if (logBox) logBox.textContent += '\nSUCCESS: ' + res.message;
            showToast('Toshiba EWB cleared and DefaultScreen reset to Home!', 'success');
            loadPrinters();
          } else {
            if (logBox) logBox.textContent += '\nFAILED: ' + res.message;
            showToast(res.message, 'error');
          }
        })
        .catch(function (err) {
          if (btn) btn.disabled = false;
          if (logBox) logBox.textContent += '\nERROR: ' + err.message;
          showToast('Error resetting EWB: ' + err.message, 'error');
        });
    });
  };

    /* =========================================================================
     UNIVERSAL ACTION DROPDOWN CONTROLLER
     Breaks out of internal scrollable containers (overflow-y: auto)
     ========================================================================= */
  window.toggleGenericDropdown = function (event, menuId, btnId, customWidth, customHeight) {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    var menu = document.getElementById(menuId);
    var btn = document.getElementById(btnId);
    if (!menu || !btn) return;

    var isOpen = menu.classList.contains('show');

    // Close all open dropdowns
    var allMenus = document.querySelectorAll('.action-dropdown-menu');
    for (var i = 0; i < allMenus.length; i++) {
      allMenus[i].classList.remove('show');
    }
    var allBtns = document.querySelectorAll('.btn-action-dropdown');
    for (var j = 0; j < allBtns.length; j++) {
      allBtns[j].classList.remove('active');
    }

    if (!isOpen) {
      var rect = btn.getBoundingClientRect();
      var menuWidth = customWidth || 210;
      var menuEstimatedHeight = customHeight || 180;

      var spaceBelow = window.innerHeight - rect.bottom;
      var spaceAbove = rect.top;

      if (spaceBelow < menuEstimatedHeight && spaceAbove > spaceBelow) {
        menu.style.top = Math.max(10, rect.top - menuEstimatedHeight) + 'px';
        menu.classList.add('dropup');
      } else {
        menu.style.top = (rect.bottom + 4) + 'px';
        menu.classList.remove('dropup');
      }

      var leftPos = rect.right - menuWidth;
      if (leftPos < 10) leftPos = 10;
      menu.style.left = leftPos + 'px';

      menu.classList.add('show');
      btn.classList.add('active');

      setTimeout(function () {
        if (menu.classList.contains('dropup')) {
          var realH = menu.offsetHeight || menuEstimatedHeight;
          menu.style.top = Math.max(10, rect.top - realH - 4) + 'px';
        }
      }, 0);
    }
  };

  window.togglePrinterDropdown = function (event, printerId) {
    window.toggleGenericDropdown(event, 'dropdown-menu-' + printerId, 'dropdown-btn-' + printerId, 230, 280);
  };

  window.toggleUserDropdown = function (event, userId) {
    window.toggleGenericDropdown(event, 'user-dropdown-menu-' + userId, 'user-dropdown-btn-' + userId, 210, 150);
  };

  window.toggleJobDropdown = function (event, jobId) {
    window.toggleGenericDropdown(event, 'job-dropdown-menu-' + jobId, 'job-dropdown-btn-' + jobId, 190, 120);
  };

  window.lockSinglePrinter = function (printerId, lock) {
    var allMenus = document.querySelectorAll('.action-dropdown-menu');
    for (var i = 0; i < allMenus.length; i++) allMenus[i].classList.remove('show');

    showToast((lock ? 'Locking HOME screen icons...' : 'Unlocking walk-up functions...'), 'info');

    fetch('/api/admin/printers/' + printerId + '/lock-functions?lock=' + lock, {
      method: 'POST'
    })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (res.success) {
          showToast(res.message, 'success');
          loadPrinters();
        } else {
          showToast('Failed: ' + res.message, 'error');
        }
      })
      .catch(function (err) {
        showToast('Communication error: ' + err.message, 'error');
      });
  };

  window.rebootSinglePrinter = function (printerId) {
    var allMenus = document.querySelectorAll('.action-dropdown-menu');
    for (var i = 0; i < allMenus.length; i++) allMenus[i].classList.remove('show');

    customConfirm(
      'Restart Toshiba MFP',
      'Send remote restart command to this Toshiba MFP controller?',
      'Restart MFP',
      'btn-action-delete'
    ).then(function (confirmed) {
      if (!confirmed) return;
      showToast('Sending restart command to Toshiba MFP...', 'info');
      fetch('/api/admin/printers/' + printerId + '/reboot', { method: 'POST' })
        .then(function (res) { return res.json(); })
        .then(function (res) {
          if (res.success) {
            showToast('MFP restart signal accepted!', 'success');
          } else {
            showToast('Failed: ' + res.message, 'error');
          }
        })
        .catch(function (err) {
          showToast('Error: ' + err.message, 'error');
        });
    });
  };

  window.lockAllFleetPrinters = function (lock) {
    customConfirm(
      (lock ? 'Lock All HOME Screens' : 'Unlock All MFP Functions'),
      (lock
        ? 'Enforce User Authentication (openAccess=1) & lock Photo Copy/Scan icons on ALL registered Toshiba MFPs?'
        : 'Unlock walk-up photocopy functions on all registered Toshiba MFPs?'),
      (lock ? 'Lock All Devices' : 'Unlock All'),
      (lock ? 'btn-yellow' : 'btn-secondary')
    ).then(function (confirmed) {
      if (!confirmed) return;
      showToast((lock ? 'Enforcing HOME screen lock across all Toshiba MFPs...' : 'Unlocking functions across all MFPs...'), 'info');
      fetch('/api/admin/printers/lock-all-functions?lock=' + lock, { method: 'POST' })
        .then(function (res) { return res.json(); })
        .then(function (res) {
          showToast(res.message, (res.succeeded && res.succeeded > 0) ? 'success' : 'info');
          loadPrinters();
        })
        .catch(function (err) {
          showToast('Error locking fleet: ' + err.message, 'error');
        });
    });
  };

  window.handleSavePrinter = function (e) {
    e.preventDefault();
    var btn = document.getElementById('printerSubmitBtn');
    if (btn && btn.disabled) return; // Prevent concurrent double-clicks!

    var id = document.getElementById('printerFormId').value;
    var isEdit = !!id;
    var origBtnText = btn ? btn.textContent : (isEdit ? 'Update Printer' : 'Register Printer');

    if (btn) {
      btn.disabled = true;
      btn.textContent = isEdit ? 'Updating Printer...' : 'Registering & Configuring MFP...';
    }

    var payload = {
      printerCode: document.getElementById('printerCode').value.trim(),
      name: document.getElementById('printerName').value.trim(),
      deviceGeneration: document.getElementById('printerDeviceGeneration').value,
      ipAddress: document.getElementById('printerIp').value.trim(),
      rawPort: parseInt(document.getElementById('printerPort').value, 10) || 9100,
      location: document.getElementById('printerLocation').value.trim(),
      model: document.getElementById('printerModel').value.trim(),
      duplexSupported: document.getElementById('printerDuplex').checked,
      colorSupported: document.getElementById('printerColor').checked,
      isActive: document.getElementById('printerActive').checked,
      adminUsername: document.getElementById('printerAdminUser').value.trim(),
      adminPassword: document.getElementById('printerAdminPass').value.trim(),
      terminalUrl: document.getElementById('printerTerminalUrl').value.trim(),
      autoConfigureEwb: document.getElementById('printerAutoEwb').checked
    };

    var url = isEdit ? '/api/admin/printers/' + id : '/api/admin/printers';
    var method = isEdit ? 'PUT' : 'POST';

    fetch(url, {
      method: method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json().then(function (err) { throw new Error(err.error || 'Request failed'); });
        }
        return res.json();
      })
      .then(function (saved) {
        if (btn) {
          btn.disabled = false;
          btn.textContent = origBtnText;
        }
        closePrinterModal();
        var msg = isEdit ? 'Printer updated successfully.' : 'New printer registered successfully.';
        if (saved.ewbConfigured) {
          msg += ' (Toshiba EWB configured automatically!)';
        }
        showToast(msg, 'success');
        loadPrinters();
        loadStats();
      })
      .catch(function (err) {
        if (btn) {
          btn.disabled = false;
          btn.textContent = origBtnText;
        }
        showToast('Error saving printer: ' + err.message, 'error');
      });
  };

  window.deletePrinter = function (id, name) {
    customConfirm(
      'Delete Printer & Reset Device',
      'Are you sure you want to delete printer "' + name + '"?\n\n' +
      'This will also clear Toshiba TopAccess EWB settings, restore Default Screen back to "Home", and restart the MFP.',
      'Delete & Reset',
      'btn-action-delete'
    ).then(function (confirmed) {
      if (!confirmed) return;

      showToast('Deleting printer and resetting Toshiba Home screen...', 'info');

      fetch('/api/admin/printers/' + id + '?resetEwb=true', { method: 'DELETE' })
        .then(function (res) {
          if (!res.ok) throw new Error('HTTP ' + res.status);
          showToast('Printer "' + name + '" deleted and Toshiba reset to Home Screen.', 'success');
          loadPrinters();
          loadStats();
        })
        .catch(function (err) {
          showToast('Failed to delete printer: ' + err.message, 'error');
        });
    });
  };

  window.testPrinter = function (id) {
    var btn = document.getElementById('btn-test-' + id);
    var statusTd = document.getElementById('printer-status-' + id);
    if (btn) {
      btn.disabled = true;
      btn.textContent = 'PROBING...';
    }

    fetch('/api/admin/printers/' + id + '/test', { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (btn) {
          btn.disabled = false;
          btn.textContent = 'TEST';
        }
        if (res.success) {
          if (statusTd) statusTd.innerHTML = '<span class="badge badge-online">ONLINE (' + res.responseTimeMs + 'ms)</span>';
          showToast('Printer test SUCCESS: ' + res.message, 'success');
        } else {
          if (statusTd) statusTd.innerHTML = '<span class="badge badge-offline">OFFLINE</span>';
          showToast('Printer test FAILED: ' + res.message, 'error');
        }
      })
      .catch(function (err) {
        if (btn) {
          btn.disabled = false;
          btn.textContent = 'TEST';
        }
        showToast('Probe request error: ' + err.message, 'error');
      });
  };

  /* =========================================================================
     USER MANAGEMENT
     ========================================================================= */

  window.loadUsers = function () {
    fetch('/api/admin/users')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        state.users = data || [];
        renderUsers();
      })
      .catch(function (err) {
        console.error('Users error:', err);
        var tbody = document.getElementById('usersTableBody');
        if (tbody) {
          tbody.innerHTML = '<tr><td colspan="10" style="text-align:center;color:#ef4444;">Failed to load users: ' + esc(err.message) + '</td></tr>';
        }
      });
  };

    /* =========================================================================
     DEPARTMENT CATEGORIZATION & USER VIEW CONTROLLERS
     ========================================================================= */
  state.userViewMode = 'grouped';
  state.selectedUserDept = 'ALL';

  window.setUserViewMode = function (mode) {
    state.userViewMode = mode;
    var btnGrp = document.getElementById('btnUserViewGroup');
    var btnFlt = document.getElementById('btnUserViewFlat');
    if (btnGrp && btnFlt) {
      if (mode === 'grouped') {
        btnGrp.classList.add('active');
        btnFlt.classList.remove('active');
      } else {
        btnFlt.classList.add('active');
        btnGrp.classList.remove('active');
      }
    }
    renderUsers();
  };

  window.selectUserDeptPill = function (dept) {
    state.selectedUserDept = dept;
    renderUsers();
  };

  state.collapsedDepts = state.collapsedDepts || {};

  window.toggleDeptGroupCollapse = function (headEl) {
    var card = headEl.closest('.dept-group-card');
    if (!card) return;
    var deptKey = headEl.getAttribute('data-dept-key');
    card.classList.toggle('is-collapsed');
    if (deptKey) {
      state.collapsedDepts[deptKey] = card.classList.contains('is-collapsed');
    }
  };

  window.toggleAllDeptGroups = function (prefix, collapse) {
    state.collapsedDepts = state.collapsedDepts || {};
    var cards = document.querySelectorAll('.dept-group-card');
    for (var i = 0; i < cards.length; i++) {
      var head = cards[i].querySelector('.dept-group-head');
      var deptKey = head ? head.getAttribute('data-dept-key') : null;
      if (!prefix || (deptKey && deptKey.indexOf(prefix) === 0)) {
        if (collapse) {
          cards[i].classList.add('is-collapsed');
          if (deptKey) state.collapsedDepts[deptKey] = true;
        } else {
          cards[i].classList.remove('is-collapsed');
          if (deptKey) state.collapsedDepts[deptKey] = false;
        }
      }
    }
  };

  function buildSingleUserRow(u, showDeptCol) {
    var sourceBadge = '<span class="badge badge-source-manual">MANUAL</span>';
    if (u.authSource === 'ACTIVE_DIRECTORY') {
      sourceBadge = '<span class="badge badge-source-ad">AD / LDAP</span>';
    } else if (u.authSource === 'WINDOWS_LOCAL') {
      sourceBadge = '<span class="badge badge-source-win">WINDOWS LOCAL</span>';
    }

    var pinBadge = u.hasPin
      ? '<span class="badge badge-pin-yes">CONFIGURED</span>'
      : '<span class="badge badge-pin-no">NOT SET</span>';

    var statusBadge = u.isActive
      ? '<span class="badge badge-online">ACTIVE</span>'
      : '<span class="badge badge-offline">DISABLED</span>';

    var printPages = (u.printCount != null) ? u.printCount : 0;
    var copyPages = (u.copyCount != null) ? u.copyCount : 0;
    var scanPages = (u.scanCount != null) ? u.scanCount : 0;
    var totalPages = printPages + copyPages;

    var cardHtml = '';
    if (u.badgeId) cardHtml += '<span class="badge" style="background:#eff6ff; color:#1d4ed8; font-weight:700; margin-right:4px;">RFID: ' + esc(u.badgeId) + '</span>';
    if (u.secondaryBadgeId) cardHtml += '<span class="badge" style="background:#f0fdf4; color:#15803d; font-weight:700;">NFC: ' + esc(u.secondaryBadgeId) + '</span>';
    if (!cardHtml) cardHtml = '<span style="color:#94a3b8;">None</span>';

    var extAttrs = [];
    if (u.externalAttribute1) extAttrs.push('<span class="badge" style="background:#F1F5F9; color:#334155; border:1px solid #CBD5E1; font-size:10.5px; font-weight:600;">Ext1: ' + esc(u.externalAttribute1) + '</span>');
    if (u.externalAttribute2) extAttrs.push('<span class="badge" style="background:#F1F5F9; color:#334155; border:1px solid #CBD5E1; font-size:10.5px; font-weight:600;">Ext2: ' + esc(u.externalAttribute2) + '</span>');
    if (u.externalAttribute3) extAttrs.push('<span class="badge" style="background:#F1F5F9; color:#334155; border:1px solid #CBD5E1; font-size:10.5px; font-weight:600;">Ext3: ' + esc(u.externalAttribute3) + '</span>');
    if (extAttrs.length > 0) {
      cardHtml += '<div style="margin-top:4px;display:flex;gap:4px;flex-wrap:wrap;">' + extAttrs.join(' ') + '</div>';
    }

    var userDropdown =
      '<div class="action-dropdown-wrap" id="user-action-wrap-' + u.id + '">' +
        '<button type="button" class="btn-action-dropdown" onclick="window.toggleUserDropdown(event, ' + u.id + ')" id="user-dropdown-btn-' + u.id + '">' +
          'Actions ' +
        '</button>' +
        '<div class="action-dropdown-menu" id="user-dropdown-menu-' + u.id + '">' +
          '<button type="button" class="action-dropdown-item item-primary" onclick="window.viewUserDetails(' + u.id + ')">' +
            '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="10" r="3"/><path d="M7 20.662V19a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v1.662"/></svg>' +
            'View User Profile' +
          '</button>' +
          '<button type="button" class="action-dropdown-item" onclick="openResetPinModal(' + u.id + ', \'' + esc(u.username) + '\')">' +
            '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>' +
            'Configure Touch PIN' +
          '</button>' +
          '<button type="button" class="action-dropdown-item" onclick="openEditUserModal(' + u.id + ')">' +
            '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>' +
            'Edit User Details' +
          '</button>' +
          '<div class="action-dropdown-divider"></div>' +
          '<button type="button" class="action-dropdown-item item-danger" onclick="deleteUser(' + u.id + ', \'' + esc(u.username) + '\')">' +
            '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' +
            'Delete User' +
          '</button>' +
        '</div>' +
      '</div>';

    var deptCell = showDeptCol ? '<td>' + esc(u.department || 'Operations') + '</td>' : '';

    return '<tr>' +
      '<td><a href="javascript:void(0)" onclick="window.viewUserDetails(' + u.id + ')" style="font-weight:700; color:#1D4ED8; text-decoration:none;" title="Click to view full user profile">' + esc(u.username) + '</a></td>' +
      '<td>' + esc(u.displayName) + '</td>' +
      deptCell +
      '<td>' + (u.costCenter ? ('<span class="badge" style="background:#fef3c7; color:#92400e; font-weight:700;">' + esc(u.costCenter) + '</span>') : '<span style="color:#94a3b8;">-</span>') + '</td>' +
      '<td>' + sourceBadge + '</td>' +
      '<td>' + pinBadge + '</td>' +
      '<td><span class="badge" style="background:#EFF6FF; color:#1D4ED8; font-weight:700; border:1px solid #BFDBFE;">PRINT: ' + printPages + '</span></td>' +
      '<td><span class="badge" style="background:#F0FDF4; color:#15803D; font-weight:700; border:1px solid #BBF7D0;">COPY: ' + copyPages + '</span></td>' +
      '<td><span class="badge" style="background:#EEF2FF; color:#4F46E5; font-weight:700; border:1px solid #C7D2FE;">SCAN: ' + scanPages + '</span></td>' +
      '<td><strong style="color:var(--text-dark); font-size:13px;">' + totalPages + '</strong></td>' +
      '<td>' + statusBadge + '</td>' +
      '<td style="text-align:right; white-space:nowrap; position:relative;">' + userDropdown + '</td>' +
    '</tr>';
  }

  function renderUsers() {
    var container = document.getElementById('usersListContainer');
    var fallbackTbody = document.getElementById('usersTableBody');
    if (!container && !fallbackTbody) return;

    if (state.users.length === 0) {
      if (container) {
        container.innerHTML = '<div class="card p-4 text-center text-muted">No users registered yet. Click "+ Add New User" to register one.</div>';
      } else if (fallbackTbody) {
        fallbackTbody.innerHTML = '<tr><td colspan="12" style="text-align:center;padding:30px;color:#6B7299;">No users registered yet.</td></tr>';
      }
      return;
    }

    // 1. Build Department Pills Strip
    var deptCounts = {};
    for (var i = 0; i < state.users.length; i++) {
      var d = state.users[i].department || 'Operations';
      deptCounts[d] = (deptCounts[d] || 0) + 1;
    }

    var pillsContainer = document.getElementById('userDeptPillsContainer');
    if (pillsContainer) {
      var pillsHtml = '<button type="button" class="dept-pill-btn' + (state.selectedUserDept === 'ALL' ? ' active' : '') + '" onclick="window.selectUserDeptPill(\'ALL\')">All Departments <span class="pill-count">' + state.users.length + '</span></button>';
      var depts = Object.keys(deptCounts).sort();
      for (var k = 0; k < depts.length; k++) {
        var dName = depts[k];
        var count = deptCounts[dName];
        pillsHtml += '<button type="button" class="dept-pill-btn' + (state.selectedUserDept === dName ? ' active' : '') + '" onclick="window.selectUserDeptPill(\'' + esc(dName) + '\')">' + esc(dName) + ' <span class="pill-count">' + count + '</span></button>';
      }
      pillsContainer.innerHTML = pillsHtml;
    }

    // 2. Filter users by search and selected department
    var searchInput = document.getElementById('userSearchInput');
    var searchVal = (searchInput ? searchInput.value : '').toLowerCase().trim();

    var filtered = state.users.filter(function (u) {
      var uDept = u.department || 'Operations';
      if (state.selectedUserDept !== 'ALL' && uDept !== state.selectedUserDept) return false;
      if (!searchVal) return true;
      return (u.username || '').toLowerCase().indexOf(searchVal) !== -1 ||
             (u.displayName || '').toLowerCase().indexOf(searchVal) !== -1 ||
             (u.badgeId || '').toLowerCase().indexOf(searchVal) !== -1 ||
             (u.secondaryBadgeId || '').toLowerCase().indexOf(searchVal) !== -1 ||
             (u.costCenter || '').toLowerCase().indexOf(searchVal) !== -1 ||
             uDept.toLowerCase().indexOf(searchVal) !== -1;
    });

    if (filtered.length === 0) {
      if (container) {
        container.innerHTML = '<div class="card p-4 text-center text-muted">No users matching "' + esc(searchVal || state.selectedUserDept) + '".</div>';
      }
      return;
    }

    // 3. Render Grouped vs Flat
    if (!container || state.userViewMode === 'flat') {
      var tableHtml =
        '<div class="card">' +
          '<div class="table-wrap">' +
            '<table>' +
              '<thead>' +
                '<tr>' +
                  '<th>Username</th>' +
                  '<th>Display Name</th>' +
                  '<th>Department</th>' +
                  '<th>Cost Center</th>' +
                  '<th>Security Role</th>' +
                  '<th>Touch PIN</th>' +
                  '<th>Print Pages</th>' +
                  '<th>Copy Pages</th>' +
                  '<th>Scan Docs</th>' +
                  '<th>Total Pages</th>' +
                  '<th>Account Status</th>' +
                  '<th style="text-align:right;">Actions</th>' +
                '</tr>' +
              '</thead>' +
              '<tbody>' +
                filtered.map(function (u) { return buildSingleUserRow(u, true); }).join('') +
              '</tbody>' +
            '</table>' +
          '</div>' +
        '</div>';
      if (container) container.innerHTML = tableHtml;
      return;
    }

    // Grouped by Department Mode
    var grouped = {};
    for (var j = 0; j < filtered.length; j++) {
      var usr = filtered[j];
      var usrDept = usr.department || 'Operations';
      if (!grouped[usrDept]) grouped[usrDept] = [];
      grouped[usrDept].push(usr);
    }

    var groupKeys = Object.keys(grouped).sort();
    var outHtml = '';

    for (var g = 0; g < groupKeys.length; g++) {
      var grpDept = groupKeys[g];
      var list = grouped[grpDept];
      
      var grpPrint = 0, grpCopy = 0, grpScan = 0;
      var ccs = {};
      for (var uIdx = 0; uIdx < list.length; uIdx++) {
        grpPrint += (list[uIdx].printCount || 0);
        grpCopy += (list[uIdx].copyCount || 0);
        grpScan += (list[uIdx].scanCount || 0);
        if (list[uIdx].costCenter) ccs[list[uIdx].costCenter] = true;
      }
      var grpTotal = grpPrint + grpCopy;
      var ccList = Object.keys(ccs).join(', ') || 'Unassigned';

      var isUserCollapsed = (state.collapsedDepts && state.collapsedDepts['user_' + grpDept]) ? ' is-collapsed' : '';

      outHtml +=
        '<div class="card dept-group-card shadow-sm' + isUserCollapsed + '" id="user-dept-card-' + esc(grpDept).replace(/[^a-zA-Z0-9]/g, '_') + '">' +
          '<div class="dept-group-head" onclick="window.toggleDeptGroupCollapse(this)" data-dept-key="user_' + esc(grpDept) + '" title="Click to collapse / expand ' + esc(grpDept) + ' department users">' +
            '<div class="d-flex align-items-center gap-2 flex-wrap">' +
              '<span class="dept-collapse-toggle"><svg class="dept-collapse-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg></span>' +
              '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--primary); flex-shrink:0;"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"/><rect x="2" y="14" width="20" height="8" rx="2" ry="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>' +
              '<strong style="font-size:13.5px; color:var(--text-dark);">' + esc(grpDept) + ' Department</strong>' +
              '<span class="badge bg-primary-subtle text-primary border border-primary-subtle ms-2">' + list.length + ' ' + (list.length === 1 ? 'User' : 'Users') + '</span>' +
              '<span class="badge bg-light text-secondary border">Cost Center: ' + esc(ccList) + '</span>' +
            '</div>' +
            '<div class="d-flex align-items-center gap-3">' +
              '<div class="text-secondary small fw-semibold">' +
                'Volume: <span class="text-primary fw-bold">' + grpPrint + ' Print</span> | <span class="text-success fw-bold">' + grpCopy + ' Copy</span> | <span class="fw-bold" style="color:#4F46E5;">' + grpScan + ' Scan</span> &bull; Total Paper: <strong>' + grpTotal + ' Pages</strong>' +
              '</div>' +
              '<span class="text-muted small" style="font-size:11px; user-select:none;">(click to collapse)</span>' +
            '</div>' +
          '</div>' +
          '<div class="table-wrap" style="max-height:360px;">' +
            '<table>' +
              '<thead>' +
                '<tr>' +
                  '<th>Username</th>' +
                  '<th>Display Name</th>' +
                  '<th>Cost Center</th>' +
                  '<th>Security Role</th>' +
                  '<th>Touch PIN</th>' +
                  '<th>Print Pages</th>' +
                  '<th>Copy Pages</th>' +
                  '<th>Scan Docs</th>' +
                  '<th>Total Pages</th>' +
                  '<th>Account Status</th>' +
                  '<th style="text-align:right;">Actions</th>' +
                '</tr>' +
              '</thead>' +
              '<tbody>' +
                list.map(function (u) { return buildSingleUserRow(u, false); }).join('') +
              '</tbody>' +
            '</table>' +
          '</div>' +
        '</div>';
    }

    container.innerHTML = outHtml;
  }

  window.openAddUserModal = function () {
    document.getElementById('userModalTitle').textContent = 'Add New User';
    document.getElementById('userFormId').value = '';
    document.getElementById('userUsername').value = '';
    document.getElementById('userUsername').disabled = false;
    document.getElementById('userDisplayName').value = '';
    document.getElementById('userDepartment').value = 'Operations';
    if (document.getElementById('userCostCenter')) document.getElementById('userCostCenter').value = '';
    document.getElementById('userRole').value = 'USER';
    document.getElementById('userPin').value = '';
    document.getElementById('userPin').required = false;
    document.getElementById('userPinHint').textContent = 'PIN entered at the Toshiba touchscreen (e.g. 1234)';
    var passElem = document.getElementById('userPassword');
    if (passElem) passElem.value = '';
    var passHint = document.getElementById('userPasswordHint');
    if (passHint) passHint.textContent = 'Password for login at MFP touchscreen';
    document.getElementById('userBadge').value = '';
    var secBadge = document.getElementById('userSecondaryBadge');
    if (secBadge) secBadge.value = '';
    document.getElementById('userEmail').value = '';
    document.getElementById('userSid').value = '';
    document.getElementById('userActive').checked = true;
    document.getElementById('userSubmitBtn').textContent = 'Add User';

    document.getElementById('userModal').style.display = 'flex';
  };

  window.openEditUserModal = function (userId) {
    var u = null;
    for (var i = 0; i < state.users.length; i++) {
      if (state.users[i].id === userId) {
        u = state.users[i];
        break;
      }
    }
    if (!u) return;

    document.getElementById('userModalTitle').textContent = 'Edit User: ' + u.username;
    document.getElementById('userFormId').value = u.id;
    document.getElementById('userUsername').value = u.username;
    document.getElementById('userUsername').disabled = true; // Username is immutable
    document.getElementById('userDisplayName').value = u.displayName;
    document.getElementById('userDepartment').value = u.department || '';
    if (document.getElementById('userCostCenter')) document.getElementById('userCostCenter').value = u.costCenter || u.externalAttribute3 || '';
    document.getElementById('userRole').value = u.role || 'USER';
    document.getElementById('userPin').value = '';
    document.getElementById('userPinHint').textContent = 'Leave empty to keep existing PIN unchanged.';
    var passElem = document.getElementById('userPassword');
    if (passElem) passElem.value = '';
    var passHint = document.getElementById('userPasswordHint');
    if (passHint) passHint.textContent = 'Leave empty to keep existing password unchanged.';
    document.getElementById('userBadge').value = u.badgeId || '';
    var secBadge = document.getElementById('userSecondaryBadge');
    if (secBadge) secBadge.value = u.secondaryBadgeId || '';
    document.getElementById('userEmail').value = u.email || '';
    document.getElementById('userSid').value = u.userSid || '';
    document.getElementById('userActive').checked = u.isActive !== false;
    document.getElementById('userSubmitBtn').textContent = 'Update User';

    document.getElementById('userModal').style.display = 'flex';
  };

  window.closeUserModal = function () {
    document.getElementById('userModal').style.display = 'none';
  };

  window.handleSaveUser = function (e) {
    e.preventDefault();
    var id = document.getElementById('userFormId').value;
    var isEdit = !!id;

    var passElem = document.getElementById('userPassword');
    var secBadgeElem = document.getElementById('userSecondaryBadge');

    var payload = {
      username: document.getElementById('userUsername').value.trim(),
      displayName: document.getElementById('userDisplayName').value.trim(),
      department: document.getElementById('userDepartment').value.trim(),
      costCenter: (document.getElementById('userCostCenter') && document.getElementById('userCostCenter').value.trim()) ? document.getElementById('userCostCenter').value.trim() : null,
      role: document.getElementById('userRole').value,
      pin: document.getElementById('userPin').value.trim() || null,
      password: (passElem && passElem.value.trim()) ? passElem.value.trim() : null,
      badgeId: document.getElementById('userBadge').value.trim() || null,
      secondaryBadgeId: (secBadgeElem && secBadgeElem.value.trim()) ? secBadgeElem.value.trim() : null,
      email: document.getElementById('userEmail').value.trim() || null,
      userSid: document.getElementById('userSid').value.trim() || null,
      isActive: document.getElementById('userActive').checked
    };

    var url = isEdit ? '/api/admin/users/' + id : '/api/admin/users';
    var method = isEdit ? 'PUT' : 'POST';

    fetch(url, {
      method: method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (res) {
        if (!res.ok) {
          return res.json().then(function (err) { throw new Error(err.error || 'Request failed'); });
        }
        return res.json();
      })
      .then(function () {
        closeUserModal();
        showToast(isEdit ? 'User updated successfully.' : 'New user created successfully.', 'success');
        loadUsers();
      loadDirectoryConfig();
        loadStats();
      })
      .catch(function (err) {
        showToast('Error saving user: ' + err.message, 'error');
      });
  };

  window.deleteUser = function (id, username) {
    customConfirm(
      'Delete User Account',
      'Are you sure you want to delete user account "' + username + '"?',
      'Delete User',
      'btn-action-delete'
    ).then(function (confirmed) {
      if (!confirmed) return;

      fetch('/api/admin/users/' + id, { method: 'DELETE' })
        .then(function (res) {
          if (!res.ok) throw new Error('HTTP ' + res.status);
          showToast('User "' + username + '" deleted successfully.', 'success');
          loadUsers();
      loadDirectoryConfig();
          loadStats();
        })
        .catch(function (err) {
          showToast('Failed to delete user: ' + err.message, 'error');
        });
    });
  };

  window.openResetPinModal = function (userId, username) {
    document.getElementById('resetPinUserId').value = userId;
    document.getElementById('resetPinUsername').textContent = username;
    document.getElementById('newPinInput').value = '';
    document.getElementById('resetPinModal').style.display = 'flex';
  };

  window.closeResetPinModal = function () {
    document.getElementById('resetPinModal').style.display = 'none';
  };

  window.handleSavePin = function (e) {
    e.preventDefault();
    var userId = document.getElementById('resetPinUserId').value;
    var newPin = document.getElementById('newPinInput').value.trim();

    if (!newPin || newPin.length < 4) {
      showToast('PIN must be at least 4 digits.', 'error');
      return;
    }

    fetch('/api/admin/users/' + userId + '/reset-pin', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin: newPin })
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function () {
        closeResetPinModal();
        showToast('PIN reset successfully.', 'success');
        loadUsers();
      loadDirectoryConfig();
      })
      .catch(function (err) {
        showToast('Failed to reset PIN: ' + err.message, 'error');
      });
  };

  /* =========================================================================
     ACTIVE DIRECTORY & LDAP INTEGRATION (PaperCut Find-Me Directory Sync)
     ========================================================================= */

  window.loadDirectoryConfig = function () {
    fetch('/api/admin/directory/config')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (cfg) {
        state.directoryConfig = cfg;
        if (!state.printers || state.printers.length === 0) { loadPrinters(); } else { window.populateDirPrintersDropdown(); }
        var elDomain = document.getElementById('dirDomainName');
        var elLdapUrl = document.getElementById('dirLdapUrl');
        var elPin = document.getElementById('dirDefaultPin');
        var elSyncInfo = document.getElementById('dirLastSyncInfo');
        var elBadge = document.getElementById('dirSyncBadge');

        if (elDomain) elDomain.textContent = cfg.domainName || 'qoa.local';
        if (elLdapUrl) elLdapUrl.textContent = cfg.ldapUrl || 'ldap://127.0.0.1:389';
        if (elPin) elPin.textContent = cfg.defaultPin || '1234';

        if (elSyncInfo) {
          if (cfg.lastSyncAt) {
            elSyncInfo.textContent = 'Last synchronized ' + new Date(cfg.lastSyncAt).toLocaleString() +
              ' (' + (cfg.lastSyncStatus || 'SUCCESS') + ', ' + (cfg.lastSyncedUsersCount || 0) + ' accounts)';
          } else {
            elSyncInfo.textContent = 'Ready to sync with Domain Controller';
          }
        }

        if (elBadge) {
          if (cfg.lastSyncStatus === 'SUCCESS') {
            elBadge.className = 'badge badge-online';
            elBadge.textContent = 'AD Synced (' + (cfg.lastSyncedUsersCount || 0) + ' users)';
          } else if (cfg.lastSyncStatus === 'FAILED') {
            elBadge.className = 'badge badge-offline';
            elBadge.textContent = 'Sync Failed';
          } else {
            elBadge.className = 'badge badge-online';
            elBadge.textContent = 'Active Directory Ready';
          }
        }

        var inpUrl = document.getElementById('ldapUrlInput');
        var inpDomain = document.getElementById('ldapDomainInput');
        var inpBaseDn = document.getElementById('ldapBaseDnInput');
        var inpBindUser = document.getElementById('ldapBindUserInput');
        var inpDefaultPin = document.getElementById('ldapDefaultPinInput');
        var inpFilter = document.getElementById('ldapFilterInput');

        if (inpUrl) inpUrl.value = cfg.ldapUrl || 'ldap://127.0.0.1:389';
        if (inpDomain) inpDomain.value = cfg.domainName || 'qoa.local';
        if (inpBaseDn) inpBaseDn.value = cfg.baseDn || 'DC=qoa,DC=local';
        if (inpBindUser) inpBindUser.value = cfg.bindUsername || '';
        if (inpDefaultPin) inpDefaultPin.value = cfg.defaultPin || '1234';
        if (inpFilter) inpFilter.value = cfg.userFilter || '(&(objectCategory=person)(objectClass=user))';
      })
      .catch(function (err) {
        console.warn('Directory config error:', err);
      });
  };

  window.openDirectoryModal = function () {
    loadDirectoryConfig();
    var modal = document.getElementById('modalDirectorySync');
    if (modal) modal.style.display = 'flex';
  };

  window.closeDirectoryModal = function () {
    var modal = document.getElementById('modalDirectorySync');
    if (modal) modal.style.display = 'none';
  };

  window.handleSaveDirectoryConfig = function (e) {
    if (e && e.preventDefault) e.preventDefault();
    var payload = {
      ldapUrl: document.getElementById('ldapUrlInput').value.trim(),
      domainName: document.getElementById('ldapDomainInput').value.trim(),
      baseDn: document.getElementById('ldapBaseDnInput').value.trim(),
      bindUsername: document.getElementById('ldapBindUserInput').value.trim(),
      bindPassword: document.getElementById('ldapBindPassInput').value,
      defaultPin: document.getElementById('ldapDefaultPinInput').value.trim() || '1234',
      userFilter: document.getElementById('ldapFilterInput').value.trim(),
      autoProvisionOnPrint: true
    };

    var logBox = document.getElementById('dirSyncLogBox');
    if (logBox) logBox.textContent = 'Saving Active Directory & LDAP configuration...';

    fetch('/api/admin/directory/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (saved) {
        showToast('Directory settings saved successfully.', 'success');
        if (logBox) logBox.textContent = 'Directory settings successfully saved.\nDomain: ' + saved.domainName + '\nLDAP URL: ' + saved.ldapUrl;
        loadDirectoryConfig();
      })
      .catch(function (err) {
        showToast('Failed to save directory config: ' + err.message, 'error');
        if (logBox) logBox.textContent = 'ERROR saving config: ' + err.message;
      });
  };

  window.handleTestLdapConnection = function () {
    var btn = document.getElementById('btnTestLdap');
    var logBox = document.getElementById('dirSyncLogBox');
    if (btn) btn.disabled = true;
    if (logBox) logBox.textContent = 'Probing LDAP / Active Directory connection to ' + document.getElementById('ldapUrlInput').value.trim() + '...';

    var payload = {
      ldapUrl: document.getElementById('ldapUrlInput').value.trim(),
      domainName: document.getElementById('ldapDomainInput').value.trim(),
      baseDn: document.getElementById('ldapBaseDnInput').value.trim(),
      bindUsername: document.getElementById('ldapBindUserInput').value.trim(),
      bindPassword: document.getElementById('ldapBindPassInput').value,
      defaultPin: document.getElementById('ldapDefaultPinInput').value.trim() || '1234',
      userFilter: document.getElementById('ldapFilterInput').value.trim(),
      autoProvisionOnPrint: true
    };

    fetch('/api/admin/directory/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (btn) btn.disabled = false;
        if (res.connected) {
          if (logBox) {
            logBox.textContent = 'SUCCESS: ' + res.message + '\n' +
              'Response latency: ' + res.responseTimeMs + 'ms\n' +
              'Discovered AD Accounts: ' + (res.discoveredUsersCount || 0);
          }
          showToast(res.message, 'success');
        } else {
          if (logBox) {
            logBox.textContent = 'FAILED TO CONNECT: ' + res.message + '\nError: ' + (res.errorDetails || 'Unknown error');
          }
          showToast('Directory test failed: ' + res.message, 'error');
        }
      })
      .catch(function (err) {
        if (btn) btn.disabled = false;
        if (logBox) logBox.textContent = 'ERROR during LDAP probe: ' + err.message;
        showToast('Probe request failed: ' + err.message, 'error');
      });
  };

  window.handleSyncActiveDirectory = function () {
    window.syncLdapUsersWithLoading();
  };

  window.handleImportWindowsUsers = function () {
    customConfirm(
      'Import Windows Local Users',
      'This will query all Windows local user accounts on this computer and synchronize them with RR Secure Print. Continue?',
      'Import Windows Users',
      'btn-action-primary'
    ).then(function (confirmed) {
      if (!confirmed) return;

      showToast('Importing local Windows users...', 'info');
      fetch('/api/admin/directory/import-windows', { method: 'POST' })
        .then(function (res) { return res.json(); })
        .then(function (res) {
          if (res.success) {
            showToast('Windows users imported! ' + res.importedCount + ' added, ' + res.updatedCount + ' updated.', 'success');
            loadUsers();
            loadStats();
          } else {
            showToast('Import failed: ' + res.message, 'error');
          }
        })
        .catch(function (err) {
          showToast('Error importing Windows users: ' + err.message, 'error');
        });
    });
  };

  /* =========================================================================
     PRINT QUEUE
     ========================================================================= */

  window.loadJobs = function () {
    fetch('/api/admin/jobs')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        state.jobs = data || [];
        renderJobs();
        renderDashboardJobs();
      })
      .catch(function (err) {
        console.error('Jobs error:', err);
        var tbody = document.getElementById('jobsTableBody');
        if (tbody) {
          tbody.innerHTML = '<tr><td colspan="10" style="text-align:center;color:#ef4444;">Failed to load jobs: ' + esc(err.message) + '</td></tr>';
        }
      });
  };

  window.renderJobs = function () {
    var tbody = document.getElementById('jobsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    var filter = 'ALL';
    var filterEl = document.getElementById('queueStatusFilter');
    if (filterEl) filter = filterEl.value;

    var filtered = [];
    for (var i = 0; i < state.jobs.length; i++) {
      if (filter === 'ALL' || state.jobs[i].status === filter) {
        filtered.push(state.jobs[i]);
      }
    }

    if (filtered.length === 0) {
      tbody.innerHTML = '<tr><td colspan="10" style="text-align:center;padding:30px;color:#6B7299;">No print jobs found matching filter "' + esc(filter) + '".</td></tr>';
      return;
    }

    for (var j = 0; j < filtered.length; j++) {
      var job = filtered[j];
      var tr = document.createElement('tr');

      var statusBadge = getJobStatusBadge(job.status);
      var attrs = (job.color ? 'Color' : 'B&W') + ' | ' + (job.duplex ? '2-Sided' : '1-Sided');

      var actions = '';
      if (job.status === 'HELD') {
        actions =
          '<div class="action-dropdown-wrap" id="job-action-wrap-' + esc(job.id) + '">' +
            '<button type="button" class="btn-action-dropdown" onclick="window.toggleJobDropdown(event, \'' + esc(job.id) + '\')" id="job-dropdown-btn-' + esc(job.id) + '">' +
              'Actions <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"/></svg>' +
            '</button>' +
            '<div class="action-dropdown-menu" id="job-dropdown-menu-' + esc(job.id) + '">' +
              '<button type="button" class="action-dropdown-item item-primary" onclick="releaseJobAdmin(\'' + esc(job.id) + '\')">' +
                '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>' +
                'Release Job Now' +
              '</button>' +
              '<div class="action-dropdown-divider"></div>' +
              '<button type="button" class="action-dropdown-item item-danger" onclick="cancelJobAdmin(\'' + esc(job.id) + '\')">' +
                '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>' +
                'Cancel &amp; Delete Job' +
              '</button>' +
            '</div>' +
          '</div>';
      } else {
        actions = '<span class="badge bg-light text-muted border">Completed</span>';
      }

      tr.innerHTML =
        '<td><strong>#' + (job.jobNumber || j + 1) + '</strong></td>' +
        '<td>' + esc(job.documentName) + '</td>' +
        '<td><strong>' + esc(job.username) + '</strong></td>' +
        '<td>' + esc(job.targetPrinterName || 'Toshiba 3518A') + '</td>' +
        '<td>' + job.pageCount + ' ' + (job.pageCount === 1 ? 'page' : 'pages') + '</td>' +
        '<td>' + esc(attrs) + '</td>' +
        '<td>' + esc(job.formattedFileSize || formatBytes(job.fileSizeBytes)) + '</td>' +
        '<td>' + statusBadge + '</td>' +
        '<td>' + formatTime(job.submittedAt) + '</td>' +
        '<td style="text-align:right; white-space:nowrap;">' + actions + '</td>';

      tbody.appendChild(tr);
    }
  };

  function renderDashboardJobs() {
    var tbody = document.getElementById('dashboardJobsTbody') || document.getElementById('dashboardJobbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (state.jobs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:24px;color:#6B7299;">No print jobs in system.</td></tr>';
      return;
    }

    var recent = state.jobs.slice(0, 5);
    for (var i = 0; i < recent.length; i++) {
      var job = recent[i];
      var tr = document.createElement('tr');
      tr.innerHTML =
        '<td><strong>#' + (job.jobNumber || i + 1) + '</strong></td>' +
        '<td>' + esc(job.documentName) + '</td>' +
        '<td>' + esc(job.username) + '</td>' +
        '<td>' + esc(job.targetPrinterName || 'Toshiba 3518A') + '</td>' +
        '<td>' + job.pageCount + '</td>' +
        '<td>' + esc(job.formattedFileSize || formatBytes(job.fileSizeBytes)) + '</td>' +
        '<td>' + getJobStatusBadge(job.status) + '</td>' +
        '<td>' + formatTime(job.submittedAt) + '</td>';
      tbody.appendChild(tr);
    }
  }

  window.releaseJobAdmin = function (jobId) {
    customConfirm('Force Release Job', 'Release this document to the physical printer now?', 'Release Now', 'btn-primary')
      .then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/jobs/' + jobId + '/release', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ printerCode: 'TOSHIBA-3518A-HQ' })
        })
          .then(function (res) { return res.json(); })
          .then(function (res) {
            if (res.success) {
              showToast('Job released successfully: ' + res.message, 'success');
              loadJobs();
              loadStats();
            } else {
              showToast('Release failed: ' + res.message, 'error');
            }
          })
          .catch(function (err) {
            showToast('Request error: ' + err.message, 'error');
          });
      });
  };

  window.cancelJobAdmin = function (jobId) {
    customConfirm('Cancel Print Job', 'Are you sure you want to cancel and delete this job?', 'Cancel Job', 'btn-action-delete')
      .then(function (confirmed) {
        if (!confirmed) return;
        fetch('/api/jobs/' + jobId, { method: 'DELETE' })
          .then(function (res) {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            showToast('Job cancelled and purged from queue.', 'success');
            loadJobs();
            loadStats();
          })
          .catch(function (err) {
            showToast('Cancellation error: ' + err.message, 'error');
          });
      });
  };

  /* =========================================================================
     SECURITY AUDIT TRAIL
     ========================================================================= */

  window.loadAudit = function () {
    fetch('/api/admin/audit')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        state.auditLogs = data || [];
        renderAudit();
      })
      .catch(function (err) {
        console.error('Audit error:', err);
        var tbody = document.getElementById('auditTableBody');
        if (tbody) {
          tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#ef4444;">Failed to load audit logs: ' + esc(err.message) + '</td></tr>';
        }
      });
  };

  function renderAudit() {
    var tbody = document.getElementById('auditTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (state.auditLogs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:30px;color:#6B7299;">No security audit events recorded.</td></tr>';
      return;
    }

    for (var i = 0; i < state.auditLogs.length; i++) {
      var log = state.auditLogs[i];
      var tr = document.createElement('tr');

      var statusBadge = log.status === 'SUCCESS'
        ? '<span class="badge badge-online">SUCCESS</span>'
        : '<span class="badge badge-offline">' + esc(log.status || 'FAILED') + '</span>';

      tr.innerHTML =
        '<td style="white-space:nowrap; font-size:11px; color:#475569;">' + formatTime(log.timestamp) + '</td>' +
        '<td><strong>' + esc(log.eventType) + '</strong></td>' +
        '<td>' + esc(log.username || 'System') + '</td>' +
        '<td>' + esc(log.printerCode || 'Server') + '</td>' +
        '<td>' + esc(log.actionDetails || '-') + '</td>' +
        '<td><code>' + esc(log.ipAddress || '127.0.0.1') + '</code></td>' +
        '<td>' + statusBadge + '</td>';

      tbody.appendChild(tr);
    }
  }

  /* =========================================================================
     WALK-UP PHOTOCOPY LOGS (TOSHIBA E-BRIDGE NEXT)
     ========================================================================= */

  window.loadCopyJobs = function () {
    fetch('/api/admin/copy-jobs')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        state.copyJobs = data || [];
        renderCopyJobs();
      })
      .catch(function (err) {
        console.error('Copy jobs error:', err);
        var tbody = document.getElementById('copyJobsTableBody');
        if (tbody) {
          tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:#ef4444;">Failed to load photocopy logs: ' + esc(err.message) + '</td></tr>';
        }
      });
  };

  function renderCopyJobs() {
    var tbody = document.getElementById('copyJobsTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (!state.copyJobs || state.copyJobs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:30px;color:#6B7299;">No walk-up photocopy jobs recorded yet. Polling runs every 60s.</td></tr>';
      return;
    }

    for (var i = 0; i < state.copyJobs.length; i++) {
      var job = state.copyJobs[i];
      var tr = document.createElement('tr');

      tr.innerHTML =
        '<td style="white-space:nowrap; font-size:11px; color:#475569;">' + formatTime(job.completedAt || job.createdAt) + '</td>' +
        '<td><strong>' + esc(job.username || 'Anonymous') + '</strong></td>' +
        '<td>' + esc(job.printerName || 'Toshiba MFP') + '</td>' +
        '<td><code>' + esc(job.printerIp || '-') + '</code></td>' +
        '<td><span class="badge" style="background:#eff6ff; color:#1d4ed8; font-weight:700; font-size:13px;">' + (job.pages || 1) + ' pgs</span></td>' +
        '<td>' + (job.monoPages || 0) + '</td>' +
        '<td>' + (job.colorPages || 0) + '</td>' +
        '<td><span style="font-size:11px; color:#64748B;">#' + esc(job.printerJobId || '-') + '</span></td>';

      tbody.appendChild(tr);
    }
  }

  window.pollFleetCopyJobs = function () {
    var btnPrinters = document.getElementById('btnPollCopyFleet');
    var btnLogs = document.getElementById('btnPollCopyFleetLogs');
    if (btnPrinters) { btnPrinters.disabled = true; btnPrinters.textContent = ' Polling MFPs...'; }
    if (btnLogs) { btnLogs.disabled = true; btnLogs.textContent = ' Polling MFPs...'; }

    fetch('/api/admin/printers/poll-copy-jobs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        showToast(data.message || 'Polled active Toshiba MFPs successfully.', 'success');
        setTimeout(function () {
          if (state.currentPage === 'copy-logs') {
            window.loadCopyJobs();
          } else if (state.currentPage === 'users') {
            window.loadUsers();
          }
        }, 1200);
      })
      .catch(function (err) {
        showToast('Fleet poll failed: ' + err.message, 'error');
      })
      .finally(function () {
        if (btnPrinters) { btnPrinters.disabled = false; btnPrinters.textContent = ' Poll Walk-Up Copy Jobs'; }
        if (btnLogs) { btnLogs.disabled = false; btnLogs.textContent = ' Poll MFPs Now'; }
      });
  };

  /* =========================================================================
     HELPERS & UTILITIES
     ========================================================================= */

  function getJobStatusBadge(status) {
    if (status === 'HELD')      return '<span class="badge badge-held">HELD</span>';
    if (status === 'COMPLETED') return '<span class="badge badge-completed">COMPLETED</span>';
    if (status === 'CANCELLED') return '<span class="badge badge-cancelled">CANCELLED</span>';
    if (status === 'EXPIRED')   return '<span class="badge badge-expired">EXPIRED</span>';
    return '<span class="badge">' + esc(status) + '</span>';
  }

  function formatTime(iso) {
    if (!iso) return '-';
    try {
      var d = new Date(iso);
      if (isNaN(d.getTime())) return iso;
      return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
      return iso;
    }
  }

  function formatBytes(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    var k = 1024;
    var sizes = ['B', 'KB', 'MB', 'GB'];
    var i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  function esc(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // Modern In-App Centralized Notification & Alert Modal System (Zero Top Banners)
  window.showToast = function showToast(msg, type, customTitle) {
    if (!msg) return;
    type = (type || 'info').toLowerCase();

    var modal = document.getElementById('notifyModal');
    var headEl = document.getElementById('notifyModalHead');
    var titleEl = document.getElementById('notifyModalTitle');
    var headIconEl = document.getElementById('notifyModalHeadIcon');
    var badgeEl = document.getElementById('notifyModalBadge');
    var badgeIconEl = document.getElementById('notifyModalBadgeIcon');
    var msgEl = document.getElementById('notifyModalMsg');
    var okBtn = document.getElementById('notifyModalOkBtn');

    if (!modal || !msgEl) {
      console.log('[' + type.toUpperCase() + '] ' + msg);
      return;
    }

    var cfg = {
      title: 'Information',
      headIcon: '',
      badgeClass: 'notify-badge-info',
      badgeIcon: '',
      borderColor: '#2563EB',
      btnClass: 'btn btn-primary',
      btnBg: '#2563EB',
      btnBorder: '#1D4ED8'
    };

    if (type === 'success') {
      cfg.title = 'Success';
      cfg.headIcon = '[OK]';
      cfg.badgeClass = 'notify-badge-success';
      cfg.badgeIcon = '[OK]';
      cfg.borderColor = '#10B981';
      cfg.btnClass = 'btn btn-primary';
      cfg.btnBg = '#059669';
      cfg.btnBorder = '#047857';
    } else if (type === 'error' || type === 'danger') {
      cfg.title = 'Error';
      cfg.headIcon = '[FAIL]';
      cfg.badgeClass = 'notify-badge-error';
      cfg.badgeIcon = '[FAIL]';
      cfg.borderColor = '#EF4444';
      cfg.btnClass = 'btn btn-danger';
      cfg.btnBg = '#DC2626';
      cfg.btnBorder = '#B91C1C';
    } else if (type === 'warning' || type === 'warn') {
      cfg.title = 'Warning';
      cfg.headIcon = '';
      cfg.badgeClass = 'notify-badge-warning';
      cfg.badgeIcon = '!';
      cfg.borderColor = '#F59E0B';
      cfg.btnClass = 'btn btn-warning';
      cfg.btnBg = '#D97706';
      cfg.btnBorder = '#B45309';
    }

    if (customTitle) {
      cfg.title = customTitle;
    }

    if (titleEl) titleEl.textContent = cfg.title;
    if (headIconEl) headIconEl.textContent = cfg.headIcon;
    if (msgEl) msgEl.textContent = msg;

    if (headEl) {
      headEl.style.borderLeft = '4px solid ' + cfg.borderColor;
    }

    if (badgeEl) {
      badgeEl.className = 'notify-type-badge ' + cfg.badgeClass;
    }
    if (badgeIconEl) {
      badgeIconEl.textContent = cfg.badgeIcon;
    }

    if (okBtn) {
      okBtn.className = cfg.btnClass;
      okBtn.style.background = cfg.btnBg;
      okBtn.style.borderColor = cfg.btnBorder;
      okBtn.style.color = '#FFFFFF';
    }

    // Display modal
    modal.style.display = 'flex';

    // Auto-focus OK button so hitting Enter/Space immediately dismisses
    setTimeout(function () {
      if (okBtn) {
        try { okBtn.focus(); } catch (e) {}
      }
    }, 50);
  };

  window.closeNotifyModal = function () {
    var modal = document.getElementById('notifyModal');
    if (modal) {
      modal.style.display = 'none';
    }
  };

  window.showAlert = window.showToast;
  window.showNotification = window.showToast;

  // Keyboard shortcut: Escape to close Notify Modal
  window.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      var modal = document.getElementById('notifyModal');
      if (modal && modal.style.display === 'flex') {
        e.preventDefault();
        window.closeNotifyModal();
      }
    }
  });

  // Modern In-App Confirmation & Alert System (Zero Browser Popups)
  var confirmResolver = null;
  window.customConfirm = function (title, msg, okText, okClass) {
    return new Promise(function (resolve) {
      confirmResolver = resolve;
      var titleEl = document.getElementById('confirmModalTitle');
      var msgEl   = document.getElementById('confirmModalMsg');
      var okBtn   = document.getElementById('confirmModalOkBtn');
      var cancelBtn = document.getElementById('confirmModalCancelBtn');

      if (titleEl) titleEl.textContent = title || 'Confirm Action';
      if (msgEl) msgEl.textContent = msg || 'Are you sure?';

      if (okBtn) {
        okBtn.textContent = okText || 'Confirm';
        okBtn.className = 'btn ' + (okClass || 'btn-primary');
      }
      if (cancelBtn) cancelBtn.style.display = 'inline-flex';

      var modal = document.getElementById('confirmModal');
      if (modal) modal.style.display = 'flex';
    });
  };

  window.closeConfirmModal = function (result) {
    var modal = document.getElementById('confirmModal');
    if (modal) modal.style.display = 'none';
    if (confirmResolver) {
      var r = confirmResolver;
      confirmResolver = null;
      r(!!result);
    }
  };

  // Override browser native alert to show custom in-app modal
  window.alert = function (msg) {
    showToast(msg, 'info');
  };




  window.sendSimulatedJobToVirtualQueue = function () {
    var docName = 'Blueprint_' + Math.floor(Math.random() * 9000 + 1000) + '.pdf';
    fetch('/api/simulation/submit-job', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: 'rr',
        documentName: docName,
        pageCount: 3,
        color: false,
        duplex: true,
        copies: 1
      })
    })
    .then(function (res) { return res.json(); })
    .then(function (data) {
      showToast('Document "' + docName + '" sent to Virtual Queue! Walk to any physical MFP to release.', 'success');
      loadJobs();
      loadDashboard();
    })
    .catch(function (err) {
      showToast('Failed to submit test print: ' + err.message, 'error');
    });
  };

  /* =========================================================================
     MULTI-VIRTUAL QUEUE & 100+ PHYSICAL PRINTER POOL MANAGEMENT
     ========================================================================= */

  state.virtualQueues = [];
  state.selectedVirtualQueueId = null;
  state.currentVirtualQueue = null;
  state.vqPrintersSearchTerm = '';
  state.vqPoolFilter = 'IN_POOL';
  state.vqPage = 1;
  state.vqPerPage = 25;
  state.vqCheckedPrinterIds = new Set();

  window.loadVirtualQueueConfig = function (targetQueueId) {
    // 1. First ensure all physical printers are loaded in state.printers
    var loadPrintersPromise = (state.printers && state.printers.length > 0)
      ? Promise.resolve(state.printers)
      : fetch('/api/admin/printers', { headers: { 'X-Admin-Role': 'ADMIN' } })
          .then(function (r) { return r.json(); })
          .then(function (pList) {
            state.printers = pList || [];
            return state.printers;
          })
          .catch(function () { return []; });

    loadPrintersPromise.then(function () {
      var url = targetQueueId ? ('/api/admin/virtual-queue/' + targetQueueId) : '/api/admin/virtual-queue';
      return fetch(url);
    })
    .then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json();
    })
    .then(function (cfg) {
      state.virtualQueueConfig = cfg;
      state.currentVirtualQueue = cfg;
      state.selectedVirtualQueueId = cfg.id;

      // Update Top Queues summary tabs
      var allQueues = cfg.allQueues || [];
      state.virtualQueues = allQueues;
      var tabsContainer = document.getElementById('vqQueuesTabsContainer');
      var totalQueuesBadge = document.getElementById('vqTotalQueuesBadge');

      if (totalQueuesBadge) {
        totalQueuesBadge.textContent = allQueues.length + (allQueues.length === 1 ? ' Virtual Queue' : ' Virtual Queues');
      }

      if (tabsContainer) {
        tabsContainer.innerHTML = '';
        if (allQueues.length === 0) {
          tabsContainer.innerHTML = '<span style="font-size:12px; color:#64748B;">No Virtual Queues configured.</span>';
        } else {
          for (var i = 0; i < allQueues.length; i++) {
            var q = allQueues[i];
            var isSel = (q.id === cfg.id);
            var tab = document.createElement('div');
            tab.className = 'vq-queue-tab';
            tab.style.cssText = 'cursor:pointer; padding:10px 14px; border-radius:0px; font-size:12.5px; transition:all 0.15s ease; border:1px solid ' +
              (isSel ? '#1D4ED8' : '#CBD5E1') + '; border-top:' + (isSel ? '3px solid #F59E0B' : '1px solid #CBD5E1') + '; background:' + (isSel ? '#EFF6FF' : '#FFFFFF') + '; min-width:210px; flex:1; max-width:280px; box-shadow:0 1px 2px rgba(0,0,0,0.03);';
            tab.setAttribute('onclick', 'window.switchVirtualQueue(' + q.id + ')');

            var badges = '';
            if (q.isDefault) {
              badges += '<span class="badge" style="background:#DCFCE7; color:#166534; font-size:10px; margin-left:6px;"> Primary Default</span>';
            }
            var count = q.physicalPrinterCount !== undefined ? q.physicalPrinterCount : 0;

            tab.innerHTML = '<div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">' +
              '<strong style="color:' + (isSel ? '#1D4ED8' : '#0F172A') + '; font-size:13px;">' + q.name + '</strong>' +
              '</div>' +
              '<div style="font-size:11px; color:#475569; margin-bottom:4px;">Code: <code style="font-size:11px; color:#1E40AF; font-weight:700;">' + q.queueCode + '</code> &nbsp;|&nbsp;  <strong>' + count + '</strong> MFPs</div>' +
              '<div style="font-size:11px; color:#64748B; font-family:monospace; word-break:break-all;">\\\\' + (cfg.serverHost || 'SERVER') + '\\' + (q.shareName || q.windowsPrinterName) + '</div>' +
              badges;

            tabsContainer.appendChild(tab);
          }
        }
      }

      // Update Overview Stats
      var selName    = document.getElementById('vqSelectedPrinter');
      var netShare   = document.getElementById('vqNetworkShare');
      var driver     = document.getElementById('vqDriverName');
      var hold       = document.getElementById('vqHoldStatus');
      var badge      = document.getElementById('vqShareBadge');
      var liveBtn    = document.getElementById('vqLiveModeBtn');
      var statName   = document.getElementById('statVqName');
      var statShare  = document.getElementById('statVqShare');
      var statStatus = document.getElementById('statVqStatus');
      var statMode   = document.getElementById('statVqMode');
      var statPool   = document.getElementById('statVqPoolCount');
      var psCmd      = document.getElementById('vqPowershellCmd');
      var liveBtnTop = document.getElementById('vqLiveModeBtnTop');
      var headerName = document.getElementById('vqCurrentDisplayName');
      var poolTitle  = document.getElementById('poolTableTitle');
      var btnDefault = document.getElementById('btnDefaultVq');
      var btnDelete  = document.getElementById('btnDeleteVq');

      var sharePath = cfg.networkSharePath || ('\\\\SERVER\\' + (cfg.shareName || cfg.selectedPrinterName));

      if (headerName) headerName.textContent = cfg.name + ' (' + cfg.queueCode + ')';
      if (poolTitle) poolTitle.textContent = 'Physical Release MFPs in Virtual Pool: ' + cfg.name;
      if (selName) selName.textContent = cfg.selectedPrinterName || 'None';
      if (netShare) netShare.textContent = sharePath;
      if (statName) statName.textContent = cfg.name || cfg.selectedPrinterName;
      if (statShare) statShare.textContent = sharePath;
      if (statPool) statPool.textContent = (cfg.physicalPrinterCount || (cfg.physicalPrinters ? cfg.physicalPrinters.length : 0)) + ' MFPs';
      if (psCmd) psCmd.textContent = 'Add-Printer -ConnectionName "' + sharePath + '"';

      if (btnDefault) {
        btnDefault.style.display = cfg.isDefault ? 'none' : 'inline-flex';
      }
      if (btnDelete) {
        btnDelete.style.display = (cfg.isDefault || allQueues.length <= 1) ? 'none' : 'inline-flex';
      }

      if (driver) driver.textContent = cfg.driverName || 'Universal Driver';
      if (hold) {
        hold.textContent = cfg.isPaused ? 'Active Hold (Quarantined in Windows Spooler)' : 'Normal Mode';
        hold.style.color = cfg.isPaused ? '#059669' : '#D97706';
      }
      if (statStatus) {
        statStatus.textContent = cfg.isPaused ? 'Active Hold (Paused)' : 'Normal Spool';
        statStatus.className = cfg.isPaused ? 'stat-val green' : 'stat-val amber';
      }

      if (badge) {
        badge.textContent = cfg.isShared ? ' Shared on Network' : ' Local Queue';
        badge.style.background = cfg.isShared ? '#DCFCE7' : '#FEF3C7';
        badge.style.color = cfg.isShared ? '#166534' : '#92400E';
      }

      if (liveBtn) {
        if (cfg.livePrintingEnabled) {
          liveBtn.innerHTML = ' Hardware Printing: LIVE (Port 9100)';
          liveBtn.style.background = '#DCFCE7';
          liveBtn.style.color = '#166534';
          liveBtn.style.borderColor = '#86EFAC';
        } else {
          liveBtn.innerHTML = ' Simulation Guard Mode (No Paper)';
          liveBtn.style.background = '#FEF3C7';
          liveBtn.style.color = '#92400E';
          liveBtn.style.borderColor = '#FCD34D';
        }
      }
      if (liveBtnTop) {
        liveBtnTop.innerHTML = cfg.livePrintingEnabled ? ' Mode: Hardware Live' : ' Mode: Simulation Guard';
      }
      if (statMode) {
        statMode.textContent = cfg.livePrintingEnabled ? 'Live (Port 9100)' : 'Simulation Guard';
        statMode.style.color = cfg.livePrintingEnabled ? '#059669' : '#D97706';
      }

      // Populate server printer dropdown
      var select = document.getElementById('selectServerVirtualPrinter');
      if (select) {
        select.innerHTML = '';
        var printers = cfg.availableServerPrinters || [];
        if (printers.length === 0) {
          var optEmpty = document.createElement('option');
          optEmpty.value = cfg.selectedPrinterName || 'QOA_Printer';
          optEmpty.textContent = (cfg.selectedPrinterName || 'QOA_Printer') + ' (Designated Server Virtual Queue)';
          optEmpty.selected = true;
          select.appendChild(optEmpty);
        } else {
          var hasSelected = false;
          for (var j = 0; j < printers.length; j++) {
            var sp = printers[j];
            var pName = sp.Name || sp.name || '';
            if (!pName) continue;
            var pDriver = sp.DriverName || sp.driverName || 'Driver';
            var pShared = sp.Shared !== undefined ? sp.Shared : (sp.shared !== undefined ? sp.shared : false);
            var pShareName = sp.ShareName || sp.shareName || pName;
            var opt = document.createElement('option');
            opt.value = pName;
            var details = pName + ' [' + pDriver + ']' + (pShared ? ' (Shared: \\\\' + (cfg.serverHost || 'SERVER') + '\\' + pShareName + ')' : '');
            opt.textContent = details;
            if (pName.toLowerCase() === (cfg.selectedPrinterName || '').toLowerCase()) {
              opt.selected = true;
              hasSelected = true;
            }
            select.appendChild(opt);
          }
          if (!hasSelected && cfg.selectedPrinterName) {
            var optFallback = document.createElement('option');
            optFallback.value = cfg.selectedPrinterName;
            optFallback.textContent = cfg.selectedPrinterName + ' (Active Virtual Queue)';
            optFallback.selected = true;
            select.insertBefore(optFallback, select.firstChild);
          }
        }
      }

      // Render 100+ Physical Printers Table
      window.renderVirtualQueuePrintersTable();
    })
    .catch(function (err) {
      console.warn('Could not load virtual queue config:', err);
    });
  };

  window.switchVirtualQueue = function (queueId) {
    if (!queueId) return;
    state.selectedVirtualQueueId = queueId;
    state.vqPage = 1;
    state.vqCheckedPrinterIds.clear();
    var checkAll = document.getElementById('vqCheckAllPrinters');
    if (checkAll) checkAll.checked = false;
    window.loadVirtualQueueConfig(queueId);
  };

  /* -------------------------------------------------------------------------
     100+ PHYSICAL PRINTERS TABLE: SEARCH, FILTER, PAGINATION & POOL ASSIGNMENT
     ------------------------------------------------------------------------- */
  window.renderVirtualQueuePrintersTable = function () {
    var tbody = document.getElementById('vqPrintersTableBody');
    if (!tbody) return;

    var allPhysical = state.printers || [];
    var cfg = state.currentVirtualQueue || {};
    var poolPrinters = cfg.physicalPrinters || [];
    var poolIds = new Set(poolPrinters.map(function (p) { return p.id; }));

    // 1. Filter by Pool status
    var filtered = allPhysical.filter(function (p) {
      if (p.printerCode === 'VIRTUAL-QUEUE') return false;
      var inPool = poolIds.has(p.id);
      if (state.vqPoolFilter === 'IN_POOL') return inPool;
      if (state.vqPoolFilter === 'NOT_IN_POOL') return !inPool;
      return true;
    });

    // 2. Filter by Search Query (Name, IP, Code, Model, Location)
    if (state.vqPrintersSearchTerm && state.vqPrintersSearchTerm.trim()) {
      var query = state.vqPrintersSearchTerm.trim().toLowerCase();
      filtered = filtered.filter(function (p) {
        return (p.name && p.name.toLowerCase().indexOf(query) !== -1) ||
               (p.printerCode && p.printerCode.toLowerCase().indexOf(query) !== -1) ||
               (p.ipAddress && p.ipAddress.toLowerCase().indexOf(query) !== -1) ||
               (p.model && p.model.toLowerCase().indexOf(query) !== -1) ||
               (p.location && p.location.toLowerCase().indexOf(query) !== -1);
      });
    }

    // 3. Update summary counters
    var poolSummaryInfo = document.getElementById('vqPoolSummaryInfo');
    if (poolSummaryInfo) {
      poolSummaryInfo.textContent = poolIds.size + ' of ' + allPhysical.length + ' registered MFPs linked to this pool';
    }

    if (filtered.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" style="text-align:center; padding:32px; color:#64748B;">' +
        (allPhysical.length === 0
          ? 'No physical printers registered in fleet. Click "+ Register New Physical MFP" or " Seed 20 Test MFPs" to add devices!'
          : 'No physical printers matching current filters / search query.') +
        '</td></tr>';

      var pageInfoEl = document.getElementById('vqPaginationInfo');
      if (pageInfoEl) pageInfoEl.textContent = 'Showing 0 of 0 printers';
      return;
    }

    // 4. Pagination
    var perPage = state.vqPerPage;
    var totalPages = Math.ceil(filtered.length / perPage);
    if (state.vqPage > totalPages) state.vqPage = Math.max(1, totalPages);
    var startIndex = (state.vqPage - 1) * perPage;
    var endIndex = Math.min(startIndex + perPage, filtered.length);
    var pageItems = filtered.slice(startIndex, endIndex);

    var pageInfo = document.getElementById('vqPaginationInfo');
    if (pageInfo) {
      pageInfo.textContent = 'Showing ' + (startIndex + 1) + '-' + endIndex + ' of ' + filtered.length + ' printers';
    }

    var pageIndicator = document.getElementById('vqPageIndicator');
    if (pageIndicator) {
      pageIndicator.textContent = 'Page ' + state.vqPage + ' of ' + totalPages;
    }

    var prevBtn = document.getElementById('vqPrevPageBtn');
    var nextBtn = document.getElementById('vqNextPageBtn');
    if (prevBtn) prevBtn.disabled = (state.vqPage <= 1);
    if (nextBtn) nextBtn.disabled = (state.vqPage >= totalPages);

    // 5. Render Table Rows
    var html = '';
    for (var k = 0; k < pageItems.length; k++) {
      var printer = pageItems[k];
      var inPool = poolIds.has(printer.id);
      var isChecked = state.vqCheckedPrinterIds.has(printer.id);

      var poolBadge = inPool
        ? '<span class="badge" style="background:#DCFCE7; color:#166534; font-weight:700;"> In Pool</span>'
        : '<span class="badge" style="background:#F1F5F9; color:#64748B;"> Not in Pool</span>';

      var actionBtn = inPool
        ? '<button type="button" class="btn btn-secondary btn-sm" onclick="window.togglePrinterPoolMembership(' + printer.id + ')" style="color:#DC2626;" title="Remove this MFP from virtual pool">Remove from Pool</button>'
        : '<button type="button" class="btn btn-primary btn-sm" onclick="window.togglePrinterPoolMembership(' + printer.id + ')" title="Link this MFP to virtual pool">+ Add to Pool</button>';

      var defaultBadge = printer.defaultHoldQueue
        ? ' <span class="badge" style="background:#EFF6FF; color:#1D4ED8; font-size:10px;"> Default MFP</span>'
        : '';

      var sInfo = resolvePrinterSeries(printer);
      var genTag = ' <span class="badge" style="background:' + sInfo.bg + '; color:' + sInfo.color + '; font-size:9.5px; border:1px solid ' + sInfo.border + ';">' + sInfo.label + '</span>';

      html += '<tr style="' + (inPool ? 'background:#FAFDFB;' : '') + '">' +
        '<td style="text-align:center;">' +
          '<input type="checkbox" onchange="window.handleVqTogglePrinterCheck(' + printer.id + ', this.checked)" ' + (isChecked ? 'checked' : '') + '>' +
        '</td>' +
        '<td><strong>' + printer.name + '</strong>' + defaultBadge + '</td>' +
        '<td><code style="font-size:11.5px; color:#1E40AF; font-weight:700;">' + printer.printerCode + '</code></td>' +
        '<td><code style="font-size:12px; color:#334155;">' + printer.ipAddress + ':' + printer.rawPort + '</code></td>' +
        '<td>' + (printer.location || 'HQ Office') + '</td>' +
        '<td style="color:#475569;">' + (printer.model || (isOldModel ? 'Toshiba e-STUDIO 306SE' : 'Toshiba e-STUDIO')) + genTag + '</td>' +
        '<td>' + poolBadge + '</td>' +
        '<td style="text-align:center;">' + actionBtn + '</td>' +
        '<td><span style="color:#059669; font-weight:600; font-size:11px;"> Online</span></td>' +
      '</tr>';
    }

    tbody.innerHTML = html;
  };

  // Filter & Search Handlers
  window.handleVqPrinterSearch = function (val) {
    state.vqPrintersSearchTerm = val || '';
    state.vqPage = 1;
    window.renderVirtualQueuePrintersTable();
  };

  window.handleVqPoolFilterChange = function (val) {
    state.vqPoolFilter = val;
    state.vqPage = 1;
    window.renderVirtualQueuePrintersTable();
  };

  window.handleVqPerPageChange = function (val) {
    state.vqPerPage = (val === 'ALL') ? 999999 : parseInt(val, 10);
    state.vqPage = 1;
    window.renderVirtualQueuePrintersTable();
  };

  window.handleVqPrevPage = function () {
    if (state.vqPage > 1) {
      state.vqPage--;
      window.renderVirtualQueuePrintersTable();
    }
  };

  window.handleVqNextPage = function () {
    state.vqPage++;
    window.renderVirtualQueuePrintersTable();
  };

  // Checkbox Selection
  window.handleVqTogglePrinterCheck = function (printerId, isChecked) {
    if (isChecked) {
      state.vqCheckedPrinterIds.add(printerId);
    } else {
      state.vqCheckedPrinterIds.delete(printerId);
    }
  };

  window.handleVqToggleCheckAll = function (isChecked) {
    var allPhysical = state.printers || [];
    if (isChecked) {
      for (var i = 0; i < allPhysical.length; i++) {
        state.vqCheckedPrinterIds.add(allPhysical[i].id);
      }
    } else {
      state.vqCheckedPrinterIds.clear();
    }
    window.renderVirtualQueuePrintersTable();
  };

  window.handleVqSelectAllFiltered = function () {
    var allPhysical = state.printers || [];
    var cfg = state.currentVirtualQueue || {};
    var poolPrinters = cfg.physicalPrinters || [];
    var poolIds = new Set(poolPrinters.map(function (p) { return p.id; }));

    for (var i = 0; i < allPhysical.length; i++) {
      var p = allPhysical[i];
      var inPool = poolIds.has(p.id);
      if (state.vqPoolFilter === 'IN_POOL' && !inPool) continue;
      if (state.vqPoolFilter === 'NOT_IN_POOL' && inPool) continue;
      state.vqCheckedPrinterIds.add(p.id);
    }
    window.renderVirtualQueuePrintersTable();
    showToast('Selected all filtered physical printers.', 'info');
  };

  // Bulk Pool Operations
  window.handleVqBulkAddSelected = function () {
    if (state.vqCheckedPrinterIds.size === 0) {
      showToast('Please check at least one physical printer first.', 'error');
      return;
    }
    var queueId = state.selectedVirtualQueueId;
    if (!queueId) return;

    var currentPool = (state.currentVirtualQueue && state.currentVirtualQueue.physicalPrinters)
      ? state.currentVirtualQueue.physicalPrinters.map(function (p) { return p.id; })
      : [];

    var unionSet = new Set(currentPool);
    state.vqCheckedPrinterIds.forEach(function (id) { unionSet.add(id); });
    var targetList = Array.from(unionSet);

    fetch('/api/admin/virtual-queue/' + queueId + '/assign-printers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ physicalPrinterIds: targetList })
    })
    .then(function (r) { return r.json(); })
    .then(function (data) {
      if (data.success) {
        showToast('Successfully added selected MFPs to pool! Total: ' + data.queue.physicalPrinterCount, 'success');
        state.vqCheckedPrinterIds.clear();
        var chkAll = document.getElementById('vqCheckAllPrinters');
        if (chkAll) chkAll.checked = false;
        window.loadVirtualQueueConfig(queueId);
      } else {
        showToast('Error: ' + data.error, 'error');
      }
    })
    .catch(function (err) {
      showToast('Bulk add failed: ' + err.message, 'error');
    });
  };

  window.handleVqBulkRemoveSelected = function () {
    if (state.vqCheckedPrinterIds.size === 0) {
      showToast('Please check at least one physical printer first.', 'error');
      return;
    }
    var queueId = state.selectedVirtualQueueId;
    if (!queueId) return;

    var currentPool = (state.currentVirtualQueue && state.currentVirtualQueue.physicalPrinters)
      ? state.currentVirtualQueue.physicalPrinters.map(function (p) { return p.id; })
      : [];

    var filteredList = currentPool.filter(function (id) {
      return !state.vqCheckedPrinterIds.has(id);
    });

    fetch('/api/admin/virtual-queue/' + queueId + '/assign-printers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ physicalPrinterIds: filteredList })
    })
    .then(function (r) { return r.json(); })
    .then(function (data) {
      if (data.success) {
        showToast('Removed selected MFPs from pool.', 'info');
        state.vqCheckedPrinterIds.clear();
        var chkAll = document.getElementById('vqCheckAllPrinters');
        if (chkAll) chkAll.checked = false;
        window.loadVirtualQueueConfig(queueId);
      } else {
        showToast('Error: ' + data.error, 'error');
      }
    })
    .catch(function (err) {
      showToast('Bulk remove failed: ' + err.message, 'error');
    });
  };

  // Single Printer Toggle in Pool
  window.togglePrinterPoolMembership = function (printerId) {
    var queueId = state.selectedVirtualQueueId;
    if (!queueId || !printerId) return;

    fetch('/api/admin/virtual-queue/' + queueId + '/toggle-printer/' + printerId, {
      method: 'POST'
    })
    .then(function (r) { return r.json(); })
    .then(function (data) {
      if (data.success) {
        showToast(data.message || 'Printer pool updated!', 'success');
        window.loadVirtualQueueConfig(queueId);
      } else {
        showToast('Error: ' + data.error, 'error');
      }
    })
    .catch(function (err) {
      showToast('Failed to update printer pool: ' + err.message, 'error');
    });
  };

  /* -------------------------------------------------------------------------
     MULTIPLE VIRTUAL QUEUE CRUD (Create, Edit, Delete, Set Default)
     ------------------------------------------------------------------------- */
  
  // Direct DOM listeners fallback for virtual queue modals
  document.addEventListener('DOMContentLoaded', function () {
    var btn = document.getElementById('btnOpenCreateVq');
    if (btn) {
      btn.addEventListener('click', function (e) {
        if (typeof window.openCreateVirtualQueueModal === 'function') {
          window.openCreateVirtualQueueModal();
        }
      });
    }
  });

  window.openCreateVirtualQueueModal = function () {
    var modal = document.getElementById('modalCreateVirtualQueue');
    if (!modal) return;

    // Reset inputs
    document.getElementById('newVqName').value = '';
    document.getElementById('newVqCode').value = 'VQ-' + Math.floor(1000 + Math.random() * 9000);
    document.getElementById('newVqWindowsPrinter').value = 'Toshiba_Pull_Print';
    document.getElementById('newVqShareName').value = 'Toshiba_Pull_Print';
    document.getElementById('newVqDriver').value = 'TOSHIBA Universal Printer 2';
    document.getElementById('newVqDescription').value = '';
    document.getElementById('newVqIsDefault').checked = false;

    // Populate physical printers checklist
    var container = document.getElementById('newVqPrintersList');
    if (container) {
      container.innerHTML = '';
      var allPrinters = state.printers || [];
      if (allPrinters.length === 0) {
        container.innerHTML = '<span style="font-size:12px; color:#64748B;">No physical printers available.</span>';
      } else {
        for (var i = 0; i < allPrinters.length; i++) {
          var pr = allPrinters[i];
          var div = document.createElement('div');
          div.className = 'modal-printer-item';
          div.style.cssText = 'padding:4px 0; font-size:12px; display:flex; align-items:center; gap:8px; border-bottom:1px solid #EEF2F6;';
          div.innerHTML = '<label class="checkbox-label" style="font-weight:500; cursor:pointer;">' +
            '<input type="checkbox" name="newVqPrinterCheckbox" value="' + pr.id + '"> ' +
            pr.name + ' (' + pr.printerCode + ') - <code style="font-size:11px; color:#475569;">' + pr.ipAddress + '</code>' +
          '</label>';
          container.appendChild(div);
        }
      }
    }

    modal.style.display = 'flex';
  };

  window.closeCreateVirtualQueueModal = function () {
    var modal = document.getElementById('modalCreateVirtualQueue');
    if (modal) modal.style.display = 'none';
  };

  window.filterModalPrinters = function (containerId, query) {
    var container = document.getElementById(containerId);
    if (!container) return;
    var items = container.querySelectorAll('.modal-printer-item');
    var q = (query || '').toLowerCase().trim();
    for (var i = 0; i < items.length; i++) {
      var text = items[i].textContent.toLowerCase();
      items[i].style.display = (q === '' || text.indexOf(q) !== -1) ? 'flex' : 'none';
    }
  };

  window.handleCreateVirtualQueueSubmit = function (event) {
    event.preventDefault();
    var name = document.getElementById('newVqName').value.trim();
    var code = document.getElementById('newVqCode').value.trim();
    var printer = document.getElementById('newVqWindowsPrinter').value.trim();
    var share = document.getElementById('newVqShareName').value.trim();
    var driver = document.getElementById('newVqDriver').value.trim();
    var desc = document.getElementById('newVqDescription').value.trim();
    var isDefault = document.getElementById('newVqIsDefault').checked;

    var checkboxes = document.querySelectorAll('input[name="newVqPrinterCheckbox"]:checked');
    var selectedPrinterIds = [];
    for (var i = 0; i < checkboxes.length; i++) {
      selectedPrinterIds.push(parseInt(checkboxes[i].value, 10));
    }

    var payload = {
      name: name,
      queueCode: code,
      windowsPrinterName: printer,
      shareName: share || printer,
      driverName: driver || 'TOSHIBA Universal Printer 2',
      description: desc,
      isDefault: isDefault,
      physicalPrinterIds: selectedPrinterIds
    };

    fetch('/api/admin/virtual-queue', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(function (r) { return r.json(); })
    .then(function (data) {
      if (data.success) {
        showToast(data.message, 'success');
        window.closeCreateVirtualQueueModal();
        window.loadVirtualQueueConfig(data.queue.id);
      } else {
        showToast('Error: ' + data.error, 'error');
      }
    })
    .catch(function (err) {
      showToast('Failed to create virtual queue: ' + err.message, 'error');
    });
  };

  window.openEditCurrentVirtualQueueModal = function () {
    var cfg = state.currentVirtualQueue;
    if (!cfg) return;
    var modal = document.getElementById('modalEditVirtualQueue');
    if (!modal) return;

    document.getElementById('editVqId').value = cfg.id;
    document.getElementById('editVqName').value = cfg.name || '';
    document.getElementById('editVqCode').value = cfg.queueCode || '';
    document.getElementById('editVqWindowsPrinter').value = cfg.selectedPrinterName || '';
    document.getElementById('editVqShareName').value = cfg.shareName || '';
    document.getElementById('editVqDriver').value = cfg.driverName || 'TOSHIBA Universal Printer 2';
    document.getElementById('editVqDescription').value = cfg.description || '';
    document.getElementById('editVqIsDefault').checked = Boolean(cfg.isDefault);

    modal.style.display = 'flex';
  };

  window.closeEditVirtualQueueModal = function () {
    var modal = document.getElementById('modalEditVirtualQueue');
    if (modal) modal.style.display = 'none';
  };

  window.handleEditVirtualQueueSubmit = function (event) {
    event.preventDefault();
    var id = document.getElementById('editVqId').value;
    var name = document.getElementById('editVqName').value.trim();
    var printer = document.getElementById('editVqWindowsPrinter').value.trim();
    var share = document.getElementById('editVqShareName').value.trim();
    var driver = document.getElementById('editVqDriver').value.trim();
    var desc = document.getElementById('editVqDescription').value.trim();
    var isDefault = document.getElementById('editVqIsDefault').checked;

    var payload = {
      name: name,
      windowsPrinterName: printer,
      shareName: share || printer,
      driverName: driver,
      description: desc,
      isDefault: isDefault
    };

    fetch('/api/admin/virtual-queue/' + id, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(function (r) { return r.json(); })
    .then(function (data) {
      if (data.success) {
        showToast(data.message, 'success');
        window.closeEditVirtualQueueModal();
        window.loadVirtualQueueConfig(id);
      } else {
        showToast('Error: ' + data.error, 'error');
      }
    })
    .catch(function (err) {
      showToast('Failed to update virtual queue: ' + err.message, 'error');
    });
  };

  window.handleSetDefaultVirtualQueue = function () {
    var id = state.selectedVirtualQueueId;
    if (!id) return;
    fetch('/api/admin/virtual-queue/' + id + '/set-default', { method: 'POST' })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (data.success) {
          showToast(data.message, 'success');
          window.loadVirtualQueueConfig(id);
        } else {
          showToast('Error: ' + data.error, 'error');
        }
      })
      .catch(function (err) {
        showToast('Failed to set default: ' + err.message, 'error');
      });
  };

  window.handleDeleteVirtualQueue = function () {
    var id = state.selectedVirtualQueueId;
    if (!id) return;
    var cfg = state.currentVirtualQueue || {};
    openConfirmModal(
      'Delete Virtual Queue',
      'Are you sure you want to delete the Virtual Queue "' + (cfg.name || '') + '" (' + (cfg.queueCode || '') + ')?',
      function () {
        fetch('/api/admin/virtual-queue/' + id, { method: 'DELETE' })
          .then(function (r) { return r.json(); })
          .then(function (data) {
            if (data.success) {
              showToast(data.message, 'success');
              window.loadVirtualQueueConfig();
            } else {
              showToast('Error: ' + data.error, 'error');
            }
          })
          .catch(function (err) {
            showToast('Delete failed: ' + err.message, 'error');
          });
      }
    );
  };

  // Seed 20 Sample Printers (To easily demonstrate 20-100 printers)
  window.handleSeedSamplePrinters = function () {
    showToast('Generating 20 sample Toshiba MFPs across floors & departments...', 'info');
    fetch('/api/admin/printers/seed-samples?count=20', {
      method: 'POST',
      headers: { 'X-Admin-Role': 'ADMIN' }
    })
    .then(function (r) { return r.json(); })
    .then(function (data) {
      if (data.success) {
        showToast(data.message, 'success');
        // Reload all printers in fleet
        fetch('/api/admin/printers', { headers: { 'X-Admin-Role': 'ADMIN' } })
          .then(function (r) { return r.json(); })
          .then(function (pList) {
            state.printers = pList || [];
            window.loadVirtualQueueConfig(state.selectedVirtualQueueId);
          });
      } else {
        showToast('Error: ' + data.error, 'error');
      }
    })
    .catch(function (err) {
      showToast('Seeding failed: ' + err.message, 'error');
    });
  };

  window.refreshVirtualQueuePrinters = function () {
    var select = document.getElementById('selectServerVirtualPrinter');
    if (select) {
      select.innerHTML = '<option value="">Scanning installed Windows printers...</option>';
    }
    showToast('Scanning server Windows printers via PowerShell...', 'info');
    fetch('/api/admin/virtual-queue/installed-printers?refresh=true')
      .then(function () {
        window.loadVirtualQueueConfig(state.selectedVirtualQueueId);
        showToast('Server printer list updated!', 'success');
      })
      .catch(function (err) {
        window.loadVirtualQueueConfig(state.selectedVirtualQueueId);
      });
  };

  window.handleSaveVirtualQueuePrinter = function () {
    var select = document.getElementById('selectServerVirtualPrinter');
    if (!select || !select.value) {
      showToast('Please select a printer from the list.', 'error');
      return;
    }
    var chosen = select.value;
    var queueId = state.selectedVirtualQueueId;

    if (queueId) {
      fetch('/api/admin/virtual-queue/' + queueId, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ windowsPrinterName: chosen, shareName: chosen })
      })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (data.success) {
          showToast('Virtual Queue Windows printer updated to: ' + chosen, 'success');
          window.loadVirtualQueueConfig(queueId);
        } else {
          showToast('Error: ' + (data.error || data.message), 'error');
        }
      })
      .catch(function (err) {
        showToast('Failed to update virtual queue: ' + err.message, 'error');
      });
    } else {
      fetch('/api/admin/virtual-queue/select', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ printerName: chosen })
      })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (data.success) {
          showToast(data.message || 'Server Virtual Queue updated successfully!', 'success');
          window.loadVirtualQueueConfig();
        } else {
          showToast('Error: ' + (data.error || data.message), 'error');
        }
      })
      .catch(function (err) {
        showToast('Failed to update virtual queue: ' + err.message, 'error');
      });
    }
  };

  window.toggleLivePrintingMode = function () {
    fetch('/api/admin/virtual-queue/toggle-live-mode', { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        showToast(data.message, data.livePrintingEnabled ? 'success' : 'info');
        window.loadVirtualQueueConfig(state.selectedVirtualQueueId);
      })
      .catch(function (err) {
        showToast('Failed to toggle print mode: ' + err.message, 'error');
      });
  };

  /* =========================================================================
     SYSTEM SETTINGS & CORPORATE BRANDING CONTROLLER
     ========================================================================= */

  var pendingLogoData = null;
  var pendingLogoFilename = null;

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function applyBrandingToSidebar(settings) {
    if (!settings) return;
    var sidebarLogoImg = document.getElementById('sidebarLogoImg');
    var sidebarLogoWrap = document.getElementById('sidebarLogoWrap');
    var defaultGroup   = document.getElementById('sidebarBrandDefaultGroup');
    var sidebarBrandName = document.getElementById('sidebarBrandName');
    var sidebarBrandSub  = document.getElementById('sidebarBrandSub');

    if (settings.logoData && settings.logoData.trim()) {
      // Full-width horizontal logo (350x106 ratio) spanning the header - title/sub hidden
      if (sidebarLogoImg) sidebarLogoImg.src = settings.logoData;
      if (sidebarLogoWrap) sidebarLogoWrap.style.display = 'flex';
      if (defaultGroup) defaultGroup.style.display = 'none';
    } else {
      // Fallback default branding (nP mark + title & subtitle)
      if (sidebarLogoImg) sidebarLogoImg.src = '';
      if (sidebarLogoWrap) sidebarLogoWrap.style.display = 'none';
      if (defaultGroup) defaultGroup.style.display = 'flex';

      if (sidebarBrandName && settings.systemTitle) {
        sidebarBrandName.textContent = settings.systemTitle;
      }
      if (sidebarBrandSub && settings.organizationName) {
        sidebarBrandSub.textContent = settings.organizationName;
      }
    }
  }

  function loadSystemBranding() {
    fetch('/api/admin/settings')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (settings) {
        applyBrandingToSidebar(settings);
      })
      .catch(function () {
        // Silently retain default branding if unauthenticated or error
      });
  }

  window.loadSettings = function () {
    if (window.renderMaintServiceTypesSettings) window.renderMaintServiceTypesSettings();
    if (window.loadTechniciansSettings) window.loadTechniciansSettings();
    fetch('/api/admin/settings')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (settings) {
        var orgInput = document.getElementById('settingOrgName');
        var titleInput = document.getElementById('settingSystemTitle');
        var subInput = document.getElementById('settingSystemSubtitle');
        var timeoutInput = document.getElementById('settingInactivityTimeout');
        var jobExpiryInput = document.getElementById('settingJobExpiryMinutes');
        var supportInput = document.getElementById('settingSupportContact');

        if (orgInput) orgInput.value = settings.organizationName || '';
        if (titleInput) titleInput.value = settings.systemTitle || '';
        if (subInput) subInput.value = settings.systemSubtitle || '';
        if (timeoutInput) timeoutInput.value = settings.inactivityTimeoutSecs || 60;
        if (jobExpiryInput) jobExpiryInput.value = settings.jobExpiryMinutes || 60;
        if (supportInput) supportInput.value = settings.supportContact || '';

        // SMTP settings
        var smtpHostInput = document.getElementById('settingSmtpHost');
        var smtpPortInput = document.getElementById('settingSmtpPort');
        var smtpUserInput = document.getElementById('settingSmtpUser');
        var smtpPassInput = document.getElementById('settingSmtpPass');
        var smtpFromInput = document.getElementById('settingSmtpFrom');

        if (smtpHostInput) smtpHostInput.value = settings.smtpHost || '';
        if (smtpPortInput) smtpPortInput.value = settings.smtpPort || 587;
        if (smtpUserInput) smtpUserInput.value = settings.smtpUsername || '';
        if (smtpPassInput) smtpPassInput.value = settings.smtpPassword || '';
        if (smtpFromInput) smtpFromInput.value = settings.smtpFromAddress || 'scan@rrconstruction.lk';

        var rStartTls = document.getElementById('secStartTls');
        var rSsl = document.getElementById('secSsl');
        var rNone = document.getElementById('secNone');
        if (settings.smtpUseSsl && rSsl) {
          rSsl.checked = true;
        } else if (settings.smtpUseTls === false && rNone) {
          rNone.checked = true;
        } else if (rStartTls) {
          rStartTls.checked = true;
        }

        updateLogoPreviewUI(settings.logoData, settings.logoFilename);
        applyBrandingToSidebar(settings);

        var statusBadge = document.getElementById('settingsSaveStatus');
        if (statusBadge) {
          statusBadge.textContent = 'Configured';
          statusBadge.style.background = '#F0FDF4';
          statusBadge.style.color = '#15803D';
        }
      })
      .catch(function (err) {
        showToast('Failed to load system settings: ' + err.message, 'error');
      });
  };

  function loadLdapDeptSettings() {
    fetch('/api/admin/directory/config')
      .then(function (res) { return res.json(); })
      .then(function (cfg) {
        state.directoryConfig = cfg;
        var elDomainBadge = document.getElementById('settingsLdapDomainBadge');
        var elStatusBadge = document.getElementById('settingsLdapStatusBadge');
        var elUserCountBadge = document.getElementById('settingsLdapUserCountBadge');
        var elDeptBadge = document.getElementById('settingsLdapDeptBadge');
        var statusEl = document.getElementById('settingLdapSyncStatus');

        if (elDomainBadge) elDomainBadge.textContent = cfg.domainName || 'qoa.local';
        if (elStatusBadge) {
          elStatusBadge.innerHTML = cfg.isEnabled !== false ? '<span class="badge badge-online">Connected</span>' : '<span class="badge badge-offline">Disabled</span>';
        }
        if (elUserCountBadge) {
          elUserCountBadge.textContent = (cfg.syncedUserCount || 6) + ' Users';
        }
        if (elDeptBadge) {
          elDeptBadge.innerHTML = '<code>' + esc(cfg.departmentAttribute || 'department') + '</code>';
        }
        if (statusEl) {
          statusEl.innerHTML = 'LDAP Host: <strong>' + esc(cfg.ldapUrl || 'ldap://127.0.0.1:389') + '</strong> &bull; Card Attribute: <code>' + esc(cfg.cardIdAttribute || 'employeeNumber') + '</code> &bull; Base DN: <code>' + esc(cfg.baseDn || 'DC=qoa,DC=local') + '</code>';
        }
      })
      .catch(function (e) {
        console.warn('Could not load LDAP settings for settings page:', e);
      });
  }

  window.handleSaveLdapDeptSettings = function (event) {
    if (event) event.preventDefault();
  };

  window.triggerSettingsAdSync = function () {
    var btn = document.getElementById('btnSettingsSyncAd');
    if (btn) { btn.disabled = true; btn.textContent = 'Syncing...'; }
    fetch('/api/admin/directory/sync-ad', { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (btn) {
          btn.disabled = false;
          btn.innerHTML = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> <span>Sync AD Users Now</span>';
        }
        showToast('Active Directory Sync complete! ' + (data.importedCount || 0) + ' users synced with department mappings.', 'success');
        loadLdapDeptSettings();
        if (typeof loadUsers === 'function') loadUsers();
      })
      .catch(function (err) {
        if (btn) {
          btn.disabled = false;
          btn.innerHTML = '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg> <span>Sync AD Users Now</span>';
        }
        showToast('AD sync error: ' + err.message, 'error');
      });
  };

  function updateLogoPreviewUI(logoData, filename) {
    var previewImg = document.getElementById('settingsLogoPreviewImg');
    var defaultMark = document.getElementById('settingsLogoDefaultMark');
    var logoInfo = document.getElementById('settingsLogoInfo');
    var logoStatusBadge = document.getElementById('logoStatusBadge');
    var btnSave = document.getElementById('btnSaveLogo');
    var btnReset = document.getElementById('btnResetLogo');

    if (logoData && logoData.trim()) {
      if (previewImg) {
        previewImg.src = logoData;
        previewImg.style.display = 'block';
      }
      if (defaultMark) defaultMark.style.display = 'none';
      if (logoInfo) {
        logoInfo.innerHTML = '<strong>' + escapeHtml(filename || 'Custom Logo') + '</strong>';
      }
      if (logoStatusBadge) {
        logoStatusBadge.textContent = 'Custom Logo Active';
        logoStatusBadge.className = 'badge badge-online';
      }
      if (btnReset) btnReset.disabled = false;
    } else {
      if (previewImg) {
        previewImg.src = '';
        previewImg.style.display = 'none';
      }
      if (defaultMark) defaultMark.style.display = 'flex';
      if (logoInfo) {
        logoInfo.textContent = 'Default nexPrint Branding';
      }
      if (logoStatusBadge) {
        logoStatusBadge.textContent = 'Default Logo';
        logoStatusBadge.className = 'badge badge-offline';
      }
      if (btnReset) btnReset.disabled = true;
    }
    if (btnSave) btnSave.disabled = true;
  }

  window.handleLogoFileSelect = function (event) {
    var file = event.target.files && event.target.files[0];
    if (!file) return;

    if (file.size > 4 * 1024 * 1024) {
      showToast('Image file too large. Maximum size is 4MB.', 'error');
      event.target.value = '';
      return;
    }

    var reader = new FileReader();
    reader.onload = function (e) {
      pendingLogoData = e.target.result;
      pendingLogoFilename = file.name;

      var previewImg = document.getElementById('settingsLogoPreviewImg');
      var defaultMark = document.getElementById('settingsLogoDefaultMark');
      var logoInfo = document.getElementById('settingsLogoInfo');
      var btnSave = document.getElementById('btnSaveLogo');

      if (previewImg) {
        previewImg.src = pendingLogoData;
        previewImg.style.display = 'block';
      }
      if (defaultMark) defaultMark.style.display = 'none';
      if (logoInfo) {
        logoInfo.innerHTML = '<strong>' + escapeHtml(file.name) + '</strong> (' + Math.round(file.size / 1024) + ' KB - Pending Save)';
      }
      if (btnSave) {
        btnSave.disabled = false;
      }
      showToast('Logo file selected. Click "Save Logo" to apply changes.', 'info');
    };
    reader.readAsDataURL(file);
  };

  window.handleSaveLogo = function () {
    if (!pendingLogoData) {
      showToast('Please select a logo file first.', 'error');
      return;
    }

    var btnSave = document.getElementById('btnSaveLogo');
    if (btnSave) btnSave.disabled = true;

    fetch('/api/admin/settings/logo', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        logoData: pendingLogoData,
        filename: pendingLogoFilename || 'custom-logo.png'
      })
    })
    .then(function (res) {
      if (!res.ok) return res.json().then(function (e) { throw new Error(e.error || 'Server error'); });
      return res.json();
    })
    .then(function (updated) {
      showToast('Organization logo updated and applied successfully!', 'success');
      pendingLogoData = null;
      pendingLogoFilename = null;
      var fileInput = document.getElementById('logoFileInput');
      if (fileInput) fileInput.value = '';
      updateLogoPreviewUI(updated.logoData, updated.logoFilename);
      applyBrandingToSidebar(updated);
    })
    .catch(function (err) {
      showToast('Failed to save logo: ' + err.message, 'error');
      if (btnSave) btnSave.disabled = false;
    });
  };

  window.handleResetLogo = function () {
    if (!confirm('Are you sure you want to reset the organization logo to the default branding?')) {
      return;
    }

    fetch('/api/admin/settings/logo', {
      method: 'DELETE'
    })
    .then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json();
    })
    .then(function (updated) {
      showToast('Logo reset to default branding.', 'success');
      pendingLogoData = null;
      pendingLogoFilename = null;
      var fileInput = document.getElementById('logoFileInput');
      if (fileInput) fileInput.value = '';
      updateLogoPreviewUI(null, null);
      applyBrandingToSidebar(updated);
    })
    .catch(function (err) {
      showToast('Failed to reset logo: ' + err.message, 'error');
    });
  };

  window.handleSaveGeneralSettings = function (event) {
    if (event) event.preventDefault();

    var orgName = (document.getElementById('settingOrgName').value || '').trim();
    var title = (document.getElementById('settingSystemTitle').value || '').trim();
    var subtitle = (document.getElementById('settingSystemSubtitle').value || '').trim();
    var timeout = parseInt(document.getElementById('settingInactivityTimeout').value, 10) || 60;
    var jobExpiryEl = document.getElementById('settingJobExpiryMinutes');
    var jobExpiryMins = jobExpiryEl ? (parseInt(jobExpiryEl.value, 10) || 60) : 60;
    var support = (document.getElementById('settingSupportContact').value || '').trim();

    if (!orgName) {
      showToast('Organization name is required.', 'error');
      return;
    }
    if (!title) {
      showToast('System title is required.', 'error');
      return;
    }
    if (jobExpiryMins < 1 || jobExpiryMins > 10080) {
      showToast('Held job auto-delete minutes must be between 1 and 10,080.', 'error');
      return;
    }

    var btnSubmit = document.getElementById('btnSaveGeneralSettings');
    if (btnSubmit) btnSubmit.disabled = true;

    fetch('/api/admin/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        organizationName: orgName,
        systemTitle: title,
        systemSubtitle: subtitle,
        inactivityTimeoutSecs: timeout,
        jobExpiryMinutes: jobExpiryMins,
        supportContact: support
      })
    })
    .then(function (res) {
      if (!res.ok) return res.json().then(function (e) { throw new Error(e.error || 'Server error'); });
      return res.json();
    })
    .then(function (saved) {
      showToast('General settings saved successfully!', 'success');
      applyBrandingToSidebar(saved);
      var statusBadge = document.getElementById('settingsSaveStatus');
      if (statusBadge) {
        statusBadge.textContent = 'Saved';
        statusBadge.style.background = '#F0FDF4';
        statusBadge.style.color = '#15803D';
      }
    })
    .catch(function (err) {
      showToast('Failed to save settings: ' + err.message, 'error');
    })
    .finally(function () {
      if (btnSubmit) btnSubmit.disabled = false;
    });
  };

  /* =========================================================================
     SCAN TO EMAIL & SMTP ADMIN CONTROLLER
     ========================================================================= */

  window.handleSaveSmtpSettings = function (event) {
    if (event) event.preventDefault();

    var host = (document.getElementById('settingSmtpHost').value || '').trim();
    var port = parseInt(document.getElementById('settingSmtpPort').value, 10) || 587;
    var user = (document.getElementById('settingSmtpUser').value || '').trim();
    var pass = document.getElementById('settingSmtpPass').value;
    var fromAddr = (document.getElementById('settingSmtpFrom').value || '').trim();

    var secSsl = document.getElementById('secSsl') && document.getElementById('secSsl').checked;
    var secNone = document.getElementById('secNone') && document.getElementById('secNone').checked;
    var useTls = !secSsl && !secNone;

    fetch('/api/admin/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        smtpHost: host,
        smtpPort: port,
        smtpUsername: user,
        smtpPassword: pass,
        smtpFromAddress: fromAddr || 'scan@rrconstruction.lk',
        smtpFromName: 'RR MFP Scanner',
        smtpUseTls: useTls,
        smtpUseSsl: secSsl,
        scanToEmailEnabled: true
      })
    })
    .then(function (res) {
      if (!res.ok) return res.json().then(function (e) { throw new Error(e.error || 'Server error'); });
      return res.json();
    })
    .then(function (saved) {
      showToast('Scan-to-Email & SMTP settings saved successfully!', 'success');
    })
    .catch(function (err) {
      showToast('Failed to save SMTP settings: ' + err.message, 'error');
    });
  };

  window.testSmtpConnection = function () {
    var host = (document.getElementById('settingSmtpHost').value || '').trim();
    var port = parseInt(document.getElementById('settingSmtpPort').value, 10) || 587;
    var user = (document.getElementById('settingSmtpUser').value || '').trim();
    var pass = document.getElementById('settingSmtpPass').value;
    var fromAddr = (document.getElementById('settingSmtpFrom').value || '').trim();
    var recipient = (document.getElementById('settingTestRecipient').value || '').trim();
    var statusEl = document.getElementById('smtpTestStatus');

    if (!host) {
      showToast('Please enter an SMTP server host before testing.', 'error');
      return;
    }
    if (!recipient) {
      showToast('Please enter a test recipient email address.', 'error');
      return;
    }

    var secSsl = document.getElementById('secSsl') && document.getElementById('secSsl').checked;
    var secNone = document.getElementById('secNone') && document.getElementById('secNone').checked;
    var useTls = !secSsl && !secNone;

    if (statusEl) {
      statusEl.textContent = 'Testing connection and sending test email...';
      statusEl.style.color = '#1D4ED8';
    }

    fetch('/api/admin/scans/test-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        host: host,
        port: port,
        username: user,
        password: pass,
        fromAddress: fromAddr || 'scan@rrconstruction.lk',
        testRecipient: recipient,
        useTls: useTls,
        useSsl: secSsl
      })
    })
    .then(function (res) { return res.json(); })
    .then(function (data) {
      if (data && data.success) {
        if (statusEl) {
          statusEl.textContent = '✓ ' + (data.message || 'Test email delivered successfully!');
          statusEl.style.color = '#15803D';
        }
        showToast('SMTP Test Passed: Test scan email delivered to ' + recipient, 'success');
      } else {
        if (statusEl) {
          statusEl.textContent = '✗ ' + (data.message || 'SMTP Connection failed.');
          statusEl.style.color = '#B91C1C';
        }
        showToast('SMTP Test Failed: ' + (data.message || 'Unknown error'), 'error');
      }
    })
    .catch(function (err) {
      if (statusEl) {
        statusEl.textContent = '✗ ' + err.message;
        statusEl.style.color = '#B91C1C';
      }
      showToast('SMTP Test Failed: ' + err.message, 'error');
    });
  };

  window.pushSmtpToPrintersModal = function () {
    fetch('/api/admin/printers')
      .then(function (res) { return res.json(); })
      .then(function (printers) {
        var physical = printers.filter(function (p) {
          return p.active && p.printerCode !== 'VIRTUAL-QUEUE';
        });
        if (!physical.length) {
          showToast('No active physical printers found to push SMTP configuration.', 'warning');
          return;
        }

        var promises = physical.map(function (p) {
          return fetch('/api/admin/printers/' + p.id + '/push-smtp', { method: 'POST' })
            .then(function (r) { return r.json(); });
        });

        Promise.all(promises).then(function (results) {
          var okCount = results.filter(function (r) { return r && r.success; }).length;
          showToast('Pushed SMTP settings to ' + okCount + ' of ' + physical.length + ' Toshiba MFPs.', 'success');
        });
      })
      .catch(function (err) {
        showToast('Error pushing SMTP to printers: ' + err.message, 'error');
      });
  };

  window.loadScanActivity = function () {
    var tbody = document.getElementById('scanLogsTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding:30px; color:var(--text-muted);">Loading scan records...</td></tr>';

    fetch('/api/admin/scans')
      .then(function (res) { return res.json(); })
      .then(function (scans) {
        if (!scans || !scans.length) {
          tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding:40px; color:var(--text-muted);"><div style="font-size:24px; margin-bottom:8px;">📧</div><div>No Scan-to-Email activity recorded yet.</div><div style="font-size:12px; margin-top:4px;">Use the Toshiba Touchscreen Terminal to scan documents directly to recipient emails.</div></td></tr>';
          return;
        }

        var html = '';
        for (var i = 0; i < scans.length; i++) {
          var s = scans[i];
          var dateStr = s.completedAt ? new Date(s.completedAt).toLocaleString() : '-';
          var statusBadge = s.status === 'SENT'
            ? '<span class="badge badge-online">SENT</span>'
            : (s.status === 'FAILED' ? '<span class="badge badge-offline">FAILED</span>' : '<span class="badge badge-pending">' + esc(s.status) + '</span>');

          html += '<tr>' +
            '<td style="white-space:nowrap; font-size:12px;">' + esc(dateStr) + '</td>' +
            '<td><strong>' + esc(s.username) + '</strong></td>' +
            '<td><code style="color:#1D4ED8; font-weight:700;">' + esc(s.recipientEmail) + '</code></td>' +
            '<td>' + esc(s.fileName || s.subject || 'Document.pdf') + '</td>' +
            '<td style="text-align:center;">' + (s.pages || 1) + '</td>' +
            '<td><span style="font-size:11.5px; background:#F1F5F9; padding:2px 6px; border-radius:4px; font-weight:600;">' + esc(s.colorMode) + ' &bull; ' + esc(s.resolution) + '</span></td>' +
            '<td>' + statusBadge + '</td>' +
            '<td style="font-size:12px; color:var(--text-muted);">' + esc(s.printerName || s.printerIp) + '</td>' +
            '</tr>';
        }
        tbody.innerHTML = html;
      })
      .catch(function (err) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding:30px; color:#DC2626;">Failed to load scan records: ' + esc(err.message) + '</td></tr>';
      });
  };

  /* =========================================================================
     SIDEBAR COLLAPSE CONTROLLER (100% Offline, Zero Internet Dependencies)
     ========================================================================= */

  window.toggleAdminSidebar = function () {
    var doc = document.documentElement;
    var b = document.body;
    var isCollapsed = doc.classList.contains('sidebar-collapsed') || (b && b.classList.contains('sidebar-collapsed'));

    if (isCollapsed) {
      doc.classList.remove('sidebar-collapsed', 'sidebar-collapsed-prerender');
      if (b) b.classList.remove('sidebar-collapsed', 'sidebar-collapsed-prerender');
      try { localStorage.setItem('nexprint_sidebar_collapsed', 'false'); } catch (e) {}
    } else {
      doc.classList.add('sidebar-collapsed');
      if (b) b.classList.add('sidebar-collapsed');
      try { localStorage.setItem('nexprint_sidebar_collapsed', 'true'); } catch (e) {}
    }

    var isNowCollapsed = !isCollapsed;
    var toggleBtn = document.getElementById('topbarSidebarToggle');
    if (toggleBtn) {
      toggleBtn.title = isNowCollapsed ? 'Expand Sidebar (Ctrl+B)' : 'Collapse Sidebar (Ctrl+B)';
      toggleBtn.setAttribute('aria-expanded', isNowCollapsed ? 'false' : 'true');
    }
    var collapseBtn = document.getElementById('sidebarCollapseBtn');
    if (collapseBtn) {
      collapseBtn.title = isNowCollapsed ? 'Expand Sidebar (Ctrl+B)' : 'Collapse Sidebar (Ctrl+B)';
    }
  };

  // Keyboard shortcut: Ctrl + B toggles sidebar
  window.addEventListener('keydown', function (e) {
    if ((e.ctrlKey || e.metaKey) && (e.key === 'b' || e.key === 'B')) {
      var tag = (e.target && e.target.tagName) ? e.target.tagName.toLowerCase() : '';
      if (tag === 'input' || tag === 'textarea' || tag === 'select') return;
      e.preventDefault();
      window.toggleAdminSidebar();
    }
  });

  // Close dropdown on outside click or Escape
  document.addEventListener('click', function (e) {
    if (!e.target.closest('.action-dropdown-wrap')) {
      var allMenus = document.querySelectorAll('.action-dropdown-menu');
      for (var i = 0; i < allMenus.length; i++) {
        allMenus[i].classList.remove('show');
      }
      var allBtns = document.querySelectorAll('.btn-action-dropdown');
      for (var j = 0; j < allBtns.length; j++) {
        allBtns[j].classList.remove('active');
      }
    }
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      var allMenus = document.querySelectorAll('.action-dropdown-menu');
      for (var i = 0; i < allMenus.length; i++) {
        allMenus[i].classList.remove('show');
      }
      var allBtns = document.querySelectorAll('.btn-action-dropdown');
      for (var j = 0; j < allBtns.length; j++) {
        allBtns[j].classList.remove('active');
      }
    }
  });

  // Close dropdown on page scroll or table scroll
  window.addEventListener('scroll', function () {
    var allMenus = document.querySelectorAll('.action-dropdown-menu.show');
    for (var i = 0; i < allMenus.length; i++) {
      allMenus[i].classList.remove('show');
    }
    var allBtns = document.querySelectorAll('.btn-action-dropdown.active');
    for (var j = 0; j < allBtns.length; j++) {
      allBtns[j].classList.remove('active');
    }
  }, true);

  // Initialize stored sidebar state on script execution
  try {
    if (localStorage.getItem('nexprint_sidebar_collapsed') === 'true') {
      document.documentElement.classList.add('sidebar-collapsed');
      document.body.classList.add('sidebar-collapsed');
    }
  } catch (e) {}


  window.pushLdapAndLockInModal = function () {
    var ip = document.getElementById('printerIp').value.trim();
    var user = document.getElementById('printerAdminUser').value.trim() || 'admin';
    var pass = document.getElementById('printerAdminPass').value || '123456';
    var url = document.getElementById('printerTerminalUrl').value.trim();
    var logBox = document.getElementById('modalEwbLog');
    var btn = document.getElementById('btnModalPushLdapLock');

    if (!ip) {
      showToast('Please enter Printer IP address first.', 'error');
      return;
    }

    if (btn) btn.disabled = true;
    if (logBox) {
      logBox.style.display = 'block';
      logBox.textContent = 'Connecting to TopAccess CWS and pushing System LDAP Settings & Card Lockdown to ' + ip + '...';
    }

    fetch('/api/admin/printers/push-ldap-lock-direct', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        printerIp: ip,
        adminUsername: user,
        adminPassword: pass,
        terminalUrl: url
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (btn) btn.disabled = false;
        if (data.success) {
          showToast('LDAP & Card Lock pushed to ' + ip + ' successfully!', 'success');
          if (logBox) logBox.textContent = ' SUCCESS: ' + (data.message || 'Configured for LDAP & Card Lockdown.') + '\nConfigured At: ' + (data.configuredAt || new Date().toISOString());
        } else {
          showToast('Push failed: ' + (data.message || 'Error communicating with MFP'), 'error');
          if (logBox) logBox.textContent = ' FAILED: ' + (data.message || 'Unknown error');
        }
      })
      .catch(function (err) {
        if (btn) btn.disabled = false;
        showToast('Network error: ' + err.message, 'error');
        if (logBox) logBox.textContent = ' ERROR: ' + err.message;
      });
  };

  window.pushLdapAndLockSinglePrinter = function (id) {
    var p = state.printers.find(function (x) { return x.id === id; });
    var pName = p ? (p.name + ' (' + p.ipAddress + ')') : ('Printer #' + id);

    customConfirm(
      'Push LDAP & Card Lock',
      'This will connect to TopAccess on ' + pName + ' and push System LDAP settings, Function Lockdown (Copy/Scan locked), and EWB Terminal URL. Continue?',
      'Push LDAP & Lock',
      'btn-action-primary'
    ).then(function (confirmed) {
      if (!confirmed) return;
      showToast('Pushing LDAP & Card Lock to ' + pName + '...', 'info');

      fetch('/api/admin/printers/' + id + '/push-ldap-lock', { method: 'POST' })
        .then(function (res) { return res.json(); })
        .then(function (data) {
          if (data.success) {
            showToast(' ' + (data.message || 'LDAP & Card Lock configured successfully!'), 'success');
            loadPrinters();
          } else {
            showToast(' Push failed: ' + (data.message || 'Unknown error'), 'error');
          }
        })
        .catch(function (err) {
          showToast(' Network error: ' + err.message, 'error');
        });
    });
  };

    window.handleQuickEwbPushLdap = function () {
    var pId = document.getElementById('quickEwbPrinterId').value;
    if (!pId) return;
    var ip = document.getElementById('quickEwbIp').value;
    var logBox = document.getElementById('quickEwbLog');
    var btn = document.getElementById('btnQuickEwbPushLdap');

    if (btn) btn.disabled = true;
    if (logBox) {
      logBox.textContent = 'Connecting to TopAccess CWS and pushing System LDAP Settings & Card Lockdown to ' + ip + '...';
    }

    fetch('/api/admin/printers/' + pId + '/push-ldap-lock', { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (btn) btn.disabled = false;
        if (data.success) {
          showToast(' LDAP & Card Lock pushed to ' + ip + ' successfully!', 'success');
          if (logBox) logBox.textContent = ' SUCCESS: ' + (data.message || 'Configured for LDAP & Card Lockdown.') + '\nConfigured At: ' + (data.configuredAt || new Date().toISOString());
          loadPrinters();
        } else {
          showToast(' Push failed: ' + (data.message || 'Error communicating with MFP'), 'error');
          if (logBox) logBox.textContent = ' FAILED: ' + (data.message || 'Unknown error');
        }
      })
      .catch(function (err) {
        if (btn) btn.disabled = false;
        showToast(' Network error: ' + err.message, 'error');
        if (logBox) logBox.textContent = ' ERROR: ' + err.message;
      });
  };

  window.populateDirPrintersDropdown = function () {
    var sel = document.getElementById('dirSinglePrinterSelect');
    if (!sel) return;
    var currentVal = sel.value;
    sel.innerHTML = '<option value="">-- Choose a Printer --</option>';
    var pList = (typeof state !== 'undefined' && state.printers) ? state.printers : (window.state && window.state.printers ? window.state.printers : []);
    pList.forEach(function (p) {
      var sInfo = resolvePrinterSeries(p);
      var isOld = (sInfo.code === 'TOSHIBA_OLD');
      if (!isOld) {
        var opt = document.createElement('option');
        opt.value = p.id;
        opt.textContent = p.name + ' (' + p.ipAddress + ')' + (p.functionsLocked ? ' [ Locked]' : '');
        sel.appendChild(opt);
      }
    });
    if (currentVal) sel.value = currentVal;
  };

  window.pushLdapToSelectedPrinterFromDirPage = function () {
    var sel = document.getElementById('dirSinglePrinterSelect');
    if (!sel || !sel.value) {
      showToast('Please select a printer from the dropdown first.', 'warning');
      return;
    }
    var id = parseInt(sel.value, 10);
    var p = (state.printers || []).find(function (x) { return x.id === id; });
    var pName = p ? (p.name + ' (' + p.ipAddress + ')') : ('Printer #' + id);
    var logBox = document.getElementById('dirPageLogBox');
    var btn = document.getElementById('btnDirPushSingle');

    if (btn) btn.disabled = true;
    if (logBox) {
      logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Pushing LDAP settings & Card Lockdown to ' + pName + '...';
    }
    showToast('Pushing LDAP to ' + pName + '...', 'info');

    fetch('/api/admin/printers/' + id + '/push-ldap-lock', { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (btn) btn.disabled = false;
        if (data.success) {
          showToast(' ' + (data.message || 'LDAP & Card Lock configured!'), 'success');
          if (logBox) {
            logBox.textContent = '[' + new Date().toLocaleTimeString() + ']  SUCCESS on ' + pName + ':\n' +
              (data.message || 'Configured for LDAP & Card Lockdown.') + '\nConfigured At: ' + (data.configuredAt || new Date().toISOString());
          }
          loadPrinters();
        } else {
          showToast(' Push failed: ' + (data.message || 'Unknown error'), 'error');
          if (logBox) {
            logBox.textContent = '[' + new Date().toLocaleTimeString() + ']  FAILED on ' + pName + ':\n' + (data.message || 'Unknown error');
          }
        }
      })
      .catch(function (err) {
        if (btn) btn.disabled = false;
        showToast(' Network error: ' + err.message, 'error');
        if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + ']  ERROR: ' + err.message;
      });
  };

  window.pushLdapLockAllFleet = function () {
    customConfirm(
      'Push LDAP & Card Lock to All MFPs',
      'This will connect to all active Toshiba MFPs across the fleet and automatically push System LDAP settings, Function Lockdown (Copy/Scan locked), and EWB Terminal screens. Continue?',
      'Push to All MFPs',
      'btn-action-primary'
    ).then(function (confirmed) {
      if (!confirmed) return;
      var btn = document.getElementById('btnPushLdapFleet');
      if (btn) btn.disabled = true;
      showToast('Pushing LDAP & Card Lock to entire fleet in background...', 'info');

      fetch('/api/admin/printers/push-ldap-lock-fleet', { method: 'POST' })
        .then(function (res) { return res.json(); })
        .then(function (data) {
          if (btn) btn.disabled = false;
          showToast('Fleet LDAP & Card Lock completed! ' + (data.successCount || 0) + ' of ' + (data.totalCount || 0) + ' MFPs updated.', 'success');
          loadPrinters();
        })
        .catch(function (err) {
          if (btn) btn.disabled = false;
          showToast('Fleet push error: ' + err.message, 'error');
        });
    });
  };



  /* =========================================================================
     STANDALONE DIRECTORY PAGE CONTROLLER & ATTRIBUTE INSPECTOR
     ========================================================================= */

  window.loadDirectoryPageConfig = function () {
    fetch('/api/admin/directory/config')
      .then(function (res) { return res.json(); })
      .then(function (cfg) {
        state.directoryConfig = cfg;
        if (!state.printers || state.printers.length === 0) { loadPrinters(); } else { window.populateDirPrintersDropdown(); }
        var setVal = function(id, val) {
          var el = document.getElementById(id);
          if (el && val !== undefined && val !== null) el.value = val;
        };

        setVal('dirLdapUrl', cfg.ldapUrl || 'ldap://127.0.0.1:389');
        setVal('dirDomain', cfg.domainName || 'qoa.local');
        setVal('dirBaseDn', cfg.baseDn || 'DC=qoa,DC=local');
        setVal('dirBindUser', cfg.bindUser || 'Administrator@qoa.local');
        setVal('dirBindPass', cfg.bindPassword || '');
        setVal('dirFilter', cfg.userSearchFilter || '(&(objectCategory=person)(objectClass=user))');
        setVal('dirDefaultPin', cfg.defaultPin || '1234');

        // Attribute mappings
        setVal('dirCardAttr', cfg.cardIdAttribute || 'employeeNumber');
        setVal('dirSecondaryCardAttr', cfg.secondaryCardAttribute || 'extensionAttribute2');
        setVal('dirExtAttr1', cfg.externalAttr1Name || 'extensionAttribute1');
        setVal('dirExtAttr2', cfg.externalAttr2Name || 'extensionAttribute2');
        setVal('dirExtAttr3', cfg.externalAttr3Name || 'extensionAttribute3');
        setVal('dirUserAttr', cfg.usernameAttribute || 'sAMAccountName');
        setVal('dirDispAttr', cfg.displayNameAttribute || 'displayName');
        setVal('dirDeptAttr', cfg.departmentAttribute || 'department');

        // Status KPIs
        var elDomain = document.getElementById('dirStatDomain');
        if (elDomain) elDomain.textContent = cfg.domainName || 'qoa.local';
        var elUrl = document.getElementById('dirStatLdapUrl');
        if (elUrl) elUrl.textContent = cfg.ldapUrl || 'ldap://127.0.0.1:389';
        var elCount = document.getElementById('dirStatUserCount');
        if (elCount) elCount.textContent = (cfg.syncedUserCount || 0) + ' Users';
        var elCard = document.getElementById('dirStatCardAttr');
        if (elCard) elCard.textContent = cfg.cardIdAttribute || 'employeeNumber';

        var logBox = document.getElementById('dirPageLogBox');
        if (logBox && cfg.lastSyncMessage) {
          logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Directory Status: ' + cfg.lastSyncMessage;
        }
      })
      .catch(function (err) {
        console.warn('loadDirectoryPageConfig error:', err);
      });
  };

  window.saveDirectoryPageConfig = function () {
    var payload = {
      ldapUrl: (document.getElementById('dirLdapUrl') ? document.getElementById('dirLdapUrl').value.trim() : ''),
      domainName: (document.getElementById('dirDomain') ? document.getElementById('dirDomain').value.trim() : ''),
      baseDn: (document.getElementById('dirBaseDn') ? document.getElementById('dirBaseDn').value.trim() : ''),
      bindUser: (document.getElementById('dirBindUser') ? document.getElementById('dirBindUser').value.trim() : ''),
      bindPassword: (document.getElementById('dirBindPass') ? document.getElementById('dirBindPass').value : ''),
      userSearchFilter: (document.getElementById('dirFilter') ? document.getElementById('dirFilter').value.trim() : ''),
      defaultPin: (document.getElementById('dirDefaultPin') ? document.getElementById('dirDefaultPin').value.trim() : '1234'),
      cardIdAttribute: (document.getElementById('dirCardAttr') ? document.getElementById('dirCardAttr').value.trim() : 'employeeNumber'),
      secondaryCardAttribute: (document.getElementById('dirSecondaryCardAttr') ? document.getElementById('dirSecondaryCardAttr').value.trim() : 'extensionAttribute2'),
      externalAttr1Name: (document.getElementById('dirExtAttr1') ? document.getElementById('dirExtAttr1').value.trim() : 'extensionAttribute1'),
      externalAttr2Name: (document.getElementById('dirExtAttr2') ? document.getElementById('dirExtAttr2').value.trim() : 'extensionAttribute2'),
      externalAttr3Name: (document.getElementById('dirExtAttr3') ? document.getElementById('dirExtAttr3').value.trim() : 'extensionAttribute3'),
      usernameAttribute: (document.getElementById('dirUserAttr') ? document.getElementById('dirUserAttr').value.trim() : 'sAMAccountName'),
      displayNameAttribute: (document.getElementById('dirDispAttr') ? document.getElementById('dirDispAttr').value.trim() : 'displayName'),
      departmentAttribute: (document.getElementById('dirDeptAttr') ? document.getElementById('dirDeptAttr').value.trim() : 'department'),
      isEnabled: true
    };

    var logBox = document.getElementById('dirPageLogBox');
    if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Saving Directory configuration & attribute mappings...';

    fetch('/api/admin/directory/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Configuration & Attribute Mappings saved successfully!';
        showToast('Active Directory & Attributes saved!', 'success');
        loadDirectoryPageConfig();
      })
      .catch(function (err) {
        if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Save failed: ' + err.message;
        showToast('Save failed: ' + err.message, 'error');
      });
  };

  window.testDirectoryConnectionFromPage = function () {
    var logBox = document.getElementById('dirPageLogBox');
    if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Testing connection to Active Directory Domain Controller...';
    var payload = {
      ldapUrl: document.getElementById('dirLdapUrl') ? document.getElementById('dirLdapUrl').value.trim() : '',
      domainName: document.getElementById('dirDomain') ? document.getElementById('dirDomain').value.trim() : '',
      baseDn: document.getElementById('dirBaseDn') ? document.getElementById('dirBaseDn').value.trim() : ''
    };
    fetch('/api/admin/directory/test', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Test Result: ' + (res.message || JSON.stringify(res));
        showToast(res.message || 'Connection test completed', res.success ? 'success' : 'warning');
      })
      .catch(function (err) {
        if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Connection Test Error: ' + err.message;
        showToast('Test failed: ' + err.message, 'error');
      });
  };

  window.syncActiveDirectoryFromPage = function () {
    window.syncLdapUsersWithLoading();
  };

  window.previewAdUserAttributes = function () {
    var username = (document.getElementById('dirPreviewUsername') ? document.getElementById('dirPreviewUsername').value.trim() : '');
    if (!username) {
      showToast('Please enter an AD username to inspect', 'warning');
      return;
    }
    var logBox = document.getElementById('dirPageLogBox');
    if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Querying Active Directory for attributes of: ' + username + '...';
    var wrap = document.getElementById('dirPreviewResultWrap');
    if (wrap) wrap.style.display = 'block';
    var tbl = document.getElementById('dirPreviewResultTable');
    if (tbl) tbl.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:12px;color:#64748b;">Querying Active Directory attributes...</td></tr>';

    fetch('/api/admin/directory/preview-user?username=' + encodeURIComponent(username))
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (!data.success) {
          if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Notice: ' + (data.message || 'User not found in AD.');
          if (tbl) tbl.innerHTML = '<tr><td colspan="4" style="color:#ef4444;text-align:center;padding:12px;">' + esc(data.message || 'User not found.') + '</td></tr>';
          return;
        }
        if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Attributes retrieved successfully for ' + username;
        if (tbl) {
          tbl.innerHTML =
            '<tr style="background:#f8fafc;"><td style="font-weight:700;width:25%;">sAMAccountName</td><td style="width:25%;"><code>' + esc(data.username) + '</code></td><td style="font-weight:700;width:25%;">Card Match Attribute (' + esc(data.cardIdAttribute) + ')</td><td style="width:25%;"><span class="badge" style="background:#eff6ff;color:#1d4ed8;font-weight:700;">' + esc(data.cardIdValue || 'Not Set') + '</span></td></tr>' +
            '<tr><td style="font-weight:700;">Display Name</td><td>' + esc(data.displayName || 'None') + '</td><td style="font-weight:700;">External Attribute 1 (' + esc(data.externalAttr1Name) + ')</td><td><code style="color:#0284c7;font-weight:600;">' + esc(data.externalAttr1Value || 'None') + '</code></td></tr>' +
            '<tr style="background:#f8fafc;"><td style="font-weight:700;">Mail</td><td>' + esc(data.mail || 'None') + '</td><td style="font-weight:700;">External Attribute 2 (' + esc(data.externalAttr2Name) + ')</td><td><code style="color:#059669;font-weight:600;">' + esc(data.externalAttr2Value || 'None') + '</code></td></tr>' +
            '<tr><td style="font-weight:700;">Department</td><td>' + esc(data.department || 'None') + '</td><td style="font-weight:700;">External Attribute 3 (' + esc(data.externalAttr3Name) + ')</td><td><code style="color:#d97706;font-weight:600;">' + esc(data.externalAttr3Value || 'None') + '</code></td></tr>';
        }
      })
      .catch(function (err) {
        if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Error inspecting user: ' + err.message;
        if (tbl) tbl.innerHTML = '<tr><td colspan="4" style="color:#ef4444;text-align:center;padding:12px;">' + esc(err.message) + '</td></tr>';
      });
  };


  window.toggleFleetActionsDropdown = function (event) {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    var btn = document.getElementById('btnFleetActionsDropdown');
    var menu = document.getElementById('fleetActionsDropdownMenu');
    if (!btn || !menu) return;

    var isOpen = menu.classList.contains('show');

    // Close all open dropdowns
    var allMenus = document.querySelectorAll('.action-dropdown-menu');
    for (var i = 0; i < allMenus.length; i++) {
      allMenus[i].classList.remove('show');
    }
    var allBtns = document.querySelectorAll('.btn-action-dropdown');
    for (var j = 0; j < allBtns.length; j++) {
      allBtns[j].classList.remove('active');
    }

    if (!isOpen) {
      var rect = btn.getBoundingClientRect();
      var menuWidth = 280;
      menu.style.top = (rect.bottom + 6) + 'px';
      var leftPos = rect.right - menuWidth;
      if (leftPos < 10) leftPos = 10;
      menu.style.left = leftPos + 'px';
      menu.style.minWidth = menuWidth + 'px';
      menu.classList.add('show');
      btn.classList.add('active');
    }
  };


  window.toggleDirectoryActionsDropdown = function (event) {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    var btn = document.getElementById('btnDirActionsDropdown');
    var menu = document.getElementById('dirActionsDropdownMenu');
    if (!btn || !menu) return;

    var isOpen = menu.classList.contains('show');

    // Close all open dropdowns
    var allMenus = document.querySelectorAll('.action-dropdown-menu');
    for (var i = 0; i < allMenus.length; i++) {
      allMenus[i].classList.remove('show');
    }
    var allBtns = document.querySelectorAll('.btn-action-dropdown');
    for (var j = 0; j < allBtns.length; j++) {
      allBtns[j].classList.remove('active');
    }

    if (!isOpen) {
      var rect = btn.getBoundingClientRect();
      var menuWidth = 260;
      menu.style.top = (rect.bottom + 6) + 'px';
      var leftPos = rect.right - menuWidth;
      if (leftPos < 10) leftPos = 10;
      menu.style.left = leftPos + 'px';
      menu.style.minWidth = menuWidth + 'px';
      menu.classList.add('show');
      btn.classList.add('active');
    }
  };

  window.importWindowsUsersFromPage = function () {
    var logBox = document.getElementById('dirPageLogBox');
    if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Scanning local Windows user accounts...';
    fetch('/api/admin/directory/import-windows', { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (logBox) {
          logBox.textContent = '[' + new Date().toLocaleTimeString() + '] ' + (res.message || 'Local Windows accounts scanned.');
        }
        showToast(res.message || 'Local Windows accounts scanned', res.success ? 'success' : 'warning');
        loadUsers();
      })
      .catch(function (err) {
        if (logBox) logBox.textContent = '[' + new Date().toLocaleTimeString() + '] Error: ' + err.message;
        showToast('Scan error: ' + err.message, 'error');
      });
  };


  
  
  /* =========================================================================
     PAGE: ENTERPRISE REPORTS & ADVANCED CUSTOM REPORT SYSTEM
     ========================================================================= */

  state.reports = {
    activeTab: 'departments', // 'departments' | 'costCenters' | 'printers' | 'users' | 'transactions'
    dateRange: 'ALL',
    startDate: '',
    endDate: '',
    filterDept: '',
    filterCostCenter: '',
    filterPrinter: '',
    filterSearch: '',
    summary: null,
    departments: [],
    costCenters: [],
    printers: [],
    users: [],
    scans: [],
    esg: null,
    transactions: []
  };

  window.loadReports = function () {
    if (window.rhInitIfNeeded) window.rhInitIfNeeded();
    var tbody = document.getElementById('reportsTableBody');
    if (tbody) {
      tbody.innerHTML = '<tr><td colspan="11" class="text-center py-4 text-muted">Loading report data...</td></tr>';
    }

    // 1. Fetch Summary KPI
    fetch('/api/admin/reports/summary')
      .then(function (r) { return r.json(); })
      .then(function (summary) {
        state.reports.summary = summary;
        var totalVol = summary.totalVolumePages || 0;
        var printPages = summary.totalPrintPages || 0;
        var copyPages = summary.totalCopyPages || 0;
        var scanPages = summary.totalScanCount || 0;
        var totalCost = (summary.totalEstimatedCostLkr != null) ? summary.totalEstimatedCostLkr : (summary.totalCost != null ? summary.totalCost : (totalVol * 3.00));
        var depts = summary.totalDepartments || 0;
        var topDept = summary.topDepartment || 'None';
        var topDeptPages = summary.topDepartmentPages || 0;
        var ccs = summary.totalCostCenters || 0;
        var topCC = summary.topCostCenter || 'None';
        var topCCPages = summary.topCostCenterPages || 0;
        var printers = summary.totalPrinters || 0;
        var topPrn = summary.topPrinter || 'None';
        var topPrnPages = summary.topPrinterPages || 0;

        var el;
        el = document.getElementById('repStatTotalVolume'); if (el) el.textContent = totalVol.toLocaleString();
        el = document.getElementById('repStatPrintPages'); if (el) el.textContent = printPages.toLocaleString();
        el = document.getElementById('repStatCopyPages'); if (el) el.textContent = copyPages.toLocaleString();
        el = document.getElementById('repStatScanPages'); if (el) el.textContent = scanPages.toLocaleString();
        el = document.getElementById('repStatTotalCost'); if (el) el.textContent = 'LKR ' + totalCost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2});
        el = document.getElementById('repStatPageRate'); if (el) el.textContent = 'LKR 3.00';
        el = document.getElementById('repStatTotalDepts'); if (el) el.textContent = depts;
        el = document.getElementById('repStatTopDept'); if (el) el.textContent = topDept + (topDeptPages > 0 ? ' (' + topDeptPages + ' p.)' : '');
        el = document.getElementById('repStatTotalCC'); if (el) el.textContent = ccs;
        el = document.getElementById('repStatTopCC'); if (el) el.textContent = topCC + (topCCPages > 0 ? ' (' + topCCPages + ' p.)' : '');
        el = document.getElementById('repStatTotalPrinters'); if (el) el.textContent = printers;
        el = document.getElementById('repStatTopPrinter'); if (el) el.textContent = topPrn + (topPrnPages > 0 ? ' (' + topPrnPages + ' p.)' : '');
      })
      .catch(function (e) {
        console.error('Error fetching report summary:', e);
      });

    // 2. Build query parameters for concurrent requests
    var qParams = '?';
    if (state.reports.filterDept) qParams += 'department=' + encodeURIComponent(state.reports.filterDept) + '&';
    if (state.reports.filterCostCenter) qParams += 'costCenter=' + encodeURIComponent(state.reports.filterCostCenter) + '&';
    if (state.reports.filterPrinter) qParams += 'printer=' + encodeURIComponent(state.reports.filterPrinter) + '&';
    if (state.reports.startDate) qParams += 'startDate=' + encodeURIComponent(state.reports.startDate) + '&';
    if (state.reports.endDate) qParams += 'endDate=' + encodeURIComponent(state.reports.endDate) + '&';
    if (state.reports.filterSearch) qParams += 'search=' + encodeURIComponent(state.reports.filterSearch) + '&';

    // 3. Fetch Departments, Cost Centers, Printers, Users, Scans, ESG, and Transactions concurrently
    Promise.all([
      fetch('/api/admin/reports/departments').then(function (r) { return r.json(); }),
      fetch('/api/admin/reports/cost-centers').then(function (r) { return r.json(); }),
      fetch('/api/admin/reports/printers').then(function (r) { return r.json(); }),
      fetch('/api/admin/reports/users').then(function (r) { return r.json(); }),
      fetch('/api/admin/reports/transactions' + qParams).then(function (r) { return r.json(); }),
      fetch('/api/admin/reports/scans' + qParams).then(function (r) { return r.json(); }),
      fetch('/api/admin/reports/esg' + qParams).then(function (r) { return r.json(); })
    ]).then(function (results) {
      state.reports.departments = Array.isArray(results[0]) ? results[0] : [results[0]];
      state.reports.costCenters = Array.isArray(results[1]) ? results[1] : [results[1]];
      state.reports.printers = Array.isArray(results[2]) ? results[2] : [results[2]];
      state.reports.users = Array.isArray(results[3]) ? results[3] : [results[3]];
      state.reports.transactions = Array.isArray(results[4]) ? results[4] : [results[4]];
      state.reports.scans = Array.isArray(results[5]) ? results[5] : [results[5]];
      state.reports.esg = results[6] || null;

      populateReportDropdowns();
      window.renderReports();
    }).catch(function (err) {
      if (tbody) {
        tbody.innerHTML = '<tr><td colspan="11" class="text-center py-4 text-danger">Failed to load report data: ' + esc(err.message) + '</td></tr>';
      }
    });
  };

  function populateReportDropdowns() {
    var deptSelect = document.getElementById('reportDeptFilter');
    var ccSelect = document.getElementById('reportCCFilter');
    var prnSelect = document.getElementById('reportPrinterFilter');
    var crDept = document.getElementById('crDeptFilter');
    var crCC = document.getElementById('crCCFilter');

    // 1. Departments
    var deptSet = {};
    state.reports.departments.forEach(function (d) { if (d && d.department) deptSet[d.department] = true; });
    state.reports.users.forEach(function (u) { if (u && u.department) deptSet[u.department] = true; });
    state.reports.printers.forEach(function (p) { if (p && p.department) deptSet[p.department] = true; });

    var currentDept = deptSelect ? deptSelect.value : '';
    var deptOpts = '<option value="">All Departments</option>';
    Object.keys(deptSet).sort().forEach(function (dept) {
      deptOpts += '<option value="' + esc(dept) + '"' + (dept === currentDept ? ' selected' : '') + '>' + esc(dept) + '</option>';
    });
    if (deptSelect) deptSelect.innerHTML = deptOpts;
    if (crDept) crDept.innerHTML = deptOpts;
    var rhDept = document.getElementById('rhDeptFilter');
    if (rhDept) {
      var rhCurrDept = rhDept.value || '';
      rhDept.innerHTML = deptOpts;
      if (rhCurrDept) rhDept.value = rhCurrDept;
    }

    // 2. Cost Centers
    var ccSet = {};
    state.reports.costCenters.forEach(function (c) { if (c && c.costCenter) ccSet[c.costCenter] = true; });
    state.reports.users.forEach(function (u) { if (u && u.costCenter) ccSet[u.costCenter] = true; });

    var currentCC = ccSelect ? ccSelect.value : '';
    var ccOpts = '<option value="">All Cost Centers</option>';
    Object.keys(ccSet).sort().forEach(function (cc) {
      ccOpts += '<option value="' + esc(cc) + '"' + (cc === currentCC ? ' selected' : '') + '>' + esc(cc) + '</option>';
    });
    if (ccSelect) ccSelect.innerHTML = ccOpts;
    if (crCC) crCC.innerHTML = ccOpts;

    // 3. Printers
    var prnOpts = '<option value="">All Printers</option>';
    var currentPrn = prnSelect ? prnSelect.value : '';
    state.reports.printers.forEach(function (p) {
      if (p && p.name) {
        prnOpts += '<option value="' + esc(p.name) + '"' + (p.name === currentPrn ? ' selected' : '') + '>' + esc(p.name) + ' (' + esc(p.ipAddress) + ')</option>';
      }
    });
    if (prnSelect) prnSelect.innerHTML = prnOpts;
    var rhPrn = document.getElementById('rhPrinterFilter');
    if (rhPrn) {
      var rhCurrPrn = rhPrn.value || '';
      rhPrn.innerHTML = prnOpts;
      if (rhCurrPrn) rhPrn.value = rhCurrPrn;
    }
  }

  window.switchReportTab = function (tab) {
    state.reports.activeTab = tab;

    var tabs = ['departments', 'costCenters', 'printers', 'users', 'scans', 'esg', 'transactions'];
    var btnMap = {
      departments: 'btnRepTabDepts',
      costCenters: 'btnRepTabCC',
      printers: 'btnRepTabPrinters',
      users: 'btnRepTabUsers',
      scans: 'btnRepTabScans',
      esg: 'btnRepTabEsg',
      transactions: 'btnRepTabTransactions'
    };

    tabs.forEach(function (t) {
      var btn = document.getElementById(btnMap[t]);
      if (btn) {
        if (t === tab) {
          btn.className = 'nav-link active btn-sm py-1 px-3 fw-bold';
        } else {
          btn.className = 'nav-link btn-sm py-1 px-3 fw-semibold text-secondary';
        }
      }
    });

    window.renderReports();
  };

  window.setReportDateRange = function (range) {
    state.reports.dateRange = range;

    var btnMap = {
      ALL: 'btnRangeAll',
      TODAY: 'btnRangeToday',
      '7DAYS': 'btnRange7d',
      '30DAYS': 'btnRange30d',
      CUSTOM: 'btnRangeCustom'
    };

    Object.keys(btnMap).forEach(function (k) {
      var btn = document.getElementById(btnMap[k]);
      if (btn) {
        if (k === range) btn.classList.add('active');
        else btn.classList.remove('active');
      }
    });

    var box = document.getElementById('customDateRangeBox');
    if (range === 'CUSTOM') {
      if (box) box.style.display = 'inline-flex';
    } else {
      if (box) box.style.display = 'none';

      var today = new Date();
      var fmt = function (d) {
        var y = d.getFullYear();
        var m = ('0' + (d.getMonth() + 1)).slice(-2);
        var day = ('0' + d.getDate()).slice(-2);
        return y + '-' + m + '-' + day;
      };

      if (range === 'TODAY') {
        state.reports.startDate = fmt(today);
        state.reports.endDate = fmt(today);
      } else if (range === '7DAYS') {
        var d7 = new Date();
        d7.setDate(today.getDate() - 7);
        state.reports.startDate = fmt(d7);
        state.reports.endDate = fmt(today);
      } else if (range === '30DAYS') {
        var d30 = new Date();
        d30.setDate(today.getDate() - 30);
        state.reports.startDate = fmt(d30);
        state.reports.endDate = fmt(today);
      } else {
        state.reports.startDate = '';
        state.reports.endDate = '';
      }

      window.loadReports();
    }
  };

  window.toggleCustomDateInputs = function () {
    window.setReportDateRange('CUSTOM');
  };

  window.applyReportFilters = function () {
    var deptEl = document.getElementById('reportDeptFilter');
    var ccEl = document.getElementById('reportCCFilter');
    var prnEl = document.getElementById('reportPrinterFilter');
    var searchEl = document.getElementById('reportSearchFilter');
    var startEl = document.getElementById('reportStartDate');
    var endEl = document.getElementById('reportEndDate');

    state.reports.filterDept = deptEl ? deptEl.value : '';
    state.reports.filterCostCenter = ccEl ? ccEl.value : '';
    state.reports.filterPrinter = prnEl ? prnEl.value : '';
    state.reports.filterSearch = searchEl ? searchEl.value.trim().toLowerCase() : '';

    if (state.reports.dateRange === 'CUSTOM') {
      state.reports.startDate = startEl ? startEl.value : '';
      state.reports.endDate = endEl ? endEl.value : '';
      window.loadReports();
      return;
    }

    window.renderReports();
  };

  window.resetReportFilters = function () {
    var deptEl = document.getElementById('reportDeptFilter');
    var ccEl = document.getElementById('reportCCFilter');
    var prnEl = document.getElementById('reportPrinterFilter');
    var searchEl = document.getElementById('reportSearchFilter');
    var startEl = document.getElementById('reportStartDate');
    var endEl = document.getElementById('reportEndDate');

    if (deptEl) deptEl.value = '';
    if (ccEl) ccEl.value = '';
    if (prnEl) prnEl.value = '';
    if (searchEl) searchEl.value = '';
    if (startEl) startEl.value = '';
    if (endEl) endEl.value = '';

    state.reports.filterDept = '';
    state.reports.filterCostCenter = '';
    state.reports.filterPrinter = '';
    state.reports.filterSearch = '';
    state.reports.startDate = '';
    state.reports.endDate = '';
    state.reports.dateRange = 'ALL';

    window.setReportDateRange('ALL');
  };

  window.renderReports = function () {
    var thead = document.getElementById('reportsTableHead') || document.getElementById('reportableHead');
    var tbody = document.getElementById('reportsTableBody');
    var tfoot = document.getElementById('reportsTableFoot') || document.getElementById('reportableFoot');
    if (!tbody) return;

    var tab = state.reports.activeTab;
    var fDept = state.reports.filterDept;
    var fCC = state.reports.filterCostCenter;
    var fPrn = state.reports.filterPrinter;
    var fSearch = state.reports.filterSearch;

    // -----------------------------------------------------------------------
    // TAB 1: DEPARTMENTS BREAKDOWN
    // -----------------------------------------------------------------------
    if (tab === 'departments') {
      thead.innerHTML =
        '<tr>' +
          '<th style="width:160px;">Department</th>' +
          '<th style="width:90px;">Active Users</th>' +
          '<th>Assigned Printers</th>' +
          '<th style="text-align:right; width:100px;">Print Pages</th>' +
          '<th style="text-align:right; width:100px;">Copy Pages</th>' +
          '<th style="text-align:right; width:100px;">Total Volume</th>' +
          '<th style="text-align:right; width:90px;">Fleet Share</th>' +
          '<th style="text-align:right; width:120px;">Estimated Cost</th>' +
          '<th style="width:220px;">Top Users</th>' +
        '</tr>';

      var depts = state.reports.departments.filter(function (d) {
        if (!d) return false;
        if (fDept && d.department !== fDept) return false;
        if (fSearch && (d.department || '').toLowerCase().indexOf(fSearch) === -1) return false;
        return true;
      });

      if (depts.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center py-4 text-muted">No department records match your current filters.</td></tr>';
        tfoot.innerHTML = '';
        return;
      }

      var totUsers = 0, totPrints = 0, totCopies = 0, totVol = 0, totCost = 0;
      var rowsHtml = '';
      depts.forEach(function (d) {
        var uCount = d.userCount || 0;
        var pPages = d.printPages || 0;
        var cPages = d.copyPages || 0;
        var tPages = (d.totalPages != null) ? d.totalPages : (pPages + cPages);
        var cost = (d.estimatedCostLkr != null) ? d.estimatedCostLkr : (d.totalCost != null ? d.totalCost : (tPages * 3.00));
        var pct = (d.percentOfTotal != null) ? d.percentOfTotal : (d.percentageOfTotal != null ? d.percentageOfTotal : 0.0);

        totUsers += uCount;
        totPrints += pPages;
        totCopies += cPages;
        totVol += tPages;
        totCost += cost;

        var prnBadges = (d.assignedPrinters || []).map(function (p) {
          return '<span class="badge bg-light text-dark border me-1" style="font-size:11px;">' + esc(p) + '</span>';
        }).join('') || '<span class="text-muted small">None Assigned</span>';

        var topUsersBadges = (d.topUsers || []).map(function (u) {
          var uName = (u && typeof u === 'object') ? (u.displayName || u.username) : u;
          var uVol = (u && typeof u === 'object' && u.totalPages != null) ? ' (' + u.totalPages + ' p.)' : '';
          return '<span class="badge bg-light text-dark border me-1 mb-1" style="font-size:11px;">' + esc(uName) + uVol + '</span>';
        }).join('') || '<span class="text-muted small">-</span>';

        rowsHtml +=
          '<tr>' +
            '<td><strong class="text-dark"><span class="badge bg-primary-subtle text-primary border border-primary-subtle me-1">DEPT</span>' + esc(d.department) + '</strong></td>' +
            '<td><span class="badge bg-secondary-subtle text-secondary border">' + uCount + ' Users</span></td>' +
            '<td>' + prnBadges + '</td>' +
            '<td class="text-end"><span class="badge bg-primary-subtle text-primary border border-primary-subtle">PRINT: ' + pPages.toLocaleString() + '</span></td>' +
            '<td class="text-end"><span class="badge bg-success-subtle text-success border border-success-subtle">COPY: ' + cPages.toLocaleString() + '</span></td>' +
            '<td class="text-end fw-bold text-dark">' + tPages.toLocaleString() + '</td>' +
            '<td class="text-end fw-semibold text-secondary">' + pct.toFixed(1) + '%</td>' +
            '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + cost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
            '<td>' + topUsersBadges + '</td>' +
          '</tr>';
      });

      tbody.innerHTML = rowsHtml;
      tfoot.innerHTML =
        '<tr class="bg-light">' +
          '<td>Total (' + depts.length + ' Departments)</td>' +
          '<td>' + totUsers + ' Users</td>' +
          '<td>-</td>' +
          '<td class="text-end">PRINT: ' + totPrints.toLocaleString() + '</td>' +
          '<td class="text-end">COPY: ' + totCopies.toLocaleString() + '</td>' +
          '<td class="text-end fw-bold text-dark">' + totVol.toLocaleString() + ' Pages</td>' +
          '<td class="text-end">100.0%</td>' +
          '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + totCost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
          '<td>-</td>' +
        '</tr>';

    // -----------------------------------------------------------------------
    // TAB 2: COST CENTERS BREAKDOWN (extAttr3)
    // -----------------------------------------------------------------------
    } else if (tab === 'costCenters') {
      thead.innerHTML =
        '<tr>' +
          '<th style="width:170px;">Cost Center (extAttr3)</th>' +
          '<th>Associated Departments</th>' +
          '<th style="width:90px;">Active Users</th>' +
          '<th style="text-align:right; width:100px;">Print Pages</th>' +
          '<th style="text-align:right; width:100px;">Copy Pages</th>' +
          '<th style="text-align:right; width:100px;">Total Volume</th>' +
          '<th style="text-align:right; width:90px;">Fleet Share</th>' +
          '<th style="text-align:right; width:120px;">Estimated Cost</th>' +
          '<th style="width:220px;">Top Users</th>' +
        '</tr>';

      var ccs = state.reports.costCenters.filter(function (c) {
        if (!c) return false;
        if (fCC && c.costCenter !== fCC) return false;
        if (fSearch && (c.costCenter || '').toLowerCase().indexOf(fSearch) === -1) return false;
        return true;
      });

      if (ccs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center py-4 text-muted">No cost center records match your current filters.</td></tr>';
        tfoot.innerHTML = '';
        return;
      }

      var totCCUsers = 0, totCCPrints = 0, totCCCopies = 0, totCCVol = 0, totCCCost = 0;
      var ccRowsHtml = '';
      ccs.forEach(function (c) {
        var uCount = c.userCount || 0;
        var pPages = c.printPages || 0;
        var cPages = c.copyPages || 0;
        var tPages = (c.totalPages != null) ? c.totalPages : (pPages + cPages);
        var cost = (c.estimatedCostLkr != null) ? c.estimatedCostLkr : (c.totalCost != null ? c.totalCost : (tPages * 3.00));
        var pct = (c.percentOfTotal != null) ? c.percentOfTotal : (c.percentageOfTotal != null ? c.percentageOfTotal : 0.0);

        totCCUsers += uCount;
        totCCPrints += pPages;
        totCCCopies += cPages;
        totCCVol += tPages;
        totCCCost += cost;

        var deptsArr = [];
        if (typeof c.departments === 'string') {
          deptsArr = c.departments.split(',').map(function (s) { return s.trim(); });
        } else if (Array.isArray(c.departments)) {
          deptsArr = c.departments;
        }

        var deptBadges = deptsArr.map(function (dept) {
          return '<span class="badge bg-light text-dark border me-1" style="font-size:11px;">' + esc(dept) + '</span>';
        }).join('') || '<span class="text-muted small">-</span>';

        var topUsersBadges = (c.topUsers || []).map(function (u) {
          var uName = (u && typeof u === 'object') ? (u.displayName || u.username) : u;
          var uVol = (u && typeof u === 'object' && u.totalPages != null) ? ' (' + u.totalPages + ' p.)' : '';
          return '<span class="badge bg-light text-dark border me-1 mb-1" style="font-size:11px;">' + esc(uName) + uVol + '</span>';
        }).join('') || '<span class="text-muted small">-</span>';

        ccRowsHtml +=
          '<tr>' +
            '<td><strong class="text-dark"><span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle me-1">CC</span>' + esc(c.costCenter) + '</strong></td>' +
            '<td>' + deptBadges + '</td>' +
            '<td><span class="badge bg-secondary-subtle text-secondary border">' + uCount + ' Users</span></td>' +
            '<td class="text-end"><span class="badge bg-primary-subtle text-primary border border-primary-subtle">PRINT: ' + pPages.toLocaleString() + '</span></td>' +
            '<td class="text-end"><span class="badge bg-success-subtle text-success border border-success-subtle">COPY: ' + cPages.toLocaleString() + '</span></td>' +
            '<td class="text-end fw-bold text-dark">' + tPages.toLocaleString() + '</td>' +
            '<td class="text-end fw-semibold text-secondary">' + pct.toFixed(1) + '%</td>' +
            '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + cost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
            '<td>' + topUsersBadges + '</td>' +
          '</tr>';
      });

      tbody.innerHTML = ccRowsHtml;
      tfoot.innerHTML =
        '<tr class="bg-light">' +
          '<td colspan="2">Total (' + ccs.length + ' Cost Centers)</td>' +
          '<td>' + totCCUsers + ' Users</td>' +
          '<td class="text-end">PRINT: ' + totCCPrints.toLocaleString() + '</td>' +
          '<td class="text-end">COPY: ' + totCCCopies.toLocaleString() + '</td>' +
          '<td class="text-end fw-bold text-dark">' + totCCVol.toLocaleString() + ' Pages</td>' +
          '<td class="text-end">100.0%</td>' +
          '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + totCCCost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
          '<td>-</td>' +
        '</tr>';

    // -----------------------------------------------------------------------
    // TAB 3: PRINTER DEVICES BREAKDOWN (BY DEPARTMENT & LOCATION)
    // -----------------------------------------------------------------------
    } else if (tab === 'printers') {
      thead.innerHTML =
        '<tr>' +
          '<th style="width:200px;">Printer Device</th>' +
          '<th style="width:110px;">Device Code</th>' +
          '<th style="width:140px;">Assigned Department</th>' +
          '<th>Physical Location</th>' +
          '<th>Hardware Model &amp; IP</th>' +
          '<th style="text-align:right; width:90px;">Print Pages</th>' +
          '<th style="text-align:right; width:90px;">Copy Pages</th>' +
          '<th style="text-align:right; width:95px;">Total Pages</th>' +
          '<th style="text-align:right; width:85px;">Fleet Share</th>' +
          '<th style="text-align:right; width:110px;">Estimated Cost</th>' +
          '<th style="width:85px;">Status</th>' +
        '</tr>';

      var prns = state.reports.printers.filter(function (p) {
        if (!p) return false;
        if (fDept && p.department !== fDept) return false;
        if (fPrn && p.name !== fPrn && p.ipAddress !== fPrn) return false;
        if (fSearch) {
          var text = ((p.name || '') + ' ' + (p.printerCode || '') + ' ' + (p.ipAddress || '') + ' ' + (p.department || '') + ' ' + (p.location || '')).toLowerCase();
          if (text.indexOf(fSearch) === -1) return false;
        }
        return true;
      });

      if (prns.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" class="text-center py-4 text-muted">No printer devices match your current filters.</td></tr>';
        tfoot.innerHTML = '';
        return;
      }

      var totPrnPrints = 0, totPrnCopies = 0, totPrnVol = 0, totPrnCost = 0;
      var prnRowsHtml = '';
      prns.forEach(function (p) {
        var pPages = p.printPages || 0;
        var cPages = p.copyPages || 0;
        var tPages = (p.totalPages != null) ? p.totalPages : (pPages + cPages);
        var cost = (p.estimatedCostLkr != null) ? p.estimatedCostLkr : (tPages * 3.00);
        var pct = (p.percentOfTotal != null) ? p.percentOfTotal : 0.0;

        totPrnPrints += pPages;
        totPrnCopies += cPages;
        totPrnVol += tPages;
        totPrnCost += cost;

        var statusBadge = (p.status === 'ONLINE')
          ? '<span class="badge bg-success-subtle text-success border border-success-subtle">ONLINE</span>'
          : '<span class="badge bg-secondary-subtle text-secondary border">OFFLINE</span>';

        prnRowsHtml +=
          '<tr>' +
            '<td><strong class="text-dark">' + esc(p.name) + '</strong></td>' +
            '<td><span class="badge bg-light text-dark border">' + esc(p.printerCode) + '</span></td>' +
            '<td><span class="badge bg-primary-subtle text-primary border border-primary-subtle">' + esc(p.department || 'Operations') + '</span></td>' +
            '<td><span class="small text-secondary">' + esc(p.location || '-') + '</span></td>' +
            '<td><span class="small fw-semibold text-dark">' + esc(p.model || 'Toshiba MFP') + '</span> <span class="small text-muted">(' + esc(p.ipAddress) + ')</span></td>' +
            '<td class="text-end"><span class="badge bg-primary-subtle text-primary border border-primary-subtle">PRINT: ' + pPages.toLocaleString() + '</span></td>' +
            '<td class="text-end"><span class="badge bg-success-subtle text-success border border-success-subtle">COPY: ' + cPages.toLocaleString() + '</span></td>' +
            '<td class="text-end fw-bold text-dark">' + tPages.toLocaleString() + '</td>' +
            '<td class="text-end fw-semibold text-secondary">' + pct.toFixed(1) + '%</td>' +
            '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + cost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
            '<td>' + statusBadge + '</td>' +
          '</tr>';
      });

      tbody.innerHTML = prnRowsHtml;
      tfoot.innerHTML =
        '<tr class="bg-light">' +
          '<td colspan="5">Total (' + prns.length + ' Physical Release Stations)</td>' +
          '<td class="text-end">PRINT: ' + totPrnPrints.toLocaleString() + '</td>' +
          '<td class="text-end">COPY: ' + totPrnCopies.toLocaleString() + '</td>' +
          '<td class="text-end fw-bold text-dark">' + totPrnVol.toLocaleString() + ' Pages</td>' +
          '<td class="text-end">100.0%</td>' +
          '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + totPrnCost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
          '<td>-</td>' +
        '</tr>';

    // -----------------------------------------------------------------------
    // TAB 4: DETAILED USERS ACCOUNTING
    // -----------------------------------------------------------------------
    } else if (tab === 'users') {
      thead.innerHTML =
        '<tr>' +
          '<th style="width:130px;">Username</th>' +
          '<th style="width:150px;">Display Name</th>' +
          '<th style="width:120px;">Department</th>' +
          '<th style="width:130px;">Cost Center</th>' +
          '<th style="width:130px;">Badge ID</th>' +
          '<th style="text-align:right; width:90px;">Print Pages</th>' +
          '<th style="text-align:right; width:90px;">Copy Pages</th>' +
          '<th style="text-align:right; width:90px;">Scan Docs</th>' +
          '<th style="text-align:right; width:90px;">Total Pages</th>' +
          '<th style="text-align:right; width:110px;">Estimated Cost</th>' +
          '<th style="width:80px;">Status</th>' +
        '</tr>';

      var users = state.reports.users.filter(function (u) {
        if (!u) return false;
        if (fDept && u.department !== fDept) return false;
        if (fCC && u.costCenter !== fCC) return false;
        if (fSearch) {
          var text = ((u.username || '') + ' ' + (u.displayName || '') + ' ' + (u.department || '') + ' ' + (u.costCenter || '')).toLowerCase();
          if (text.indexOf(fSearch) === -1) return false;
        }
        return true;
      });

      if (users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" class="text-center py-4 text-muted">No user accounts match your current filters.</td></tr>';
        tfoot.innerHTML = '';
        return;
      }

      var totUPrints = 0, totUCopies = 0, totUScans = 0, totUVol = 0, totUCost = 0;
      var uRowsHtml = '';
      users.forEach(function (u) {
        var pPages = u.printPages || 0;
        var cPages = u.copyPages || 0;
        var sPages = u.scanPages || 0;
        var tPages = (u.totalPages != null) ? u.totalPages : (pPages + cPages);
        var cost = (u.estimatedCostLkr != null) ? u.estimatedCostLkr : (tPages * 3.00);

        totUPrints += pPages;
        totUCopies += cPages;
        totUScans += sPages;
        totUVol += tPages;
        totUCost += cost;

        var ccBadge = u.costCenter
          ? '<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle">' + esc(u.costCenter) + '</span>'
          : '<span class="text-muted small">-</span>';

        var badgeIdText = u.badgeId
          ? '<span class="badge bg-light text-dark border">' + esc(u.badgeId) + '</span>'
          : '<span class="text-muted small">None</span>';

        var statusBadge = u.isActive
          ? '<span class="badge bg-success-subtle text-success border border-success-subtle">ACTIVE</span>'
          : '<span class="badge bg-secondary-subtle text-secondary border">DISABLED</span>';

        uRowsHtml +=
          '<tr>' +
            '<td><strong class="text-dark">' + esc(u.username) + '</strong></td>' +
            '<td>' + esc(u.displayName || '-') + '</td>' +
            '<td><span class="badge bg-primary-subtle text-primary border border-primary-subtle">' + esc(u.department || '-') + '</span></td>' +
            '<td>' + ccBadge + '</td>' +
            '<td>' + badgeIdText + '</td>' +
            '<td class="text-end"><span class="badge bg-primary-subtle text-primary border border-primary-subtle">PRINT: ' + pPages.toLocaleString() + '</span></td>' +
            '<td class="text-end"><span class="badge bg-success-subtle text-success border border-success-subtle">COPY: ' + cPages.toLocaleString() + '</span></td>' +
            '<td class="text-end"><span class="badge border" style="background:#EEF2FF; color:#4F46E5; border-color:#C7D2FE !important;">SCAN: ' + sPages.toLocaleString() + '</span></td>' +
            '<td class="text-end fw-bold text-dark">' + tPages.toLocaleString() + '</td>' +
            '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + cost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
            '<td>' + statusBadge + '</td>' +
          '</tr>';
      });

      tbody.innerHTML = uRowsHtml;
      tfoot.innerHTML =
        '<tr class="bg-light">' +
          '<td colspan="5">Total (' + users.length + ' Directory Users)</td>' +
          '<td class="text-end">PRINT: ' + totUPrints.toLocaleString() + '</td>' +
          '<td class="text-end">COPY: ' + totUCopies.toLocaleString() + '</td>' +
          '<td class="text-end" style="color:#4F46E5;">SCAN: ' + totUScans.toLocaleString() + '</td>' +
          '<td class="text-end fw-bold text-dark">' + totUVol.toLocaleString() + ' Pages</td>' +
          '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + totUCost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
          '<td>-</td>' +
        '</tr>';

    // -----------------------------------------------------------------------
    // TAB 5: SCAN TO EMAIL & DIGITAL CAPTURE AUDIT
    // -----------------------------------------------------------------------
    } else if (tab === 'scans') {
      thead.innerHTML =
        '<tr>' +
          '<th style="width:145px;">Date / Time</th>' +
          '<th style="width:110px;">User</th>' +
          '<th style="width:120px;">Department</th>' +
          '<th style="width:120px;">Cost Center</th>' +
          '<th>Source MFP</th>' +
          '<th>Recipient Email</th>' +
          '<th>Attached Document</th>' +
          '<th style="text-align:right; width:75px;">Pages</th>' +
          '<th style="width:85px;">Mode</th>' +
          '<th style="width:85px;">Delivery</th>' +
        '</tr>';

      var scans = (state.reports.scans || []).filter(function (s) {
        if (!s) return false;
        if (fDept && s.department !== fDept) return false;
        if (fCC && s.costCenter !== fCC) return false;
        if (fPrn && s.printerName !== fPrn && s.printerIp !== fPrn) return false;
        if (fSearch) {
          var text = ((s.username || '') + ' ' + (s.displayName || '') + ' ' + (s.recipientEmail || '') + ' ' + (s.fileName || '') + ' ' + (s.department || '') + ' ' + (s.printerName || '')).toLowerCase();
          if (text.indexOf(fSearch) === -1) return false;
        }
        return true;
      });

      if (scans.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" class="text-center py-4 text-muted">No scan records match your current filters.</td></tr>';
        tfoot.innerHTML = '';
        return;
      }

      var totScansCount = scans.length;
      var totScanPages = 0;
      var sRowsHtml = '';

      scans.forEach(function (s) {
        var p = s.pages || 1;
        totScanPages += p;

        var statusBadge = (s.status === 'SENT' || s.status === 'DELIVERED')
          ? '<span class="badge bg-success-subtle text-success border border-success-subtle">DELIVERED</span>'
          : '<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle">' + esc(s.status || 'PROCESSED') + '</span>';

        sRowsHtml +=
          '<tr>' +
            '<td class="small text-secondary" style="font-family:monospace; font-size:11px;">' + esc(s.timestamp) + '</td>' +
            '<td><strong class="text-dark">' + esc(s.displayName || s.username) + '</strong></td>' +
            '<td><span class="badge bg-primary-subtle text-primary border border-primary-subtle">' + esc(s.department || 'Operations') + '</span></td>' +
            '<td><span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle">' + esc(s.costCenter || 'General') + '</span></td>' +
            '<td><strong class="text-dark">' + esc(s.printerName) + '</strong> <span class="small text-muted">(' + esc(s.printerIp) + ')</span></td>' +
            '<td><span class="text-primary fw-semibold"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="me-1"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>' + esc(s.recipientEmail) + '</span></td>' +
            '<td><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="me-1 text-danger"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg><span class="fw-medium text-dark">' + esc(s.fileName) + '</span></td>' +
            '<td class="text-end fw-bold text-dark">' + p + '</td>' +
            '<td><span class="badge border" style="background:#EEF2FF; color:#4F46E5;">' + esc(s.colorMode || 'COLOR') + '</span></td>' +
            '<td>' + statusBadge + '</td>' +
          '</tr>';
      });

      tbody.innerHTML = sRowsHtml;
      tfoot.innerHTML =
        '<tr class="bg-light">' +
          '<td colspan="7">Total (' + totScansCount + ' Scanned Documents Dispatched)</td>' +
          '<td class="text-end fw-bold" style="color:#4F46E5;">' + totScanPages + ' Pages</td>' +
          '<td colspan="2">-</td>' +
        '</tr>';

    // -----------------------------------------------------------------------
    // TAB 6: ESG & ENVIRONMENTAL GREEN IMPACT
    // -----------------------------------------------------------------------
    } else if (tab === 'esg') {
      var esg = state.reports.esg || {
        totalPagesPrinted: 0,
        duplexPagesSaved: 0,
        digitalScansProcessed: 0,
        treesConsumed: 0,
        treesSaved: 0,
        carbonKgEmitted: 0,
        carbonKgSaved: 0,
        energyKwhConsumed: 0,
        duplexSavingPercentage: 0,
        departmentBreakdown: []
      };

      thead.innerHTML =
        '<tr>' +
          '<th>Department</th>' +
          '<th style="text-align:right;">Pages Printed</th>' +
          '<th style="text-align:right;">Digital Scans</th>' +
          '<th style="text-align:right;">Trees Consumed</th>' +
          '<th style="text-align:right;">CO₂ Footprint (kg)</th>' +
          '<th style="text-align:right;">Sustainability Rating</th>' +
        '</tr>';

      var deptsEsg = (esg.departmentBreakdown || []).filter(function (d) {
        if (!d) return false;
        if (fDept && d.department !== fDept) return false;
        if (fSearch && (d.department || '').toLowerCase().indexOf(fSearch) === -1) return false;
        return true;
      });

      if (deptsEsg.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center py-4 text-muted">No ESG records match your current filters.</td></tr>';
        tfoot.innerHTML = '';
        return;
      }

      var esgRowsHtml = '';
      deptsEsg.forEach(function (d) {
        var scoreBadge = (d.scansProcessed >= d.pagesPrinted)
          ? '<span class="badge bg-success-subtle text-success border border-success-subtle">EXCELLENT (A+)</span>'
          : (d.scansProcessed > 0 ? '<span class="badge bg-primary-subtle text-primary border border-primary-subtle">GOOD (B)</span>' : '<span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle">PAPER HEAVY</span>');

        esgRowsHtml +=
          '<tr>' +
            '<td><strong class="text-dark">' + esc(d.department) + '</strong></td>' +
            '<td class="text-end fw-semibold text-dark">' + (d.pagesPrinted || 0).toLocaleString() + '</td>' +
            '<td class="text-end fw-bold" style="color:#4F46E5;">' + (d.scansProcessed || 0).toLocaleString() + '</td>' +
            '<td class="text-end text-danger fw-semibold">' + (d.treesConsumed || 0).toFixed(3) + ' trees</td>' +
            '<td class="text-end text-warning-emphasis fw-bold">' + (d.carbonKgEmitted || 0).toFixed(2) + ' kg CO₂</td>' +
            '<td class="text-end">' + scoreBadge + '</td>' +
          '</tr>';
      });

      tbody.innerHTML = esgRowsHtml;
      tfoot.innerHTML =
        '<tr class="bg-success-subtle text-success-emphasis border-top border-2">' +
          '<td>Fleet ESG Totals</td>' +
          '<td class="text-end fw-bold">' + (esg.totalPagesPrinted || 0).toLocaleString() + ' Pages</td>' +
          '<td class="text-end fw-bold" style="color:#4F46E5;">' + (esg.digitalScansProcessed || 0).toLocaleString() + ' Scans</td>' +
          '<td class="text-end fw-bold text-danger">' + (esg.treesConsumed || 0).toFixed(3) + ' Trees</td>' +
          '<td class="text-end fw-bold text-warning-emphasis">' + (esg.carbonKgEmitted || 0).toFixed(2) + ' kg CO₂</td>' +
          '<td class="text-end fw-bold text-success">Trees Saved: +' + (esg.treesSaved || 0).toFixed(3) + '</td>' +
        '</tr>';

    // -----------------------------------------------------------------------
    // TAB 5: TRANSACTION LOG (ALL PRINT & COPY AUDIT JOBS)
    // -----------------------------------------------------------------------
    } else if (tab === 'transactions') {
      thead.innerHTML =
        '<tr>' +
          '<th style="width:145px;">Date / Time</th>' +
          '<th style="width:75px;">Type</th>' +
          '<th style="width:110px;">User</th>' +
          '<th style="width:110px;">Department</th>' +
          '<th style="width:110px;">Cost Center</th>' +
          '<th>Target Printer</th>' +
          '<th>Document / Operation</th>' +
          '<th style="text-align:right; width:75px;">Pages</th>' +
          '<th style="text-align:right; width:70px;">Mono</th>' +
          '<th style="text-align:right; width:70px;">Color</th>' +
          '<th style="text-align:right; width:100px;">Est. Cost</th>' +
          '<th style="width:85px;">Status</th>' +
        '</tr>';

      var txs = state.reports.transactions.filter(function (t) {
        if (!t) return false;
        if (fDept && t.department !== fDept) return false;
        if (fCC && t.costCenter !== fCC) return false;
        if (fPrn && t.printerName !== fPrn && t.printerIp !== fPrn) return false;
        if (fSearch) {
          var text = ((t.username || '') + ' ' + (t.displayName || '') + ' ' + (t.documentName || '') + ' ' + (t.department || '') + ' ' + (t.printerName || '')).toLowerCase();
          if (text.indexOf(fSearch) === -1) return false;
        }
        return true;
      });

      if (txs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="12" class="text-center py-4 text-muted">No transaction logs match your current filter parameters.</td></tr>';
        tfoot.innerHTML = '';
        return;
      }

      var totTxPages = 0, totTxMono = 0, totTxColor = 0, totTxCost = 0;
      var txRowsHtml = '';
      txs.forEach(function (t) {
        var p = t.pages || 0;
        var mono = t.monoPages || 0;
        var color = t.colorPages || 0;
        var cost = (t.estimatedCostLkr != null) ? t.estimatedCostLkr : (p * 3.00);

        totTxPages += p;
        totTxMono += mono;
        totTxColor += color;
        totTxCost += cost;

        var typeBadge = (t.type === 'COPY')
          ? '<span class="badge bg-success-subtle text-success border border-success-subtle">COPY</span>'
          : '<span class="badge bg-primary-subtle text-primary border border-primary-subtle">PRINT</span>';

        txRowsHtml +=
          '<tr>' +
            '<td><span class="small font-monospace text-secondary">' + esc(t.timestamp) + '</span></td>' +
            '<td>' + typeBadge + '</td>' +
            '<td><strong class="text-dark">' + esc(t.username) + '</strong></td>' +
            '<td><span class="badge bg-primary-subtle text-primary border border-primary-subtle">' + esc(t.department || '-') + '</span></td>' +
            '<td><span class="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle">' + esc(t.costCenter || '-') + '</span></td>' +
            '<td><span class="small fw-semibold text-dark">' + esc(t.printerName || '-') + '</span></td>' +
            '<td><span class="small text-truncate d-inline-block" style="max-width:240px;" title="' + esc(t.documentName || '-') + '">' + esc(t.documentName || '-') + '</span></td>' +
            '<td class="text-end fw-bold text-dark">' + p + '</td>' +
            '<td class="text-end text-muted small">' + mono + '</td>' +
            '<td class="text-end text-muted small">' + color + '</td>' +
            '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + cost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
            '<td><span class="badge bg-light text-success border border-success-subtle">COMPLETED</span></td>' +
          '</tr>';
      });

      tbody.innerHTML = txRowsHtml;
      tfoot.innerHTML =
        '<tr class="bg-light">' +
          '<td colspan="7">Total (' + txs.length + ' Print &amp; Copy Audit Events)</td>' +
          '<td class="text-end fw-bold text-dark">' + totTxPages.toLocaleString() + '</td>' +
          '<td class="text-end small">' + totTxMono.toLocaleString() + '</td>' +
          '<td class="text-end small">' + totTxColor.toLocaleString() + '</td>' +
          '<td class="text-end fw-bold text-warning-emphasis">Rs. ' + totTxCost.toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2}) + '</td>' +
          '<td>-</td>' +
        '</tr>';
    }
  };

  // -------------------------------------------------------------------------
  // EXPORT & PRINT HANDLERS
  // -------------------------------------------------------------------------
  window.exportCurrentReportCsv = function () {
    var tab = state.reports.activeTab || 'reports';
    window.exportAdminTableCsv('reportsMainTable', 'nexPrint_' + tab + '_report.xls', 'Enterprise Cost Center Report (' + tab + ')');
  };

  window.printReportView = function () {
    window.print();
  };

  // -------------------------------------------------------------------------
  // CUSTOM REPORT BUILDER MODAL HANDLERS
  // -------------------------------------------------------------------------
  window.openCustomReportModal = function () {
    var modal = document.getElementById('modalCustomReport');
    if (modal) modal.style.display = 'flex';
  };

  window.closeCustomReportModal = function () {
    var modal = document.getElementById('modalCustomReport');
    if (modal) modal.style.display = 'none';
  };

  window.handleGenerateCustomReport = function (e) {
    if (e) e.preventDefault();
    window.handleCustomReportView();
  };

  window.handleCustomReportView = function () {
    var type = document.getElementById('crReportType').value;
    var dept = document.getElementById('crDeptFilter').value;
    var cc = document.getElementById('crCCFilter').value;
    var start = document.getElementById('crStartDate').value;
    var end = document.getElementById('crEndDate').value;

    state.reports.filterDept = dept;
    state.reports.filterCostCenter = cc;
    state.reports.startDate = start;
    state.reports.endDate = end;

    if (document.getElementById('reportDeptFilter')) document.getElementById('reportDeptFilter').value = dept;
    if (document.getElementById('reportCCFilter')) document.getElementById('reportCCFilter').value = cc;
    if (document.getElementById('reportStartDate')) document.getElementById('reportStartDate').value = start;
    if (document.getElementById('reportEndDate')) document.getElementById('reportEndDate').value = end;

    window.closeCustomReportModal();
    window.switchReportTab(type);
    window.loadReports();
  };

  window.handleCustomReportCsv = function () {
    var type = document.getElementById('crReportType').value;
    var dept = encodeURIComponent(document.getElementById('crDeptFilter').value || '');
    var cc = encodeURIComponent(document.getElementById('crCCFilter').value || '');
    var start = encodeURIComponent(document.getElementById('crStartDate').value || '');
    var end = encodeURIComponent(document.getElementById('crEndDate').value || '');

    var url = '/api/admin/reports/export-csv?type=' + type;
    if (dept) url += '&department=' + dept;
    if (cc) url += '&costCenter=' + cc;
    if (start) url += '&startDate=' + start;
    if (end) url += '&endDate=' + end;

    window.closeCustomReportModal();
    window.location.href = url;
  };

  window.handleCustomReportPrint = function () {
    window.handleCustomReportView();
    setTimeout(function () {
      window.print();
    }, 500);
  };

  /* =========================================================================
     USER PROFILE & DETAILS VIEW MODAL CONTROLLER
     ========================================================================= */
  state.selectedViewUserId = null;

  window.viewUserDetails = function (userId) {
    var u = null;
    for (var i = 0; i < state.users.length; i++) {
      if (state.users[i].id === userId) {
        u = state.users[i];
        break;
      }
    }
    if (!u) return;
    state.selectedViewUserId = u.id;

    var modalTitle = document.getElementById('viewUserModalTitle');
    if (modalTitle) modalTitle.textContent = 'User Profile: ' + (u.displayName || u.username);

    var avatar = document.getElementById('viewUserAvatar');
    if (avatar) {
      var raw = (u.displayName || u.username || 'U').trim();
      var parts = raw.split(' ');
      var initials = parts.length > 1 ? (parts[0][0] + parts[parts.length - 1][0]) : raw.substring(0, 2);
      avatar.textContent = initials.toUpperCase() || 'U';
    }

    var nameEl = document.getElementById('viewUserName');
    if (nameEl) nameEl.textContent = (u.displayName || u.username) + ' (' + u.username + ')';

    var statusBadge = document.getElementById('viewUserStatusBadge');
    if (statusBadge) {
      statusBadge.className = u.isActive !== false ? 'badge badge-online' : 'badge badge-offline';
      statusBadge.textContent = u.isActive !== false ? 'ACTIVE' : 'DISABLED';
    }

    var roleBadge = document.getElementById('viewUserRoleBadge');
    if (roleBadge) {
      roleBadge.textContent = u.role || 'USER';
    }

    var subEl = document.getElementById('viewUserSubtitle');
    if (subEl) {
      var src = 'Manual Account';
      if (u.authSource === 'ACTIVE_DIRECTORY') src = 'Active Directory / LDAP Synchronized';
      else if (u.authSource === 'WINDOWS_LOCAL') src = 'Windows Local SAM Account';
      subEl.textContent = src + (u.department ? ' - ' + u.department + ' Department' : '');
    }

    // Identification & RFID Card Info (prominently displayed)
    var badgeEl = document.getElementById('viewUserBadgeId');
    if (badgeEl) {
      badgeEl.textContent = u.badgeId ? u.badgeId : 'No RFID Badge Assigned';
      badgeEl.style.color = u.badgeId ? '#1D4ED8' : '#94A3B8';
    }

    var secBadgeEl = document.getElementById('viewUserSecBadgeId');
    if (secBadgeEl) {
      secBadgeEl.textContent = u.secondaryBadgeId ? u.secondaryBadgeId : 'No NFC Tag Assigned';
      secBadgeEl.style.color = u.secondaryBadgeId ? '#15803D' : '#94A3B8';
    }

    var pinStatusEl = document.getElementById('viewUserPinStatus');
    if (pinStatusEl) {
      pinStatusEl.innerHTML = u.hasPin
        ? '<span class="badge badge-pin-yes">CONFIGURED</span>'
        : '<span class="badge badge-pin-no">NOT SET</span>';
    }

    var deptEl = document.getElementById('viewUserDept');
    if (deptEl) deptEl.textContent = u.department || 'Operations';

    var ccEl = document.getElementById('viewUserCostCenter');
    if (ccEl) ccEl.textContent = u.costCenter || u.externalAttribute3 || '-';

    var emailEl = document.getElementById('viewUserEmail');
    if (emailEl) emailEl.textContent = u.email || 'None';

    var sidEl = document.getElementById('viewUserSid');
    if (sidEl) sidEl.textContent = u.userSid || 'None';

    var authSrcEl = document.getElementById('viewUserAuthSource');
    if (authSrcEl) {
      if (u.authSource === 'ACTIVE_DIRECTORY') authSrcEl.innerHTML = '<span class="badge badge-source-ad">AD / LDAP</span>';
      else if (u.authSource === 'WINDOWS_LOCAL') authSrcEl.innerHTML = '<span class="badge badge-source-win">WINDOWS LOCAL</span>';
      else authSrcEl.innerHTML = '<span class="badge badge-source-manual">MANUAL</span>';
    }

    var extAttrsEl = document.getElementById('viewUserExtAttrs');
    if (extAttrsEl) {
      var badges = [];
      if (u.externalAttribute1) badges.push('<span class="badge" style="background:#F1F5F9; color:#334155; border:1px solid #CBD5E1;">Ext1: ' + esc(u.externalAttribute1) + '</span>');
      if (u.externalAttribute2) badges.push('<span class="badge" style="background:#F1F5F9; color:#334155; border:1px solid #CBD5E1;">Ext2: ' + esc(u.externalAttribute2) + '</span>');
      if (u.externalAttribute3) badges.push('<span class="badge" style="background:#F1F5F9; color:#334155; border:1px solid #CBD5E1;">Ext3: ' + esc(u.externalAttribute3) + '</span>');
      extAttrsEl.innerHTML = badges.length ? badges.join(' ') : '<span style="color:#94a3b8; font-size:11.5px;">None</span>';
    }

    // Volume & Accounting
    var printP = u.printCount || 0;
    var copyP = u.copyCount || 0;
    var scanP = u.scanCount || 0;
    var totalP = printP + copyP;
    var estCost = (totalP * 3.0).toFixed(2); // LKR 3.00 standard rate

    var printCountEl = document.getElementById('viewUserPrintCount');
    if (printCountEl) printCountEl.textContent = printP;

    var copyCountEl = document.getElementById('viewUserCopyCount');
    if (copyCountEl) copyCountEl.textContent = copyP;

    var scanCountEl = document.getElementById('viewUserScanCount');
    if (scanCountEl) scanCountEl.textContent = scanP;

    var totalPagesEl = document.getElementById('viewUserTotalPages');
    if (totalPagesEl) totalPagesEl.textContent = totalP + ' Pages';

    var estCostEl = document.getElementById('viewUserEstCost');
    if (estCostEl) estCostEl.textContent = 'Rs. ' + estCost;

    document.getElementById('viewUserModal').style.display = 'flex';
  };

  window.closeViewUserModal = function () {
    var m = document.getElementById('viewUserModal');
    if (m) m.style.display = 'none';
  };

  window.triggerResetPinFromView = function () {
    if (!state.selectedViewUserId) return;
    var u = null;
    for (var i = 0; i < state.users.length; i++) {
      if (state.users[i].id === state.selectedViewUserId) { u = state.users[i]; break; }
    }
    window.closeViewUserModal();
    if (u) window.openResetPinModal(u.id, u.username);
  };

  window.triggerEditFromView = function () {
    if (!state.selectedViewUserId) return;
    var uid = state.selectedViewUserId;
    window.closeViewUserModal();
    window.openEditUserModal(uid);
  };


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


  /* =========================================================================
     FLEET PRINTER MONITORING CONTROLLER (REAL HARDWARE & CRISP LIGHT THEME)
     Real-Time RFC 3805 SNMP Telemetry Engine
     ========================================================================= */

  var nocState = {
    fleetData: null,
    printers: [],
    activeView: 'matrix', // 'matrix' or 'cards'
    filterStatus: 'ALL',
    filterFloor: 'ALL',
    searchQuery: '',
    countdownSecs: 15,
    pollTimer: null,
    clockTimer: null,
    isInitialized: false,
    isTvMode: false,
    selectedPrinter: null
  };

  window.initNocMonitoring = function () {
    startNocClock();
    startNocPollingCountdown();
    window.refreshNocFleet(true, true);
  };

  function startNocClock() {
    if (nocState.clockTimer) return;
    updateNocClockDisplay();
    nocState.clockTimer = setInterval(updateNocClockDisplay, 1000);
  }

  function updateNocClockDisplay() {
    var clockEl = document.getElementById('nocLiveClock');
    var dateEl  = document.getElementById('nocLiveDate');
    if (!clockEl) return;

    var now = new Date();
    var h = String(now.getHours()).padStart(2, '0');
    var m = String(now.getMinutes()).padStart(2, '0');
    var s = String(now.getSeconds()).padStart(2, '0');
    clockEl.textContent = h + ':' + m + ':' + s;

    if (dateEl) {
      var options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
      dateEl.textContent = now.toLocaleDateString('en-US', options).toUpperCase();
    }
  }

  function startNocPollingCountdown() {
    if (nocState.pollTimer) clearInterval(nocState.pollTimer);
    nocState.countdownSecs = 5;
    updateCountdownBadge();

    nocState.pollTimer = setInterval(function () {
      if (state.currentPage !== 'monitoring') return;
      nocState.countdownSecs--;
      if (nocState.countdownSecs <= 0) {
        nocState.countdownSecs = 5;
        window.refreshNocFleet(true, true);
      }
      updateCountdownBadge();
    }, 1000);
  }

  function updateCountdownBadge() {
    var badge = document.getElementById('nocCountdownBadge');
    if (badge) {
      badge.textContent = 'Polls in ' + nocState.countdownSecs + 's';
    }
  }

  window.refreshNocFleet = function (force, silent) {
    if (force === undefined) force = false;
    var refreshBtn = document.getElementById('btnNocRefresh');
    if (refreshBtn && force && !silent) {
      refreshBtn.style.opacity = '0.6';
      refreshBtn.style.pointerEvents = 'none';
    }

    // Only query real hardware printers added in the database (no dummy printers)
    var url = '/api/admin/printers/monitoring' + (force ? '?refresh=true' : '');
    fetch(url)
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        if (refreshBtn && force && !silent) {
          refreshBtn.style.opacity = '1';
          refreshBtn.style.pointerEvents = 'auto';
        }
        nocState.fleetData = data;
        nocState.printers = data.printers || [];
        updateNocKpis(data);
        renderNocFleet();
        var nocModalEl = document.getElementById('modalNocDeviceDetail');
        if (nocModalEl && nocModalEl.style.display === 'flex' && nocState.selectedPrinter && nocState.selectedPrinter.id) {
          window.openNocDeviceModal(nocState.selectedPrinter.id);
        }
        if (force && !silent) showToast('SNMP Telemetry refreshed from hardware!', 'success');
      })
      .catch(function (err) {
        if (refreshBtn && force) {
          refreshBtn.style.opacity = '1';
          refreshBtn.style.pointerEvents = 'auto';
        }
        console.error('SNMP Poll error:', err);
      });
  };

  function updateNocKpis(data) {
    if (!data) return;
    var elTotal    = document.getElementById('nocKpiTotal');
    var elReady    = document.getElementById('nocKpiReady');
    var elPrinting = document.getElementById('nocKpiPrinting');
    var elWarning  = document.getElementById('nocKpiWarning');
    var elError    = document.getElementById('nocKpiError');
    var elVolume   = document.getElementById('nocKpiVolume');
    var badgeCount = document.getElementById('nocActiveCountBadge');

    var count = data.totalPrinters || 0;
    if (elTotal)    elTotal.textContent    = count;
    if (badgeCount) badgeCount.textContent = count + ' MFPs';
    if (elReady)    elReady.textContent    = (data.onlinePrinters - data.printingPrinters - data.warningPrinters) || 0;
    if (elPrinting) elPrinting.textContent = data.printingPrinters || 0;
    if (elWarning)  elWarning.textContent  = data.warningPrinters || 0;
    if (elError)    elError.textContent    = (data.errorPrinters + data.offlinePrinters) || 0;
    if (elVolume)   elVolume.textContent   = Number(data.totalFleetPrints || 0).toLocaleString();

    // Populate Floor / Location dropdown with real locations
    var select = document.getElementById('nocFloorFilter');
    if (select && data.printers) {
      var currentVal = nocState.filterFloor;
      var locs = [];
      for (var i = 0; i < data.printers.length; i++) {
        var loc = data.printers[i].location || data.printers[i].floor;
        if (loc && locs.indexOf(loc) === -1) locs.push(loc);
      }
      var sHtml = '<option value="ALL">📍 All (' + count + ')</option>';
      for (var j = 0; j < locs.length; j++) {
        var sel = (locs[j] === currentVal) ? ' selected' : '';
        sHtml += '<option value="' + escapeHtml(locs[j]) + '"' + sel + '>' + escapeHtml(locs[j]) + '</option>';
      }
      select.innerHTML = sHtml;
    }
  }

  function getFilteredFleet() {
    var list = nocState.printers.slice();

    // 1. Status Filter
    if (nocState.filterStatus !== 'ALL') {
      list = list.filter(function (p) {
        if (nocState.filterStatus === 'READY') {
          return (p.status === 'READY');
        } else if (nocState.filterStatus === 'PRINTING') {
          return (p.status === 'PRINTING');
        } else if (nocState.filterStatus === 'WARNING') {
          return (p.status === 'WARNING');
        } else if (nocState.filterStatus === 'ERROR') {
          return (p.status === 'ERROR' || p.status === 'OFFLINE');
        }
        return true;
      });
    }

    // 2. Floor / Location Filter
    if (nocState.filterFloor !== 'ALL') {
      list = list.filter(function (p) {
        return (p.location === nocState.filterFloor || p.floor === nocState.filterFloor);
      });
    }

    // 3. Text Search Query
    if (nocState.searchQuery) {
      var q = nocState.searchQuery.toLowerCase();
      list = list.filter(function (p) {
        return (p.printerCode || '').toLowerCase().indexOf(q) !== -1 ||
               (p.name || '').toLowerCase().indexOf(q) !== -1 ||
               (p.ipAddress || '').toLowerCase().indexOf(q) !== -1 ||
               (p.department || '').toLowerCase().indexOf(q) !== -1 ||
               (p.location || '').toLowerCase().indexOf(q) !== -1 ||
               (p.model || '').toLowerCase().indexOf(q) !== -1;
      });
    }

    return list;
  }

  function renderNocFleet() {
    var filtered = getFilteredFleet();
    if (nocState.activeView === 'matrix') {
      renderNocMatrix(filtered);
    } else {
      renderNocCards(filtered);
    }
  }



  /* =========================================================================
     VIEW 1: NOC MATRIX GRID (CRISP LIGHT THEME + 3D MFP ICON + ERRORS)
     ========================================================================= */
  function renderNocMatrix(list) {
    var container = document.getElementById('nocMatrixContainer');
    var cardsContainer = document.getElementById('nocCardsContainer');
    if (!container) return;

    container.style.display = 'grid';
    if (cardsContainer) cardsContainer.style.display = 'none';

    if (list.length === 0) {
      container.innerHTML = '<div style="grid-column: 1 / -1; text-align:center; padding:50px; color:#64748B; font-size:14px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:10px;">No registered printers match the filter criteria.</div>';
      return;
    }

    var html = '';
    for (var i = 0; i < list.length; i++) {
      var p = list[i];
      var statClass = (p.status || 'READY').toLowerCase();
      var indicatorClass = statClass;
      if (p.status === 'OFFLINE') statClass = 'status-offline';
      else statClass = 'status-' + statClass;

      var tonerPct = 80;
      var tonerColor = '#059669';
      if (p.toners && p.toners.length > 0) {
        tonerPct = p.toners[0].levelPercent || 80;
      }
      if (tonerPct <= 10) tonerColor = '#DC2626';
      else if (tonerPct <= 25) tonerColor = '#D97706';

      var realBadge = '<span class="noc-badge-real">LAN</span>';
      var isPrinting = (p.currentlyPrinting || p.status === 'PRINTING' || p.hrPrinterStatusText === 'PRINTING');

      var bulbClass = 'normal';
      var bulbTitle = 'Normal / Ready';
      if (isPrinting) {
        bulbClass = 'printing';
        bulbTitle = 'Currently Printing';
      } else if (p.status === 'WARNING') {
        bulbClass = 'warning';
        bulbTitle = 'Warning' + ((p.alertDetails && p.alertDetails.length > 0) ? (' #' + p.alertDetails[0].code) : '');
      } else if (p.status === 'ERROR') {
        bulbClass = 'error';
        bulbTitle = 'Fault' + ((p.alertDetails && p.alertDetails.length > 0) ? (' #' + p.alertDetails[0].code) : '');
      } else if (p.status === 'OFFLINE') {
        bulbClass = 'offline';
        bulbTitle = 'Offline';
      }

      var bulbTag = '<span class="noc-bulb ' + bulbClass + '" title="' + escapeHtml(bulbTitle) + '"></span>';

      var isOffline = (p.status === 'OFFLINE');
      var hasError = !isOffline && (p.status === 'ERROR');
      var emptyDrawers = [];
      if (!isOffline && p.trays) {
        p.trays.forEach(function (t) {
          if (String(t.status).toUpperCase() === 'EMPTY') {
            var tn = String(t.trayName || '').replace(/Drawer\s*/i, 'D').replace(/Tray\s*/i, 'D').replace(/Bypass.*/i, 'BYP').trim();
            if (tn && emptyDrawers.indexOf(tn) === -1) emptyDrawers.push(tn);
          }
        });
      }
      if (!isOffline && emptyDrawers.length === 0) {
        var checkTexts = [];
        if (p.alerts) checkTexts = checkTexts.concat(p.alerts);
        if (p.detectedErrors) checkTexts = checkTexts.concat(p.detectedErrors);
        if (p.alertDetails) p.alertDetails.forEach(function (ad) { checkTexts.push((ad.location || '') + ' ' + (ad.description || '')); });
        checkTexts.forEach(function (txt) {
          var m = String(txt).match(/(?:Drawer|Tray|Cassette)\s*([1-4])/i);
          if (m && emptyDrawers.indexOf('D' + m[1]) === -1) emptyDrawers.push('D' + m[1]);
        });
      }
      var hasPaperEmpty = !isOffline && (
        emptyDrawers.length > 0 ||
        (p.detectedErrors && p.detectedErrors.some(function (e) { var u = String(e).toUpperCase(); return u.indexOf('PAPER') !== -1 || u.indexOf('EMPTY') !== -1; })) ||
        (p.alerts && p.alerts.some(function (a) { return String(a).toUpperCase().indexOf('PAPER') !== -1; })) ||
        (p.alertDetails && p.alertDetails.some(function (ad) { return String((ad.codeName || '') + ' ' + (ad.description || '')).toUpperCase().indexOf('PAPER') !== -1; }))
      );
      var drawerLabel = emptyDrawers.length > 0 ? emptyDrawers.join(',') : 'EMPTY';
      var paperEmptyIcon = hasPaperEmpty
        ? '<span title="Paper Empty: ' + escapeHtml(drawerLabel) + '" style="display:inline-flex; align-items:center; gap:2px; background:#FEF2F2; border:1px solid #FECACA; color:#DC2626; font-size:9px; font-weight:800; padding:0 3px; line-height:1.3; white-space:nowrap; font-family:\'JetBrains Mono\',monospace;"><img src="img/paper-empty.jpg" alt="Paper Empty" style="width:13px; height:13px; object-fit:contain; mix-blend-mode:multiply;"><span>' + escapeHtml(drawerLabel) + '</span></span>'
        : '';
      var shortModel = String(p.name || p.model || '').replace(/Toshiba\s*/i, '').replace(/e-?STUDIO\s*/i, '').trim() || p.printerCode;
      var tileStateClass = isOffline ? ' noc-offline-gray' : (hasError ? ' noc-error-blink' : '');
      html += '<div class="noc-tile' + tileStateClass + '" style="overflow:hidden;" onclick="window.openNocDeviceModal(' + p.id + ')" title="' + escapeHtml(p.printerCode + ' - ' + p.name + ' (' + p.statusMessage + ')') + '">';
      html += '  <div class="noc-tile-head" style="gap:4px; flex-wrap:nowrap;">';
      html += '    <div style="display:flex; align-items:center; gap:5px; min-width:0;">';
      html += '      <img src="img/toshiba-mfp.png" alt="MFP" style="width:24px; height:24px; object-fit:contain; flex-shrink:0;">';
      html += '      <span class="noc-tile-code" style="font-size:11px;">' + escapeHtml(p.printerCode) + '</span>';
      html += '    </div>';
      var onlineDot = !isOffline
        ? '<span title="Online" style="display:inline-block; width:7px; height:7px; background:#10B981; border-radius:50% !important; flex-shrink:0;"></span>'
        : '';
      var showAlertBulb = (p.status === 'WARNING' || p.status === 'ERROR' || p.status === 'OFFLINE' || isPrinting);
      html += '    <div style="display:flex; align-items:center; gap:4px; flex-shrink:0;">' + paperEmptyIcon + onlineDot + (showAlertBulb ? bulbTag : '') + '</div>';
      html += '  </div>';
      html += '  <div class="noc-tile-loc" style="font-weight:700;">' + escapeHtml(shortModel) + '</div>';
      html += '  <div style="font-size:10px; color:#475569; font-family:\'JetBrains Mono\',monospace; margin-bottom:4px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">' + escapeHtml(p.location || 'Office') + ' \u2022 ' + escapeHtml(p.ipAddress || '') + '</div>';
      html += '  <div class="noc-tile-toner-row">';
      html += '    <div class="noc-mini-toner-track" title="Toner ' + tonerPct + '%">';
      html += '      <div class="noc-mini-toner-bar" style="width:' + Math.max(5, tonerPct) + '%; background:' + tonerColor + ';"></div>';
      html += '    </div>';
      html += '    <span style="font-size:9.5px; color:#475569; font-family:\'JetBrains Mono\',monospace; font-weight:700;">' + tonerPct + '%</span>';
      html += '  </div>';
      html += '  <div class="noc-tile-foot">';
      html += '    <span>' + escapeHtml(p.location || 'Office') + '</span>';
      html += '    <span>' + Number(p.lifeCount || 0).toLocaleString() + ' p</span>';
      html += '  </div>';
      html += '</div>';
    }

    container.innerHTML = html;
  }

  /* =========================================================================
     VIEW 2: VISUAL FLEET EQUIPMENT CARDS (CRISP LIGHT THEME + 3D ICON + ERRORS)
     ========================================================================= */
  function renderNocCards(list) {
    var container = document.getElementById('nocCardsContainer');
    var matrixContainer = document.getElementById('nocMatrixContainer');
    if (!container) return;

    container.style.display = 'grid';
    if (matrixContainer) matrixContainer.style.display = 'none';

    if (list.length === 0) {
      container.innerHTML = '<div style="grid-column: 1 / -1; text-align:center; padding:50px; color:#64748B; font-size:14px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:10px;">No registered printers match the filter criteria.</div>';
      return;
    }

    var html = '';
    for (var i = 0; i < list.length; i++) {
      var p = list[i];
      var statKey = (p.status || 'READY').toLowerCase();
      var isPrinting = (p.currentlyPrinting || p.status === 'PRINTING' || p.hrPrinterStatusText === 'PRINTING');

      // Toner Cylinders (Short CMYK Symbols)
      var tonerBarsHtml = '';
      if (p.toners && p.toners.length > 0) {
        for (var t = 0; t < p.toners.length; t++) {
          var tn = p.toners[t];
          var fillPct = Math.max(4, Math.min(100, tn.levelPercent));
          var fillBg = tn.color || '#2563EB';
          var shortCode = 'K';
          var tLower = (tn.name || '').toLowerCase();
          if (tLower.indexOf('black') !== -1 || tLower === 'k') { shortCode = 'K'; fillBg = '#1E293B'; }
          else if (tLower.indexOf('cyan') !== -1 || tLower === 'c') { shortCode = 'C'; fillBg = '#0284C7'; }
          else if (tLower.indexOf('magenta') !== -1 || tLower === 'm') { shortCode = 'M'; fillBg = '#DB2777'; }
          else if (tLower.indexOf('yellow') !== -1 || tLower === 'y') { shortCode = 'Y'; fillBg = '#D97706'; }
          else { shortCode = tn.name.substring(0, 3).toUpperCase(); }

          if (fillPct <= 10) fillBg = '#DC2626';
          else if (fillPct <= 20) fillBg = '#D97706';

          tonerBarsHtml += '<div class="noc-toner-cyl" title="' + escapeHtml(tn.name) + ': ' + fillPct + '%">';
          tonerBarsHtml += '  <div class="noc-toner-cyl-head">';
          tonerBarsHtml += '    <span style="font-weight:800;">' + shortCode + '</span>';
          tonerBarsHtml += '    <span>' + fillPct + '%</span>';
          tonerBarsHtml += '  </div>';
          tonerBarsHtml += '  <div class="noc-toner-track">';
          tonerBarsHtml += '    <div class="noc-toner-fill" style="width:' + fillPct + '%; background:' + fillBg + ';"></div>';
          tonerBarsHtml += '  </div>';
          tonerBarsHtml += '</div>';
        }
      }

      // Trays (Short Codes D1, D2, LCF, BYP)
      var traysHtml = '';
      if (p.trays && p.trays.length > 0) {
        for (var ty = 0; ty < p.trays.length; ty++) {
          var tray = p.trays[ty];
          var emptyClass = tray.status === 'EMPTY' ? 'empty' : '';
          var shortTray = (tray.trayName || '')
            .replace(/Drawer\s*/i, 'D')
            .replace(/Tray\s*/i, 'T')
            .replace(/Large Capacity Feeder|LCF/i, 'LCF')
            .replace(/Bypass\s*(Tray)?/i, 'BYP');

          traysHtml += '<div class="noc-tray-chip ' + emptyClass + '" title="' + escapeHtml(tray.trayName) + ' (' + escapeHtml(tray.paperSize) + ')">';
          traysHtml += '  <span>' + escapeHtml(shortTray) + ' <small style="opacity:0.8;">(' + escapeHtml(tray.paperSize) + ')</small></span>';
          traysHtml += '  <span style="font-weight:700;">' + (tray.status === 'EMPTY' ? '\u2715 0%' : tray.levelPercent + '%') + '</span>';
          traysHtml += '</div>';
        }
      }

      // Alert Banners (Compact RFC 3805 Error Codes)
      var alertsHtml = '';
      if (p.alertDetails && p.alertDetails.length > 0) {
        for (var aIdx = 0; aIdx < p.alertDetails.length; aIdx++) {
          var alt = p.alertDetails[aIdx];
          var isCritical = alt.severity === 'CRITICAL' || p.status === 'ERROR';
          alertsHtml += '<div class="noc-card-alert ' + (isCritical ? 'critical' : '') + '" style="margin-bottom:6px; padding:6px 10px;">';
          alertsHtml += '  <div style="display:flex; justify-content:space-between; align-items:center; font-weight:800; font-size:11px;">';
          alertsHtml += '    <span>\u26A0\uFE0F #' + alt.code + ' ' + escapeHtml(alt.codeName) + '</span>';
          if (alt.location) alertsHtml += '    <span style="font-weight:700; opacity:0.85;">\uD83D\uDCCD ' + escapeHtml(alt.location) + '</span>';
          alertsHtml += '  </div>';
          alertsHtml += '  <div style="font-size:11px; margin-top:2px; font-weight:600;">' + escapeHtml(alt.description) + '</div>';
          alertsHtml += '</div>';
        }
      } else if (p.alerts && p.alerts.length > 0) {
        var isCrit = p.status === 'ERROR';
        alertsHtml = '<div class="noc-card-alert ' + (isCrit ? 'critical' : '') + '" style="padding:6px 10px;"><span>\u26A0\uFE0F ' + escapeHtml(p.alerts[0]) + '</span></div>';
      }

      // Bitfield detected errors (Short symbols)
      if (p.detectedErrors && p.detectedErrors.length > 0) {
        alertsHtml += '<div style="font-size:10px; padding:3px 8px; background:#FEF2F2; border:1px solid #FECACA; color:#B91C1C; border-radius:4px; font-weight:700; margin-top:4px;">\uD83D\uDEA8 ' + escapeHtml(p.detectedErrors.join(' \u2022 ')) + '</div>';
      }

      // Status Badge with small bulb (Normal=Green, Printing=Violet, Warning=Amber, Error=Red)
      var statusBadgeHtml = '';
      if (isPrinting) {
        statusBadgeHtml = '<span class="noc-status-badge printing"><span class="noc-bulb printing"></span> PRINT</span>';
      } else if (p.status === 'READY') {
        statusBadgeHtml = '<span class="noc-status-badge ready"><span class="noc-bulb normal"></span> READY</span>';
      } else if (p.status === 'WARNING') {
        var codeTag = (p.alertDetails && p.alertDetails.length > 0) ? (' #' + p.alertDetails[0].code) : '';
        statusBadgeHtml = '<span class="noc-status-badge warning"><span class="noc-bulb warning"></span> WARN' + codeTag + '</span>';
      } else if (p.status === 'ERROR') {
        var errTag = (p.alertDetails && p.alertDetails.length > 0) ? (' #' + p.alertDetails[0].code) : '';
        statusBadgeHtml = '<span class="noc-status-badge error"><span class="noc-bulb error"></span> ERR' + errTag + '</span>';
      } else {
        statusBadgeHtml = '<span class="noc-status-badge offline"><span class="noc-bulb offline"></span> OFF</span>';
      }

      var wasteBadge = '<span style="font-size:10px; font-weight:700; padding:2px 6px; border-radius:4px; background:' + (p.wasteTonerStatus === 'FULL' ? '#FEE2E2' : '#F1F5F9') + '; color:' + (p.wasteTonerStatus === 'FULL' ? '#DC2626' : '#475569') + '; border:1px solid ' + (p.wasteTonerStatus === 'FULL' ? '#FECACA' : '#E2E8F0') + ';" title="Waste Toner Box">\uD83D\uDDD1\uFE0F ' + escapeHtml(p.wasteTonerStatus || 'OK') + '</span>';

      html += '<div class="noc-card">';
      html += '  <div class="noc-card-header">';
      html += '    <div class="noc-card-identity">';
      html += '      <div class="noc-card-icon-box ' + statKey + '">';
      html += '        <img src="img/toshiba-mfp.png" alt="Toshiba MFP" style="width:78px; height:78px; object-fit:contain; filter:drop-shadow(0 4px 8px rgba(0,0,0,0.14));">';
      html += '      </div>';
      html += '      <div>';
      html += '        <div class="noc-card-code" style="display:flex; align-items:center; gap:6px;">' + escapeHtml(p.printerCode) + ' <span class="noc-badge-real">LAN</span> ' + wasteBadge + '</div>';
      html += '        <div class="noc-card-name">' + escapeHtml(p.name) + '</div>';
      html += '        <div class="noc-card-meta">' + escapeHtml(p.ipAddress) + ' \u2022 \uD83D\uDCCD ' + escapeHtml(p.location || 'Office') + '</div>';
      html += '      </div>';
      html += '    </div>';
      html += '    ' + statusBadgeHtml;
      html += '  </div>';

      if (alertsHtml) {
        html += '<div style="margin-top:10px;">' + alertsHtml + '</div>';
      }

      html += '  <div class="noc-card-consumables">';
      html += '    <div class="noc-toners-row">' + tonerBarsHtml + '</div>';
      if (traysHtml) {
        html += '    <div class="noc-trays-row">' + traysHtml + '</div>';
      }
      html += '  </div>';

      html += '  <div class="noc-card-footer">';
      html += '    <div class="noc-odometer" title="Lifetime Page Count"><span>\uD83D\uDCC4</span> <span>' + Number(p.lifeCount || 0).toLocaleString() + '</span></div>';
      html += '    <button type="button" class="btn btn-sm btn-secondary" onclick="window.openNocDeviceModal(' + p.id + ')" style="padding:4px 10px; font-size:11px; font-weight:700;">Diag \u2197</button>';
      html += '  </div>';
      html += '</div>';
    }

    container.innerHTML = html;
  }

  /* =========================================================================
     VIEW SWITCHING & CONTROLS
     ========================================================================= */
  window.switchNocView = function (mode) {
    nocState.activeView = mode;
    var btnM = document.getElementById('btnNocViewMatrix');
    var btnC = document.getElementById('btnNocViewCards');

    if (btnM) btnM.classList.toggle('active', mode === 'matrix');
    if (btnC) btnC.classList.toggle('active', mode === 'cards');

    renderNocFleet();
  };

  window.toggleNocTvMode = function () {
    var body = document.body;
    var isNowTv = !body.classList.contains('noc-fullscreen-active');
    body.classList.toggle('noc-fullscreen-active', isNowTv);
    nocState.isTvMode = isNowTv;

    var btnText = document.getElementById('btnNocTvText');
    if (btnText) {
      btnText.textContent = isNowTv ? 'Exit TV Mode' : 'TV Wall Mode';
    }

    if (isNowTv) {
      if (document.documentElement.requestFullscreen) {
        document.documentElement.requestFullscreen().catch(function () {});
      }
    } else {
      if (document.exitFullscreen && document.fullscreenElement) {
        document.exitFullscreen().catch(function () {});
      }
    }
  };

  // Exit TV mode on ESC key or browser fullscreen exit
  document.addEventListener('fullscreenchange', function () {
    if (!document.fullscreenElement) {
      document.body.classList.remove('noc-fullscreen-active');
      var btnText = document.getElementById('btnNocTvText');
      if (btnText) btnText.textContent = 'TV Wall Mode';
    }
  });

  /* =========================================================================
     FILTERING & SEARCH
     ========================================================================= */
  window.filterNocStatus = function (status) {
    nocState.filterStatus = status;
    updateFilterPillStyles();
    renderNocFleet();
  };

  window.filterNocFloor = function (floor) {
    nocState.filterFloor = floor;
    renderNocFleet();
  };

  window.searchNocFleet = function (query) {
    nocState.searchQuery = (query || '').trim();
    renderNocFleet();
  };

  function updateFilterPillStyles() {
    var pAll      = document.getElementById('pillNocAll');
    var pReady    = document.getElementById('pillNocReady');
    var pPrinting = document.getElementById('pillNocPrinting');
    var pWarning  = document.getElementById('pillNocWarning');
    var pError    = document.getElementById('pillNocError');

    var s = nocState.filterStatus;
    if (pAll)      pAll.classList.toggle('active', s === 'ALL');
    if (pReady)    pReady.classList.toggle('active', s === 'READY');
    if (pPrinting) pPrinting.classList.toggle('active', s === 'PRINTING');
    if (pWarning)  pWarning.classList.toggle('active', s === 'WARNING');
    if (pError)    pError.classList.toggle('active', s === 'ERROR');
  }

  /* =========================================================================
     DETAILED SNMP DEVICE DIAGNOSTICS MODAL (LIGHT THEME + FULL ERROR TELEMETRY)
     ========================================================================= */
  window.openNocDeviceModal = function (printerId) {
    var p = nocState.printers.find(function (item) { return item.id === printerId; });
    if (!p) return;

    nocState.selectedPrinter = p;

    var modal = document.getElementById('modalNocDeviceDetail');
    if (!modal) return;

    var isPrinting = (p.currentlyPrinting || p.status === 'PRINTING' || p.hrPrinterStatusText === 'PRINTING');

    document.getElementById('nocModalTitle').textContent = p.printerCode + ' - ' + p.name;
    document.getElementById('nocModalSub').textContent = (p.location || 'Office') + ' \u2022 ' + (p.department || 'General') + ' \u2022 ' + p.ipAddress + ' \u2022 ' + (p.model || 'Toshiba MFP');
    document.getElementById('nocModalUptime').textContent = p.uptime || '0s';
    document.getElementById('nocModalLifeCount').textContent = Number(p.lifeCount || 0).toLocaleString() + ' clicks';
    document.getElementById('nocModalPing').textContent = (p.pingMs || 1) + ' ms';
    document.getElementById('nocModalIp').textContent = p.ipAddress;
    document.getElementById('nocModalHardwareType').textContent = 'Physical Toshiba e-BRIDGE Next MFP (LAN Hardware)';
    document.getElementById('nocModalLastPolled').textContent = p.lastPolled ? new Date(p.lastPolled).toLocaleTimeString() : new Date().toLocaleTimeString();

    // Engine & Printing State
    var elEngine = document.getElementById('nocModalEngineState');
    if (elEngine) {
      elEngine.textContent = p.hrPrinterStatusText || (isPrinting ? 'PRINTING' : (p.status === 'OFFLINE' ? 'OFFLINE' : 'IDLE'));
      elEngine.style.color = isPrinting ? '#0284C7' : (p.status === 'ERROR' ? '#DC2626' : '#059669');
    }

    var elIsPrinting = document.getElementById('nocModalIsPrinting');
    if (elIsPrinting) {
      elIsPrinting.textContent = isPrinting ? 'YES (Active)' : 'NO (Idle)';
      elIsPrinting.style.color = isPrinting ? '#0284C7' : '#64748B';
    }

    var elWaste = document.getElementById('nocModalWasteBox');
    if (elWaste) {
      var wStat = p.wasteTonerStatus || 'OK';
      elWaste.textContent = wStat;
      elWaste.style.color = (wStat === 'FULL') ? '#DC2626' : (wStat === 'NEAR_FULL' ? '#D97706' : '#059669');
    }

    // Live State Badge in Header
    var liveBadge = document.getElementById('nocModalLiveStateBadge');
    if (liveBadge) {
      if (isPrinting) {
        liveBadge.textContent = 'PRINTING';
        liveBadge.style.background = '#F5F3FF';
        liveBadge.style.color = '#7C3AED';
      } else if (p.status === 'READY') {
        liveBadge.textContent = 'IDLE / READY';
        liveBadge.style.background = '#DCFCE7';
        liveBadge.style.color = '#15803D';
      } else if (p.status === 'WARNING') {
        liveBadge.textContent = 'WARNING';
        liveBadge.style.background = '#FEF3C7';
        liveBadge.style.color = '#B45309';
      } else if (p.status === 'ERROR') {
        liveBadge.textContent = 'FAULT';
        liveBadge.style.background = '#FEE2E2';
        liveBadge.style.color = '#B91C1C';
      } else {
        liveBadge.textContent = 'OFFLINE';
        liveBadge.style.background = '#F1F5F9';
        liveBadge.style.color = '#64748B';
      }
    }

    var openWebBtn = document.getElementById('btnNocOpenWebUi');
    if (openWebBtn) {
      openWebBtn.href = 'https://' + p.ipAddress;
    }

    // Status Dot Color (Small bulb: normal=green, printing=violet, warning=amber, error=red)
    var dot = document.getElementById('nocModalStatusDot');
    if (dot) {
      var bulbType = isPrinting ? 'printing' : (p.status === 'READY' ? 'normal' : (p.status === 'WARNING' ? 'warning' : (p.status === 'ERROR' ? 'error' : 'offline')));
      dot.className = 'noc-bulb ' + bulbType;
      var color = '#10B981';
      if (isPrinting) color = '#8B5CF6';
      else if (p.status === 'WARNING') color = '#F59E0B';
      else if (p.status === 'ERROR') color = '#EF4444';
      else if (p.status === 'OFFLINE') color = '#94A3B8';
      dot.style.background = color;
      dot.style.boxShadow = '0 0 8px ' + color;
    }

    // Active Alerts & Detailed RFC Error Codes
    var alertSec = document.getElementById('nocModalAlertSection');
    var alertList = document.getElementById('nocModalAlertList');
    var badgeAlertCount = document.getElementById('nocModalAlertCountBadge');
    var flagsBar = document.getElementById('nocModalDetectedErrorsBar');

    // Bitfield flags bar
    if (flagsBar) {
      if (p.detectedErrors && p.detectedErrors.length > 0) {
        flagsBar.style.display = 'block';
        flagsBar.innerHTML = '\uD83D\uDEA8 <strong>Flags:</strong> ' + escapeHtml(p.detectedErrors.join(' \u2022 '));
      } else {
        flagsBar.style.display = 'none';
        flagsBar.innerHTML = '';
      }
    }

    if (alertSec && alertList) {
      if (p.alertDetails && p.alertDetails.length > 0) {
        alertSec.style.display = 'block';
        if (badgeAlertCount) badgeAlertCount.textContent = p.alertDetails.length + (p.alertDetails.length === 1 ? ' Fault' : ' Faults');
        
        var aHtml = '';
        for (var k = 0; k < p.alertDetails.length; k++) {
          var ad = p.alertDetails[k];
          var isCrit = ad.severity === 'CRITICAL' || p.status === 'ERROR';
          var bg = isCrit ? '#FEF2F2' : '#FFFBEB';
          var border = isCrit ? '#FECACA' : '#FDE68A';
          var textCol = isCrit ? '#991B1B' : '#92400E';
          var sevBadge = isCrit 
            ? '<span style="background:#DC2626; color:#FFFFFF; padding:2px 6px; border-radius:4px; font-size:10px; font-weight:800;">CRIT</span>' 
            : '<span style="background:#F59E0B; color:#FFFFFF; padding:2px 6px; border-radius:4px; font-size:10px; font-weight:800;">WARN</span>';

          aHtml += '<div style="background:' + bg + '; border:1px solid ' + border + '; color:' + textCol + '; padding:8px 12px; border-radius:8px;">';
          aHtml += '  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:3px;">';
          aHtml += '    <div style="font-size:12px; font-weight:800; font-family:\'JetBrains Mono\',monospace;">\u26A0\uFE0F #' + ad.code + ' [' + escapeHtml(ad.codeName) + ']</div>';
          aHtml += '    <div>' + sevBadge + '</div>';
          aHtml += '  </div>';
          aHtml += '  <div style="font-size:11.5px; font-weight:600; margin-bottom:4px;">' + escapeHtml(ad.description) + '</div>';
          aHtml += '  <div style="font-size:10.5px; color:#64748B; display:flex; gap:12px; font-family:\'JetBrains Mono\',monospace;">';
          if (ad.location) aHtml += '    <span>\uD83D\uDCCD <strong style="color:#0F172A;">' + escapeHtml(ad.location) + '</strong></span>';
          if (ad.time) aHtml += '    <span>\u23F1\uFE0F <strong style="color:#0F172A;">' + escapeHtml(ad.time) + '</strong></span>';
          aHtml += '  </div>';
          aHtml += '</div>';
        }
        alertList.innerHTML = aHtml;
      } else if ((p.alerts && p.alerts.length > 0) || p.status === 'ERROR' || p.status === 'WARNING' || p.status === 'OFFLINE') {
        var extraAlerts = (p.alerts && p.alerts.length > 0) ? p.alerts : [p.statusMessage || ('Device status: ' + p.status)];
        alertSec.style.display = 'block';
        if (badgeAlertCount) badgeAlertCount.textContent = extraAlerts.length;
        alertList.innerHTML = extraAlerts.map(function (a) {
          var isErr = (p.status === 'ERROR' || p.status === 'OFFLINE');
          return '<div style="background:' + (isErr ? '#FEF2F2' : '#FFFBEB') + '; border:1px solid ' + (isErr ? '#FECACA' : '#FDE68A') + '; color:' + (isErr ? '#991B1B' : '#B45309') + '; padding:8px 12px; font-size:12px; font-weight:700;">\u26A0\uFE0F ' + escapeHtml(a) + '</div>';
        }).join('');
      } else {
        alertSec.style.display = 'none';
        alertList.innerHTML = '';
      }
    }

    // Toners
    var tonerList = document.getElementById('nocModalTonerList');
    if (tonerList) {
      var tHtml = '';
      if (p.toners && p.toners.length > 0) {
        for (var i = 0; i < p.toners.length; i++) {
          var t = p.toners[i];
          var pct = Math.max(0, Math.min(100, t.levelPercent));
          var barColor = '#1E293B';
          var shortCode = 'K';
          var tLower = (t.name || '').toLowerCase();
          if (tLower.indexOf('cyan') !== -1 || tLower === 'c') { shortCode = 'C (Cyan)'; barColor = '#0284C7'; }
          else if (tLower.indexOf('magenta') !== -1 || tLower === 'm') { shortCode = 'M (Magenta)'; barColor = '#DB2777'; }
          else if (tLower.indexOf('yellow') !== -1 || tLower === 'y') { shortCode = 'Y (Yellow)'; barColor = '#D97706'; }
          else { shortCode = 'K (Black)'; }

          if (pct <= 10) barColor = '#DC2626';
          else if (pct <= 20) barColor = '#D97706';

          tHtml += '<div style="background:#F8FAFC; border:1px solid #E2E8F0; border-radius:8px; padding:8px 12px;">';
          tHtml += '  <div style="display:flex; justify-content:space-between; font-size:11.5px; font-weight:700; margin-bottom:5px;">';
          tHtml += '    <span style="color:#0F172A;">' + shortCode + '</span>';
          tHtml += '    <span style="font-family:\'JetBrains Mono\',monospace; color:' + barColor + ';">' + pct + '% ' + (t.status === 'OK' ? '\u2713' : '\u26A0') + '</span>';
          tHtml += '  </div>';
          tHtml += '  <div style="height:7px; background:#E2E8F0; border-radius:4px; overflow:hidden;">';
          tHtml += '    <div style="height:100%; width:' + pct + '%; background:' + barColor + '; border-radius:4px;"></div>';
          tHtml += '  </div>';
          tHtml += '</div>';
        }
      }
      tonerList.innerHTML = tHtml;
    }

    // Trays
    var trayList = document.getElementById('nocModalTrayList');
    if (trayList) {
      var trHtml = '';
      if (p.trays && p.trays.length > 0) {
        for (var j = 0; j < p.trays.length; j++) {
          var tr = p.trays[j];
          var isEmp = tr.status === 'EMPTY';
          var shortTray = (tr.trayName || '')
            .replace(/Drawer\s*/i, 'D')
            .replace(/Tray\s*/i, 'T')
            .replace(/Large Capacity Feeder|LCF/i, 'LCF')
            .replace(/Bypass\s*(Tray)?/i, 'BYP');

          trHtml += '<div style="background:#F8FAFC; border:1px solid ' + (isEmp ? '#FCA5A5' : '#E2E8F0') + '; border-radius:8px; padding:8px 12px;">';
          trHtml += '  <div style="font-size:10px; color:#64748B; text-transform:uppercase; font-weight:700;">' + escapeHtml(shortTray) + ' (' + escapeHtml(tr.paperSize) + ')</div>';
          trHtml += '  <div style="font-size:13px; font-weight:800; font-family:\'JetBrains Mono\',monospace; margin-top:2px; color:' + (isEmp ? '#DC2626' : '#0F172A') + ';">';
          trHtml += isEmp ? '\u2715 EMPTY' : tr.levelPercent + '%';
          trHtml += '  </div>';
          trHtml += '</div>';
        }
      }
      trayList.innerHTML = trHtml;
    }

    modal.style.display = 'flex';
  };

  window.closeNocDeviceModal = function () {
    var modal = document.getElementById('modalNocDeviceDetail');
    if (modal) modal.style.display = 'none';
  };

  /* =========================================================================
     20 STANDARD ISO REPORTS HUB & ADMIN TABLE UTILITIES
     ========================================================================= */
  var RH_GROUPS = [
    ['Users & Cost Centers', [
      { id: 'user-summary', code: 'RPT-01', label: 'User-Wise Summary', desc: 'Print, copy & scan volumes and estimated LKR spend per user.' },
      { id: 'user-history', code: 'RPT-02', label: 'Detailed Transaction History', desc: 'Chronological ISO 8601 audit ledger of all print, copy, and scan jobs.' },
      { id: 'department-summary', code: 'RPT-03', label: 'Department Allocation', desc: 'Volume share and cost attribution by organizational department.' },
      { id: 'top-users', code: 'RPT-04', label: 'Top 10 Heavy Users', desc: 'Highest-volume users ranked by total print & copy pages.' },
      { id: 'inactive-users', code: 'RPT-05', label: 'Zero-Usage / Inactive Users', desc: 'Directory accounts with zero recorded print, copy, or scan output.' },
      { id: 'quota-compliance', code: 'RPT-06', label: 'User Quota Compliance', desc: 'Tracks monthly consumption against the 1,000-page threshold.' },
      { id: 'user-printer-matrix', code: 'RPT-21', label: 'User x Printer Copy & Print Breakdown', desc: 'Shows which printers each user used and exact copy/print/scan counts per MFP.' }
    ]],
    ['Printers & Consumables', [
      { id: 'printer-utilization', code: 'RPT-07', label: 'Printer Fleet Utilization', desc: 'Job counts, page throughput, share %, and online status per MFP.' },
      { id: 'series-comparison', code: 'RPT-08', label: 'MFP Series Comparison', desc: 'Workload split across Toshiba e-STUDIO 08 / 18 / 28 generations.' },
      { id: 'toner-forecast', code: 'RPT-09', label: 'SNMP Toner Forecast', desc: 'Live RFC 3805 toner levels, engine status, and active hardware alerts.' },
      { id: 'paper-errors', code: 'RPT-10', label: 'Paper & Error Incidents', desc: 'Blocked releases, paper-empty warnings, and device errors.' }
    ]],
    ['Paper & Environment', [
      { id: 'paper-size', code: 'RPT-11', label: 'Paper Size (A4 / A3)', desc: 'Volume split between standard A4 and large-format 2x-weighted A3 jobs.' },
      { id: 'duplex-ratio', code: 'RPT-12', label: 'Simplex vs Duplex Ratio', desc: 'Double-sided printing adoption and physical paper sheets saved.' },
      { id: 'color-mono', code: 'RPT-13', label: 'Color vs Mono Usage', desc: 'Monochrome and color page distribution across prints and copies.' },
      { id: 'waste-expired', code: 'RPT-14', label: 'Waste Reduction (Expired)', desc: 'Held jobs purged automatically on timeout, saving forgotten paper.' },
      { id: 'user-cancelled', code: 'RPT-15', label: 'User-Cancelled Jobs', desc: 'Print jobs cancelled cleanly by users at the touchscreen queue.' }
    ]],
    ['Time Trends & Security', [
      { id: 'daily-trend', code: 'RPT-16', label: 'Daily Volume Trend', desc: 'Day-by-day print, copy & scan roll-up formatted in YYYY-MM-DD.' },
      { id: 'monthly-summary', code: 'RPT-17', label: 'Monthly Executive Summary', desc: 'Month-over-month fleet throughput and cost roll-up (YYYY-MM).' },
      { id: 'peak-hours', code: 'RPT-18', label: 'Peak Hour Activity (24h)', desc: 'Identifies busiest office hours across print, copy, and scan jobs.' },
      { id: 'scan-activity', code: 'RPT-19', label: 'Scan-to-Email Relay Log', desc: 'Digital document relay history with SMTP delivery status.' },
      { id: 'security-audit', code: 'RPT-20', label: 'Security & Audit Trail', desc: 'Authentication events, auto-locks, expiries, and admin changes.' }
    ]]
  ];

  var rhState = {
    initialized: false,
    activeId: '',
    catalogQuery: '',
    data: null,
    sortCol: -1,
    sortDir: 'asc'
  };

  function rhFindMeta(reportId) {
    for (var g = 0; g < RH_GROUPS.length; g++) {
      var cat = RH_GROUPS[g][0];
      var items = RH_GROUPS[g][1];
      for (var i = 0; i < items.length; i++) {
        if (items[i].id === reportId) {
          return {
            id: items[i].id,
            code: items[i].code,
            label: items[i].label,
            desc: items[i].desc,
            category: cat
          };
        }
      }
    }
    return null;
  }

  function rhToIsoDate(d) {
    var y = d.getFullYear();
    var m = d.getMonth() + 1;
    var day = d.getDate();
    return y + '-' + (m < 10 ? '0' : '') + m + '-' + (day < 10 ? '0' : '') + day;
  }

  function rhComputeDates() {
    var sel = document.getElementById('rhPeriod');
    var mode = sel ? sel.value : 'all';
    var now = new Date();
    var today = rhToIsoDate(now);
    if (mode === 'today') return { from: today, to: today };
    if (mode === '7d') {
      var d7 = new Date(now.getTime() - 6 * 86400000);
      return { from: rhToIsoDate(d7), to: today };
    }
    if (mode === '30d') {
      var d30 = new Date(now.getTime() - 29 * 86400000);
      return { from: rhToIsoDate(d30), to: today };
    }
    if (mode === 'month') {
      return { from: today.slice(0, 7) + '-01', to: today };
    }
    if (mode === 'custom') {
      var fEl = document.getElementById('rhFrom');
      var tEl = document.getElementById('rhTo');
      return {
        from: fEl ? (fEl.value || '') : '',
        to: tEl ? (tEl.value || '') : ''
      };
    }
    return { from: '', to: '' };
  }

  function rhBuildNav() {
    var sel = document.getElementById('rhReportSelect');
    if (sel) {
      var optHtml = '';
      for (var g0 = 0; g0 < RH_GROUPS.length; g0++) {
        var cat0 = RH_GROUPS[g0][0];
        var items0 = RH_GROUPS[g0][1];
        optHtml += '<optgroup label="' + esc(cat0) + '">';
        for (var i0 = 0; i0 < items0.length; i0++) {
          var it0 = items0[i0];
          var isSel = rhState.activeId === it0.id ? ' selected' : '';
          optHtml += '<option value="' + esc(it0.id) + '"' + isSel + '>' + esc(it0.code + ' \u2014 ' + it0.label) + '</option>';
        }
        optHtml += '</optgroup>';
      }
      sel.innerHTML = optHtml;
      if (rhState.activeId) sel.value = rhState.activeId;
    }

    var nav = document.getElementById('rhNav');
    if (!nav) return;
    var q = rhState.catalogQuery;
    var html = '';
    var matchCount = 0;

    for (var g = 0; g < RH_GROUPS.length; g++) {
      var cat = RH_GROUPS[g][0];
      var items = RH_GROUPS[g][1];
      var catHtml = '';
      for (var i = 0; i < items.length; i++) {
        var it = items[i];
        var hay = (it.code + ' ' + it.label + ' ' + it.desc + ' ' + cat).toLowerCase();
        if (q && hay.indexOf(q) === -1) continue;
        matchCount++;
        var isAct = rhState.activeId === it.id;
        catHtml += '<button type="button" class="rh-item-btn' + (isAct ? ' active' : '') + '" data-report-id="' + esc(it.id) + '" title="' + esc(it.desc) + '" onclick="window.rhSelect(\'' + esc(it.id) + '\')">';
        catHtml += '<span class="rh-item-code">' + esc(it.code) + '</span>';
        catHtml += '<span class="text-truncate">' + esc(it.label) + '</span>';
        catHtml += '</button>';
      }
      if (catHtml) {
        html += '<div class="rh-cat-label">' + esc(cat) + '</div>' + catHtml;
      }
    }

    if (matchCount === 0) {
      html = '<div class="text-center text-muted small py-3">No matching reports found.</div>';
    }
    nav.innerHTML = html;
  }

  window.switchReportsWorkspaceMode = function (mode) {
    var isoView = document.getElementById('isoReportsHubView');
    var sapView = document.getElementById('sapReportBuilderView');
    var expView = document.getElementById('legacyReportsExplorerView');
    var btnIso = document.getElementById('btnModeIsoHub');
    var btnSap = document.getElementById('btnModeSapBuilder');
    var btnExp = document.getElementById('btnModeLegacyExplorer');

    [isoView, sapView, expView].forEach(function (v) { if (v) v.style.display = 'none'; });
    [btnIso, btnSap, btnExp].forEach(function (b) { if (b) { b.classList.remove('btn-primary'); b.classList.add('btn-secondary'); } });

    if (mode === 'explorer') {
      if (expView) expView.style.display = '';
      if (btnExp) { btnExp.classList.remove('btn-secondary'); btnExp.classList.add('btn-primary'); }
    } else if (mode === 'sap') {
      if (sapView) sapView.style.display = '';
      if (btnSap) { btnSap.classList.remove('btn-secondary'); btnSap.classList.add('btn-primary'); }
      if (!sapState.data && window.sapExecuteQuery) window.sapExecuteQuery();
    } else {
      if (isoView) isoView.style.display = '';
      if (btnIso) { btnIso.classList.remove('btn-secondary'); btnIso.classList.add('btn-primary'); }
      if (window.rhInitIfNeeded) window.rhInitIfNeeded();
    }
  };

  window.rhFilterCatalog = function (val) {
    rhState.catalogQuery = (val || '').trim().toLowerCase();
    rhBuildNav();
  };

  window.rhInitIfNeeded = function () {
    if (!rhState.initialized) {
      rhState.initialized = true;
      rhBuildNav();
    } else {
      var nav = document.getElementById('rhNav');
      if (nav && !nav.children.length) rhBuildNav();
    }
    if (!rhState.activeId) {
      window.rhSelect('user-summary');
    }
  };

  window.rhSelect = function (reportId) {
    rhState.activeId = reportId;
    rhState.sortCol = -1;
    rhState.sortDir = 'asc';
    var sr = document.getElementById('rhSearch');
    if (sr) sr.value = '';

    var sel = document.getElementById('rhReportSelect');
    if (sel && sel.value !== reportId) sel.value = reportId;

    var btns = document.querySelectorAll('#rhNav .rh-item-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].classList.toggle('active', btns[i].getAttribute('data-report-id') === reportId);
    }

    var meta = rhFindMeta(reportId);
    var codeEl = document.getElementById('rhCodeBadge');
    var catEl = document.getElementById('rhCategoryBadge');
    var titleEl = document.getElementById('rhTitle');
    var descEl = document.getElementById('rhDesc');
    if (meta) {
      if (codeEl) codeEl.textContent = meta.code;
      if (catEl) catEl.textContent = meta.category;
      if (titleEl) titleEl.textContent = meta.label;
      if (descEl) descEl.textContent = meta.desc;
    }

    window.rhRun();
  };

  window.rhPeriodChanged = function () {
    var sel = document.getElementById('rhPeriod');
    var mode = sel ? sel.value : 'all';
    var customs = document.querySelectorAll('.rh-custom');
    for (var i = 0; i < customs.length; i++) {
      customs[i].style.display = (mode === 'custom') ? '' : 'none';
    }
    if (mode === 'custom') {
      var fEl = document.getElementById('rhFrom');
      var tEl = document.getElementById('rhTo');
      var today = new Date();
      var prior = new Date(today.getTime() - 29 * 86400000);
      if (fEl && !fEl.value) fEl.value = rhToIsoDate(prior);
      if (tEl && !tEl.value) tEl.value = rhToIsoDate(today);
    }
    window.rhRun();
  };

  window.rhRun = function () {
    if (!rhState.activeId) rhState.activeId = 'user-summary';
    var dates = rhComputeDates();
    var deptEl = document.getElementById('rhDeptFilter');
    var prnEl = document.getElementById('rhPrinterFilter');
    var dept = deptEl ? (deptEl.value || '') : '';
    var printer = prnEl ? (prnEl.value || '') : '';

    var params = [];
    if (dates.from) params.push('from=' + encodeURIComponent(dates.from));
    if (dates.to) params.push('to=' + encodeURIComponent(dates.to));
    if (dept) params.push('department=' + encodeURIComponent(dept));
    if (printer) params.push('printer=' + encodeURIComponent(printer));

    var url = '/api/admin/reports/hub/' + encodeURIComponent(rhState.activeId) + (params.length ? '?' + params.join('&') : '');

    var emptyEl = document.getElementById('rhEmpty');
    var resEl = document.getElementById('rhResult');
    var runBtn = document.getElementById('rhBtnRun');
    if (runBtn) runBtn.disabled = true;
    if (emptyEl) {
      emptyEl.style.display = '';
      emptyEl.innerHTML = '<div class="fw-semibold mb-1">Generating report...</div><div class="small text-secondary">Fetching live database &amp; hardware telemetry.</div>';
    }
    if (resEl) resEl.style.display = 'none';

    fetch(url)
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status + ' while loading report');
        return res.json();
      })
      .then(function (data) {
        rhState.data = data;
        var codeEl = document.getElementById('rhCodeBadge');
        var catEl = document.getElementById('rhCategoryBadge');
        var titleEl = document.getElementById('rhTitle');
        var descEl = document.getElementById('rhDesc');
        var metaEl = document.getElementById('rhMeta');
        if (codeEl && data.code) codeEl.textContent = data.code;
        if (catEl && data.category) catEl.textContent = data.category;
        if (titleEl && data.title) titleEl.textContent = data.title;
        if (descEl && data.description) descEl.textContent = data.description;
        var rowCount = Array.isArray(data.rows) ? data.rows.length : 0;
        var rangeText = (dates.from || dates.to) ? ((dates.from || 'Start') + ' to ' + (dates.to || 'Now')) : 'All time';
        if (metaEl) {
          metaEl.textContent = 'ISO 8601 Period: ' + rangeText + ' | Generated: ' + (data.generatedAt || '') + ' | Records: ' + rowCount;
        }
        if (emptyEl) emptyEl.style.display = 'none';
        if (resEl) resEl.style.display = '';
        window.rhRender();
      })
      .catch(function (err) {
        if (resEl) resEl.style.display = 'none';
        if (emptyEl) {
          emptyEl.style.display = '';
          emptyEl.innerHTML = '<div class="fw-semibold text-danger mb-1">Failed to generate report</div><div class="small text-secondary">' + esc(err && err.message ? err.message : 'Server error') + '</div>';
        }
      })
      .finally(function () {
        if (runBtn) runBtn.disabled = false;
      });
  };

  function rhGetVisibleRows() {
    var data = rhState.data;
    if (!data || !Array.isArray(data.rows)) return [];
    var srEl = document.getElementById('rhSearch');
    var q = srEl ? (srEl.value || '').trim().toLowerCase() : '';
    var list = data.rows.filter(function (row) {
      if (!q) return true;
      for (var c = 0; c < row.length; c++) {
        var val = row[c];
        if (val != null && String(val).toLowerCase().indexOf(q) !== -1) return true;
      }
      return false;
    });

    if (rhState.sortCol >= 0) {
      var colIdx = rhState.sortCol;
      var dir = rhState.sortDir === 'desc' ? -1 : 1;
      list.sort(function (a, b) {
        var va = a[colIdx];
        var vb = b[colIdx];
        if (va == null && vb == null) return 0;
        if (va == null) return 1;
        if (vb == null) return -1;
        if (typeof va === 'number' && typeof vb === 'number') {
          return (va - vb) * dir;
        }
        return String(va).localeCompare(String(vb), undefined, { numeric: true, sensitivity: 'base' }) * dir;
      });
    }
    return list;
  }

  function rhFormatStatusBadge(text) {
    var up = String(text).toUpperCase();
    if (up === 'ONLINE' || up === 'ACTIVE' || up === 'COMPLIANT' || up === 'OK' || up === 'SENT' || up === 'DELIVERED' || up === 'LOW' || up === 'TRACKED' || up === 'SUCCESS') {
      return '<span class="badge badge-completed">' + esc(text) + '</span>';
    }
    if (up === 'WARNING' || up === 'LOW TONER' || up === 'MODERATE') {
      return '<span class="badge badge-held">' + esc(text) + '</span>';
    }
    if (up === 'OFFLINE' || up === 'EXCEEDED' || up === 'FAILURE' || up === 'FAILED' || up === 'REPLACE SOON' || up === 'PEAK' || up === 'DISABLED' || up === 'CANCELLED') {
      return '<span class="badge badge-cancelled">' + esc(text) + '</span>';
    }
    return null;
  }

  window.rhSort = function (colIdx) {
    if (rhState.sortCol === colIdx) {
      rhState.sortDir = (rhState.sortDir === 'asc') ? 'desc' : 'asc';
    } else {
      rhState.sortCol = colIdx;
      rhState.sortDir = 'asc';
    }
    window.rhRender();
  };

  window.rhRender = function () {
    var data = rhState.data;
    if (!data) return;

    // 1. 4-KPI Summary Strip
    var kpiRow = document.getElementById('rhKpiRow');
    if (kpiRow) {
      var kpis = Array.isArray(data.kpis) ? data.kpis : [];
      var kHtml = '';
      for (var k = 0; k < kpis.length; k++) {
        var item = kpis[k];
        var kVal = (typeof item.value === 'number') ? item.value.toLocaleString() : esc(item.value != null ? item.value : '-');
        kHtml += '<div class="col-6 col-xl-3"><div class="rh-kpi-card">';
        kHtml += '<div class="rh-kpi-label">' + esc(item.label || '') + '</div>';
        kHtml += '<div class="rh-kpi-val">' + kVal + '</div>';
        if (item.sub) kHtml += '<div class="rh-kpi-sub">' + esc(item.sub) + '</div>';
        kHtml += '</div></div>';
      }
      kpiRow.innerHTML = kHtml;
    }

    // 2. Columns
    var cols = Array.isArray(data.columns) ? data.columns : [];
    var headEl = document.getElementById('rhHead');
    if (headEl) {
      var hHtml = '';
      for (var c = 0; c < cols.length; c++) {
        var arrow = (rhState.sortCol === c) ? (rhState.sortDir === 'asc' ? ' \u25B2' : ' \u25BC') : '';
        hHtml += '<th class="rh-th-sort" onclick="window.rhSort(' + c + ')">' + esc(cols[c]) + arrow + '</th>';
      }
      headEl.innerHTML = hHtml;
    }

    // 3. Rows
    var visible = rhGetVisibleRows();
    var bodyEl = document.getElementById('rhBody');
    if (bodyEl) {
      if (visible.length === 0) {
        bodyEl.innerHTML = '<tr><td colspan="' + Math.max(1, cols.length) + '" class="text-center text-muted py-4">No records match the selected filters for this period.</td></tr>';
      } else {
        var bHtml = '';
        for (var r = 0; r < visible.length; r++) {
          var row = visible[r];
          bHtml += '<tr>';
          for (var c2 = 0; c2 < cols.length; c2++) {
            var val = c2 < row.length ? row[c2] : '';
            var colName = (cols[c2] || '').toLowerCase();
            if (typeof val === 'number') {
              var isCurrency = colName.indexOf('cost') !== -1 || colName.indexOf('lkr') !== -1 || colName.indexOf('saved (lkr)') !== -1 || colName.indexOf('%') !== -1;
              var disp = isCurrency ? val.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : val.toLocaleString();
              bHtml += '<td class="text-end font-monospace">' + esc(disp) + '</td>';
            } else {
              var strVal = val != null ? String(val) : '';
              var isStatusCol = colName.indexOf('status') !== -1 || colName.indexOf('compliance') !== -1 || colName.indexOf('forecast') !== -1 || colName.indexOf('severity') !== -1 || colName.indexOf('level') !== -1;
              var badge = isStatusCol ? rhFormatStatusBadge(strVal) : null;
              if (badge) {
                bHtml += '<td>' + badge + '</td>';
              } else {
                var monoClass = (colName.indexOf('timestamp') !== -1 || colName.indexOf('date') !== -1 || colName.indexOf('month') !== -1 || colName.indexOf('hour') !== -1 || colName.indexOf('ip') !== -1) ? ' class="font-monospace"' : '';
                bHtml += '<td' + monoClass + '>' + esc(strVal) + '</td>';
              }
            }
          }
          bHtml += '</tr>';
        }
        bodyEl.innerHTML = bHtml;
      }
    }

    // 4. Footer
    var footEl = document.getElementById('rhFoot');
    if (footEl) {
      var footer = Array.isArray(data.footer) ? data.footer : [];
      if (footer.length === 0) {
        footEl.innerHTML = '';
      } else {
        var fHtml = '';
        for (var f = 0; f < cols.length; f++) {
          var fv = f < footer.length ? footer[f] : '';
          var fCol = (cols[f] || '').toLowerCase();
          if (typeof fv === 'number') {
            var fCurr = fCol.indexOf('cost') !== -1 || fCol.indexOf('lkr') !== -1 || fCol.indexOf('saved (lkr)') !== -1 || fCol.indexOf('%') !== -1;
            var fDisp = fCurr ? fv.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : fv.toLocaleString();
            fHtml += '<td class="text-end font-monospace">' + esc(fDisp) + '</td>';
          } else {
            fHtml += '<td>' + esc(fv != null ? fv : '') + '</td>';
          }
        }
        footEl.innerHTML = fHtml;
      }
    }
  };

  function rhCsvCell(val) {
    var s = val != null ? String(val) : '';
    return '"' + s.replace(/"/g, '""') + '"';
  }

  var _nexPrintLogoDataUri = '';
  (function preloadExcelLogo() {
    try {
      fetch('/img/nexprint-logo.png')
        .then(function (r) { return r.ok ? r.blob() : null; })
        .then(function (blob) {
          if (!blob) return;
          var reader = new FileReader();
          reader.onloadend = function () { if (reader.result) _nexPrintLogoDataUri = String(reader.result); };
          reader.readAsDataURL(blob);
        })
        .catch(function () {});
    } catch (e) {}
  })();

  window.exportStyledExcel = function (opts) {
    opts = opts || {};
    var cols = Array.isArray(opts.columns) ? opts.columns : [];
    var rows = Array.isArray(opts.rows) ? opts.rows : [];
    var footer = Array.isArray(opts.footer) ? opts.footer : [];
    var kpis = Array.isArray(opts.kpis) ? opts.kpis : [];
    var outName = String(opts.filename || 'nexprint-report').replace(/\.(csv|xls|xlsx)$/i, '') + '.xlsx';

    fetch('/api/admin/reports/export-excel', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        filename: outName,
        title: opts.title || 'nexPrint Report',
        subtitle: opts.subtitle || ('Generated: ' + window.formatIsoDateTime(new Date()) + ' (ISO 8601) | nexPrint Enterprise Secure Print Management'),
        kpis: kpis,
        columns: cols,
        rows: rows,
        footer: footer
      })
    })
    .then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.blob();
    })
    .then(function (blob) {
      var link = document.createElement('a');
      var url = URL.createObjectURL(blob);
      link.href = url;
      link.download = outName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    })
    .catch(function (err) {
      if (typeof showToast === 'function') showToast('Excel export failed: ' + err.message, 'error');
    });
  };

  window.rhExportCsv = function () {
    var data = rhState.data;
    if (!data || !Array.isArray(data.columns)) {
      if (typeof showToast === 'function') showToast('Run a report before exporting.', 'error');
      return;
    }
    var visible = rhGetVisibleRows();
    var fileId = (data.code ? data.code + '-' : '') + (rhState.activeId || 'report') + '-' + rhToIsoDate(new Date()) + '.xls';
    window.exportStyledExcel({
      filename: fileId,
      title: (data.code ? data.code + ' — ' : '') + (data.title || 'Report'),
      subtitle: (data.description ? data.description + ' | ' : '') + 'Generated: ' + (data.generatedAt || window.formatIsoDateTime(new Date())),
      kpis: data.kpis,
      columns: data.columns,
      rows: visible,
      footer: data.footer
    });
  };

  window.rhPrint = function () {
    var data = rhState.data;
    if (!data) {
      window.print();
      return;
    }
    var w = window.open('', '_blank', 'width=1100,height=800');
    if (!w) {
      window.print();
      return;
    }
    var doc = w.document;
    doc.open();
    doc.write('<!DOCTYPE html><html><head><title></title></head><body></body></html>');
    doc.close();

    var titleText = (data.code ? data.code + ' - ' : '') + (data.title || 'Report');
    doc.title = titleText;

    var style = doc.createElement('style');
    style.textContent = 'body{font-family:Segoe UI,Arial,sans-serif;padding:24px;color:#0F172A;font-size:12px;}h1{font-size:18px;margin:0 0 4px;}p{color:#475569;margin:0 0 14px;}table{width:100%;border-collapse:collapse;margin-top:10px;}th,td{border:1px solid #CBD5E1;padding:6px 8px;text-align:left;}th{background:#F1F5F9;font-weight:700;}tfoot td{background:#F8FAFC;font-weight:700;}.num{text-align:right;font-family:Consolas,monospace;}';
    doc.head.appendChild(style);

    var h1 = doc.createElement('h1');
    h1.textContent = titleText;
    doc.body.appendChild(h1);

    var p = doc.createElement('p');
    var metaEl = document.getElementById('rhMeta');
    p.textContent = (metaEl ? metaEl.textContent : ('Generated: ' + (data.generatedAt || '')));
    doc.body.appendChild(p);

    var table = doc.createElement('table');
    var thead = doc.createElement('thead');
    var headTr = doc.createElement('tr');
    var cols = Array.isArray(data.columns) ? data.columns : [];
    for (var c = 0; c < cols.length; c++) {
      var th = doc.createElement('th');
      th.textContent = cols[c];
      headTr.appendChild(th);
    }
    thead.appendChild(headTr);
    table.appendChild(thead);

    var tbody = doc.createElement('tbody');
    var visible = rhGetVisibleRows();
    for (var r = 0; r < visible.length; r++) {
      var tr = doc.createElement('tr');
      for (var c2 = 0; c2 < cols.length; c2++) {
        var td = doc.createElement('td');
        var val = c2 < visible[r].length ? visible[r][c2] : '';
        if (typeof val === 'number') {
          td.className = 'num';
          td.textContent = val.toLocaleString();
        } else {
          td.textContent = val != null ? String(val) : '';
        }
        tr.appendChild(td);
      }
      tbody.appendChild(tr);
    }
    table.appendChild(tbody);

    if (Array.isArray(data.footer) && data.footer.length) {
      var tfoot = doc.createElement('tfoot');
      var fTr = doc.createElement('tr');
      for (var f = 0; f < cols.length; f++) {
        var fTd = doc.createElement('td');
        var fv = f < data.footer.length ? data.footer[f] : '';
        if (typeof fv === 'number') fTd.className = 'num';
        fTd.textContent = fv != null ? String(fv) : '';
        fTr.appendChild(fTd);
      }
      tfoot.appendChild(fTr);
      table.appendChild(tfoot);
    }

    doc.body.appendChild(table);
    w.focus();
    setTimeout(function () { w.print(); }, 150);
  };

  /* =========================================================================
     UNIFIED ADMIN TABLE SEARCH, CSV EXPORT & STICKY WRAPPER ENHANCER
     ========================================================================= */
  window.filterAdminTable = function (tbodyId, query) {
    var tbody = document.getElementById(tbodyId);
    if (!tbody) return;
    var q = (query || '').trim().toLowerCase();
    var rows = tbody.querySelectorAll('tr');
    for (var i = 0; i < rows.length; i++) {
      var tr = rows[i];
      if (!q) {
        tr.style.display = '';
        continue;
      }
      var txt = (tr.textContent || '').toLowerCase();
      tr.style.display = txt.indexOf(q) !== -1 ? '' : 'none';
    }
  };

  window.exportAdminTableCsv = function (triggerOrId, filename, customTitle) {
    var table = null;
    if (typeof triggerOrId === 'string') {
      var el = document.getElementById(triggerOrId);
      table = el ? (el.tagName === 'TABLE' ? el : el.closest('table')) : null;
    } else if (triggerOrId && triggerOrId.closest) {
      var page = triggerOrId.closest('.page-view');
      if (page) table = page.querySelector('table');
    }
    if (!table) {
      if (typeof showToast === 'function') showToast('No table data found to export.', 'error');
      return;
    }

    var cols = [];
    var headRows = table.querySelectorAll('thead tr');
    if (headRows.length > 0) {
      var ths = headRows[headRows.length - 1].querySelectorAll('th, td');
      for (var c = 0; c < ths.length; c++) {
        cols.push((ths[c].textContent || '').replace(/[\u25B2\u25BC]/g, '').replace(/\s+/g, ' ').trim());
      }
    }

    var rows = [];
    var bodyRows = table.querySelectorAll('tbody tr');
    for (var b = 0; b < bodyRows.length; b++) {
      var br = bodyRows[b];
      if (br.style.display === 'none') continue;
      var tds = br.querySelectorAll('td');
      if (tds.length === 1 && tds[0].colSpan > 1) continue;
      var bArr = [];
      for (var d = 0; d < tds.length; d++) {
        bArr.push((tds[d].textContent || '').replace(/\s+/g, ' ').trim());
      }
      if (bArr.length) rows.push(bArr);
    }

    if (rows.length === 0) {
      if (typeof showToast === 'function') showToast('No visible rows to export.', 'error');
      return;
    }

    var footer = [];
    var footTds = table.querySelectorAll('tfoot tr td, tfoot tr th');
    for (var f = 0; f < footTds.length; f++) {
      footer.push((footTds[f].textContent || '').replace(/\s+/g, ' ').trim());
    }

    window.exportStyledExcel({
      filename: filename || 'nexprint-export.xls',
      title: customTitle || ('nexPrint Export — ' + String(filename || 'Report').replace(/\.(csv|xls|xlsx)$/i, '')),
      columns: cols,
      rows: rows,
      footer: footer
    });
  };

  document.addEventListener('DOMContentLoaded', function () {
    var tables = document.querySelectorAll('.page-view table');
    for (var i = 0; i < tables.length; i++) {
      var t = tables[i];
      if (t.id === 'rhTable' || t.id === 'reportsMainTable') continue;
      if (!t.closest('.table-responsive') && !t.closest('.table-wrap')) {
        var wrap = document.createElement('div');
        wrap.className = 'table-responsive';
        t.parentNode.insertBefore(wrap, t);
        wrap.appendChild(t);
      }
      var thead = t.querySelector('thead');
      if (thead) {
        thead.style.position = 'sticky';
        thead.style.top = '0';
        thead.style.zIndex = '2';
      }
    }
    if (document.getElementById('rhReportSelect') || document.getElementById('rhNav')) {
      rhBuildNav();
    }

    // ISO 9241-171 / WCAG 2.1 AA: ensure table headers have scope="col"
    var ths = document.querySelectorAll('.page-view table thead th:not([scope])');
    for (var k = 0; k < ths.length; k++) {
      ths[k].setAttribute('scope', 'col');
    }

    // ISO 9241-171: live region for toast notifications
    var toastEl = document.getElementById('toastStrip') || document.querySelector('.toast-strip');
    if (toastEl && !toastEl.getAttribute('aria-live')) {
      toastEl.setAttribute('aria-live', 'polite');
      toastEl.setAttribute('role', 'status');
    }
  });

  // ISO 8601 DateTime & ISO 4217 Currency global formatters
  window.formatIsoDateTime = function (input) {
    if (!input) return '-';
    var d = (input instanceof Date) ? input : new Date(input);
    if (isNaN(d.getTime())) return String(input);
    var p = function (n) { return (n < 10 ? '0' : '') + n; };
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' +
           p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
  };

  window.formatLkr = function (amount) {
    var num = Number(amount);
    if (isNaN(num)) num = 0;
    return 'LKR ' + num.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  // ISO 9241-171: Esc key closes any visible modal-backdrop
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;
    var modals = document.querySelectorAll('.modal-backdrop');
    for (var i = modals.length - 1; i >= 0; i--) {
      if (modals[i].style.display && modals[i].style.display !== 'none') {
        modals[i].style.display = 'none';
        break;
      }
    }
  });

  window.openResetCountersModal = function () {
    var m = document.getElementById('modalResetCounters');
    if (m) m.style.display = 'flex';
  };

  window.rcScopeChanged = function () {
    var sc = document.getElementById('rcScope');
    var grp = document.getElementById('rcTargetGroup');
    if (sc && grp) grp.style.display = (sc.value === 'ALL') ? 'none' : '';
  };

  window.handleResetCounters = function () {
    var sc = (document.getElementById('rcScope') || {}).value || 'USER';
    var tgt = ((document.getElementById('rcTarget') || {}).value || '').trim();
    if (sc !== 'ALL' && !tgt) {
      if (typeof showToast === 'function') showToast('Please enter a target username, printer, or department.', 'error');
      return;
    }
    fetch('/api/admin/reports/reset-counters', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ scope: sc, target: tgt })
    })
    .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(function (res) {
      var m = document.getElementById('modalResetCounters');
      if (m) m.style.display = 'none';
      if (typeof showToast === 'function') showToast('Counters reset (' + res.scope + '): ' + res.usersReset + ' users, ' + res.jobsZeroed + ' jobs zeroed.', 'success');
      if (window.rhRun) window.rhRun();
    })
    .catch(function (err) {
      if (typeof showToast === 'function') showToast('Reset failed: ' + err.message, 'error');
    });
  };

  window.openAdvReportBuilder = function () {
    var m = document.getElementById('modalAdvReportBuilder');
    if (m) m.style.display = 'flex';
  };

  window.handleRunAdvReportBuilder = function () {
    var payload = {
      groupBy: (document.getElementById('arbGroupBy') || {}).value || 'USER_PRINTER',
      from: (document.getElementById('arbFrom') || {}).value || '',
      to: (document.getElementById('arbTo') || {}).value || '',
      department: ((document.getElementById('arbDept') || {}).value || '').trim(),
      printer: ((document.getElementById('arbPrinter') || {}).value || '').trim()
    };
    fetch('/api/admin/reports/builder', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(function (data) {
      var m = document.getElementById('modalAdvReportBuilder');
      if (m) m.style.display = 'none';
      rhState.data = data;
      var codeEl = document.getElementById('rhCodeBadge');
      var catEl = document.getElementById('rhCategoryBadge');
      var titleEl = document.getElementById('rhTitle');
      var descEl = document.getElementById('rhDesc');
      var emptyEl = document.getElementById('rhEmpty');
      var resEl = document.getElementById('rhResult');
      if (codeEl) codeEl.textContent = data.code || 'CUSTOM';
      if (catEl) catEl.textContent = data.category || 'Custom Report Builder';
      if (titleEl) titleEl.textContent = data.title || 'Custom Report';
      if (descEl) descEl.textContent = data.description || '';
      if (emptyEl) emptyEl.style.display = 'none';
      if (resEl) resEl.style.display = '';
      window.rhRender();
    })
    .catch(function (err) {
      if (typeof showToast === 'function') showToast('Report builder failed: ' + err.message, 'error');
    });
  };

  var sapState = { data: null, activeCols: [], sortCol: -1, sortDir: 'asc' };

  window.sapExecuteQuery = function () {
    var payload = {
      groupBy: (document.getElementById('sapGroupBy') || {}).value || 'USER_PRINTER',
      from: (document.getElementById('sapFrom') || {}).value || '',
      to: (document.getElementById('sapTo') || {}).value || '',
      department: ((document.getElementById('sapDept') || {}).value || '').trim(),
      printer: ((document.getElementById('sapPrinter') || {}).value || '').trim()
    };
    fetch('/api/admin/reports/builder', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(function (data) {
      sapState.data = data;
      sapState.sortCol = -1;
      sapState.sortDir = 'asc';
      var cols = Array.isArray(data.columns) ? data.columns : [];
      sapState.activeCols = cols.map(function (_, idx) { return idx; });
      window.sapRenderColChooser();
      window.sapRenderGrid();
    })
    .catch(function (err) {
      if (typeof showToast === 'function') showToast('SAP query failed: ' + err.message, 'error');
    });
  };

  window.sapRenderColChooser = function () {
    var box = document.getElementById('sapColChooser');
    if (!box || !sapState.data || !Array.isArray(sapState.data.columns)) return;
    var cols = sapState.data.columns;
    box.innerHTML = cols.map(function (c, idx) {
      var checked = sapState.activeCols.indexOf(idx) !== -1 ? ' checked' : '';
      return '<label class="d-inline-flex align-items-center gap-1 border px-2 py-1 bg-light" style="cursor:pointer;">' +
        '<input type="checkbox" value="' + idx + '"' + checked + ' onchange="window.sapToggleCol(' + idx + ', this.checked)"> ' +
        '<span>' + esc(c) + '</span>' +
        '<span style="color:#64748B; font-size:10px; margin-left:3px;" title="Move left" onclick="event.preventDefault(); window.sapMoveCol(' + idx + ', -1)">&#9664;</span>' +
        '<span style="color:#64748B; font-size:10px;" title="Move right" onclick="event.preventDefault(); window.sapMoveCol(' + idx + ', 1)">&#9654;</span>' +
        '</label>';
    }).join('');
  };

  window.sapToggleCol = function (idx, on) {
    if (on) {
      if (sapState.activeCols.indexOf(idx) === -1) sapState.activeCols.push(idx);
    } else {
      sapState.activeCols = sapState.activeCols.filter(function (i) { return i !== idx; });
    }
    window.sapRenderGrid();
  };

  window.sapMoveCol = function (idx, delta) {
    var pos = sapState.activeCols.indexOf(idx);
    if (pos === -1) return;
    var next = pos + delta;
    if (next < 0 || next >= sapState.activeCols.length) return;
    var tmp = sapState.activeCols[pos];
    sapState.activeCols[pos] = sapState.activeCols[next];
    sapState.activeCols[next] = tmp;
    window.sapRenderColChooser();
    window.sapRenderGrid();
  };

  window.sapSelectAllCols = function (all) {
    if (!sapState.data || !Array.isArray(sapState.data.columns)) return;
    sapState.activeCols = all ? sapState.data.columns.map(function (_, idx) { return idx; }) : [0, 1].filter(function (i) { return i < sapState.data.columns.length; });
    window.sapRenderColChooser();
    window.sapRenderGrid();
  };

  window.sapApplyPreset = function (preset) {
    if (!sapState.data || !Array.isArray(sapState.data.columns)) return;
    var cols = sapState.data.columns;
    var out = [];
    cols.forEach(function (c, idx) {
      var low = String(c).toLowerCase();
      if (preset === 'copy') {
        if (low.indexOf('user') !== -1 || low.indexOf('dept') !== -1 || low.indexOf('department') !== -1 || low.indexOf('printer') !== -1 || low.indexOf('copy') !== -1 || low.indexOf('ip') !== -1) out.push(idx);
      } else if (preset === 'cost') {
        if (low.indexOf('user') !== -1 || low.indexOf('dept') !== -1 || low.indexOf('department') !== -1 || low.indexOf('printer') !== -1 || low.indexOf('total') !== -1 || low.indexOf('cost') !== -1 || low.indexOf('lkr') !== -1) out.push(idx);
      }
    });
    sapState.activeCols = out.length ? out : cols.map(function (_, idx) { return idx; });
    window.sapRenderColChooser();
    window.sapRenderGrid();
  };

  window.sapSort = function (colIdx) {
    if (sapState.sortCol === colIdx) {
      sapState.sortDir = (sapState.sortDir === 'asc') ? 'desc' : 'asc';
    } else {
      sapState.sortCol = colIdx;
      sapState.sortDir = 'asc';
    }
    window.sapRenderGrid();
  };

  window.sapClearQuery = function () {
    ['sapFrom', 'sapTo', 'sapDept', 'sapPrinter', 'sapQuickFilter'].forEach(function (id) {
      var el = document.getElementById(id); if (el) el.value = '';
    });
    sapState.data = null;
    sapState.activeCols = [];
    var cc = document.getElementById('sapColChooser'); if (cc) cc.innerHTML = '';
    var b = document.getElementById('sapBody');
    if (b) b.innerHTML = '<tr><td class="text-center text-muted py-4">Cleared. Click <strong>Execute (F8)</strong> to run query.</td></tr>';
    var h = document.getElementById('sapHead'); if (h) h.innerHTML = '';
    var f = document.getElementById('sapFoot'); if (f) f.innerHTML = '';
    var k = document.getElementById('sapKpiRow'); if (k) k.innerHTML = '';
  };

  window.sapRenderGrid = function () {
    var data = sapState.data;
    if (!data) return;
    var kpiRow = document.getElementById('sapKpiRow');
    if (kpiRow && Array.isArray(data.kpis)) {
      kpiRow.innerHTML = data.kpis.map(function (item) {
        var v = (typeof item.value === 'number') ? item.value.toLocaleString() : esc(item.value != null ? item.value : '-');
        return '<div class="col-6 col-xl-3"><div class="rh-kpi-card"><div class="rh-kpi-label">' + esc(item.label || '') + '</div><div class="rh-kpi-val">' + v + '</div><div class="rh-kpi-sub">' + esc(item.sub || '') + '</div></div></div>';
      }).join('');
    }
    var cols = Array.isArray(data.columns) ? data.columns : [];
    var visCols = sapState.activeCols.length ? sapState.activeCols : cols.map(function (_, i) { return i; });
    var q = ((document.getElementById('sapQuickFilter') || {}).value || '').trim().toLowerCase();
    var headEl = document.getElementById('sapHead');
    if (headEl) {
      headEl.innerHTML = visCols.map(function (colIdx) {
        var arrow = (sapState.sortCol === colIdx) ? (sapState.sortDir === 'asc' ? ' \u25B2' : ' \u25BC') : '';
        return '<th class="rh-th-sort" onclick="window.sapSort(' + colIdx + ')">' + esc(cols[colIdx]) + arrow + '</th>';
      }).join('');
    }
    var rows = (Array.isArray(data.rows) ? data.rows.slice() : []).filter(function (row) {
      if (!q) return true;
      return visCols.some(function (colIdx) {
        var cell = row[colIdx];
        return cell != null && String(cell).toLowerCase().indexOf(q) !== -1;
      });
    });
    if (sapState.sortCol >= 0) {
      var sc = sapState.sortCol;
      var dir = sapState.sortDir === 'desc' ? -1 : 1;
      rows.sort(function (a, b) {
        var va = a[sc], vb = b[sc];
        if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir;
        return String(va != null ? va : '').localeCompare(String(vb != null ? vb : ''), undefined, { numeric: true, sensitivity: 'base' }) * dir;
      });
    }
    var bodyEl = document.getElementById('sapBody');
    if (bodyEl) {
      bodyEl.innerHTML = rows.length === 0
        ? '<tr><td colspan="' + Math.max(1, visCols.length) + '" class="text-center text-muted py-4">No matching records.</td></tr>'
        : rows.map(function (r) {
            return '<tr>' + visCols.map(function (idx) {
              var v = r[idx];
              return typeof v === 'number' ? '<td class="text-end font-monospace">' + esc(v.toLocaleString()) + '</td>' : '<td>' + esc(v != null ? v : '') + '</td>';
            }).join('') + '</tr>';
          }).join('');
    }
    var footEl = document.getElementById('sapFoot');
    if (footEl && Array.isArray(data.footer)) {
      footEl.innerHTML = visCols.map(function (idx) {
        var fv = data.footer[idx];
        return typeof fv === 'number' ? '<td class="text-end font-monospace">' + esc(fv.toLocaleString()) + '</td>' : '<td>' + esc(fv != null ? fv : '') + '</td>';
      }).join('');
    }
  };

  window.sapExportCsv = function () {
    window.exportAdminTableCsv('sapAlvTable', 'nexPrint-sap-report-builder.xls', 'SAP ALV Ad-Hoc Report');
  };

  /* =========================================================================
     PRINTER MAINTENANCE & CONSUMABLES LOGBOOK
     ========================================================================= */
  var DEFAULT_MAINT_TYPES = [
    'TONER_REPLACEMENT', 'REPAIR', 'DRUM_UNIT', 'FUSER_UNIT', 'PREVENTIVE_SERVICE', 'FIRMWARE_UPDATE', 'OTHER'
  ];
  var maintRecordsCache = [];
  var maintPrintersCache = [];

  window.getMaintServiceTypes = function () {
    try {
      var raw = localStorage.getItem('nexprint_maint_service_types');
      if (raw) {
        var parsed = JSON.parse(raw);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch (e) {}
    return DEFAULT_MAINT_TYPES.slice();
  };

  window.renderMaintServiceTypesSettings = function () {
    var box = document.getElementById('maintServiceTypesList');
    if (!box) return;
    var types = window.getMaintServiceTypes();
    box.innerHTML = types.map(function (t) {
      return '<span class="badge bg-light text-dark border d-inline-flex align-items-center gap-2 px-2 py-1">' +
        '<span>' + esc(t) + '</span>' +
        '<button type="button" class="btn btn-sm p-0 text-danger fw-bold" style="border:none;background:transparent;line-height:1;" onclick="window.removeMaintServiceType(\'' + esc(t) + '\')">&times;</button>' +
        '</span>';
    }).join('');
    window.populateMaintServiceTypeDropdowns();
  };

  window.addMaintServiceType = function () {
    var inp = document.getElementById('newMaintTypeInput');
    var val = inp ? inp.value.trim().toUpperCase().replace(/\s+/g, '_') : '';
    if (!val) return;
    var types = window.getMaintServiceTypes();
    if (types.indexOf(val) === -1) types.push(val);
    localStorage.setItem('nexprint_maint_service_types', JSON.stringify(types));
    if (inp) inp.value = '';
    window.renderMaintServiceTypesSettings();
    if (typeof showToast === 'function') showToast('Service type added: ' + val, 'success');
  };

  window.removeMaintServiceType = function (val) {
    var types = window.getMaintServiceTypes().filter(function (t) { return t !== val; });
    if (types.length === 0) types = DEFAULT_MAINT_TYPES.slice();
    localStorage.setItem('nexprint_maint_service_types', JSON.stringify(types));
    window.renderMaintServiceTypesSettings();
  };

  window.populateMaintServiceTypeDropdowns = function () {
    var types = window.getMaintServiceTypes();
    var fSel = document.getElementById('maintTypeFilter');
    if (fSel) {
      var curr = fSel.value || '';
      fSel.innerHTML = '<option value="">All Types</option>' + types.map(function (t) {
        return '<option value="' + esc(t) + '"' + (t === curr ? ' selected' : '') + '>' + esc(t.replace(/_/g, ' ')) + '</option>';
      }).join('');
    }
    var mSel = document.getElementById('maintFormType');
    if (mSel) {
      var mCurr = mSel.value || '';
      mSel.innerHTML = '<option value="">-- Select --</option>' + types.map(function (t) {
        return '<option value="' + esc(t) + '"' + (t === mCurr ? ' selected' : '') + '>' + esc(t.replace(/_/g, ' ')) + '</option>';
      }).join('');
    }
  };

  window.openMaintPrinterDropdown = function () {
    window.filterMaintPrinterDropdown((document.getElementById('maintFormPrinterSearch') || {}).value || '');
    var dd = document.getElementById('maintPrinterDropdown');
    if (dd) dd.style.display = 'block';
  };

  window.filterMaintPrinterDropdown = function (query) {
    var dd = document.getElementById('maintPrinterDropdown');
    if (!dd) return;
    var q = (query || '').trim().toLowerCase();
    var matches = maintPrintersCache.filter(function (p) {
      if (!q) return true;
      var hay = ((p.printerCode || '') + ' ' + (p.name || '') + ' ' + (p.ipAddress || '') + ' ' + (p.location || '')).toLowerCase();
      return hay.indexOf(q) !== -1;
    });
    if (matches.length === 0) {
      dd.innerHTML = '<div class="p-2 small text-muted">No matching printers.</div>';
      dd.style.display = 'block';
      return;
    }
    dd.innerHTML = matches.map(function (p) {
      var code = p.printerCode || '';
      return '<div class="px-2 py-2 border-bottom" style="cursor:pointer; font-size:12px;" onmouseover="this.style.background=\'#EFF6FF\'" onmouseout="this.style.background=\'#FFFFFF\'" onclick="window.selectMaintPrinter(\'' + esc(code) + '\')">' +
        '<div><strong>' + esc(code) + '</strong> &bull; ' + esc(p.name || '') + '</div>' +
        '<div class="small text-muted font-monospace">' + esc(p.ipAddress || '') + ' &bull; ' + esc(p.location || 'Office') + '</div>' +
        '</div>';
    }).join('');
    dd.style.display = 'block';
  };

  window.selectMaintPrinter = function (code) {
    var p = maintPrintersCache.filter(function (x) { return (x.printerCode || '') === code; })[0];
    var hid = document.getElementById('maintFormPrinter');
    var srch = document.getElementById('maintFormPrinterSearch');
    var dd = document.getElementById('maintPrinterDropdown');
    if (hid) hid.value = code;
    if (srch) srch.value = p ? (p.printerCode + ' - ' + p.name + ' (' + p.ipAddress + ')') : code;
    if (dd) dd.style.display = 'none';
    window.onMaintPrinterInput(code);
  };

  window.onMaintPrinterInput = function (explicitCode) {
    var hid = document.getElementById('maintFormPrinter');
    var box = document.getElementById('maintPrevSnapshot');
    var cntBadge = document.getElementById('maintPrevCountBadge');
    if (!box) return;
    var code = (explicitCode || (hid ? hid.value : '') || '').trim().toUpperCase();
    if (!code) {
      if (cntBadge) cntBadge.textContent = '0 records';
      box.innerHTML = 'Select a printer on the left to inspect its previous repairs, toner replacements, and total maintenance spend.';
      return;
    }

    var prev = maintRecordsCache.filter(function (m) {
      return (m.printerCode || '').toUpperCase() === code;
    });
    if (cntBadge) cntBadge.textContent = prev.length + (prev.length === 1 ? ' record' : ' records');
    if (prev.length === 0) {
      box.innerHTML = '<div class="text-muted py-2">No previous service records found for <strong>' + esc(code) + '</strong>.</div>';
      return;
    }
    var totalSpend = prev.reduce(function (acc, m) { return acc + (Number(m.costLkr) || 0); }, 0);
    var itemsHtml = prev.slice(0, 6).map(function (m) {
      return '<div class="bg-white border p-2 mb-2">' +
        '<div class="d-flex justify-content-between"><strong class="text-dark">' + esc(m.maintenanceType || '') + '</strong><span class="font-monospace text-muted">' + esc(m.serviceDate || '') + '</span></div>' +
        '<div class="text-dark mt-1">' + esc(m.partReplaced || m.notes || '-') + '</div>' +
        '<div class="d-flex justify-content-between small text-muted mt-1"><span>' + esc(m.technician || '-') + '</span><strong class="font-monospace text-primary">LKR ' + Number(m.costLkr || 0).toLocaleString(undefined, { minimumFractionDigits: 2 }) + '</strong></div>' +
        '</div>';
    }).join('');
    box.innerHTML = '<div class="bg-white border p-2 mb-2 d-flex justify-content-between"><span>Total Spend (' + esc(code) + ')</span><strong class="text-primary font-monospace">LKR ' + totalSpend.toLocaleString(undefined, { minimumFractionDigits: 2 }) + '</strong></div>' + itemsHtml;
  };

  window.loadMaintenance = function () {
    window.populateMaintServiceTypeDropdowns();
    var pFilter = document.getElementById('maintPrinterFilter');
    var tFilter = document.getElementById('maintTypeFilter');
    var sFilter = document.getElementById('maintStatusFilter');

    if (pFilter && pFilter.options.length <= 1) {
      fetch('/api/admin/printers')
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(function (printers) {
          if (!Array.isArray(printers)) return;
          maintPrintersCache = printers;
          var opts = '<option value="">All Printers</option>';
          printers.forEach(function (p) {
            var lbl = (p.printerCode || '') + ' - ' + (p.name || '') + (p.ipAddress ? ' (' + p.ipAddress + ')' : '');
            opts += '<option value="' + esc(p.printerCode || '') + '">' + esc(lbl) + '</option>';
          });
          pFilter.innerHTML = opts;
        })
        .catch(function () {});
    }

    var params = [];
    if (pFilter && pFilter.value) params.push('printerCode=' + encodeURIComponent(pFilter.value));
    if (tFilter && tFilter.value) params.push('type=' + encodeURIComponent(tFilter.value));
    if (sFilter && sFilter.value) params.push('status=' + encodeURIComponent(sFilter.value));

    var url = '/api/admin/maintenance' + (params.length ? '?' + params.join('&') : '');
    fetch(url)
      .then(function (r) { return r.json(); })
      .then(function (data) {
        var el;
        el = document.getElementById('maintStatTotal'); if (el) el.textContent = (data.totalRecords || 0).toLocaleString();
        el = document.getElementById('maintStatToner'); if (el) el.textContent = (data.tonerReplacements || 0).toLocaleString();
        el = document.getElementById('maintStatRepairs'); if (el) el.textContent = (data.repairsCount || 0).toLocaleString();
        el = document.getElementById('maintStatCost'); if (el) el.textContent = window.formatLkr(data.totalCostLkr || 0);
        el = document.getElementById('maintTopRepaired'); if (el) el.textContent = data.topRepairedPrinter || 'None';
        el = document.getElementById('maintTopToner'); if (el) el.textContent = data.topTonerPrinter || 'None';
        el = document.getElementById('maintTopSpend'); if (el) el.textContent = data.topSpendPrinter || 'None';

        var list = Array.isArray(data.records) ? data.records : [];
        maintRecordsCache = list;
        var tbody = document.getElementById('maintTableBody');
        if (!tbody) return;
        if (list.length === 0) {
          tbody.innerHTML = '<tr><td colspan="10" class="text-center py-4 text-muted">No maintenance or toner replacement records found. Click <strong>+ Log Maintenance / Toner</strong> to add one.</td></tr>';
          return;
        }
        tbody.innerHTML = list.map(function (m) {
          var stClass = m.status === 'COMPLETED' ? 'badge-completed' : (m.status === 'IN_PROGRESS' ? 'badge-held' : 'badge-expired');
          var partMeta = [m.partNumber ? 'SKU: ' + m.partNumber : '', m.partSerial ? 'SN: ' + m.partSerial : ''].filter(Boolean).join(' | ');
          var partInfo = (m.tonerColor && m.tonerColor !== 'N/A' ? '[' + esc(m.tonerColor) + '] ' : '') + esc(m.partReplaced || m.notes || '-') +
            (partMeta ? '<div class="small text-muted font-monospace">' + esc(partMeta) + '</div>' : '');
          var techVendor = [m.technician, m.vendor].filter(Boolean).map(esc).join(' / ') || '-';
          var completeBtn = m.status !== 'COMPLETED'
            ? '<button type="button" class="btn btn-outline-primary btn-sm py-0 px-2 me-1" onclick="window.completeMaintenance(' + m.id + ')">Done</button>'
            : '';
          return '<tr>' +
            '<td class="font-monospace">' + esc(m.serviceDate || '') + '</td>' +
            '<td><strong>' + esc(m.printerCode || '') + '</strong><div class="small text-muted">' + esc(m.printerName || '') + '</div></td>' +
            '<td><span class="badge bg-light text-dark border">' + esc(m.maintenanceType || '') + '</span></td>' +
            '<td>' + partInfo + '</td>' +
            '<td class="text-end font-monospace">' + Number(m.odometerPages || 0).toLocaleString() + '</td>' +
            '<td class="text-end font-monospace">' + Number(m.costLkr || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + '</td>' +
            '<td>' + techVendor + '</td>' +
            '<td><span class="badge ' + stClass + '">' + esc(m.status || '') + '</span>' + (m.durationMinutes ? '<div class="small text-muted font-monospace">' + m.durationMinutes + ' min</div>' : '') + '</td>' +
            '<td class="font-monospace">' + esc(m.nextServiceDate || '-') + '</td>' +
            '<td>' + completeBtn + '<button type="button" class="btn btn-secondary btn-sm py-0 px-2" onclick="window.deleteMaintenance(' + m.id + ')">Del</button></td>' +
            '</tr>';
        }).join('');
      })
      .catch(function (err) {
        var tbody = document.getElementById('maintTableBody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="10" class="text-center py-4 text-danger">Failed to load maintenance logs: ' + esc(err.message) + '</td></tr>';
      });
  };

  window.maintTypeChanged = function () {
    var t = (document.getElementById('maintFormType') || {}).value || '';
    var grp = document.getElementById('maintTonerColorGroup');
    if (grp) grp.style.display = (t === 'TONER_REPLACEMENT') ? '' : 'none';
  };

  window.openMaintenanceModal = function () {
    window.populateMaintServiceTypeDropdowns();
    var dEl = document.getElementById('maintFormDate');
    if (dEl && !dEl.value) {
      var now = new Date();
      var mm = String(now.getMonth() + 1).padStart(2, '0');
      var dd = String(now.getDate()).padStart(2, '0');
      dEl.value = now.getFullYear() + '-' + mm + '-' + dd;
    }
    window.maintTypeChanged();
    window.onMaintPrinterInput();
    var m = document.getElementById('modalAddMaintenance');
    if (m) m.style.display = 'flex';
  };

  window.handleSaveMaintenance = function (event) {
    if (event) event.preventDefault();
    var rawPrinter = ((document.getElementById('maintFormPrinter') || {}).value || '').trim();
    var pCode = rawPrinter.split(' - ')[0].trim();
    var payload = {
      printerCode: pCode,
      maintenanceType: (document.getElementById('maintFormType') || {}).value || 'REPAIR',
      tonerColor: (document.getElementById('maintFormTonerColor') || {}).value || 'N/A',
      partReplaced: ((document.getElementById('maintFormPart') || {}).value || '').trim(),
      partNumber: ((document.getElementById('maintFormPartNum') || {}).value || '').trim(),
      partSerial: ((document.getElementById('maintFormPartSerial') || {}).value || '').trim(),
      odometerPages: parseInt((document.getElementById('maintFormOdometer') || {}).value, 10) || 0,
      costLkr: parseFloat((document.getElementById('maintFormCost') || {}).value) || 0,
      status: (document.getElementById('maintFormStatus') || {}).value || 'COMPLETED',
      technician: ((document.getElementById('maintFormTech') || {}).value || '').trim(),
      vendor: ((document.getElementById('maintFormVendor') || {}).value || '').trim(),
      serviceDate: (document.getElementById('maintFormDate') || {}).value || '',
      nextServiceDate: (document.getElementById('maintFormNextDate') || {}).value || '',
      notes: ((document.getElementById('maintFormNotes') || {}).value || '').trim()
    };
    fetch('/api/admin/maintenance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(function () {
      var m = document.getElementById('modalAddMaintenance');
      if (m) m.style.display = 'none';
      ['maintFormPrinter', 'maintFormPrinterSearch', 'maintFormType', 'maintFormTonerColor', 'maintFormStatus', 'maintFormPart', 'maintFormPartNum', 'maintFormPartSerial', 'maintFormTech', 'maintFormVendor', 'maintFormNextDate', 'maintFormNotes'].forEach(function (id) {
        var el = document.getElementById(id); if (el) el.value = '';
      });
      var odo = document.getElementById('maintFormOdometer'); if (odo) odo.value = '0';
      var cst = document.getElementById('maintFormCost'); if (cst) cst.value = '0';
      window.onMaintPrinterInput('');
      if (typeof showToast === 'function') showToast('Maintenance record saved!', 'success');
      window.loadMaintenance();
    })
    .catch(function (err) {
      if (typeof showToast === 'function') showToast('Save failed: ' + err.message, 'error');
    });
  };

  window.completeMaintenance = function (id) {
    fetch('/api/admin/maintenance/' + id + '/complete', { method: 'PUT' })
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
      .then(function () {
        if (typeof showToast === 'function') showToast('Marked as COMPLETED.', 'success');
        window.loadMaintenance();
      });
  };

  window.deleteMaintenance = function (id) {
    fetch('/api/admin/maintenance/' + id, { method: 'DELETE' })
      .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); })
      .then(function () {
        if (typeof showToast === 'function') showToast('Record deleted.', 'success');
        window.loadMaintenance();
      });
  };

  window.loadTechniciansSettings = function () {
    var box = document.getElementById('techListContainer');
    if (!box) return;
    fetch('/api/admin/technicians')
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (list) {
        if (!Array.isArray(list) || list.length === 0) {
          box.innerHTML = '<div class="small text-muted">No technicians registered yet.</div>';
          return;
        }
        box.innerHTML = list.map(function (t) {
          return '<span class="badge bg-light text-dark border d-inline-flex align-items-center gap-2 px-2 py-1">' +
            '<strong>' + esc(t.name) + '</strong> <span class="font-monospace text-primary">PIN:' + esc(t.pin) + '</span>' +
            (t.vendor ? ' <span class="text-muted">(' + esc(t.vendor) + ')</span>' : '') +
            '<button type="button" class="btn btn-sm p-0 text-danger fw-bold" style="border:none;background:transparent;line-height:1;" onclick="window.deleteTechnician(' + t.id + ')">&times;</button>' +
            '</span>';
        }).join('');
      });
  };

  window.handleAddTechnician = function () {
    var name = ((document.getElementById('techNameInput') || {}).value || '').trim();
    var pin = ((document.getElementById('techPinInput') || {}).value || '').trim();
    var vendor = ((document.getElementById('techVendorInput') || {}).value || '').trim();
    if (!name || !pin) {
      if (typeof showToast === 'function') showToast('Technician name and PIN are required.', 'error');
      return;
    }
    fetch('/api/admin/technicians', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name, pin: pin, vendor: vendor })
    })
    .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(function () {
      ['techNameInput', 'techPinInput', 'techVendorInput'].forEach(function (id) {
        var el = document.getElementById(id); if (el) el.value = '';
      });
      if (typeof showToast === 'function') showToast('Technician saved!', 'success');
      window.loadTechniciansSettings();
    });
  };

  window.deleteTechnician = function (id) {
    fetch('/api/admin/technicians/' + id, { method: 'DELETE' })
      .then(function () { window.loadTechniciansSettings(); });
  };

})();


// =========================================================================
// PRINTER PROFILE & MAINTENANCE REPORTS (REPAIR FREQUENCY & TONER REPORTS)
// =========================================================================
window.switchMaintSubTab = function(tab) {
  var logSec = document.getElementById('maintLogSection');
  var repSec = document.getElementById('maintReportsSection');
  var btnLog = document.getElementById('btnMaintTabLog');
  var btnRep = document.getElementById('btnMaintTabReports');
  if (tab === 'reports') {
    if (logSec) logSec.style.display = 'none';
    if (repSec) repSec.style.display = '';
    if (btnLog) { btnLog.classList.remove('btn-primary'); btnLog.classList.add('btn-outline-primary'); }
    if (btnRep) { btnRep.classList.remove('btn-outline-primary'); btnRep.classList.add('btn-primary'); }
    window.loadMaintenanceReports();
  } else {
    if (logSec) logSec.style.display = '';
    if (repSec) repSec.style.display = 'none';
    if (btnLog) { btnLog.classList.remove('btn-outline-primary'); btnLog.classList.add('btn-primary'); }
    if (btnRep) { btnRep.classList.remove('btn-primary'); btnRep.classList.add('btn-outline-primary'); }
  }
};

window.loadMaintenanceReports = function() {
  var from = (document.getElementById('repMaintFrom') || {}).value || '';
  var to = (document.getElementById('repMaintTo') || {}).value || '';
  var dept = (document.getElementById('repMaintDept') || {}).value || '';
  var printer = (document.getElementById('repMaintPrinter') || {}).value || '';

  var qs = [];
  if (from) qs.push('from=' + encodeURIComponent(from));
  if (to) qs.push('to=' + encodeURIComponent(to));
  if (dept) qs.push('department=' + encodeURIComponent(dept));
  if (printer) qs.push('printerCode=' + encodeURIComponent(printer));

  var url = '/api/admin/maintenance/reports' + (qs.length ? '?' + qs.join('&') : '');
  fetch(url, { headers: window.getAuthHeaders ? window.getAuthHeaders() : {} })
    .then(function(r) { return r.json(); })
    .then(function(data) {
      var s = data.summary || {};
      var setTxt = function(id, v) { var el = document.getElementById(id); if (el) el.textContent = v; };
      setTxt('repMaintTotalRepairs', s.totalRepairs || 0);
      setTxt('repMaintTotalToners', s.totalToners || 0);
      setTxt('repMaintTotalCost', 'LKR ' + Number(s.totalCostLkr || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
      setTxt('repMaintAvgTime', (s.avgRepairDurationMinutes || 0) + ' min');

      // Populate department & printer dropdowns if empty
      var deptSel = document.getElementById('repMaintDept');
      if (deptSel && deptSel.options.length <= 1 && Array.isArray(data.departmentWise)) {
        data.departmentWise.forEach(function(d) {
          var opt = document.createElement('option');
          opt.value = d.department;
          opt.textContent = d.department;
          deptSel.appendChild(opt);
        });
      }
      var prnSel = document.getElementById('repMaintPrinter');
      if (prnSel && prnSel.options.length <= 1 && Array.isArray(data.printerWise)) {
        data.printerWise.forEach(function(p) {
          var opt = document.createElement('option');
          opt.value = p.printerCode;
          opt.textContent = p.printerCode + ' (' + (p.printerName || p.printerCode) + ')';
          prnSel.appendChild(opt);
        });
      }

      var esc = window.escapeHtml || function(x) { return String(x == null ? '' : x); };

      // Printer-wise table
      var pBody = document.getElementById('repMaintPrinterBody');
      if (pBody) {
        var pList = data.printerWise || [];
        if (!pList.length) {
          pBody.innerHTML = '<tr><td colspan="10" class="text-center py-3 text-muted">No maintenance records match these filters.</td></tr>';
        } else {
          pBody.innerHTML = pList.map(function(p) {
            return '<tr>' +
              '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(p.printerCode) + '\')">' + esc(p.printerCode) + '</a><div class="small text-muted">' + esc(p.printerName) + '</div></td>' +
              '<td>' + esc(p.department) + '</td>' +
              '<td class="small">' + esc(p.model) + '</td>' +
              '<td><span class="badge bg-danger-subtle text-danger fw-bold">' + (p.repairCount || 0) + '</span></td>' +
              '<td><span class="badge bg-primary-subtle text-primary fw-bold">' + (p.tonerCount || 0) + '</span></td>' +
              '<td>' + (p.otherServiceCount || 0) + '</td>' +
              '<td><span class="badge bg-light text-dark border">' + esc(p.repairFrequency || '-') + '</span></td>' +
              '<td>' + (p.totalDurationMinutes || 0) + ' min</td>' +
              '<td class="fw-semibold">' + Number(p.totalCostLkr || 0).toLocaleString('en-US', { minimumFractionDigits: 2 }) + '</td>' +
              '<td class="small">' + esc(p.lastServiceDate || '-') + '</td>' +
              '</tr>';
          }).join('');
        }
      }

      // Department-wise table
      var dBody = document.getElementById('repMaintDeptBody');
      if (dBody) {
        var dList = data.departmentWise || [];
        if (!dList.length) {
          dBody.innerHTML = '<tr><td colspan="6" class="text-center py-3 text-muted">No department data.</td></tr>';
        } else {
          dBody.innerHTML = dList.map(function(d) {
            return '<tr>' +
              '<td class="fw-bold">' + esc(d.department) + '</td>' +
              '<td>' + (d.printerCount || 0) + '</td>' +
              '<td><span class="badge bg-danger-subtle text-danger fw-bold">' + (d.repairCount || 0) + '</span></td>' +
              '<td><span class="badge bg-primary-subtle text-primary fw-bold">' + (d.tonerCount || 0) + '</span></td>' +
              '<td>' + (d.totalDurationMinutes || 0) + ' min</td>' +
              '<td class="fw-semibold">' + Number(d.totalCostLkr || 0).toLocaleString('en-US', { minimumFractionDigits: 2 }) + '</td>' +
              '</tr>';
          }).join('');
        }
      }

      // Toner breakdown table
      var tBody = document.getElementById('repMaintTonerBody');
      if (tBody) {
        var tList = data.tonerBreakdown || [];
        if (!tList.length) {
          tBody.innerHTML = '<tr><td colspan="8" class="text-center py-3 text-muted">No toner replacements in this period.</td></tr>';
        } else {
          tBody.innerHTML = tList.map(function(t) {
            return '<tr>' +
              '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(t.printerCode) + '\')">' + esc(t.printerCode) + '</a></td>' +
              '<td class="small">' + esc(t.department) + '</td>' +
              '<td>' + (t.blackCount || 0) + '</td>' +
              '<td>' + (t.cyanCount || 0) + '</td>' +
              '<td>' + (t.magentaCount || 0) + '</td>' +
              '<td>' + (t.yellowCount || 0) + '</td>' +
              '<td class="fw-bold">' + (t.totalToners || 0) + '</td>' +
              '<td class="fw-semibold">' + Number(t.totalTonerCostLkr || 0).toLocaleString('en-US', { minimumFractionDigits: 2 }) + '</td>' +
              '</tr>';
          }).join('');
        }
      }
    })
    .catch(function(err) {
      console.error('loadMaintenanceReports error:', err);
    });
};

window.openPrinterProfile = function(printerCode) {
  if (!printerCode) return;
  window._currentProfilePrinterCode = printerCode;
  var modalEl = document.getElementById('printerProfileModal');
  if (!modalEl) return;
  var modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  document.getElementById('ppTitle').textContent = 'Printer Profile: ' + printerCode;
  document.getElementById('ppSubtitle').textContent = 'Loading counters, repairs & history...';
  document.getElementById('ppMetaCards').innerHTML = '';
  document.getElementById('ppCounterCards').innerHTML = '';
  document.getElementById('ppMaintCards').innerHTML = '';
  document.getElementById('ppHistoryBody').innerHTML = '<tr><td colspan="9" class="text-center py-3 text-muted">Loading...</td></tr>';
  modal.show();

  fetch('/api/admin/maintenance/printer-profile/' + encodeURIComponent(printerCode), {
    headers: window.getAuthHeaders ? window.getAuthHeaders() : {}
  })
    .then(function(r) { return r.json(); })
    .then(function(data) {
      var esc = window.escapeHtml || function(x) { return String(x == null ? '' : x); };
      var p = data.printer || {};
      var c = data.counters || {};
      var m = data.maintenanceSummary || {};

      document.getElementById('ppTitle').textContent = (p.printerCode || printerCode) + ' — ' + (p.name || '');
      document.getElementById('ppSubtitle').textContent = (p.model || '') + ' • ' + (p.department || 'Unassigned') + ' • IP: ' + (p.ipAddress || '-') + ' • Location: ' + (p.location || '-');

      var card = function(label, val, sub) {
        return '<div class="col-6 col-md-3"><div class="rh-kpi-card"><div class="rh-kpi-label">' + label + '</div><div class="rh-kpi-val" style="font-size:1.15rem;">' + val + '</div>' + (sub ? '<div class="rh-kpi-sub">' + sub + '</div>' : '') + '</div></div>';
      };

      document.getElementById('ppMetaCards').innerHTML =
        card('Status', esc(p.status || 'UNKNOWN'), p.colorSupported ? 'Color + B&W' : 'Monochrome') +
        card('Department', esc(p.department || 'Unassigned'), esc(p.location || '-')) +
        card('Duplex / EWB', p.duplexSupported ? 'Duplex Yes' : 'Simplex', p.ewbConfigured ? 'EWB Configured' : 'EWB Pending') +
        card('Latest Odometer', Number(c.latestOdometerPages || 0).toLocaleString() + ' pgs', 'From service logs');

      document.getElementById('ppCounterCards').innerHTML =
        card('Current Counter', Number(c.currentCounterPages || 0).toLocaleString() + ' pgs', 'Since reset (offset: ' + Number(c.counterOffsetPages || 0).toLocaleString() + ')') +
        card('Actual Lifetime', Number(c.totalVolumePages || 0).toLocaleString() + ' pgs', 'Print + Copy + Scan') +
        card('Print Pages', Number(c.printPages || 0).toLocaleString(), (c.printJobs || 0) + ' jobs (' + (c.colorPages || 0) + ' color / ' + (c.bwPages || 0) + ' B&W)') +
        card('Copy / Scan', Number(c.copyPages || 0).toLocaleString() + ' / ' + Number(c.scanPages || 0).toLocaleString(), (c.copyJobs || 0) + ' copy, ' + (c.scanJobs || 0) + ' scan');

      document.getElementById('ppMaintCards').innerHTML =
        card('Total Repairs', m.totalRepairs || 0, (m.openTickets || 0) + ' open / in-progress') +
        card('Toner Replacements', m.totalTonerReplacements || 0, 'All cartridges') +
        card('Total Service Time', (m.totalDurationMinutes || 0) + ' min', 'Last: ' + esc(m.lastServiceDate || 'Never')) +
        card('Total Maintenance Cost', 'LKR ' + Number(m.totalCostLkr || 0).toLocaleString('en-US', { minimumFractionDigits: 2 }), (m.totalRecords || 0) + ' total records');

      var hist = data.history || [];
      var hBody = document.getElementById('ppHistoryBody');
      if (!hist.length) {
        hBody.innerHTML = '<tr><td colspan="9" class="text-center py-3 text-muted">No maintenance or toner history for this printer yet.</td></tr>';
      } else {
        hBody.innerHTML = hist.map(function(r) {
          var partOrColor = r.maintenanceType === 'TONER_REPLACEMENT' ? ('Toner: ' + (r.tonerColor || 'BLACK')) : (r.partReplaced || '-');
          return '<tr>' +
            '<td class="small">' + esc(r.serviceDate || '-') + '</td>' +
            '<td><span class="badge bg-light text-dark border">' + esc(r.maintenanceType) + '</span></td>' +
            '<td class="small">' + esc(partOrColor) + '</td>' +
            '<td class="small">' + Number(r.odometerPages || 0).toLocaleString() + '</td>' +
            '<td class="small">' + esc(r.technician || '-') + '</td>' +
            '<td class="small">' + (r.durationMinutes || 0) + ' min</td>' +
            '<td class="small fw-semibold">' + Number(r.costLkr || 0).toLocaleString('en-US', { minimumFractionDigits: 2 }) + '</td>' +
            '<td><span class="badge ' + (r.status === 'COMPLETED' ? 'bg-success-subtle text-success' : 'bg-warning-subtle text-warning') + '">' + esc(r.status) + '</span></td>' +
            '<td class="small text-muted">' + esc(r.notes || '') + '</td>' +
            '</tr>';
        }).join('');
      }
    })
    .catch(function(err) {
      console.error('openPrinterProfile error:', err);
      document.getElementById('ppSubtitle').textContent = 'Failed to load printer profile.';
    });
};

window.resetPrinterCounter = function(code) {
  var pCode = code || window._currentProfilePrinterCode;
  if (!pCode) return;
  if (!confirm('Reset counter to 0 for printer ' + pCode + '? The current actual counter will be saved as the new baseline.')) return;
  fetch('/api/admin/maintenance/reset-counter/' + encodeURIComponent(pCode), {
    method: 'POST',
    headers: window.getAuthHeaders ? window.getAuthHeaders() : {}
  })
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (window.showToast) window.showToast('Counter reset to 0 for ' + pCode + ' (baseline: ' + (res.counterOffsetPages || 0) + ')', 'success');
      if (window._currentProfilePrinterCode === pCode) {
        window.openPrinterProfile(pCode);
      }
    })
    .catch(function(err) {
      console.error('resetPrinterCounter error:', err);
      if (window.showToast) window.showToast('Failed to reset counter', 'danger');
    });
};

window.resetAllPrinterCounters = function() {
  if (!confirm('Reset counters to 0 for ALL printers? Each printer\'s current actual counter will be saved as its new baseline.')) return;
  fetch('/api/admin/maintenance/reset-all-counters', {
    method: 'POST',
    headers: window.getAuthHeaders ? window.getAuthHeaders() : {}
  })
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (window.showToast) window.showToast('Reset counters to 0 for ' + (res.resetCount || 0) + ' printers.', 'success');
    })
    .catch(function(err) {
      console.error('resetAllPrinterCounters error:', err);
      if (window.showToast) window.showToast('Failed to reset all counters', 'danger');
    });
};

window.switchAdvMaintTab = function(tab) {
  var panels = {
    overview: 'repPanelOverview',
    counter: 'repPanelCounter',
    reliability: 'repPanelReliability',
    partlife: 'repPanelPartLife',
    techpm: 'repPanelTechPm',
    tcoexec: 'repPanelTcoExec',
    sapmaint: 'repPanelSapMaint'
  };
  var btns = {
    overview: 'btnAdvTabOverview',
    counter: 'btnAdvTabCounter',
    reliability: 'btnAdvTabReliability',
    partlife: 'btnAdvTabPartLife',
    techpm: 'btnAdvTabTechPm',
    tcoexec: 'btnAdvTabTcoExec',
    sapmaint: 'btnAdvTabSapMaint'
  };
  Object.keys(panels).forEach(function(k) {
    var p = document.getElementById(panels[k]);
    var b = document.getElementById(btns[k]);
    if (p) p.style.display = (k === tab) ? '' : 'none';
    if (b) {
      if (k === tab) b.classList.add('active');
      else b.classList.remove('active');
    }
  });
  if (tab === 'sapmaint' && typeof window.sapMaintOnSourceChange === 'function') {
    var cp = document.getElementById('sapMaintColPicker');
    if (cp && !cp.children.length) window.sapMaintOnSourceChange();
  }
};

window.loadAdvancedMaintenanceReports = function() {
  var from = (document.getElementById('repMaintFrom') || {}).value || '';
  var to = (document.getElementById('repMaintTo') || {}).value || '';
  var dept = (document.getElementById('repMaintDept') || {}).value || '';
  var printer = (document.getElementById('repMaintPrinter') || {}).value || '';

  var qs = [];
  if (from) qs.push('from=' + encodeURIComponent(from));
  if (to) qs.push('to=' + encodeURIComponent(to));
  if (dept) qs.push('department=' + encodeURIComponent(dept));
  if (printer) qs.push('printerCode=' + encodeURIComponent(printer));

  var url = '/api/admin/maintenance/advanced-reports' + (qs.length ? '?' + qs.join('&') : '');
  fetch(url, { headers: window.getAuthHeaders ? window.getAuthHeaders() : {} })
    .then(function(r) { return r.json(); })
    .then(function(data) {
      var esc = window.escapeHtml || function(x) { return String(x == null ? '' : x); };
      var fmt = function(n) { return Number(n || 0).toLocaleString(); };
      var fmtLkr = function(n) { return Number(n || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); };

      // 1. Counter Delta
      var cdBody = document.getElementById('repCounterDeltaBody');
      if (cdBody) {
        var cdList = data.counterDeltaReport || [];
        cdBody.innerHTML = !cdList.length
          ? '<tr><td colspan="10" class="text-center py-3 text-muted">No counter data.</td></tr>'
          : cdList.map(function(r) {
              return '<tr>' +
                '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(r.printerCode) + '\')">' + esc(r.printerCode) + '</a><div class="small text-muted">' + esc(r.printerName) + '</div></td>' +
                '<td class="small">' + esc(r.department) + '</td>' +
                '<td>' + fmt(r.printBwPages) + '</td>' +
                '<td>' + fmt(r.printColorPages) + '</td>' +
                '<td>' + fmt(r.copyPages) + '</td>' +
                '<td>' + fmt(r.scanPages) + '</td>' +
                '<td class="fw-bold">' + fmt(r.periodTotalPages) + '</td>' +
                '<td><span class="badge bg-primary-subtle text-primary fw-bold">' + fmt(r.currentCounterSinceReset) + '</span></td>' +
                '<td class="small text-muted">' + fmt(r.counterOffsetPages) + '</td>' +
                '<td class="small fw-semibold">' + fmt(r.lifetimeActualPages) + '</td>' +
                '</tr>';
            }).join('');
      }

      // 2. Counter Reset History
      var crBody = document.getElementById('repCounterResetBody');
      if (crBody) {
        var crList = data.counterResetHistory || [];
        crBody.innerHTML = !crList.length
          ? '<tr><td colspan="7" class="text-center py-3 text-muted">No counter resets recorded yet.</td></tr>'
          : crList.map(function(r) {
              var dt = r.resetAt ? String(r.resetAt).replace('T', ' ').substring(0, 19) : '-';
              return '<tr>' +
                '<td class="small">' + esc(dt) + '</td>' +
                '<td class="fw-bold">' + esc(r.printerCode) + '</td>' +
                '<td class="small">' + esc(r.department) + '</td>' +
                '<td>' + fmt(r.previousOffsetPages) + '</td>' +
                '<td>' + fmt(r.actualPagesAtReset) + '</td>' +
                '<td class="fw-bold text-success">+' + fmt(r.deltaSinceLastReset) + '</td>' +
                '<td class="small">' + esc(r.resetBy) + '</td>' +
                '</tr>';
            }).join('');
      }

      // 3. Reliability & TCO
      var relBody = document.getElementById('repReliabilityBody');
      if (relBody) {
        var relList = data.reliabilityScorecard || [];
        relBody.innerHTML = !relList.length
          ? '<tr><td colspan="11" class="text-center py-3 text-muted">No reliability data.</td></tr>'
          : relList.map(function(r) {
              var badge = r.isHighDrain
                ? '<span class="badge bg-danger">High Drain</span>'
                : '<span class="badge bg-success-subtle text-success">Healthy</span>';
              return '<tr>' +
                '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(r.printerCode) + '\')">' + esc(r.printerCode) + '</a></td>' +
                '<td class="small">' + esc(r.department) + '</td>' +
                '<td>' + (r.repairCount || 0) + '</td>' +
                '<td>' + (r.mtbfDays ? r.mtbfDays + ' d' : '-') + '</td>' +
                '<td>' + (r.mtbfPages ? fmt(r.mtbfPages) : '-') + '</td>' +
                '<td>' + (r.mttrMinutes || 0) + ' min</td>' +
                '<td>' + (r.slaBreaches > 0 ? '<span class="badge bg-warning text-dark">' + r.slaBreaches + '</span>' : '0') + '</td>' +
                '<td class="fw-semibold">' + (r.uptimePct || 100) + '%</td>' +
                '<td class="fw-semibold">' + fmtLkr(r.tcoLkr) + '</td>' +
                '<td>' + fmtLkr(r.costPerPageLkr) + '</td>' +
                '<td>' + badge + '</td>' +
                '</tr>';
            }).join('');
      }

      // 4. Part Life & Toner Yield
      var plBody = document.getElementById('repPartLifeBody');
      if (plBody) {
        var plList = data.partLifeReport || [];
        plBody.innerHTML = !plList.length
          ? '<tr><td colspan="7" class="text-center py-3 text-muted">No part or toner replacement history yet.</td></tr>'
          : plList.map(function(r) {
              return '<tr>' +
                '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(r.printerCode) + '\')">' + esc(r.printerCode) + '</a></td>' +
                '<td class="small">' + esc(r.department) + '</td>' +
                '<td class="fw-semibold">' + esc(r.itemType) + '</td>' +
                '<td>' + (r.replacements || 0) + '</td>' +
                '<td>' + (r.avgPagesPerReplacement ? fmt(r.avgPagesPerReplacement) + ' pgs' : '-') + '</td>' +
                '<td class="small">' + fmt(r.lastOdometerPages) + '</td>' +
                '<td class="fw-semibold">' + fmtLkr(r.totalCostLkr) + '</td>' +
                '</tr>';
            }).join('');
      }

      // 5. Technician Scorecard
      var tBody = document.getElementById('repTechScoreBody');
      if (tBody) {
        var tList = data.technicianScorecard || [];
        tBody.innerHTML = !tList.length
          ? '<tr><td colspan="6" class="text-center py-3 text-muted">No technician records.</td></tr>'
          : tList.map(function(r) {
              return '<tr>' +
                '<td class="fw-bold">' + esc(r.technician) + '</td>' +
                '<td>' + (r.totalTickets || 0) + '</td>' +
                '<td class="text-success fw-semibold">' + (r.completedTickets || 0) + '</td>' +
                '<td>' + (r.openTickets || 0) + '</td>' +
                '<td>' + (r.avgDurationMinutes || 0) + ' min</td>' +
                '<td class="fw-semibold">' + fmtLkr(r.totalCostLkr) + '</td>' +
                '</tr>';
            }).join('');
      }

      // 6. PM Schedule
      var pmBody = document.getElementById('repPmScheduleBody');
      if (pmBody) {
        var pmList = data.pmScheduleReport || [];
        pmBody.innerHTML = !pmList.length
          ? '<tr><td colspan="7" class="text-center py-3 text-muted">No upcoming or overdue PM schedules.</td></tr>'
          : pmList.map(function(r) {
              var stBadge = r.pmStatus === 'OVERDUE'
                ? '<span class="badge bg-danger">OVERDUE</span>'
                : (r.pmStatus === 'DUE_SOON' ? '<span class="badge bg-warning text-dark">DUE SOON</span>' : '<span class="badge bg-info-subtle text-info">SCHEDULED</span>');
              return '<tr>' +
                '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(r.printerCode) + '\')">' + esc(r.printerCode) + '</a></td>' +
                '<td class="small">' + esc(r.department) + '</td>' +
                '<td class="small">' + esc(r.maintenanceType) + '</td>' +
                '<td class="small">' + esc(r.lastServiceDate) + '</td>' +
                '<td class="fw-semibold">' + esc(r.nextServiceDate) + '</td>' +
                '<td>' + r.daysRemaining + ' d</td>' +
                '<td>' + stBadge + '</td>' +
                '</tr>';
            }).join('');
      }
      // 7. Phase 3: Toner Burn-Rate & Forecast
      var tbBody = document.getElementById('repTonerBurnBody');
      if (tbBody) {
        var tbList = data.tonerBurnRateForecast || [];
        tbBody.innerHTML = !tbList.length
          ? '<tr><td colspan="11" class="text-center py-3 text-muted">No toner replacement history yet.</td></tr>'
          : tbList.map(function(r) {
              var alBadge = r.yieldAlert === 'LOW_YIELD'
                ? '<span class="badge bg-danger">LOW YIELD</span>'
                : (r.yieldAlert === 'DUE_SOON' ? '<span class="badge bg-warning text-dark">DUE SOON</span>' : '<span class="badge bg-success-subtle text-success">NORMAL</span>');
              return '<tr>' +
                '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(r.printerCode) + '\')">' + esc(r.printerCode) + '</a></td>' +
                '<td class="small">' + esc(r.department) + '</td>' +
                '<td><span class="badge bg-light text-dark border">' + esc(r.tonerColor) + '</span></td>' +
                '<td>' + (r.replacementsCount || 0) + '</td>' +
                '<td>' + (r.avgDaysBetween ? r.avgDaysBetween + ' d' : '-') + '</td>' +
                '<td>' + (r.avgPagesPerCartridge ? fmt(r.avgPagesPerCartridge) : '-') + '</td>' +
                '<td class="small text-muted">' + fmt(r.ratedYieldPages) + '</td>' +
                '<td class="fw-semibold">' + (r.yieldEfficiencyPct ? r.yieldEfficiencyPct + '%' : '-') + '</td>' +
                '<td class="small">' + esc(r.lastReplacedDate) + '</td>' +
                '<td class="small fw-semibold">' + esc(r.predictedNextDate) + (r.daysUntilEmpty != null ? ' (' + r.daysUntilEmpty + 'd)' : '') + '</td>' +
                '<td>' + alBadge + '</td>' +
                '</tr>';
            }).join('');
      }

      // 8. Phase 4: Department TCO Rollup
      var dtBody = document.getElementById('repDeptTcoBody');
      if (dtBody) {
        var dtList = data.departmentTcoReport || [];
        dtBody.innerHTML = !dtList.length
          ? '<tr><td colspan="8" class="text-center py-3 text-muted">No department TCO data.</td></tr>'
          : dtList.map(function(r) {
              return '<tr>' +
                '<td class="fw-bold">' + esc(r.department) + '</td>' +
                '<td>' + (r.printerCount || 0) + '</td>' +
                '<td>' + fmt(r.lifetimePages) + '</td>' +
                '<td>' + fmtLkr(r.repairCostLkr) + '</td>' +
                '<td>' + fmtLkr(r.tonerCostLkr) + '</td>' +
                '<td class="fw-bold">' + fmtLkr(r.totalTcoLkr) + '</td>' +
                '<td>' + fmtLkr(r.costPerPageLkr) + '</td>' +
                '<td>' + (r.highDrainCount > 0 ? '<span class="badge bg-danger">' + r.highDrainCount + '</span>' : '0') + '</td>' +
                '</tr>';
            }).join('');
      }

      // 9. Phase 4: Executive Replace vs Repair Decision Matrix
      var rmBody = document.getElementById('repReplaceMatrixBody');
      if (rmBody) {
        var rmList = data.replaceVsRepairMatrix || [];
        rmBody.innerHTML = !rmList.length
          ? '<tr><td colspan="11" class="text-center py-3 text-muted">No printer TCO data.</td></tr>'
          : rmList.map(function(r) {
              var recBadge = r.recommendation === 'REPLACE_RECOMMENDED'
                ? '<span class="badge bg-danger">REPLACE RECOMMENDED</span>'
                : (r.recommendation === 'MONITOR_CLOSELY' ? '<span class="badge bg-warning text-dark">MONITOR CLOSELY</span>' : '<span class="badge bg-success-subtle text-success">KEEP HEALTHY</span>');
              var scoreColor = r.healthScore < 50 ? 'text-danger' : (r.healthScore < 75 ? 'text-warning' : 'text-success');
              return '<tr>' +
                '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(r.printerCode) + '\')">' + esc(r.printerCode) + '</a></td>' +
                '<td class="small">' + esc(r.department) + '</td>' +
                '<td class="small">' + esc(r.model) + '</td>' +
                '<td>' + fmt(r.lifetimePages) + '</td>' +
                '<td>' + (r.repairCount || 0) + '</td>' +
                '<td>' + fmtLkr(r.repairCostLkr) + '</td>' +
                '<td>' + fmtLkr(r.tonerCostLkr) + '</td>' +
                '<td class="fw-bold">' + fmtLkr(r.totalTcoLkr) + '</td>' +
                '<td>' + fmtLkr(r.costPerPageLkr) + '</td>' +
                '<td class="fw-bold ' + scoreColor + '">' + r.healthScore + '/100</td>' +
                '<td>' + recBadge + '</td>' +
                '</tr>';
            }).join('');
      }
      if (typeof window.renderMaintVisualCharts === 'function') window.renderMaintVisualCharts(data);
    })
    .catch(function(err) {
      console.error('loadAdvancedMaintenanceReports error:', err);
    });
};

(function() {
  var origLoadMaintRep = window.loadMaintenanceReports;
  window.loadMaintenanceReports = function() {
    if (typeof origLoadMaintRep === 'function') origLoadMaintRep();
    window.loadAdvancedMaintenanceReports();
  };
})();

window.exportExecutiveMaintExcel = function() {
  var from = (document.getElementById('repMaintFrom') || {}).value || '';
  var to = (document.getElementById('repMaintTo') || {}).value || '';
  var dept = (document.getElementById('repMaintDept') || {}).value || '';
  var printer = (document.getElementById('repMaintPrinter') || {}).value || '';
  var qs = [];
  if (from) qs.push('from=' + encodeURIComponent(from));
  if (to) qs.push('to=' + encodeURIComponent(to));
  if (dept) qs.push('department=' + encodeURIComponent(dept));
  if (printer) qs.push('printerCode=' + encodeURIComponent(printer));
  var url = '/api/admin/maintenance/export-executive-excel' + (qs.length ? '?' + qs.join('&') : '');
  fetch(url, { headers: window.getAuthHeaders ? window.getAuthHeaders() : {} })
    .then(function(r) { return r.blob(); })
    .then(function(blob) {
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'nexPrint_executive_maintenance_tco.xlsx';
      document.body.appendChild(a);
      a.click();
      a.remove();
    })
    .catch(function(err) {
      console.error('exportExecutiveMaintExcel error:', err);
      if (window.showToast) window.showToast('Failed to export executive Excel', 'danger');
    });
};

window.emailExecutiveMaintReport = function() {
  var email = prompt('Enter recipient email address for the 6-Sheet Executive Maintenance & TCO Report:');
  if (!email || !email.trim()) return;
  var payload = {
    email: email.trim(),
    from: (document.getElementById('repMaintFrom') || {}).value || '',
    to: (document.getElementById('repMaintTo') || {}).value || '',
    department: (document.getElementById('repMaintDept') || {}).value || '',
    printerCode: (document.getElementById('repMaintPrinter') || {}).value || ''
  };
  var hdrs = Object.assign({ 'Content-Type': 'application/json' }, window.getAuthHeaders ? window.getAuthHeaders() : {});
  fetch('/api/admin/maintenance/email-executive-report', {
    method: 'POST',
    headers: hdrs,
    body: JSON.stringify(payload)
  })
    .then(function(r) { return r.json(); })
    .then(function(res) {
      if (window.showToast) window.showToast(res.message || (res.success ? 'Executive report emailed!' : 'Email failed'), res.success ? 'success' : 'danger');
    })
    .catch(function(err) {
      console.error('emailExecutiveMaintReport error:', err);
      if (window.showToast) window.showToast('Failed to email report', 'danger');
    });
};

window.renderMaintVisualCharts = function(data) {
  var esc = window.escapeHtml || function(x) { return String(x == null ? '' : x); };

  // 1. Department TCO Horizontal Stacked Bars (Repairs vs Toners)
  var tcoEl = document.getElementById('chartDeptTco');
  if (tcoEl) {
    var dList = data.departmentTcoReport || [];
    if (!dList.length) {
      tcoEl.innerHTML = '<div class="text-muted small py-3 text-center">No department TCO data yet.</div>';
    } else {
      var maxTco = Math.max(1, Math.max.apply(null, dList.map(function(d) { return d.totalTcoLkr || 0; })));
      tcoEl.innerHTML = dList.slice(0, 6).map(function(d) {
        var repPct = Math.round(((d.repairCostLkr || 0) / maxTco) * 100);
        var tonPct = Math.round(((d.tonerCostLkr || 0) / maxTco) * 100);
        return '<div class="mb-2">' +
          '<div class="d-flex justify-content-between small mb-1"><span class="fw-semibold">' + esc(d.department) + '</span><span class="text-muted">LKR ' + Number(d.totalTcoLkr || 0).toLocaleString() + '</span></div>' +
          '<div class="progress" style="height:10px;">' +
            '<div class="progress-bar bg-danger" style="width:' + repPct + '%" title="Repairs: LKR ' + (d.repairCostLkr || 0) + '"></div>' +
            '<div class="progress-bar bg-primary" style="width:' + tonPct + '%" title="Toners: LKR ' + (d.tonerCostLkr || 0) + '"></div>' +
          '</div>' +
        '</div>';
      }).join('') +
      '<div class="d-flex gap-3 small text-muted mt-2"><span><span class="badge bg-danger">&nbsp;</span> Repairs</span><span><span class="badge bg-primary">&nbsp;</span> Toners</span></div>';
    }
  }

  // 2. Fleet Health & Replace-vs-Repair Breakdown
  var fhEl = document.getElementById('chartFleetHealth');
  if (fhEl) {
    var mList = data.replaceVsRepairMatrix || [];
    if (!mList.length) {
      fhEl.innerHTML = '<div class="text-muted small py-3 text-center">No fleet health data yet.</div>';
    } else {
      var healthy = 0, monitor = 0, replace = 0;
      mList.forEach(function(r) {
        if (r.recommendation === 'REPLACE_RECOMMENDED') replace++;
        else if (r.recommendation === 'MONITOR_CLOSELY') monitor++;
        else healthy++;
      });
      var tot = Math.max(1, mList.length);
      var barRow = function(lbl, cnt, cls) {
        var pct = Math.round((cnt / tot) * 100);
        return '<div class="mb-2">' +
          '<div class="d-flex justify-content-between small mb-1"><span class="fw-semibold">' + lbl + '</span><span>' + cnt + ' printers (' + pct + '%)</span></div>' +
          '<div class="progress" style="height:10px;"><div class="progress-bar ' + cls + '" style="width:' + pct + '%"></div></div>' +
        '</div>';
      };
      fhEl.innerHTML =
        barRow('Keep Healthy (Score 75-100)', healthy, 'bg-success') +
        barRow('Monitor Closely (Score 50-74)', monitor, 'bg-warning') +
        barRow('Replace Recommended (<50)', replace, 'bg-danger');
    }
  }
};

// =========================================================================
// SAP-STYLE MAINTENANCE & COUNTER AD-HOC REPORT BUILDER (ALV GRID)
// =========================================================================
window._sapMaintSchemas = {
  MAINT_LOGS: [
    { key: 'serviceDate', label: 'Service Date', def: true },
    { key: 'printerCode', label: 'Printer Code', def: true },
    { key: 'printerName', label: 'Printer Name', def: true },
    { key: 'maintenanceType', label: 'Service Type', def: true },
    { key: 'tonerColor', label: 'Toner Color', def: false },
    { key: 'partReplaced', label: 'Part Replaced', def: true },
    { key: 'odometerPages', label: 'Odometer', num: true, def: true },
    { key: 'technician', label: 'Technician', def: true },
    { key: 'durationMinutes', label: 'Duration (min)', num: true, def: true },
    { key: 'costLkr', label: 'Cost (LKR)', num: true, dec: true, def: true },
    { key: 'status', label: 'Status', def: true },
    { key: 'notes', label: 'Notes', def: false }
  ],
  PRINTER_TCO: [
    { key: 'printerCode', label: 'Printer Code', def: true },
    { key: 'printerName', label: 'Printer Name', def: true },
    { key: 'department', label: 'Department', def: true },
    { key: 'model', label: 'Model', def: false },
    { key: 'lifetimePages', label: 'Lifetime Pages', num: true, def: true },
    { key: 'repairCount', label: 'Repairs', num: true, def: true },
    { key: 'repairCostLkr', label: 'Repair Cost (LKR)', num: true, dec: true, def: true },
    { key: 'tonerCostLkr', label: 'Toner Cost (LKR)', num: true, dec: true, def: true },
    { key: 'totalTcoLkr', label: 'Total TCO (LKR)', num: true, dec: true, def: true },
    { key: 'costPerPageLkr', label: 'CPP (LKR)', num: true, dec: true, def: true },
    { key: 'healthScore', label: 'Health Score', num: true, def: true },
    { key: 'recommendation', label: 'Recommendation', def: true }
  ],
  COUNTER_DELTA: [
    { key: 'printerCode', label: 'Printer Code', def: true },
    { key: 'printerName', label: 'Printer Name', def: true },
    { key: 'department', label: 'Department', def: true },
    { key: 'printBwPages', label: 'Print B&W', num: true, def: true },
    { key: 'printColorPages', label: 'Print Color', num: true, def: true },
    { key: 'copyPages', label: 'Copy Pages', num: true, def: true },
    { key: 'scanPages', label: 'Scan Pages', num: true, def: true },
    { key: 'periodTotalPages', label: 'Period Total', num: true, def: true },
    { key: 'currentCounterSinceReset', label: 'Current (Since Reset)', num: true, def: true },
    { key: 'counterOffsetPages', label: 'Reset Offset', num: true, def: false },
    { key: 'lifetimeActualPages', label: 'Lifetime Actual', num: true, def: true }
  ],
  TONER_YIELD: [
    { key: 'printerCode', label: 'Printer Code', def: true },
    { key: 'department', label: 'Department', def: true },
    { key: 'tonerColor', label: 'Color', def: true },
    { key: 'replacementsCount', label: 'Replacements', num: true, def: true },
    { key: 'avgDaysBetween', label: 'Burn Rate (Days)', num: true, def: true },
    { key: 'avgPagesPerCartridge', label: 'Actual Yield', num: true, def: true },
    { key: 'ratedYieldPages', label: 'Rated Yield', num: true, def: true },
    { key: 'yieldEfficiencyPct', label: 'Efficiency %', num: true, dec: true, def: true },
    { key: 'lastReplacedDate', label: 'Last Replaced', def: true },
    { key: 'predictedNextDate', label: 'Predicted Next', def: true },
    { key: 'totalTonerCostLkr', label: 'Total Cost (LKR)', num: true, dec: true, def: true },
    { key: 'yieldAlert', label: 'Alert', def: true }
  ],
  DEPT_TCO: [
    { key: 'department', label: 'Department', def: true },
    { key: 'printerCount', label: 'Printers', num: true, def: true },
    { key: 'lifetimePages', label: 'Lifetime Pages', num: true, def: true },
    { key: 'repairCostLkr', label: 'Repair Cost (LKR)', num: true, dec: true, def: true },
    { key: 'tonerCostLkr', label: 'Toner Cost (LKR)', num: true, dec: true, def: true },
    { key: 'totalTcoLkr', label: 'Total TCO (LKR)', num: true, dec: true, def: true },
    { key: 'costPerPageLkr', label: 'CPP (LKR)', num: true, dec: true, def: true },
    { key: 'highDrainCount', label: 'High-Drain Units', num: true, def: true }
  ],
  TECH_SCORECARD: [
    { key: 'technician', label: 'Technician', def: true },
    { key: 'totalTickets', label: 'Total Tickets', num: true, def: true },
    { key: 'completedTickets', label: 'Completed', num: true, def: true },
    { key: 'openTickets', label: 'Open', num: true, def: true },
    { key: 'totalDurationMinutes', label: 'Total Time (min)', num: true, def: true },
    { key: 'avgDurationMinutes', label: 'Avg Time (min)', num: true, def: true },
    { key: 'totalCostLkr', label: 'Total Cost (LKR)', num: true, dec: true, def: true }
  ]
};

window._sapMaintState = { rows: [], cols: [], sortKey: null, sortAsc: true };

window.sapMaintOnSourceChange = function() {
  var src = (document.getElementById('sapMaintSource') || {}).value || 'MAINT_LOGS';
  var schema = window._sapMaintSchemas[src] || [];
  var picker = document.getElementById('sapMaintColPicker');
  if (!picker) return;
  picker.innerHTML = schema.map(function(col) {
    return '<label class="form-check form-check-inline mb-0">' +
      '<input class="form-check-input sap-maint-col-chk" type="checkbox" value="' + col.key + '" ' + (col.def ? 'checked' : '') + '>' +
      '<span class="form-check-label">' + col.label + '</span></label>';
  }).join('');
};

window.sapMaintToggleCols = function(checked) {
  document.querySelectorAll('.sap-maint-col-chk').forEach(function(cb) { cb.checked = !!checked; });
};

window.sapMaintClear = function() {
  ['sapMaintFrom', 'sapMaintTo', 'sapMaintDept', 'sapMaintPrinter', 'sapMaintMinCost', 'sapMaintKeyword'].forEach(function(id) {
    var el = document.getElementById(id);
    if (el) el.value = '';
  });
  ['sapMaintType', 'sapMaintStatus'].forEach(function(id) {
    var el = document.getElementById(id);
    if (el) el.value = '';
  });
  window.sapMaintExecute();
};

window.sapMaintExecute = function() {
  var src = (document.getElementById('sapMaintSource') || {}).value || 'MAINT_LOGS';
  var from = (document.getElementById('sapMaintFrom') || {}).value || '';
  var to = (document.getElementById('sapMaintTo') || {}).value || '';
  var dept = ((document.getElementById('sapMaintDept') || {}).value || '').trim().toLowerCase();
  var printer = ((document.getElementById('sapMaintPrinter') || {}).value || '').trim().toLowerCase();
  var svcType = (document.getElementById('sapMaintType') || {}).value || '';
  var status = (document.getElementById('sapMaintStatus') || {}).value || '';
  var minCost = parseFloat((document.getElementById('sapMaintMinCost') || {}).value || '0') || 0;
  var kw = ((document.getElementById('sapMaintKeyword') || {}).value || '').trim().toLowerCase();

  var schema = window._sapMaintSchemas[src] || [];
  var selectedKeys = [];
  document.querySelectorAll('.sap-maint-col-chk:checked').forEach(function(cb) { selectedKeys.push(cb.value); });
  var activeCols = schema.filter(function(c) { return selectedKeys.indexOf(c.key) !== -1; });
  if (!activeCols.length) activeCols = schema.filter(function(c) { return c.def; });

  var qs = [];
  if (from) qs.push('from=' + encodeURIComponent(from));
  if (to) qs.push('to=' + encodeURIComponent(to));

  var endpoint = (src === 'MAINT_LOGS')
    ? '/api/admin/maintenance'
    : ('/api/admin/maintenance/advanced-reports' + (qs.length ? '?' + qs.join('&') : ''));

  fetch(endpoint, { headers: window.getAuthHeaders ? window.getAuthHeaders() : {} })
    .then(function(r) { return r.json(); })
    .then(function(data) {
      var raw = [];
      if (src === 'MAINT_LOGS') raw = data.records || [];
      else if (src === 'PRINTER_TCO') raw = data.replaceVsRepairMatrix || [];
      else if (src === 'COUNTER_DELTA') raw = data.counterDeltaReport || [];
      else if (src === 'TONER_YIELD') raw = data.tonerBurnRateForecast || [];
      else if (src === 'DEPT_TCO') raw = data.departmentTcoReport || [];
      else if (src === 'TECH_SCORECARD') raw = data.technicianScorecard || [];

      var filtered = raw.filter(function(r) {
        if (src === 'MAINT_LOGS') {
          if (from && r.serviceDate && r.serviceDate < from) return false;
          if (to && r.serviceDate && r.serviceDate > to) return false;
          if (svcType && String(r.maintenanceType || '').toUpperCase() !== svcType) return false;
          if (status && String(r.status || '').toUpperCase() !== status) return false;
        }
        if (dept && String(r.department || '').toLowerCase().indexOf(dept) === -1) return false;
        if (printer) {
          var pStr = (String(r.printerCode || '') + ' ' + String(r.printerName || '')).toLowerCase();
          if (pStr.indexOf(printer) === -1) return false;
        }
        if (minCost > 0) {
          var cVal = Number(r.costLkr || r.totalTcoLkr || r.totalTonerCostLkr || r.totalCostLkr || 0);
          if (cVal < minCost) return false;
        }
        if (kw) {
          var blob = JSON.stringify(r).toLowerCase();
          if (blob.indexOf(kw) === -1) return false;
        }
        return true;
      });

      window._sapMaintState = { rows: filtered, cols: activeCols, sortKey: null, sortAsc: true };
      window.sapMaintRenderAlv();
    })
    .catch(function(err) {
      console.error('sapMaintExecute error:', err);
    });
};

window.sapMaintSortBy = function(key) {
  var st = window._sapMaintState;
  if (!st || !st.rows) return;
  if (st.sortKey === key) st.sortAsc = !st.sortAsc;
  else { st.sortKey = key; st.sortAsc = true; }
  st.rows.sort(function(a, b) {
    var va = a[key], vb = b[key];
    if (typeof va === 'number' && typeof vb === 'number') return st.sortAsc ? va - vb : vb - va;
    return st.sortAsc
      ? String(va == null ? '' : va).localeCompare(String(vb == null ? '' : vb))
      : String(vb == null ? '' : vb).localeCompare(String(va == null ? '' : va));
  });
  window.sapMaintRenderAlv();
};

window.sapMaintRenderAlv = function() {
  var st = window._sapMaintState;
  var esc = window.escapeHtml || function(x) { return String(x == null ? '' : x); };
  var head = document.getElementById('sapMaintAlvHead');
  var body = document.getElementById('sapMaintAlvBody');
  var foot = document.getElementById('sapMaintAlvFoot');
  var cnt = document.getElementById('sapMaintRowCount');
  if (cnt) cnt.textContent = st.rows.length + ' rows';

  if (head) {
    head.innerHTML = '<tr>' + st.cols.map(function(c) {
      var arrow = st.sortKey === c.key ? (st.sortAsc ? ' ▲' : ' ▼') : '';
      return '<th style="cursor:pointer; user-select:none;" onclick="window.sapMaintSortBy(\'' + c.key + '\')">' + esc(c.label) + arrow + '</th>';
    }).join('') + '</tr>';
  }

  if (body) {
    if (!st.rows.length) {
      body.innerHTML = '<tr><td colspan="' + Math.max(1, st.cols.length) + '" class="text-center py-4 text-muted">No matching records found.</td></tr>';
    } else {
      body.innerHTML = st.rows.map(function(r) {
        return '<tr>' + st.cols.map(function(c) {
          var v = r[c.key];
          if (c.key === 'printerCode' && v) {
            return '<td><a href="javascript:void(0)" class="fw-bold text-primary text-decoration-none" onclick="window.openPrinterProfile(\'' + esc(v) + '\')">' + esc(v) + '</a></td>';
          }
          if (c.num) {
            var n = Number(v || 0);
            return '<td class="text-end font-monospace">' + (c.dec ? n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : n.toLocaleString()) + '</td>';
          }
          return '<td>' + esc(v == null ? '-' : v) + '</td>';
        }).join('') + '</tr>';
      }).join('');
    }
  }

  if (foot) {
    if (!st.rows.length) {
      foot.innerHTML = '';
    } else {
      foot.innerHTML = '<tr>' + st.cols.map(function(c, idx) {
        if (idx === 0) return '<td>TOTAL (' + st.rows.length + ')</td>';
        if (c.num && c.key !== 'healthScore' && c.key !== 'costPerPageLkr' && c.key !== 'yieldEfficiencyPct') {
          var sum = st.rows.reduce(function(acc, r) { return acc + Number(r[c.key] || 0); }, 0);
          return '<td class="text-end font-monospace">' + (c.dec ? sum.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : sum.toLocaleString()) + '</td>';
        }
        return '<td></td>';
      }).join('') + '</tr>';
    }
  }
};

document.addEventListener('keydown', function(e) {
  if (e.key === 'F8') {
    var p = document.getElementById('repPanelSapMaint');
    if (p && p.style.display !== 'none') {
      e.preventDefault();
      window.sapMaintExecute();
    }
  }
});
