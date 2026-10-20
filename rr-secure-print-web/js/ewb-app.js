/* ==========================================================================
   RR Secure Print — Toshiba e-STUDIO 3518A EWB Touchscreen Controller
   Engine: Pure ES5 Vanilla JavaScript (Strictly No ES6+, No Arrow Functions,
           No Fetch API, No Let/Const, No Promises)
   ========================================================================== */

(function () {
  'use strict';

  // Global State
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
  var pinDisplay = null;
  var alertBox = null;
  var screenAuth = null;
  var screenQueue = null;
  var userBadgeText = null;
  var timerDisplay = null;
  var jobListContainer = null;
  var releaseBtn = null;
  var cancelBtn = null;
  var selectAllBtn = null;

  // Initialize on Window Load
  window.onload = function () {
    pinDisplay = document.getElementById('pinDisplay');
    alertBox = document.getElementById('alertBox');
    screenAuth = document.getElementById('screenAuth');
    screenQueue = document.getElementById('screenQueue');
    userBadgeText = document.getElementById('userBadgeText');
    timerDisplay = document.getElementById('timerDisplay');
    jobListContainer = document.getElementById('jobListContainer');
    releaseBtn = document.getElementById('releaseBtn');
    cancelBtn = document.getElementById('cancelBtn');
    selectAllBtn = document.getElementById('selectAllBtn');

    // Reset touch activity timer on any screen tap
    document.body.onmousedown = resetInactivityTimer;
    if (window.ontouchstart !== undefined) {
      document.body.ontouchstart = resetInactivityTimer;
    }

    // Check if session already exists
    checkCurrentSession();
  };

  // -------------------------------------------------------------------------
  // REST API Client via Standard XMLHttpRequest (ES5 Compatible)
  // -------------------------------------------------------------------------
  function apiRequest(method, url, data, callback) {
    var xhr = new XMLHttpRequest();
    xhr.open(method, url, true);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');

    xhr.onreadystatechange = function () {
      if (xhr.readyState === 4) {
        var responseJson = null;
        if (xhr.responseText && xhr.responseText.length > 0) {
          try {
            responseJson = JSON.parse(xhr.responseText);
          } catch (e) {
            responseJson = { message: xhr.responseText };
          }
        }
        callback(xhr.status, responseJson);
      }
    };

    if (data) {
      xhr.send(JSON.stringify(data));
    } else {
      xhr.send();
    }
  }

  // -------------------------------------------------------------------------
  // Session & Authentication
  // -------------------------------------------------------------------------
  function checkCurrentSession() {
    apiRequest('GET', '/api/auth/current', null, function (status, data) {
      if (status === 200 && data && data.authenticated) {
        onLoginSuccess(data);
      } else {
        showAuthScreen();
      }
    });
  }

  window.handleKeypad = function (val) {
    if (state.isBusy) return;

    if (val === 'CLEAR') {
      state.pinBuffer = '';
    } else if (val === 'ENTER') {
      submitPinLogin();
      return;
    } else {
      if (state.pinBuffer.length < 8) {
        state.pinBuffer += val;
      }
    }
    updatePinDisplay();
  };

  function updatePinDisplay() {
    var masked = '';
    for (var i = 0; i < state.pinBuffer.length; i++) {
      masked += '● ';
    }
    pinDisplay.innerHTML = masked || 'ENTER PIN';
  }

  function submitPinLogin() {
    if (!state.pinBuffer || state.pinBuffer.length === 0) {
      showAlert('Please enter your 4-digit PIN code.', 'error');
      return;
    }

    state.isBusy = true;
    showAlert('Authenticating...', 'info');

    var payload = { pin: state.pinBuffer };
    apiRequest('POST', '/api/auth/login', payload, function (status, data) {
      state.isBusy = false;
      state.pinBuffer = '';
      updatePinDisplay();

      if (status === 200 && data.authenticated) {
        onLoginSuccess(data);
      } else {
        var msg = (data && data.message) ? data.message : 'Invalid PIN code. Access denied.';
        showAlert(msg, 'error');
      }
    });
  }

  window.quickDemoLogin = function (username, pin) {
    state.pinBuffer = pin;
    submitPinLogin();
  };

  function onLoginSuccess(user) {
    state.currentUser = user;
    userBadgeText.innerHTML = (user.displayName || user.username) + ' (' + (user.department || 'Staff') + ')';
    showQueueScreen();
    startSessionTimer();
    loadJobs();
  }

  window.handleLogout = function () {
    if (state.isBusy) return;
    state.isBusy = true;
    apiRequest('POST', '/api/auth/logout', null, function () {
      state.isBusy = false;
      state.currentUser = null;
      state.jobs = [];
      state.selectedJobIds = {};
      stopSessionTimer();
      showAuthScreen();
      showAlert('You have been logged out securely.', 'info');
    });
  };

  // -------------------------------------------------------------------------
  // Screen Switching
  // -------------------------------------------------------------------------
  function showAuthScreen() {
    screenAuth.className = 'screen-panel screen-active';
    screenQueue.className = 'screen-panel';
    state.pinBuffer = '';
    updatePinDisplay();
  }

  function showQueueScreen() {
    screenAuth.className = 'screen-panel';
    screenQueue.className = 'screen-panel screen-active';
  }

  // -------------------------------------------------------------------------
  // Pull-Print Jobs Management
  // -------------------------------------------------------------------------
  window.loadJobs = function () {
    showAlert('Loading held print jobs...', 'info');
    apiRequest('GET', '/api/jobs', null, function (status, data) {
      if (status === 200 && data) {
        state.jobs = data;
        state.selectedJobIds = {};
        renderJobList();
        hideAlert();
      } else if (status === 401) {
        handleLogout();
      } else {
        showAlert('Unable to load jobs from server.', 'error');
      }
    });
  };

  function renderJobList() {
    jobListContainer.innerHTML = '';
    var list = state.jobs;

    if (!list || list.length === 0) {
      jobListContainer.innerHTML =
        '<div class="card-box empty-queue-box">' +
          '<div class="empty-queue-icon">📄</div>' +
          '<h3>No Pending Print Jobs Found</h3>' +
          '<p>Send jobs from your workstation to "RR Secure Pull Print" to release them here.</p>' +
        '</div>';
      updateActionButtons();
      return;
    }

    for (var i = 0; i < list.length; i++) {
      var job = list[i];
      var isSelected = !!state.selectedJobIds[job.id];

      var itemDiv = document.createElement('div');
      itemDiv.className = 'job-item' + (isSelected ? ' selected' : '');
      itemDiv.setAttribute('data-id', job.id);
      itemDiv.onclick = (function (id) {
        return function () { toggleJobSelection(id); };
      })(job.id);

      var simBadge = job.simulated ? ' <span style="color:#ffaa44;font-size:12px;">[SIMULATED]</span>' : '';

      var html =
        '<div class="job-checkbox-col">' +
          '<input type="checkbox" class="job-checkbox-touch" ' + (isSelected ? 'checked' : '') + ' onclick="event.stopPropagation(); window.toggleJobSelection(\'' + job.id + '\');">' +
        '</div>' +
        '<div class="job-details-col">' +
          '<div class="job-name">' + escapeHtml(job.documentName) + simBadge + '</div>' +
          '<div class="job-meta-row">' +
            '<span class="job-meta-tag">' + job.pageCount + ' Pages</span>' +
            '<span class="job-meta-tag">' + (job.color ? 'Color' : 'B&W') + '</span>' +
            '<span class="job-meta-tag">' + (job.duplex ? '2-Sided (Duplex)' : '1-Sided') + '</span>' +
            '<span class="job-meta-tag">' + job.formattedFileSize + '</span>' +
            '<span class="job-meta-tag">Printer: ' + escapeHtml(job.targetPrinterCode) + '</span>' +
          '</div>' +
        '</div>' +
        '<div class="job-status-col">' +
          '<span class="status-badge badge-held">' + job.status + '</span>' +
        '</div>';

      itemDiv.innerHTML = html;
      jobListContainer.appendChild(itemDiv);
    }

    updateActionButtons();
  }

  window.toggleJobSelection = function (jobId) {
    if (state.selectedJobIds[jobId]) {
      delete state.selectedJobIds[jobId];
    } else {
      state.selectedJobIds[jobId] = true;
    }
    renderJobList();
  };

  window.toggleSelectAll = function () {
    var count = 0;
    for (var k in state.selectedJobIds) {
      if (state.selectedJobIds.hasOwnProperty(k)) count++;
    }

    if (count === state.jobs.length) {
      state.selectedJobIds = {};
    } else {
      state.selectedJobIds = {};
      for (var i = 0; i < state.jobs.length; i++) {
        state.selectedJobIds[state.jobs[i].id] = true;
      }
    }
    renderJobList();
  };

  function updateActionButtons() {
    var count = 0;
    for (var k in state.selectedJobIds) {
      if (state.selectedJobIds.hasOwnProperty(k)) count++;
    }

    if (count > 0) {
      releaseBtn.className = 'btn-touch btn-primary-release';
      releaseBtn.innerHTML = 'Release Selected (' + count + ')';
      cancelBtn.className = 'btn-touch btn-danger-cancel';
      cancelBtn.innerHTML = 'Cancel Selected (' + count + ')';
    } else {
      releaseBtn.className = 'btn-touch btn-primary-release btn-disabled';
      releaseBtn.innerHTML = 'Release Selected';
      cancelBtn.className = 'btn-touch btn-danger-cancel btn-disabled';
      cancelBtn.innerHTML = 'Cancel Selected';
    }

    if (selectAllBtn) {
      if (state.jobs.length > 0 && count === state.jobs.length) {
        selectAllBtn.innerHTML = 'Deselect All';
      } else {
        selectAllBtn.innerHTML = 'Select All';
      }
    }
  }

  // -------------------------------------------------------------------------
  // Release & Cancel Operations (Concurrency-Guarded)
  // -------------------------------------------------------------------------
  window.releaseSelectedJobs = function () {
    var selectedIds = [];
    for (var id in state.selectedJobIds) {
      if (state.selectedJobIds.hasOwnProperty(id)) {
        selectedIds.push(id);
      }
    }

    if (selectedIds.length === 0 || state.isBusy) return;

    state.isBusy = true;
    showAlert('Releasing ' + selectedIds.length + ' document(s) to Toshiba queue...', 'info');

    var successCount = 0;
    var failCount = 0;
    var processed = 0;

    function processNext() {
      if (processed >= selectedIds.length) {
        state.isBusy = false;
        var summary = 'Released ' + successCount + ' document(s) successfully.';
        if (failCount > 0) {
          summary += ' (' + failCount + ' failed or already released)';
        }
        showAlert(summary, successCount > 0 ? 'simulated' : 'error');
        loadJobs();
        return;
      }

      var jobId = selectedIds[processed];
      processed++;

      var payload = { printerCode: 'TOSHIBA-3518A-HQ' };
      apiRequest('POST', '/api/jobs/' + jobId + '/release', payload, function (status, data) {
        if (status === 200 && data && data.success) {
          successCount++;
        } else {
          failCount++;
        }
        processNext();
      });
    }

    processNext();
  };

  window.cancelSelectedJobs = function () {
    var selectedIds = [];
    for (var id in state.selectedJobIds) {
      if (state.selectedJobIds.hasOwnProperty(id)) {
        selectedIds.push(id);
      }
    }

    if (selectedIds.length === 0 || state.isBusy) return;

    state.isBusy = true;
    showAlert('Cancelling selected jobs...', 'info');

    var processed = 0;

    function processNext() {
      if (processed >= selectedIds.length) {
        state.isBusy = false;
        showAlert('Cancelled selected jobs.', 'info');
        loadJobs();
        return;
      }

      var jobId = selectedIds[processed];
      processed++;

      apiRequest('DELETE', '/api/jobs/' + jobId, null, function () {
        processNext();
      });
    }

    processNext();
  };

  // -------------------------------------------------------------------------
  // Session Inactivity Timer (MFP Security Auto-Logout)
  // -------------------------------------------------------------------------
  function startSessionTimer() {
    stopSessionTimer();
    state.inactivitySeconds = 120;
    updateTimerText();

    state.timerInterval = setInterval(function () {
      state.inactivitySeconds--;
      updateTimerText();

      if (state.inactivitySeconds <= 0) {
        stopSessionTimer();
        showAlert('Session timed out due to inactivity.', 'error');
        handleLogout();
      }
    }, 1000);
  }

  function stopSessionTimer() {
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
    if (timerDisplay) {
      timerDisplay.innerHTML = 'Auto-Logout: ' + state.inactivitySeconds + 's';
    }
  }

  // -------------------------------------------------------------------------
  // Utilities & Alerts
  // -------------------------------------------------------------------------
  function showAlert(msg, type) {
    if (!alertBox) return;
    alertBox.style.display = 'block';
    alertBox.className = 'alert-box alert-' + (type || 'info');
    alertBox.innerHTML = msg;
  }

  function hideAlert() {
    if (!alertBox) return;
    alertBox.style.display = 'none';
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

})();
