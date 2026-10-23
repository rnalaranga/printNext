/* ==========================================================================
   RR Secure Print - Toshiba EWB Controller v10 (STRICTLY NO ICONS)
   Pure ES5 Vanilla JS - NO Font Dependencies, NO Emojis, NO Icons
   Ultra Lightweight & Instant Render on Toshiba e-STUDIO MFP
   ========================================================================== */

(function () {
  'use strict';

  // Bulletproof ES5 String Trim Polyfill / Safe Trimmer
  function safeTrim(s) {
    if (s === null || s === undefined) return '';
    return ('' + s).replace(/^\s+|\s+$/g, '');
  }

  // Toshiba EWB Global Diagnostic Catch
  window.onerror = function (msg, url, line) {
    var errText = 'EWB Script Notice: ' + msg + ' (L:' + line + ')';
    if (window.console && window.console.error) window.console.error(errText);
    var alertEl = document.getElementById('alertBox');
    if (alertEl) {
      alertEl.textContent = errText;
      alertEl.className = 'pc-alert-strip pc-alert-error';
      alertEl.style.display = 'block';
    }
  };

  var state = {
    currentUser: null,
    pinBuffer: '',
    jobs: [],
    selectedJobIds: {},
    inactivitySeconds: 120,
    timerInterval: null,
    isBusy: false
  };

  // DOM Elements
  var pinDisplay          = null;
  var alertBox            = null;
  var screenAuth          = null;
  var screenQueue         = null;
  var userHeaderSection   = null;
  var deviceStatusHeader  = null;
  var userBadgeText       = null;
  var userDeptText        = null;
  var timerCountdown      = null;
  var queueCountBadge     = null;
  var printAllBtn         = null;
  var printAllCountText   = null;
  var selectAllCheckbox   = null;
  var jobListTableBody    = null;
  var emptyQueueMessage   = null;
  var selectedSummaryText = null;
  var selectedPagesText   = null;
  var selectedCostText    = null;
  var releaseBtn          = null;
  var cancelBtn           = null;

  window.onload = function () {
    pinDisplay          = document.getElementById('pinDisplay');
    alertBox            = document.getElementById('alertBox');
    screenAuth          = document.getElementById('screenAuth');
    screenQueue         = document.getElementById('screenQueue');
    userHeaderSection   = document.getElementById('userHeaderSection');
    deviceStatusHeader  = document.getElementById('deviceStatusHeader');
    userBadgeText       = document.getElementById('userBadgeText');
    userDeptText        = document.getElementById('userDeptText');
    timerCountdown      = document.getElementById('timerCountdown');
    queueCountBadge     = document.getElementById('queueCountBadge');
    printAllBtn         = document.getElementById('printAllBtn');
    printAllCountText   = document.getElementById('printAllCountText');
    selectAllCheckbox   = document.getElementById('selectAllCheckbox');
    jobListTableBody    = document.getElementById('jobListTableBody');
    emptyQueueMessage   = document.getElementById('emptyQueueMessage');
    selectedSummaryText = document.getElementById('selectedSummaryText');
    selectedPagesText   = document.getElementById('selectedPagesText');
    selectedCostText    = document.getElementById('selectedCostText');
    releaseBtn          = document.getElementById('releaseBtn');
    cancelBtn           = document.getElementById('cancelBtn');

    // Reset inactivity timer on screen touches
    document.body.onmousedown  = resetInactivityTimer;
    document.body.ontouchstart = resetInactivityTimer;

    // Immediate Hardware Sleep / Energy Saver / Hide Event Handlers
    if (document.addEventListener) {
      document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
          // Hardware Sleep button pressed OR panel turned off into Energy Saver!
          window.emergencyLockAndLogout('HARDWARE_SLEEP_BUTTON');
        } else if (document.visibilityState === 'visible') {
          // Machine woke up from sleep - enforce lock and show PIN pad
          window.emergencyLockAndLogout('HARDWARE_WAKE_VERIFY');
        }
      });
    }

    if (window.addEventListener) {
      window.addEventListener('pagehide', function () {
        window.emergencyLockAndLogout('PAGE_HIDE');
      });
      window.addEventListener('blur', function () {
        if (state.currentUser) {
          window.emergencyLockAndLogout('WINDOW_BLUR_SLEEP');
        }
      });
      window.addEventListener('focus', function () {
        if (!state.currentUser) {
          window.emergencyLockAndLogout('WINDOW_FOCUS_WAKE');
        }
      });
      window.addEventListener('online', onWakeOrReconnect);
      window.addEventListener('pageshow', onWakeOrReconnect);
    }

    // Hook into Toshiba OpenPlatform injected system sleep events
    try {
      if (window.topaccess && window.topaccess.system && window.topaccess.system.addEventListener) {
        window.topaccess.system.addEventListener('sleep', function () {
          window.emergencyLockAndLogout('TOPACCESS_SYSTEM_SLEEP');
        });
        window.topaccess.system.addEventListener('powersave', function () {
          window.emergencyLockAndLogout('TOPACCESS_SYSTEM_POWERSAVE');
        });
        window.topaccess.system.addEventListener('autoclear', function () {
          window.emergencyLockAndLogout('TOPACCESS_SYSTEM_AUTOCLEAR');
        });
      }
    } catch (e) {}

    // Check existing session
    checkCurrentSession();

    // Initialize physical release station info
    initStationInfo();
  };


  /* ------------------------------------------------------------------ */
  /* Emergency Sleep Auto-Lock & Device Security                        */
  /* ------------------------------------------------------------------ */
  window.emergencyLockAndLogout = function (reason) {
    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;
    var payload = JSON.stringify({
      printerCode: stationCode,
      printerIp: stationIp,
      reason: reason || 'SLEEP_MODE'
    });

    // 1. Send beacon immediately (guaranteed delivery across page freeze/sleep)
    if (navigator.sendBeacon) {
      try {
        var blob = new Blob([payload], { type: 'application/json' });
        navigator.sendBeacon('/api/station/auto-lock', blob);
      } catch (e) {}
    }

    // 2. Fetch with keepalive fallback
    try {
      if (window.fetch) {
        fetch('/api/station/auto-lock', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: payload,
          keepalive: true
        });
      }
    } catch (e) {}

    // 3. Reset local UI state to locked PIN pad
    state.currentUser = null;
    state.pinBuffer = '';
    stopHeartbeat();
    stopInactivityTimer();
    window.hideCopySessionModal();
    showAuthScreen();
    showAlert('Device locked & secured. Enter PIN to start.', 'info');
  };

  /* ------------------------------------------------------------------ */
  /* Touch Terminal Heartbeat (Keeps session alive while active)        */
  /* ------------------------------------------------------------------ */
  var heartbeatTimer = null;
  function startHeartbeat() {
    stopHeartbeat();
    sendHeartbeat();
    heartbeatTimer = setInterval(sendHeartbeat, 8000); // Send heartbeat every 8 seconds
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  function sendHeartbeat() {
    if (!state.currentUser) {
      stopHeartbeat();
      return;
    }
    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;
    apiRequest('POST', '/api/station/heartbeat', { printerCode: stationCode, printerIp: stationIp }, function (status, data) {
      if (status === 401 || (data && data.locked)) {
        window.emergencyLockAndLogout('HEARTBEAT_EXPIRED');
      }
    });
  }


  /* ------------------------------------------------------------------ */
  /* Physical Station Management (PaperCut Find-Me Architecture)       */
  /* ------------------------------------------------------------------ */
  function applyEwbBranding(branding) {
    if (!branding) return;
    var defaultGroup = document.getElementById('ewbDefaultBrandGroup');
    var logoImg = document.getElementById('ewbLogoImg');
    var brandName = document.getElementById('ewbBrandName');
    var brandSub  = document.getElementById('ewbBrandSubtitle');

    if (branding.hasCustomLogo && branding.logoData) {
      if (logoImg) {
        logoImg.src = branding.logoData;
        logoImg.style.display = 'inline-block';
      }
      if (defaultGroup) defaultGroup.style.display = 'none';
    } else {
      if (logoImg) {
        logoImg.src = '';
        logoImg.style.display = 'none';
      }
      if (defaultGroup) defaultGroup.style.display = 'inline-flex';
      if (brandName && branding.systemTitle) {
        brandName.textContent = branding.systemTitle;
      }
      if (brandSub && branding.organizationName) {
        brandSub.textContent = branding.organizationName;
      }
    }
  }

  function initStationInfo() {
    var params = window.location.search || '';
    apiRequest('GET', '/api/station/current' + params, null, function (status, data) {
      if (status === 200 && data && data.currentStation) {
        if (data.branding) {
          applyEwbBranding(data.branding);
        }
        state.station = data.currentStation;
        var nameEl = document.getElementById('stationNameHeader');
        var ipEl = document.getElementById('stationIpHeader');
        var selectEl = document.getElementById('stationSelect');

        if (nameEl) nameEl.textContent = data.currentStation.name || data.currentStation.printerCode;
        if (ipEl) ipEl.textContent = data.currentStation.ipAddress;

        if (selectEl && data.allStations && data.allStations.length > 1) {
          selectEl.innerHTML = '';
          for (var i = 0; i < data.allStations.length; i++) {
            var st = data.allStations[i];
            var opt = document.createElement('option');
            opt.value = st.printerCode;
            opt.textContent = st.name + ' (' + st.ipAddress + ')';
            if (st.printerCode === data.currentStation.printerCode) {
              opt.selected = true;
            }
            selectEl.appendChild(opt);
          }
          selectEl.style.display = 'inline-block';
        }
      }
    });
  }

  window.handleSwitchStation = function (code) {
    if (!code) return;
    window.location.search = '?printer=' + encodeURIComponent(code);
  };

  function onWakeOrReconnect() {
    sessionRetryCount = 0;
    if (state.currentUser) {
      window.emergencyLockAndLogout('HARDWARE_WAKE_VERIFY');
    } else {
      checkCurrentSession();
    }
  }

  /* ------------------------------------------------------------------ */
  /* Native XMLHttpRequest (ES5 Standard)                               */
  /* ------------------------------------------------------------------ */
  function apiRequest(method, url, data, callback) {
    var xhr = new XMLHttpRequest();
    xhr.open(method, url, true);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
    xhr.onreadystatechange = function () {
      if (xhr.readyState === 4) {
        var json = null;
        if (xhr.responseText) {
          try {
            json = JSON.parse(xhr.responseText);
          } catch (e) {
            json = { message: xhr.responseText };
          }
        }
        if (typeof callback === 'function') {
          callback(xhr.status, json);
        }
      }
    };
    xhr.send(data ? JSON.stringify(data) : null);
  }

  /* ------------------------------------------------------------------ */
  /* Authentication & Keypad Handling                                   */
  /* ------------------------------------------------------------------ */
  var sessionRetryCount = 0;
  function checkCurrentSession() {
    apiRequest('GET', '/api/auth/current', null, function (status, data) {
      if (status === 200 && data && data.authenticated) {
        sessionRetryCount = 0;
        onLoginSuccess(data);
      } else if (status === 0) {
        // Network waking up from MFP sleep mode - silently retry every 1.5s
        if (sessionRetryCount < 10) {
          sessionRetryCount++;
          setTimeout(checkCurrentSession, 1500);
        } else {
          showAuthScreen();
        }
      } else {
        sessionRetryCount = 0;
        showAuthScreen();
      }
    });
  }

  window.handleKeypad = function (val) {
    if (state.isBusy) return;
    if (val === 'CLEAR') {
      state.pinBuffer = '';
    } else if (val === 'ENTER') {
      submitPin();
      return;
    } else {
      if (state.pinBuffer.length < 8) {
        state.pinBuffer += val;
      }
    }
    refreshPinDisplay();
  };

  function refreshPinDisplay() {
    var dots = '';
    for (var i = 0; i < state.pinBuffer.length; i++) {
      // Pure CSS square dots: zero emoji, zero unicode dependency!
      dots += '<span class="pc-pin-dot"></span>';
    }
    if (pinDisplay) {
      pinDisplay.innerHTML = dots || '<span class="pc-pin-placeholder">ENTER PIN</span>';
    }
  }

  function submitPin() {
    if (!state.pinBuffer) {
      showAlert('Please enter your 4-digit PIN code.', 'error');
      return;
    }
    state.isBusy = true;
    showAlert('Authenticating with secure print server...', 'info');

    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;

    apiRequest('POST', '/api/auth/login', {
      pin: state.pinBuffer,
      printerCode: stationCode,
      printerIp: stationIp
    }, function (status, data) {
      state.isBusy = false;
      state.pinBuffer = '';
      refreshPinDisplay();
      if (status === 200 && data && data.authenticated) {
        onLoginSuccess(data);
      } else {
        showAlert((data && data.message) || 'Invalid PIN code. Access denied.', 'error');
      }
    });
  }

  window.quickDemoLogin = function (username, pin) {
    if (state.isBusy) return;
    state.isBusy = true;
    showAlert('Signing in as ' + username.toUpperCase() + '...', 'info');

    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;

    apiRequest('POST', '/api/auth/login', {
      username: username,
      pin: pin,
      printerCode: stationCode,
      printerIp: stationIp
    }, function (status, data) {
      state.isBusy = false;
      state.pinBuffer = '';
      refreshPinDisplay();
      if (status === 200 && data && data.authenticated) {
        onLoginSuccess(data);
      } else {
        showAlert((data && data.message) || 'Authentication failed.', 'error');
      }
    });
  };

  /* ------------------------------------------------------------------ */
  /* PaperCut MF Username & Password Authentication                     */
  /* ------------------------------------------------------------------ */
  state.authMode = 'PIN';

  window.switchAuthMode = function (mode) {
    state.authMode = mode;
    var tabPin = document.getElementById('tabAuthPin');
    var tabUserPass = document.getElementById('tabAuthUserPass');
    var viewPin = document.getElementById('authViewPin');
    var viewUserPass = document.getElementById('authViewUserPass');

    if (mode === 'USER_PASS') {
      if (tabPin) tabPin.className = 'pc-auth-tab';
      if (tabUserPass) tabUserPass.className = 'pc-auth-tab active';
      if (viewPin) viewPin.style.display = 'none';
      if (viewUserPass) viewUserPass.style.display = 'block';
      var inpUser = document.getElementById('inputUsername');
      if (inpUser) {
        setTimeout(function () {
          try { inpUser.focus(); } catch (err) {}
        }, 120);
      }
    } else {
      if (tabPin) tabPin.className = 'pc-auth-tab active';
      if (tabUserPass) tabUserPass.className = 'pc-auth-tab';
      if (viewPin) viewPin.style.display = 'block';
      if (viewUserPass) viewUserPass.style.display = 'none';
    }
  };

  window.clearUserPass = function () {
    var inpUser = document.getElementById('inputUsername');
    var inpPass = document.getElementById('inputPassword');
    if (inpUser) {
      inpUser.value = '';
      try { inpUser.focus(); } catch (err) {}
    }
    if (inpPass) inpPass.value = '';
  };

  window.quickFillUserPass = function (username, password) {
    var inpUser = document.getElementById('inputUsername');
    var inpPass = document.getElementById('inputPassword');
    if (inpUser) inpUser.value = username;
    if (inpPass) inpPass.value = password;
    window.submitUserPass();
  };

  window.submitUserPass = function (e) {
    if (e && e.preventDefault) {
      try { e.preventDefault(); } catch (err) {}
    }
    if (state.isBusy) return;

    var inpUser = document.getElementById('inputUsername');
    var inpPass = document.getElementById('inputPassword');
    var username = inpUser ? safeTrim(inpUser.value) : '';
    var password = inpPass ? inpPass.value : '';

    if (!username) {
      showAlert('Please enter your username.', 'error');
      if (inpUser) {
        try { inpUser.focus(); } catch (err) {}
      }
      return;
    }
    if (!password) {
      showAlert('Please enter your password.', 'error');
      if (inpPass) {
        try { inpPass.focus(); } catch (err) {}
      }
      return;
    }

    state.isBusy = true;
    showAlert('Authenticating ' + username.toUpperCase() + ' with Active Directory...', 'info');

    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;

    apiRequest('POST', '/api/auth/login', {
      username: username,
      password: password,
      printerCode: stationCode,
      printerIp: stationIp
    }, function (status, data) {
      state.isBusy = false;
      if (status === 200 && data && data.authenticated) {
        if (inpPass) inpPass.value = '';
        onLoginSuccess(data);
      } else {
        var msg = (data && data.message) ? data.message : 'Invalid credentials. Please verify your Active Directory username and password.';
        showAlert(msg, 'error');
      }
    });
  };

  function updateUserCounters(user) {
    if (!user) return;
    state.currentUser = user;
    var userPrintCount = document.getElementById('userPrintCount');
    if (userPrintCount) userPrintCount.textContent = (user.printCount != null) ? user.printCount : '0';
    var userCopyCount = document.getElementById('userCopyCount');
    if (userCopyCount) userCopyCount.textContent = (user.copyCount != null) ? user.copyCount : '0';
  }

  function onLoginSuccess(user) {
    state.currentUser = user;

    var displayName = user.displayName || user.username || 'User';
    if (userBadgeText) userBadgeText.textContent = displayName;
    if (userDeptText) userDeptText.textContent = user.department || 'Staff';
    updateUserCounters(user);

    if (userHeaderSection) userHeaderSection.style.display = 'block';
    if (deviceStatusHeader) deviceStatusHeader.style.display = 'none';

    showQueueScreen();
    startInactivityTimer();
    startHeartbeat();
    window.loadJobs();
  }

  window.handleLogout = function () {
    if (state.isBusy) return;
    state.isBusy = true;
    stopHeartbeat();
    stopInactivityTimer();
    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;
    apiRequest('POST', '/api/auth/logout', { printerCode: stationCode, printerIp: stationIp }, function () {
      state.isBusy = false;
      state.currentUser = null;
      state.jobs = [];
      state.selectedJobIds = {};

      if (userHeaderSection) userHeaderSection.style.display = 'none';
      if (deviceStatusHeader) deviceStatusHeader.style.display = 'block';

      showAuthScreen();
      showAlert('You have logged out successfully.', 'info');
    });
  };

  /* Open Native Toshiba Device Functions (Copy / Scan / Home) */
  window.handleOpenDeviceFunctions = function () {
    showAlert('Unlocking Photocopy functions on this Toshiba MFP...', 'info');

    // Notify backend that user opened device functions so copy tracking window is active
    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;
    try {
      apiRequest('POST', '/api/station/device-functions', { printerCode: stationCode, printerIp: stationIp }, function (status, data) {
        if (status === 200) {
          showAlert('Walk-up photocopy unlocked! Press [Copy] or use feeder.', 'success');
        }
        window.showCopySessionModal();
      });
    } catch (e) {
      window.showCopySessionModal();
    }

    // 1. Toshiba e-BRIDGE OpenPlatform injected system hooks
    try {
      if (window.topaccess && window.topaccess.system) {
        if (typeof window.topaccess.system.openHomeScreen === 'function') {
          window.topaccess.system.openHomeScreen();
          return;
        } else if (typeof window.topaccess.system.openCopyScreen === 'function') {
          window.topaccess.system.openCopyScreen();
          return;
        }
      }
    } catch (e) {}

    // 2. Check top/parent window hooks
    try {
      if (window.top && window.top.topaccess && window.top.topaccess.system && typeof window.top.topaccess.system.openHomeScreen === 'function') {
        window.top.topaccess.system.openHomeScreen();
        return;
      }
    } catch (e) {}

    // 3. In Toshiba e-BRIDGE Next EWB, window.close() / top.close() drops browser overlay to native UI
    try {
      if (window.close) {
        window.close();
      }
    } catch (e) {}
    try {
      if (window.top && window.top !== window && window.top.close) {
        window.top.close();
      }
    } catch (e) {}
    try {
      if (window.parent && window.parent !== window && window.parent.close) {
        window.parent.close();
      }
    } catch (e) {}
  };

  window.showCopySessionModal = function () {
    var modal = document.getElementById('copySessionModal');
    var usernameSpan = document.getElementById('copyModalUsername');
    if (usernameSpan && state.currentUser) {
      usernameSpan.textContent = (state.currentUser.fullName || state.currentUser.displayName || state.currentUser.username || 'User').toUpperCase();
    }
    if (modal) {
      modal.style.display = 'flex';
    }
  };

  window.hideCopySessionModal = function () {
    var modal = document.getElementById('copySessionModal');
    if (modal) {
      modal.style.display = 'none';
    }
    // Re-lock copy restriction on the Toshiba MFP if modal was dismissed
    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;
    apiRequest('POST', '/api/station/auto-lock', { printerCode: stationCode, printerIp: stationIp, reason: 'MODAL_DISMISSED' }, function () {});
  };

  window.handleFinishCopying = function () {
    window.hideCopySessionModal();
    window.emergencyLockAndLogout('FINISH_COPYING_BUTTON');
  };

  function showAuthScreen() {
    screenAuth.className = 'pc-screen pc-auth-screen pc-active';
    screenQueue.className = 'pc-screen pc-queue-screen';
    state.pinBuffer = '';
    refreshPinDisplay();
  }

  function showQueueScreen() {
    screenAuth.className = 'pc-screen pc-auth-screen';
    screenQueue.className = 'pc-screen pc-queue-screen pc-active';
  }

  /* ------------------------------------------------------------------ */
  /* Print Queue Management                                              */
  /* ------------------------------------------------------------------ */
  window.loadJobs = function () {
    showAlert('Loading held print jobs...', 'info');
    apiRequest('GET', '/api/jobs', null, function (status, data) {
      if (status === 200 && data) {
        state.jobs = data;
        state.selectedJobIds = {};
        try {
          renderJobs();
          hideAlert();
        } catch (e) {
          showAlert('Render error: ' + (e.message || e), 'error');
        }
      } else if (status === 401) {
        window.handleLogout();
      } else {
        showAlert('Unable to load print queue (Status ' + status + ').', 'error');
      }
    });
  };

  function renderJobs() {
    if (!jobListTableBody) return;
    jobListTableBody.innerHTML = '';
    var list = state.jobs || [];

    if (list.length === 0) {
      if (emptyQueueMessage) emptyQueueMessage.style.display = 'block';
      refreshDockAndStats();
      return;
    }

    if (emptyQueueMessage) emptyQueueMessage.style.display = 'none';

    for (var i = 0; i < list.length; i++) {
      var job = list[i];
      var isSelected = !!state.selectedJobIds[job.id];

      var tr = document.createElement('tr');
      tr.className = isSelected ? 'pc-selected' : '';
      tr.setAttribute('data-id', job.id);

      // Tapping row selects/deselects
      tr.onclick = (function (id) {
        return function () { toggleJobSelection(id); };
      })(job.id);

      var timeStr = 'Today';
      if (job.submittedAt) {
        try {
          var d = new Date(job.submittedAt);
          timeStr = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) + ' | ' + d.toLocaleDateString();
        } catch (e) {}
      }

      var html =
        '<td style="text-align:center;">' +
          '<input type="checkbox" class="pc-checkbox-touch" ' + (isSelected ? 'checked' : '') + ' onclick="event.stopPropagation(); window.toggleJobSelection(\'' + job.id + '\');">' +
        '</td>' +
        '<td>' +
          '<div class="pc-doc-title" title="' + esc(job.documentName) + '">' + esc(job.documentName) + '</div>' +
        '</td>' +
        '<td class="pc-col-hide-sm" style="color:#5E678A; font-size:11.5px;">' + timeStr + '</td>' +
        '<td style="text-align:center; font-weight:bold;">' + job.pageCount + ' ' + (job.pageCount === 1 ? 'pg' : 'pgs') + '</td>' +
        '<td class="pc-col-hide-sm">' +
          '<span class="pc-attr-tag">' + (job.color ? 'Color' : 'B&W') + '</span>' +
          '<span class="pc-attr-tag">' + (job.duplex ? '2-Sided' : '1-Sided') + '</span>' +
        '</td>' +
        '<td class="pc-col-hide-sm" style="text-align:right; font-weight:bold; color:#242C6B;">LKR 0.00</td>' +
        '<td style="text-align:right; padding-right:12px;">' +
          '<span class="pc-badge-held">' + esc(job.status || 'HELD') + '</span>' +
        '</td>';

      tr.innerHTML = html;
      jobListTableBody.appendChild(tr);
    }

    refreshDockAndStats();
  }

  function toggleJobSelection(id) {
    if (state.selectedJobIds[id]) {
      delete state.selectedJobIds[id];
    } else {
      state.selectedJobIds[id] = true;
    }
    renderJobs();
  }
  window.toggleJobSelection = toggleJobSelection;

  window.toggleSelectAllCheckbox = function (isChecked) {
    state.selectedJobIds = {};
    if (isChecked) {
      for (var i = 0; i < state.jobs.length; i++) {
        state.selectedJobIds[state.jobs[i].id] = true;
      }
    }
    renderJobs();
  };

  function refreshDockAndStats() {
    var totalCount = state.jobs ? state.jobs.length : 0;
    var selectedIds = Object.keys(state.selectedJobIds);
    var selectedCount = selectedIds.length;

    var selectedPages = 0;
    for (var i = 0; i < totalCount; i++) {
      if (state.selectedJobIds[state.jobs[i].id]) {
        selectedPages += (state.jobs[i].pageCount || 1);
      }
    }

    if (queueCountBadge) queueCountBadge.textContent = '(' + totalCount + ')';
    if (printAllCountText) printAllCountText.textContent = '(' + totalCount + ')';

    if (selectAllCheckbox) {
      selectAllCheckbox.checked = (selectedCount === totalCount && totalCount > 0);
    }

    if (selectedSummaryText) {
      selectedSummaryText.textContent = selectedCount + ' of ' + totalCount + ' selected';
    }
    if (selectedPagesText) {
      selectedPagesText.textContent = selectedPages + ' ' + (selectedPages === 1 ? 'pg' : 'pgs');
    }
    if (selectedCostText) {
      selectedCostText.textContent = 'LKR 0.00';
    }

    if (releaseBtn) {
      if (selectedCount > 0) {
        releaseBtn.className = 'pc-btn-dock-print';
        releaseBtn.textContent = 'Print Selected (' + selectedCount + ')';
      } else {
        releaseBtn.className = 'pc-btn-dock-print pc-btn-disabled';
        releaseBtn.textContent = 'Print Selected';
      }
    }

    if (cancelBtn) {
      if (selectedCount > 0) {
        cancelBtn.className = 'pc-btn-dock-cancel';
        cancelBtn.textContent = 'Delete (' + selectedCount + ')';
      } else {
        cancelBtn.className = 'pc-btn-dock-cancel pc-btn-disabled';
        cancelBtn.textContent = 'Delete';
      }
    }

    if (printAllBtn) {
      if (totalCount > 0) {
        printAllBtn.className = 'pc-btn-print-all';
      } else {
        printAllBtn.className = 'pc-btn-print-all pc-btn-disabled';
      }
    }
  }

  /* ------------------------------------------------------------------ */
  /* PaperCut 1-Tap "Print All"                                         */
  /* ------------------------------------------------------------------ */
  window.printAllJobs = function () {
    if (!state.jobs || state.jobs.length === 0 || state.isBusy) return;

    state.selectedJobIds = {};
    for (var i = 0; i < state.jobs.length; i++) {
      state.selectedJobIds[state.jobs[i].id] = true;
    }

    window.releaseSelectedJobs();
  };

  /* ------------------------------------------------------------------ */
  /* Release Selected Jobs                                              */
  /* ------------------------------------------------------------------ */
  window.releaseSelectedJobs = function () {
    var ids = Object.keys(state.selectedJobIds);
    if (ids.length === 0 || state.isBusy) return;

    state.isBusy = true;
    showAlert('Releasing ' + ids.length + ' document(s) to Toshiba printer...', 'info');

    var successCount = 0;
    var failCount = 0;
    var completed = 0;

    for (var i = 0; i < ids.length; i++) {
      (function (jobId) {
        var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
        apiRequest('POST', '/api/jobs/' + jobId + '/release', { printerCode: stationCode }, function (status, data) {
          if (status === 200 && data && data.success) {
            successCount++;
          } else {
            failCount++;
          }
          completed++;

          if (completed === ids.length) {
            state.isBusy = false;
            var summary = successCount + ' document(s) printed at ' + ((state.station && state.station.name) ? state.station.name : 'Toshiba MFP') + '!';
            if (failCount > 0) {
              summary += ' (' + failCount + ' failed)';
            }
            showAlert(summary, successCount > 0 ? 'success' : 'error');
            window.loadJobs();
            apiRequest('GET', '/api/auth/current', null, function (s, u) {
              if (s === 200 && u) updateUserCounters(u);
            });
          }
        });
      })(ids[i]);
    }
  };

  /* ------------------------------------------------------------------ */
  /* Cancel Selected Jobs                                               */
  /* ------------------------------------------------------------------ */
  window.cancelSelectedJobs = function () {
    var ids = Object.keys(state.selectedJobIds);
    if (ids.length === 0 || state.isBusy) return;

    state.isBusy = true;
    showAlert('Deleting selected jobs from queue...', 'info');

    var completed = 0;
    for (var i = 0; i < ids.length; i++) {
      (function (jobId) {
        apiRequest('DELETE', '/api/jobs/' + jobId, null, function () {
          completed++;
          if (completed === ids.length) {
            state.isBusy = false;
            showAlert('Deleted ' + ids.length + ' job(s) from secure queue.', 'info');
            window.loadJobs();
          }
        });
      })(ids[i]);
    }
  };

  /* ------------------------------------------------------------------ */
  /* Inactivity Logout Timer                                            */
  /* ------------------------------------------------------------------ */
  function startInactivityTimer() {
    stopInactivityTimer();
    state.inactivitySeconds = 60;
    updateTimerText();

    state.timerInterval = setInterval(function () {
      state.inactivitySeconds--;
      updateTimerText();

      if (state.inactivitySeconds <= 0) {
        stopInactivityTimer();
        showAlert('Session timed out due to inactivity. Device locked.', 'error');
        window.emergencyLockAndLogout('INACTIVITY_TIMEOUT');
      }
    }, 1000);
  }

  function stopInactivityTimer() {
    if (state.timerInterval) {
      clearInterval(state.timerInterval);
      state.timerInterval = null;
    }
  }

  function resetInactivityTimer() {
    if (state.currentUser) {
      state.inactivitySeconds = 60;
      updateTimerText();
    }
  }

  function updateTimerText() {
    if (timerCountdown) {
      timerCountdown.textContent = '(' + state.inactivitySeconds + 's)';
    }
  }

  /* ------------------------------------------------------------------ */
  /* Alert Banners                                                      */
  /* ------------------------------------------------------------------ */
  function showAlert(msg, type) {
    if (!alertBox) return;
    alertBox.className = 'pc-alert-strip pc-alert-' + (type || 'info');
    alertBox.textContent = msg;
  }

  function hideAlert() {
    if (!alertBox) return;
    alertBox.className = 'pc-alert-strip';
    alertBox.textContent = '';
  }

  function esc(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

})();
