/* ==========================================================================
   RR Secure Print - Toshiba EWB Controller v10 (STRICTLY NO ICONS)
   Pure ES5 Vanilla JS - NO Font Dependencies, NO Emojis, NO Icons
   Ultra Lightweight & Instant Render on Toshiba e-STUDIO MFP
   ========================================================================== */

(function () {
  'use strict';

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

    // Wake / sleep / reconnect handlers (auto-heal when waking from energy saver)
    if (window.addEventListener) {
      window.addEventListener('online', onWakeOrReconnect);
      window.addEventListener('focus', onWakeOrReconnect);
      window.addEventListener('pageshow', onWakeOrReconnect);
      if (document.addEventListener) {
        document.addEventListener('visibilitychange', function () {
          if (document.visibilityState === 'visible') {
            onWakeOrReconnect();
          }
        });
      }
    }

    // Check existing session
    checkCurrentSession();
  };

  function onWakeOrReconnect() {
    sessionRetryCount = 0;
    checkCurrentSession();
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
        callback(xhr.status, json);
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

    apiRequest('POST', '/api/auth/login', { pin: state.pinBuffer }, function (status, data) {
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

    apiRequest('POST', '/api/auth/login', { username: username, pin: pin }, function (status, data) {
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

  function onLoginSuccess(user) {
    state.currentUser = user;

    var displayName = user.displayName || user.username || 'User';
    if (userBadgeText) userBadgeText.textContent = displayName;
    if (userDeptText) userDeptText.textContent = user.department || 'Staff';

    if (userHeaderSection) userHeaderSection.style.display = 'block';
    if (deviceStatusHeader) deviceStatusHeader.style.display = 'none';

    showQueueScreen();
    startInactivityTimer();
    window.loadJobs();
  }

  window.handleLogout = function () {
    if (state.isBusy) return;
    state.isBusy = true;
    apiRequest('POST', '/api/auth/logout', null, function () {
      state.isBusy = false;
      state.currentUser = null;
      state.jobs = [];
      state.selectedJobIds = {};
      stopInactivityTimer();

      if (userHeaderSection) userHeaderSection.style.display = 'none';
      if (deviceStatusHeader) deviceStatusHeader.style.display = 'block';

      showAuthScreen();
      showAlert('You have logged out successfully.', 'info');
    });
  };

  /* Open Native Toshiba Device Functions (Copy / Scan / Home) */
  window.handleOpenDeviceFunctions = function () {
    showAlert('Exiting to Toshiba Device Functions (Copy / Scan)...', 'info');
    // 1. In Toshiba e-BRIDGE Next Open Platform EWB, window.close() drops to native UI
    try {
      if (window.close) {
        window.close();
      }
    } catch (e) {}
    // 2. Toshiba EWB topaccess system hook
    try {
      if (window.topaccess && window.topaccess.system && typeof window.topaccess.system.openHomeScreen === 'function') {
        window.topaccess.system.openHomeScreen();
      }
    } catch (e) {}
    try {
      if (window.parent && window.parent.close) {
        window.parent.close();
      }
    } catch (e) {}
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
          '<div class="pc-doc-title">' + esc(job.documentName) + '</div>' +
        '</td>' +
        '<td style="color:#5E678A; font-size:12px;">' + timeStr + '</td>' +
        '<td style="text-align:center; font-weight:bold;">' + job.pageCount + ' ' + (job.pageCount === 1 ? 'page' : 'pgs') + '</td>' +
        '<td>' +
          '<span class="pc-attr-tag">' + (job.color ? 'Color' : 'B&W') + '</span>' +
          '<span class="pc-attr-tag">' + (job.duplex ? '2-Sided' : '1-Sided') + '</span>' +
        '</td>' +
        '<td style="text-align:right; font-weight:bold; color:#242C6B;">LKR 0.00</td>' +
        '<td style="text-align:right; padding-right:16px;">' +
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
      selectedSummaryText.textContent = selectedCount + ' of ' + totalCount + ' jobs selected';
    }
    if (selectedPagesText) {
      selectedPagesText.textContent = selectedPages + ' ' + (selectedPages === 1 ? 'page' : 'pages');
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
        cancelBtn.textContent = 'Delete Selected (' + selectedCount + ')';
      } else {
        cancelBtn.className = 'pc-btn-dock-cancel pc-btn-disabled';
        cancelBtn.textContent = 'Delete Selected';
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
        apiRequest('POST', '/api/jobs/' + jobId + '/release', { printerCode: 'TOSHIBA-3518A-HQ' }, function (status, data) {
          if (status === 200 && data && data.success) {
            successCount++;
          } else {
            failCount++;
          }
          completed++;

          if (completed === ids.length) {
            state.isBusy = false;
            var summary = successCount + ' document(s) sent to Toshiba MFP!';
            if (failCount > 0) {
              summary += ' (' + failCount + ' failed)';
            }
            showAlert(summary, successCount > 0 ? 'success' : 'error');
            window.loadJobs();
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
    state.inactivitySeconds = 120;
    updateTimerText();

    state.timerInterval = setInterval(function () {
      state.inactivitySeconds--;
      updateTimerText();

      if (state.inactivitySeconds <= 0) {
        stopInactivityTimer();
        showAlert('Session timed out due to inactivity.', 'error');
        window.handleLogout();
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
      state.inactivitySeconds = 120;
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
