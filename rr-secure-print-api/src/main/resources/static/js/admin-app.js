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
    auditLogs: [],
    stats: {
      totalJobs: 0,
      heldJobs: 0,
      completedJobs: 0,
      printerCount: 0,
      userCount: 0
    },
    serverTerminalUrl: 'http://192.168.1.80:8080/'
  };

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
      // Determine page from URL hash (e.g. #printers, #users)
      var hash = (window.location.hash || '').replace('#', '').toLowerCase();
      var validPages = ['dashboard', 'printers', 'users', 'queue', 'audit'];
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
        navItems[i].className = 'sidebar-item active';
      } else {
        navItems[i].className = 'sidebar-item';
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

    // Update Topbar Title & Subtitle
    var topbarTitle = document.getElementById('topbarTitle');
    var topbarSub   = document.getElementById('topbarSub');

    if (pageId === 'dashboard') {
      topbarTitle.textContent = 'System Administration Overview';
      topbarSub.textContent   = 'Enterprise Pull-Print Server & Hardware Monitor';
      loadStats();
      loadJobs();
    } else if (pageId === 'printers') {
      topbarTitle.textContent = 'Printer Device Management';
      topbarSub.textContent   = 'Toshiba Multifunction Printers & Network JetDirect Queues';
      loadPrinters();
      loadVirtualQueueConfig();
    } else if (pageId === 'users') {
      topbarTitle.textContent = 'User Identity Management';
      topbarSub.textContent   = 'Security Credentials, Touchscreen PINs, and Windows SIDs';
      loadUsers();
    } else if (pageId === 'queue') {
      topbarTitle.textContent = 'Global Held Print Queue';
      topbarSub.textContent   = 'Active Print Jobs Quarantined Awaiting Walk-up Release';
      loadJobs();
    } else if (pageId === 'audit') {
      topbarTitle.textContent = 'Security Audit Trail';
      topbarSub.textContent   = 'Tamper-Evident Security Log & Device Release Events';
      loadAudit();
    }
  };

  window.refreshCurrentPage = function () {
    window.navigatePage(state.currentPage, false);
    showToast('Refreshing ' + state.currentPage.toUpperCase() + ' data...', 'info');
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
      return 'http://192.168.1.80:8080/';
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

  function renderPrinters() {
    var tbody = document.getElementById('printersTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (state.printers.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:30px;color:#6B7299;">No physical printers registered in the Follow-Me pool yet. Click "+ Register New Printer" to add one.</td></tr>';
      return;
    }

    for (var i = 0; i < state.printers.length; i++) {
      var p = state.printers[i];
      var tr = document.createElement('tr');

      var statusBadge = p.status === 'ONLINE'
        ? '<span class="badge badge-online">ONLINE</span>'
        : '<span class="badge badge-offline">' + esc(p.status || 'OFFLINE') + '</span>';

      var ewbBadge = p.ewbConfigured
        ? '<span class="badge badge-ewb-ready" title="Terminal URL: ' + esc(p.ewbUrl || '') + '">EWB READY</span>'
        : '<span class="badge badge-ewb-none" title="Not pushed to device yet">NO EWB</span>';

      var roleBadge = '<span class="badge" style="background:#EFF6FF; color:#1D4ED8; font-weight:600; border:1px solid #BFDBFE;">Follow-Me Station</span>';

      tr.innerHTML =
        '<td><strong>' + esc(p.name) + '</strong></td>' +
        '<td><code>' + esc(p.printerCode) + '</code></td>' +
        '<td>' + esc(p.ipAddress) + ':' + p.rawPort + '</td>' +
        '<td>' + esc(p.location || 'Office') + '</td>' +
        '<td>' + esc(p.model || 'Toshiba MFP') + '</td>' +
        '<td>' + roleBadge + '</td>' +
        '<td>' + ewbBadge + '</td>' +
        '<td id="printer-status-' + p.id + '">' + statusBadge + '</td>' +
        '<td style="text-align:right; white-space:nowrap;">' +
          '<button class="btn btn-sm btn-action-test" id="btn-test-' + p.id + '" onclick="testPrinter(' + p.id + ')">TEST</button>' +
          '<button class="btn btn-sm btn-action-ewb" onclick="openQuickEwbModal(' + p.id + ')">PUSH EWB</button>' +
          '<button class="btn btn-sm btn-action-web" onclick="window.open(\'http://' + esc(p.ipAddress) + '/\', \'_blank\')" title="Open TopAccess in new tab">TOPACCESS</button>' +
          '<button class="btn btn-sm btn-action-edit" onclick="openEditPrinterModal(' + p.id + ')">EDIT</button>' +
          '<button class="btn btn-sm btn-action-delete" onclick="deletePrinter(' + p.id + ', \'' + esc(p.name) + '\')">DEL</button>' +
        '</td>';

      tbody.appendChild(tr);
    }
  }

  window.openAddPrinterModal = function () {
    document.getElementById('printerModalTitle').textContent = 'Register New Printer';
    document.getElementById('printerFormId').value = '';
    document.getElementById('printerName').value = '';
    document.getElementById('printerCode').value = '';
    document.getElementById('printerCode').disabled = false;
    document.getElementById('printerIp').value = '';
    document.getElementById('printerPort').value = '9100';
    document.getElementById('printerLocation').value = '';
    document.getElementById('printerModel').value = 'Toshiba e-STUDIO 3518A';
    document.getElementById('printerDuplex').checked = true;
    document.getElementById('printerColor').checked = false;
    document.getElementById('printerActive').checked = true;

    // EWB fields
    document.getElementById('printerAdminUser').value = 'admin';
    document.getElementById('printerAdminPass').value = '123456';
    document.getElementById('printerTerminalUrl').value = getDefaultTerminalUrl();
    document.getElementById('printerAutoEwb').checked = true;

    var logBox = document.getElementById('modalEwbLog');
    if (logBox) { logBox.style.display = 'none'; logBox.textContent = ''; }

    document.getElementById('printerSubmitBtn').textContent = 'Register Printer';
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
    document.getElementById('printerDuplex').checked = !!p.duplexSupported;
    document.getElementById('printerColor').checked = !!p.colorSupported;
    document.getElementById('printerActive').checked = p.isActive !== false;

    // EWB fields
    document.getElementById('printerAdminUser').value = p.adminUsername || 'admin';
    document.getElementById('printerAdminPass').value = p.adminPassword || '123456';
    document.getElementById('printerTerminalUrl').value = sanitizeTerminalUrl(p.ewbUrl, p.ipAddress);
    document.getElementById('printerAutoEwb').checked = false;

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

    var logBox = document.getElementById('quickEwbLog');
    logBox.textContent = 'Device: ' + p.name + ' (' + p.ipAddress + ')\nReady to push EWB Home Page to printer. Click "Push EWB Now".';

    document.getElementById('quickEwbModal').style.display = 'flex';
  };

  window.closeQuickEwbModal = function () {
    document.getElementById('quickEwbModal').style.display = 'none';
  };

  window.handleQuickEwbSubmit = function (e) {
    e.preventDefault();
    var id = document.getElementById('quickEwbPrinterId').value;
    var user = document.getElementById('quickEwbUser').value.trim();
    var pass = document.getElementById('quickEwbPass').value.trim();
    var url = document.getElementById('quickEwbUrl').value.trim();
    var btn = document.getElementById('btnQuickEwbSubmit');
    var logBox = document.getElementById('quickEwbLog');

    if (btn) { btn.disabled = true; btn.textContent = 'Pushing...'; }
    if (logBox) {
      logBox.textContent = '[1/3] Connecting to TopAccess XML endpoint...\n';
    }

    fetch('/api/admin/printers/' + id + '/configure-ewb', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        adminUsername: user,
        adminPassword: pass,
        terminalUrl: url
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (res) {
        if (btn) { btn.disabled = false; btn.textContent = 'Push EWB Now'; }
        if (res.success) {
          logBox.textContent += '[2/3] Administrator login... STATUS_OK\n' +
                                '[3/3] Updating EWBSettings (HomePage, MDS, AutoScreenChange)... Normal\n' +
                                'SUCCESS: ' + res.message + '\n' +
                                'Verified Model: ' + (res.printerModel || 'Toshiba MFP');
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

  window.handleSavePrinter = function (e) {
    e.preventDefault();
    var id = document.getElementById('printerFormId').value;
    var isEdit = !!id;

    var payload = {
      printerCode: document.getElementById('printerCode').value.trim(),
      name: document.getElementById('printerName').value.trim(),
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
          tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;color:#ef4444;">Failed to load users: ' + esc(err.message) + '</td></tr>';
        }
      });
  };

  function renderUsers() {
    var tbody = document.getElementById('usersTableBody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (state.users.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:30px;color:#6B7299;">No users registered yet.</td></tr>';
      return;
    }

    for (var i = 0; i < state.users.length; i++) {
      var u = state.users[i];
      var tr = document.createElement('tr');

      var roleBadge = u.role === 'ADMIN'
        ? '<span class="badge badge-role-admin">ADMIN</span>'
        : '<span class="badge badge-role-user">USER</span>';

      var pinBadge = u.hasPin
        ? '<span class="badge badge-pin-yes">CONFIGURED</span>'
        : '<span class="badge badge-pin-no">NOT SET</span>';

      var statusBadge = u.isActive
        ? '<span class="badge badge-online">ACTIVE</span>'
        : '<span class="badge badge-offline">DISABLED</span>';

      tr.innerHTML =
        '<td><strong>' + esc(u.username) + '</strong></td>' +
        '<td>' + esc(u.displayName) + '</td>' +
        '<td>' + esc(u.department || 'Operations') + '</td>' +
        '<td>' + roleBadge + '</td>' +
        '<td>' + pinBadge + '</td>' +
        '<td>' + (u.badgeId ? '<code>' + esc(u.badgeId) + '</code>' : '<span style="color:#94a3b8;">None</span>') + '</td>' +
        '<td><span style="font-size:11px; color:#475569;">' + esc(u.userSid) + '</span></td>' +
        '<td>' + statusBadge + '</td>' +
        '<td style="text-align:right; white-space:nowrap;">' +
          '<button class="btn btn-sm btn-action-pin" onclick="openResetPinModal(' + u.id + ', \'' + esc(u.username) + '\')">PIN</button>' +
          '<button class="btn btn-sm btn-action-edit" onclick="openEditUserModal(' + u.id + ')">EDIT</button>' +
          '<button class="btn btn-sm btn-action-delete" onclick="deleteUser(' + u.id + ', \'' + esc(u.username) + '\')">DELETE</button>' +
        '</td>';

      tbody.appendChild(tr);
    }
  }

  window.openAddUserModal = function () {
    document.getElementById('userModalTitle').textContent = 'Add New User';
    document.getElementById('userFormId').value = '';
    document.getElementById('userUsername').value = '';
    document.getElementById('userUsername').disabled = false;
    document.getElementById('userDisplayName').value = '';
    document.getElementById('userDepartment').value = 'Operations';
    document.getElementById('userRole').value = 'USER';
    document.getElementById('userPin').value = '';
    document.getElementById('userPin').required = false;
    document.getElementById('userPinHint').textContent = 'PIN entered at the Toshiba touchscreen (e.g. 1234)';
    document.getElementById('userBadge').value = '';
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
    document.getElementById('userRole').value = u.role || 'USER';
    document.getElementById('userPin').value = '';
    document.getElementById('userPinHint').textContent = 'Leave empty to keep existing PIN unchanged.';
    document.getElementById('userBadge').value = u.badgeId || '';
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

    var payload = {
      username: document.getElementById('userUsername').value.trim(),
      displayName: document.getElementById('userDisplayName').value.trim(),
      department: document.getElementById('userDepartment').value.trim(),
      role: document.getElementById('userRole').value,
      pin: document.getElementById('userPin').value.trim() || null,
      badgeId: document.getElementById('userBadge').value.trim() || null,
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
      })
      .catch(function (err) {
        showToast('Failed to reset PIN: ' + err.message, 'error');
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
        actions = '<button class="btn btn-sm btn-action-edit" onclick="releaseJobAdmin(\'' + job.id + '\')">RELEASE</button>' +
                  '<button class="btn btn-sm btn-action-delete" onclick="cancelJobAdmin(\'' + job.id + '\')">CANCEL</button>';
      } else {
        actions = '<span style="color:#94a3b8; font-size:11px;">Completed</span>';
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
    var tbody = document.getElementById('dashboardJobsTbody');
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

  window.showToast = function showToast(msg, type) {
    var toast = document.getElementById('toastNotification');
    if (!toast) return;
    toast.textContent = msg;
    toast.className = 'toast-strip toast-' + (type || 'info');
    toast.style.display = 'block';

    setTimeout(function () {
      toast.style.display = 'none';
    }, 4500);
  }

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

  // Override browser native alert to completely disable browser popup messages
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
     SERVER VIRTUAL PRINT QUEUE CONFIGURATION
     ========================================================================= */

  window.loadVirtualQueueConfig = function () {
    var select = document.getElementById('selectServerVirtualPrinter');
    fetch('/api/admin/virtual-queue')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (cfg) {
        state.virtualQueueConfig = cfg;
        var selName = document.getElementById('vqSelectedPrinter');
        var netShare = document.getElementById('vqNetworkShare');
        var driver = document.getElementById('vqDriverName');
        var hold = document.getElementById('vqHoldStatus');
        var badge = document.getElementById('vqShareBadge');
        var tagsContainer = document.getElementById('vqPhysicalPrintersTags');
        var liveBtn = document.getElementById('vqLiveModeBtn');

        if (selName) selName.textContent = cfg.selectedPrinterName || 'None';
        if (netShare) netShare.textContent = cfg.networkSharePath || ('\\\\SERVER\\' + (cfg.shareName || cfg.selectedPrinterName));
        if (driver) driver.textContent = cfg.driverName || 'Universal Driver';
        if (hold) {
          hold.textContent = cfg.isPaused ? 'Active Hold (Quarantined in Windows Spooler)' : 'Normal Mode';
          hold.style.color = cfg.isPaused ? '#059669' : '#D97706';
        }
        if (badge) {
          badge.textContent = cfg.isShared ? '🟢 Shared on Network' : '🟡 Local Queue';
          badge.style.background = cfg.isShared ? '#DCFCE7' : '#FEF3C7';
          badge.style.color = cfg.isShared ? '#166534' : '#92400E';
        }

        if (liveBtn) {
          if (cfg.livePrintingEnabled) {
            liveBtn.innerHTML = '🟢 Hardware Printing: LIVE (Port 9100)';
            liveBtn.style.background = '#DCFCE7';
            liveBtn.style.color = '#166534';
            liveBtn.style.borderColor = '#86EFAC';
          } else {
            liveBtn.innerHTML = '🟡 Simulation Guard Mode (No Paper)';
            liveBtn.style.background = '#FEF3C7';
            liveBtn.style.color = '#92400E';
            liveBtn.style.borderColor = '#FCD34D';
          }
        }

        if (tagsContainer) {
          tagsContainer.innerHTML = '';
          var phys = cfg.physicalPrinters || state.printers || [];
          if (phys.length === 0) {
            tagsContainer.innerHTML = '<span style="font-size:12px; color:#94A3B8; font-style:italic;">No physical printers linked yet. Click "Add Physical Printer to Virtual Pool" to connect your Toshiba MFP!</span>';
          } else {
            for (var p = 0; p < phys.length; p++) {
              var pr = phys[p];
              var pill = document.createElement('div');
              pill.style.cssText = 'background:#FFFFFF; border:1px solid #CBD5E1; border-radius:6px; padding:4px 10px; font-size:12px; display:inline-flex; align-items:center; gap:6px; box-shadow:0 1px 2px rgba(0,0,0,0.04);';
              var defBadge = '';
              pill.innerHTML = '🖨️ <strong>' + (pr.name || pr.printerCode) + '</strong> <code style="font-size:11px; color:#475569;">' + pr.ipAddress + ':' + pr.rawPort + '</code>' + defBadge + ' <span style="color:#059669; font-size:10px;">● Online</span>';
              tagsContainer.appendChild(pill);
            }
          }
        }

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
            for (var i = 0; i < printers.length; i++) {
              var sp = printers[i];
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
      })
      .catch(function (err) {
        console.warn('Could not load virtual queue config:', err);
        if (select && select.options.length <= 1) {
          select.innerHTML = '<option value="QOA_Printer" selected>QOA_Printer (Designated Virtual Queue)</option>';
        }
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
        window.loadVirtualQueueConfig();
        showToast('Server printer list updated!', 'success');
      })
      .catch(function (err) {
        window.loadVirtualQueueConfig();
      });
  };

  window.handleSaveVirtualQueuePrinter = function () {
    var select = document.getElementById('selectServerVirtualPrinter');
    if (!select || !select.value) {
      showToast('Please select a printer from the list.', 'error');
      return;
    }
    var chosen = select.value;
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
  };

  window.toggleLivePrintingMode = function () {
    fetch('/api/admin/virtual-queue/toggle-live-mode', { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        showToast(data.message, data.livePrintingEnabled ? 'success' : 'info');
        window.loadVirtualQueueConfig();
      })
      .catch(function (err) {
        showToast('Failed to toggle print mode: ' + err.message, 'error');
      });
  };

})();
