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

  // Safe ES3/ES5 Object.keys Polyfill for embedded WebKit
  if (!Object.keys) {
    Object.keys = function (obj) {
      var keys = [];
      if (!obj) return keys;
      for (var prop in obj) {
        if (Object.prototype.hasOwnProperty.call(obj, prop)) {
          keys.push(prop);
        }
      }
      return keys;
    };
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
    isBusy: false,
    isNativeOperationActive: false
  };

  // DOM Elements
  var pinDisplay          = null;
  var alertBox            = null;
  var screenAuth          = null;
  var screenMenu          = null;
  var screenQueue         = null;
  var screenCopy          = null;
  var screenScan          = null;
  var userHeaderSection   = null;
  var deviceStatusHeader  = null;
  var userBadgeText       = null;
  var userDeptText        = null;
  var timerCountdown      = null;
  var queueCountBadge     = null;
  var menuPrintBadge      = null;
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
    screenMenu          = document.getElementById('screenMenu');
    screenQueue         = document.getElementById('screenQueue');
    screenCopy          = document.getElementById('screenCopy');
    screenScan          = document.getElementById('screenScan');
    userHeaderSection   = document.getElementById('userHeaderSection');
    deviceStatusHeader  = document.getElementById('deviceStatusHeader');
    userBadgeText       = document.getElementById('userBadgeText');
    userDeptText        = document.getElementById('userDeptText');
    timerCountdown      = document.getElementById('timerCountdown');
    queueCountBadge     = document.getElementById('queueCountBadge');
    menuPrintBadge      = document.getElementById('menuPrintBadge');
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

    // Immediate Hardware Sleep / Energy Saver / Wake Lock Handlers
    if (document.addEventListener) {
      document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
          if (state.isNativeOperationActive) {
            // Native Copy screen has opened on panel! Pre-restore DefaultScreen to EWB for sleep security
            sendCopyScreenOpenedBeacon();
          } else {
            window.emergencyLockAndLogout('HARDWARE_SLEEP_BUTTON');
          }
        } else if (document.visibilityState === 'visible') {
          // Machine woke up from sleep or returned to nexPrint
          // CRITICAL: Always lock and enforce PIN on wake-up!
          state.isNativeOperationActive = false;
          window.emergencyLockAndLogout('HARDWARE_WAKE_LOCK');
        }
      });
    }

    if (window.addEventListener) {
      window.addEventListener('pagehide', function () {
        if (!state.isNativeOperationActive) {
          window.emergencyLockAndLogout('PAGE_HIDE');
        }
      });
      window.addEventListener('blur', function () {
        if (state.isNativeOperationActive) {
          sendCopyScreenOpenedBeacon();
        }
      });
      window.addEventListener('focus', function () {
        if (!state.currentUser) {
          state.isNativeOperationActive = false;
          window.emergencyLockAndLogout('WINDOW_FOCUS_LOCK');
        }
      });
      window.addEventListener('online', onWakeOrReconnect);
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

    // Initialize physical MFP touchscreen display scaling
    initMfpScreenScale();

    // Initialize Card Reader USB HID Catcher
    setupCardCatcher();
  };


  /* ------------------------------------------------------------------ */
  /* Emergency Sleep Auto-Lock & Device Security                        */
  /* ------------------------------------------------------------------ */
  window.emergencyLockAndLogout = function (reason) {
    if (state.isNativeOperationActive &&
        (reason === 'WINDOW_BLUR_SLEEP' || reason === 'PAGE_HIDE')) {
      // User is actively transitioning to native Copy / Scan mode on physical panel
      return;
    }
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
    state.isNativeOperationActive = false;
    state.currentUser = null;
    state.pinBuffer = '';
    stopHeartbeat();
    stopInactivityTimer();
    window.hideCopySessionModal();
    showAuthScreen();
    showAlert('Device locked & secured. Enter PIN to start.', 'info');
  };

  function sendCopyScreenOpenedBeacon() {
    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;
    var payload = JSON.stringify({
      printerCode: stationCode,
      printerIp: stationIp
    });
    if (navigator.sendBeacon) {
      try {
        var blob = new Blob([payload], { type: 'application/json' });
        navigator.sendBeacon('/api/station/copy-screen-opened', blob);
      } catch (e) {}
    }
    try {
      if (window.fetch) {
        fetch('/api/station/copy-screen-opened', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: payload,
          keepalive: true
        });
      }
    } catch (e) {}
  }

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
  /* MFP Screen Scale & Viewport Adaptation Controller                 */
  /* Automatically optimizes layout, logo, and button sizes for        */
  /* Toshiba e-STUDIO MFP physical touch screens (1024x600 / 1280x800)  */
  /* ------------------------------------------------------------------ */
  function setRootScaleClass(cls) {
    var targets = [document.documentElement, document.body];
    for (var i = 0; i < targets.length; i++) {
      var el = targets[i];
      if (el) {
        var c = (el.className || '').replace(/\bmfp-scale-\S+/g, '').replace(/\s+/g, ' ');
        el.className = safeTrim(c + ' ' + cls);
      }
    }
  }

  function updateScaleButtonLabel(scaleName) {
    var btn = document.getElementById('mfpScaleBtn');
    if (!btn) return;
    if (scaleName === 'xl') {
      btn.textContent = '🖵 Size: XL';
    } else if (scaleName === 'normal') {
      btn.textContent = '🖵 Size: Standard';
    } else {
      btn.textContent = '🖵 Size: Large';
    }
  }

  window.applyMfpScale = function (scaleName) {
    if (scaleName !== 'xl' && scaleName !== 'normal') {
      scaleName = 'large';
    }
    setRootScaleClass('mfp-scale-' + scaleName);
    updateScaleButtonLabel(scaleName);
    try {
      localStorage.setItem('nexprint_mfp_scale', scaleName);
    } catch (e) {}
  };

  window.cycleMfpScale = function () {
    var saved = 'large';
    try {
      saved = localStorage.getItem('nexprint_mfp_scale') || 'large';
    } catch (e) {}

    var next = 'large';
    if (saved === 'large') next = 'xl';
    else if (saved === 'xl') next = 'normal';
    else if (saved === 'normal') next = 'large';

    window.applyMfpScale(next);
  };

  function initMfpScreenScale() {
    var saved = null;
    try {
      saved = localStorage.getItem('nexprint_mfp_scale');
    } catch (e) {}

    if (saved && (saved === 'normal' || saved === 'large' || saved === 'xl')) {
      window.applyMfpScale(saved);
      return;
    }

    // Default to clean standard scale ('normal') so 1024x600 and 1280x800 screens fit 100% without overflow
    window.applyMfpScale('normal');
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
        logoImg.style.maxHeight = '34px';
        logoImg.style.maxWidth = '160px';
        logoImg.style.objectFit = 'contain';
        logoImg.style.marginLeft = '4px';
        logoImg.style.display = 'inline-block';
      }
      if (defaultGroup) defaultGroup.style.display = 'none';
    } else {
      if (logoImg) {
        logoImg.src = '';
        logoImg.style.display = 'none';
      }
      if (defaultGroup) defaultGroup.style.display = 'inline-block';
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
        if (!state.currentUser && !state.isNativeOperationActive) {
          window.emergencyLockAndLogout('KIOSK_BOOT_LOCK');
        }
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
      sendDiagnostics();
    });
  }

  function sendDiagnostics() {
    try {
      var diag = {
        userAgent: navigator.userAgent,
        hasTopaccess: !!window.topaccess,
        topaccessKeys: window.topaccess ? Object.keys(window.topaccess) : [],
        systemKeys: (window.topaccess && window.topaccess.system) ? Object.keys(window.topaccess.system) : [],
        hasExternal: !!window.external
      };
      if (window.topaccess && window.topaccess.system) {
        var sm = {};
        for (var k in window.topaccess.system) {
          try { sm[k] = typeof window.topaccess.system[k]; } catch(e){}
        }
        diag.systemMethods = sm;
      }
      apiRequest('POST', '/api/station/diagnostics', diag, function () {});
    } catch (e) {}
  }

  window.handleSwitchStation = function (code) {
    if (!code) return;
    window.location.search = '?printer=' + encodeURIComponent(code);
  };

  function onWakeOrReconnect() {
    sessionRetryCount = 0;
    state.isNativeOperationActive = false;
    window.emergencyLockAndLogout('HARDWARE_WAKE_VERIFY');
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

  /* ------------------------------------------------------------------ */
  /* PaperCut MF RFID & NFC Card Reader HID Wedge Interceptor           */
  /* ------------------------------------------------------------------ */
  var cardKeystrokeBuffer = '';
  var cardLastKeyTime = 0;
  var cardDebounceTimer = null;
  state.pendingCardBadgeId = null;

  window.loginWithCard = function (badgeId) {
    if (!badgeId || state.isBusy) return;
    badgeId = safeTrim(badgeId);
    if (!badgeId) return;

    // Fail-safe: If 1-4 pure digits arrived while in PIN mode, it is a PIN entry, NOT an RFID card
    if (state.authMode === 'PIN' && /^\d{1,4}$/.test(badgeId)) {
      state.pinBuffer = badgeId;
      refreshPinDisplay();
      submitPin();
      return;
    }

    state.isBusy = true;
    showAlert('RFID / NFC Card detected (' + badgeId + '). Verifying...', 'info');

    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;

    apiRequest('POST', '/api/auth/login', {
      badgeId: badgeId,
      printerCode: stationCode,
      printerIp: stationIp
    }, function (status, data) {
      state.isBusy = false;
      if (status === 200 && data && data.authenticated) {
        state.pendingCardBadgeId = null;
        updateCardPromptUI();
        onLoginSuccess(data);
      } else if (data && data.unregisteredCard) {
        // PaperCut MF Self-Association workflow
        state.pendingCardBadgeId = data.badgeId || badgeId;
        updateCardPromptUI();
        window.switchAuthMode('PIN');
        showAlert('New card detected: ' + state.pendingCardBadgeId + '. Enter your PIN below to register.', 'info');
      } else {
        state.pendingCardBadgeId = null;
        updateCardPromptUI();
        showAlert((data && data.message) || 'Card verification failed.', 'error');
      }
    });
  };

  window.cancelCardAssociation = function () {
    state.pendingCardBadgeId = null;
    updateCardPromptUI();
    showAlert('Card registration cancelled.', 'info');
  };

  function updateCardPromptUI() {
    var assocBox = document.getElementById('cardAssociationPrompt');
    var badgeLabel = document.getElementById('detectedCardId');

    if (state.pendingCardBadgeId) {
      if (assocBox) assocBox.style.display = 'block';
      if (badgeLabel) badgeLabel.textContent = state.pendingCardBadgeId;
    } else {
      if (assocBox) assocBox.style.display = 'none';
      if (badgeLabel) badgeLabel.textContent = '';
    }
  }

  function updateRawKeyDebug(evtType, e) {
    var dbg = document.getElementById('rawKeyDebug');
    if (dbg) {
      var code = e ? (e.keyCode || e.which) : '';
      var k = e ? (e.key || '') : '';
      var act = document.activeElement ? (document.activeElement.id || document.activeElement.tagName) : 'none';
      var dispBuf = cardKeystrokeBuffer ? (cardKeystrokeBuffer.length > 8 ? cardKeystrokeBuffer.substring(0, 4) + '...' + cardKeystrokeBuffer.slice(-4) : cardKeystrokeBuffer) : '(empty)';
      dbg.textContent = 'READER I/O [' + evtType + ']: code=' + code + ' key=' + k + ' buf=' + dispBuf + ' [focus:' + act + ']';
      dbg.style.color = '#1D4ED8';
      dbg.style.borderColor = '#2563EB';
    }
  }

  function focusCardCatcher() {
    // In PIN / RFID mode, we NEVER focus any input field!
    // Focusing an input causes Toshiba EWB (and touch browsers) to pop up the native software keyboard (SIP).
    // Instead, we ensure all inputs are blurred so no virtual keyboard can ever appear.
    if (state.authMode === 'PIN') {
      var active = document.activeElement;
      if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
        try { active.blur(); } catch (err) {}
      }
      updateRawKeyDebug('STANDBY', null);
      return;
    }

    // In USER_PASS mode, only focus username if nothing else is focused
    if (state.authMode === 'USER_PASS') {
      var active = document.activeElement;
      if (!active || (active.id !== 'inputUsername' && active.id !== 'inputPassword')) {
        var inpUser = document.getElementById('inputUsername');
        if (inpUser) {
          try { inpUser.focus(); } catch (err) {}
        }
      }
    }
  }
  window.focusCardCatcher = focusCardCatcher;

  function setupCardCatcher() {
    focusCardCatcher();
    var catcher = document.getElementById('pcCardCatcher');
    if (catcher) {
      // Direct keyboard and input events on catcher
      catcher.addEventListener('keydown', handleCardKey, false);
      catcher.addEventListener('keypress', function (e) {
        var ch = String.fromCharCode(e.which || e.keyCode);
        if (ch) updateRawKeyDebug('CATCHER_PRESS', { keyCode: e.which || e.keyCode, key: ch });
      }, false);
      catcher.addEventListener('input', function () {
        var val = (catcher.value || '').trim();
        if (val) {
          // If in PIN mode and it's pure short digits, do NOT treat as card swipe!
          if (state.authMode === 'PIN' && /^\d{1,4}$/.test(val)) {
            catcher.value = '';
            return;
          }
          updateRawKeyDebug('INPUT', { keyCode: 0, key: val });
          cardKeystrokeBuffer = val;
          if (cardDebounceTimer) clearTimeout(cardDebounceTimer);
          cardDebounceTimer = setTimeout(function () {
            var autoCardId = (catcher.value || cardKeystrokeBuffer || '').trim();
            if (autoCardId && autoCardId.length >= 4 && !(/^\d{1,4}$/.test(autoCardId) && state.authMode === 'PIN')) {
              catcher.value = '';
              cardKeystrokeBuffer = '';
              window.loginWithCard(autoCardId);
            }
          }, 350);
        }
      });
      catcher.addEventListener('paste', function () {
        setTimeout(function () {
          var autoCardId = (catcher.value || '').trim();
          if (autoCardId && autoCardId.length >= 3) {
            catcher.value = '';
            cardKeystrokeBuffer = '';
            window.loginWithCard(autoCardId);
          }
        }, 50);
      });
    }

    // In PIN mode, dismiss any accidental native keyboard when screen is touched
    document.addEventListener('click', function (e) {
      if (state.authMode === 'PIN') {
        var active = document.activeElement;
        if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
          try { active.blur(); } catch (err) {}
        }
      }
    }, false);
    document.addEventListener('touchstart', function (e) {
      if (state.authMode === 'PIN') {
        var active = document.activeElement;
        if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
          try { active.blur(); } catch (err) {}
        }
      }
    }, false);
  }

  function handleCardKey(e) {
    if (e.repeat) return; // Prevent key bounce/repeats
    if (e._cardHandled) return;
    e._cardHandled = true;

    var authScreen = document.getElementById('screenAuth');
    if (!authScreen || (authScreen.style.display === 'none' && !authScreen.classList.contains('pc-active'))) {
      return;
    }

    var active = document.activeElement;
    if (active && (active.id === 'inputUsername' || active.id === 'inputPassword' || active.id === 'userUsername' || active.id === 'userPassword')) {
      return;
    }

    var keyCode = e.keyCode || e.which;
    var key = e.key;
    var catcher = document.getElementById('pcCardCatcher');

    // 1. Backspace: delete last PIN digit when in PIN mode
    if (keyCode === 8 || key === 'Backspace') {
      if (state.authMode === 'PIN') {
        if (e.preventDefault) e.preventDefault();
        if (state.pinBuffer && state.pinBuffer.length > 0) {
          state.pinBuffer = state.pinBuffer.slice(0, -1);
          refreshPinDisplay();
        }
        if (catcher) catcher.value = '';
        cardKeystrokeBuffer = '';
        updateRawKeyDebug('BACKSPACE', e);
        return;
      }
    }

    // 2. Escape or Delete: clear PIN
    if (keyCode === 27 || keyCode === 46 || key === 'Escape' || key === 'Delete') {
      if (state.authMode === 'PIN') {
        if (e.preventDefault) e.preventDefault();
        window.handleKeypad('CLEAR');
        if (catcher) catcher.value = '';
        cardKeystrokeBuffer = '';
        updateRawKeyDebug('CLEAR', e);
        return;
      }
    }

    // 3. Enter or Tab: submit card swipe if buffer has card ID, or submit PIN
    if (keyCode === 13 || keyCode === 9 || key === 'Enter' || key === 'Tab') {
      if (cardDebounceTimer) { clearTimeout(cardDebounceTimer); cardDebounceTimer = null; }

      var catcherVal = catcher ? (catcher.value || '').trim() : '';
      var candidateCard = (cardKeystrokeBuffer || catcherVal || '').replace(/[\r\n]/g, '').trim();

      // Measure stream speed to distinguish RFID card burst vs slow manual typing
      var streamDuration = (new Date().getTime()) - (window._cardStreamStartTime || 0);
      var streamCount = window._cardStreamCount || candidateCard.length || 1;
      var avgKeyInterval = streamCount > 1 ? (streamDuration / streamCount) : 999;
      var isFastRfidBurst = (avgKeyInterval < 120);

      // If buffer has card-length characters (>= 4 digits):
      // An RFID card is typically 5-16 digits OR arrived in high-speed burst (<120ms/char) OR contains non-digits
      if (candidateCard && candidateCard.length >= 4 && (isFastRfidBurst || candidateCard.length > 5 || !/^\d{4,5}$/.test(candidateCard))) {
        if (e.preventDefault) e.preventDefault();
        if (e.stopPropagation) e.stopPropagation();
        cardKeystrokeBuffer = '';
        if (catcher) catcher.value = '';
        state.pinBuffer = '';
        refreshPinDisplay();
        window._cardStreamCount = 0;
        window.loginWithCard(candidateCard);
        return;
      }

      // Otherwise, manual PIN submission
      if (state.authMode === 'PIN' && state.pinBuffer && state.pinBuffer.length > 0) {
        if (e.preventDefault) e.preventDefault();
        if (e.stopPropagation) e.stopPropagation();
        if (catcher) catcher.value = '';
        cardKeystrokeBuffer = '';
        window._cardStreamCount = 0;
        submitPin();
        return;
      }

      if (candidateCard && candidateCard.length >= 3) {
        if (e.preventDefault) e.preventDefault();
        if (e.stopPropagation) e.stopPropagation();
        cardKeystrokeBuffer = '';
        if (catcher) catcher.value = '';
        window.loginWithCard(candidateCard);
        return;
      }
      return;
    }

    // 4. Capture printable characters (Standard alphanumeric, Numpad, and Numpad without NumLock)
    var char = '';
    var isDigit = false;
    if (key && key.length === 1 && !e.ctrlKey && !e.metaKey) {
      char = key;
      isDigit = (key >= '0' && key <= '9');
    } else if (keyCode >= 48 && keyCode <= 57) { // 0-9
      char = String.fromCharCode(keyCode);
      isDigit = true;
    } else if (keyCode >= 96 && keyCode <= 105) { // Numpad 0-9
      char = String.fromCharCode(keyCode - 48);
      isDigit = true;
    } else if (keyCode >= 65 && keyCode <= 90) { // A-Z
      char = String.fromCharCode(keyCode);
    } else if (keyCode === 189 || keyCode === 109) { // Hyphen / dash
      char = '-';
    } else if (keyCode === 45) { // Numpad 0 (Insert when NumLock is OFF)
      char = '0'; isDigit = true;
    } else if (keyCode === 35) { // Numpad 1 (End when NumLock is OFF)
      char = '1'; isDigit = true;
    } else if (keyCode === 40) { // Numpad 2 (Down when NumLock is OFF)
      char = '2'; isDigit = true;
    } else if (keyCode === 34) { // Numpad 3 (PageDown when NumLock is OFF)
      char = '3'; isDigit = true;
    } else if (keyCode === 37) { // Numpad 4 (Left when NumLock is OFF)
      char = '4'; isDigit = true;
    } else if (keyCode === 12) { // Numpad 5 (Clear when NumLock is OFF)
      char = '5'; isDigit = true;
    } else if (keyCode === 39) { // Numpad 6 (Right when NumLock is OFF)
      char = '6'; isDigit = true;
    } else if (keyCode === 36) { // Numpad 7 (Home when NumLock is OFF)
      char = '7'; isDigit = true;
    } else if (keyCode === 38) { // Numpad 8 (Up when NumLock is OFF)
      char = '8'; isDigit = true;
    } else if (keyCode === 33) { // Numpad 9 (PageUp when NumLock is OFF)
      char = '9'; isDigit = true;
    }

    if (!char) return;

    var now = new Date().getTime();
    var timeSinceLastKey = now - cardLastKeyTime;
    cardLastKeyTime = now;

    // Track stream start and key counter
    if (timeSinceLastKey > 900) {
      cardKeystrokeBuffer = '';
      window._cardStreamStartTime = now;
      window._cardStreamCount = 0;
    }
    window._cardStreamCount = (window._cardStreamCount || 0) + 1;
    cardKeystrokeBuffer += char;
    updateRawKeyDebug('CHAR', { keyCode: keyCode, key: char });

    // Also mirror to PIN buffer only if user is manually typing slow single digits on physical keyboard
    if (state.authMode === 'PIN' && isDigit && timeSinceLastKey > 150) {
      if (state.pinBuffer.length < 10) {
        state.pinBuffer += char;
        refreshPinDisplay();
      }
    }

    if (cardDebounceTimer) clearTimeout(cardDebounceTimer);
    cardDebounceTimer = setTimeout(function () {
      var catcherVal = catcher ? (catcher.value || '').trim() : '';
      var autoCardId = (cardKeystrokeBuffer || catcherVal || '').replace(/[\r\n]/g, '').trim();
      var duration = (new Date().getTime()) - (window._cardStreamStartTime || 0);
      var count = window._cardStreamCount || autoCardId.length || 1;
      var avgInterval = count > 1 ? (duration / count) : 999;

      // Auto-trigger card login if length >= 4 AND (burst speed OR long ID OR non-digit)
      if (autoCardId && autoCardId.length >= 4 && (avgInterval < 120 || autoCardId.length > 5 || !/^\d{4,5}$/.test(autoCardId))) {
        cardKeystrokeBuffer = '';
        if (catcher) catcher.value = '';
        state.pinBuffer = '';
        refreshPinDisplay();
        window._cardStreamCount = 0;
        window.loginWithCard(autoCardId);
      }
    }, 280);
  }

  // Clean single listener binding on document or window
  if (document.addEventListener) {
    document.addEventListener('keydown', handleCardKey, false);
  } else if (window.addEventListener) {
    window.addEventListener('keydown', handleCardKey, false);
  }

  window.handleKeypad = function (val) {
    if (state.isBusy) return;
    // Dismiss any accidental native keyboard immediately
    var active = document.activeElement;
    if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
      try { active.blur(); } catch (e) {}
    }
    if (val === 'CLEAR') {
      state.pinBuffer = '';
      state.pendingCardBadgeId = null;
      updateCardPromptUI();
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

    var reqData = {
      pin: state.pinBuffer,
      printerCode: stationCode,
      printerIp: stationIp
    };
    if (state.pendingCardBadgeId && state.pendingCardBadgeId !== state.pinBuffer) {
      reqData.badgeId = state.pendingCardBadgeId;
    }

    apiRequest('POST', '/api/auth/login', reqData, function (status, data) {
      state.isBusy = false;
      state.pinBuffer = '';
      refreshPinDisplay();
      if (status === 200 && data && data.authenticated) {
        var wasCardLinked = !!state.pendingCardBadgeId;
        state.pendingCardBadgeId = null;
        updateCardPromptUI();
        onLoginSuccess(data);
        if (wasCardLinked) {
          showAlert('Card successfully linked to ' + data.displayName + '!', 'success');
        }
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
      if (tabPin) tabPin.className = 'pc-auth-pill';
      if (tabUserPass) tabUserPass.className = 'pc-auth-pill active';
      if (viewPin) viewPin.style.display = 'none';
      if (viewUserPass) viewUserPass.style.display = 'block';
      var inpUser = document.getElementById('inputUsername');
      if (inpUser) {
        setTimeout(function () {
          try { inpUser.focus(); } catch (err) {}
        }, 120);
      }
    } else {
      if (tabPin) tabPin.className = 'pc-auth-pill active';
      if (tabUserPass) tabUserPass.className = 'pc-auth-pill';
      if (viewPin) viewPin.style.display = 'block';
      if (viewUserPass) viewUserPass.style.display = 'none';
      var active = document.activeElement;
      if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
        try { active.blur(); } catch (err) {}
      }
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

    var reqData = {
      username: username,
      password: password,
      printerCode: stationCode,
      printerIp: stationIp
    };
    if (state.pendingCardBadgeId) {
      reqData.badgeId = state.pendingCardBadgeId;
    }

    apiRequest('POST', '/api/auth/login', reqData, function (status, data) {
      state.isBusy = false;
      if (status === 200 && data && data.authenticated) {
        var wasCardLinked = !!state.pendingCardBadgeId;
        state.pendingCardBadgeId = null;
        updateCardPromptUI();
        if (inpPass) inpPass.value = '';
        onLoginSuccess(data);
        if (wasCardLinked) {
          showAlert('Card successfully linked to ' + data.displayName + '!', 'success');
        }
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
    var userScanCount = document.getElementById('userScanCount');
    if (userScanCount) userScanCount.textContent = (user.scanCount != null) ? user.scanCount : '0';
    var copyStationCount = document.getElementById('copyStationCount');
    if (copyStationCount) copyStationCount.textContent = (user.copyCount != null) ? user.copyCount : '0';
    var scanStationCount = document.getElementById('scanStationCount');
    if (scanStationCount) scanStationCount.textContent = (user.scanCount != null) ? user.scanCount : '0';
  }

  function onLoginSuccess(user) {
    state.currentUser = user;

    var displayName = user.displayName || user.username || 'User';
    if (userBadgeText) userBadgeText.textContent = displayName;
    if (userDeptText) userDeptText.textContent = user.department || 'Staff';
    updateUserCounters(user);

    if (userHeaderSection) userHeaderSection.style.display = 'block';
    if (deviceStatusHeader) deviceStatusHeader.style.display = 'none';

    // Show PaperCut MF 3-Action Launcher upon successful login
    window.showMenuScreen();
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

  /* =========================================================================
     PaperCut MF Touch Screen Switcher (Auth -> Apps Menu -> Print Queue / Copy / Scan)
     ========================================================================= */
  function showAuthScreen() {
    screenAuth.className = 'pc-screen pc-auth-screen pc-active';
    if (screenMenu) screenMenu.className = 'pc-screen pc-menu-screen';
    if (screenQueue) screenQueue.className = 'pc-screen pc-queue-screen';
    if (screenCopy) screenCopy.className = 'pc-screen pc-copy-screen';
    if (screenScan) screenScan.className = 'pc-screen pc-scan-screen';
    state.pinBuffer = '';
    state.pendingCardBadgeId = null;
    window.switchAuthMode('PIN');
    updateCardPromptUI();
    refreshPinDisplay();
    focusCardCatcher();
  }

  window.showMenuScreen = function () {
    screenAuth.className = 'pc-screen pc-auth-screen';
    if (screenMenu) screenMenu.className = 'pc-screen pc-menu-screen pc-active';
    if (screenQueue) screenQueue.className = 'pc-screen pc-queue-screen';
    if (screenCopy) screenCopy.className = 'pc-screen pc-copy-screen';
    if (screenScan) screenScan.className = 'pc-screen pc-scan-screen';
    updateMenuJobCount();
  };

  window.showQueueScreen = function () {
    screenAuth.className = 'pc-screen pc-auth-screen';
    if (screenMenu) screenMenu.className = 'pc-screen pc-menu-screen';
    if (screenQueue) screenQueue.className = 'pc-screen pc-queue-screen pc-active';
    if (screenCopy) screenCopy.className = 'pc-screen pc-copy-screen';
    if (screenScan) screenScan.className = 'pc-screen pc-scan-screen';
  };

  window.showCopyScreen = function () {
    screenAuth.className = 'pc-screen pc-auth-screen';
    if (screenMenu) screenMenu.className = 'pc-screen pc-menu-screen';
    if (screenQueue) screenQueue.className = 'pc-screen pc-queue-screen';
    if (screenCopy) screenCopy.className = 'pc-screen pc-copy-screen pc-active';
    if (screenScan) screenScan.className = 'pc-screen pc-scan-screen';
  };

  window.showScanScreen = function () {
    screenAuth.className = 'pc-screen pc-auth-screen';
    if (screenMenu) screenMenu.className = 'pc-screen pc-menu-screen';
    if (screenQueue) screenQueue.className = 'pc-screen pc-queue-screen';
    if (screenCopy) screenCopy.className = 'pc-screen pc-copy-screen';
    if (screenScan) screenScan.className = 'pc-screen pc-scan-screen pc-active';
  };

  /* Action Handlers for the 3 Application Launcher Tiles */
  window.openPrintRelease = function () {
    window.showQueueScreen();
  };

  window.openCopyFunction = function () {
    state.isNativeOperationActive = true;
    state.inactivitySeconds = 30;
    updateTimerText();

    showAlert('Copier ready! Press [HOME] or [CLEAR] on panel', 'success');

    // Trigger native unlock & CWS DefaultScreen = Copy
    unlockDeviceTarget('COPY');
  };

  window.openScanFunction = function () {
    state.isNativeOperationActive = true;
    state.inactivitySeconds = 30;
    updateTimerText();

    showAlert('Scanner ready! Press [HOME] or [CLEAR] on panel', 'success');

    // Trigger native unlock & CWS DefaultScreen = Scan
    unlockDeviceTarget('SCAN');
    window.showScanScreen();
    updateScanStationUI();
    if (typeof window.initScanStation === 'function') {
      window.initScanStation();
    }
  };

  function updateScanStationUI() {
    var scUser = document.getElementById('scanStationUser');
    if (scUser) {
      scUser.textContent = (state.currentUser && (state.currentUser.displayName || state.currentUser.username)) || 'User';
    }
    var scCount = document.getElementById('scanStationCount');
    if (scCount) {
      scCount.textContent = (state.currentUser && state.currentUser.scanCount != null) ? state.currentUser.scanCount : '0';
    }
    var scTime = document.getElementById('scanStationTime');
    if (scTime) {
      scTime.textContent = state.inactivitySeconds + 's remaining';
    }
  }
  window.updateScanStationUI = updateScanStationUI;

  window.openDeviceHome = function () {
    state.isNativeOperationActive = true;
    state.inactivitySeconds = 90;
    updateTimerText();

    var userName = (state.currentUser && (state.currentUser.displayName || state.currentUser.username)) || 'User';
    showAlert('▶ DEVICE HOME READY! PRESS [CLEAR] OR [HOME] ◀', 'info');

    // Trigger native unlock & CWS DefaultScreen = Home
    unlockDeviceTarget('HOME');
  };

  // Backwards compatibility alias
  window.handleOpenDeviceFunctions = function () {
    window.openCopyFunction();
  };

  function unlockDeviceTarget(mode) {
    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;
    var modeLabel = (mode === 'SCAN' ? 'Scanner' : (mode === 'HOME' ? 'Device Home' : 'Photocopier'));

    state.isNativeOperationActive = true;

    // Immediate attempt on local browser environment
    triggerToshibaNativeHook(mode);

    var now = Date.now();
    if (state.lastUnlockMode === mode && (now - (state.lastUnlockTime || 0) < 15000)) {
      showAlert(modeLabel + ' ready! Press [HOME] or [CLEAR] on panel', 'success');
      return;
    }
    state.lastUnlockMode = mode;
    state.lastUnlockTime = now;

    try {
      apiRequest('POST', '/api/station/device-functions', {
        printerCode: stationCode,
        printerIp: stationIp,
        mode: mode,
        username: (state.currentUser && (state.currentUser.username || '')) || '',
        switchScreen: 'true'
      }, function (status, data) {
        if (status === 200) {
          showAlert(modeLabel + ' ready! Press [HOME] or [CLEAR] on panel', 'success');
        } else {
          showAlert(modeLabel + ' ready! Press [HOME] or [CLEAR] on panel', 'info');
        }
      });
    } catch (e) {
      // non-fatal
    }
  }

  function triggerToshibaNativeHook(mode) {
    var screenName = (mode === 'SCAN') ? 'scan' : 'copy';
    var screenCapitalized = (mode === 'SCAN') ? 'Scan' : 'Copy';
    var screenUpper = (mode === 'SCAN') ? 'SCAN' : 'COPY';
    var funcId = (mode === 'COPY') ? 1 : 2;

    // Report what topaccess actually has right at click for diagnostics
    try {
      if (window.topaccess && window.topaccess.system) {
        var methods = [];
        for (var m in window.topaccess.system) {
          methods.push(m + ':' + typeof window.topaccess.system[m]);
        }
        apiRequest('POST', '/api/station/diagnostics', { triggerMode: mode, systemMethods: methods }, function(){});
      }
    } catch(e) {}

    // 1. Check all candidate Toshiba OpenPlatform global namespaces
    var taList = [
      window.topaccess,
      (window.top && window.top !== window ? window.top.topaccess : null),
      (window.parent && window.parent !== window ? window.parent.topaccess : null),
      window.toshiba,
      (window.top && window.top !== window ? window.top.toshiba : null)
    ];

    for (var i = 0; i < taList.length; i++) {
      var ta = taList[i];
      if (!ta) continue;

      try {
        if (ta.system) {
          // Direct named functions
          if (mode === 'COPY') {
            if (typeof ta.system.openCopyScreen === 'function') { ta.system.openCopyScreen(); return; }
            if (typeof ta.system.openCopy === 'function') { ta.system.openCopy(); return; }
          } else {
            if (typeof ta.system.openScanScreen === 'function') { ta.system.openScanScreen(); return; }
            if (typeof ta.system.openScan === 'function') { ta.system.openScan(); return; }
          }

          // Generic screen switchers (openScreen / changeScreen / setScreen)
          if (typeof ta.system.openScreen === 'function') {
            try { ta.system.openScreen(screenName); return; } catch(e){}
            try { ta.system.openScreen(screenCapitalized); return; } catch(e){}
            try { ta.system.openScreen(screenUpper); return; } catch(e){}
            try { ta.system.openScreen(funcId); return; } catch(e){}
          }
          if (typeof ta.system.changeScreen === 'function') {
            try { ta.system.changeScreen(screenName); return; } catch(e){}
            try { ta.system.changeScreen(screenCapitalized); return; } catch(e){}
            try { ta.system.changeScreen(screenUpper); return; } catch(e){}
            try { ta.system.changeScreen(funcId); return; } catch(e){}
          }
          if (typeof ta.system.setScreen === 'function') {
            try { ta.system.setScreen(screenName); return; } catch(e){}
            try { ta.system.setScreen(screenCapitalized); return; } catch(e){}
          }
          if (typeof ta.system.launchApp === 'function') {
            try { ta.system.launchApp(screenName); return; } catch(e){}
            try { ta.system.launchApp(screenCapitalized); return; } catch(e){}
          }
          if (typeof ta.system.openProgramableKey === 'function') {
            try { ta.system.openProgramableKey(funcId); return; } catch(e){}
          }
          if (typeof ta.system.executeProgramableKey === 'function') {
            try { ta.system.executeProgramableKey(funcId); return; } catch(e){}
          }

          // Native Toshiba openHomeScreen - try with screen target first, then fallback
          if (typeof ta.system.openHomeScreen === 'function') {
            try { ta.system.openHomeScreen(screenName); return; } catch(e){}
            try { ta.system.openHomeScreen(funcId); return; } catch(e){}
            ta.system.openHomeScreen();
            return;
          }
        }

        // Direct device/copier modules
        if (mode === 'COPY') {
          if (ta.copier && typeof ta.copier.open === 'function') { ta.copier.open(); return; }
          if (ta.copy && typeof ta.copy.open === 'function') { ta.copy.open(); return; }
        } else {
          if (ta.scanner && typeof ta.scanner.open === 'function') { ta.scanner.open(); return; }
          if (ta.scan && typeof ta.scan.open === 'function') { ta.scan.open(); return; }
        }

        if (ta.ui && typeof ta.ui.showScreen === 'function') {
          try { ta.ui.showScreen(screenName); return; } catch(e){}
        }
      } catch (err) {}
    }

    // 2. Check window.external
    try {
      if (window.external) {
        if (mode === 'COPY' && typeof window.external.openCopyScreen === 'function') { window.external.openCopyScreen(); return; }
        if (typeof window.external.openScreen === 'function') { window.external.openScreen(screenName); return; }
        if (typeof window.external.changeScreen === 'function') { window.external.changeScreen(screenName); return; }
      }
    } catch (err) {}

    // Drop browser overlay to reveal Toshiba native UI
    try { window.open('', '_self', ''); window.close(); } catch (e) {}
    try { if (window.top) { window.top.open('', '_self', ''); window.top.close(); } } catch (e) {}
    try { if (window.parent) { window.parent.open('', '_self', ''); window.parent.close(); } } catch (e) {}
    try { if (window.close) window.close(); } catch (e) {}
  }

  window.triggerNativeCopyDirect = function (mode) {
    unlockDeviceTarget(mode || 'COPY');
  };

  /* =========================================================================
     Photocopy & Scan Walk-Up Station Handlers
     ========================================================================= */

  var scanState = {
    colorMode: 'COLOR',
    resolution: '300DPI',
    fileFormat: 'PDF',
    duplex: 'SIMPLEX',
    isScanning: false
  };

  window.initScanStation = function () {
    var userName = (state.currentUser && (state.currentUser.displayName || state.currentUser.username)) || 'User';
    var userEmail = (state.currentUser && state.currentUser.email) || '';
    if (!userEmail && state.currentUser && state.currentUser.username) {
      userEmail = state.currentUser.username + '@rrconstruction.lk';
    }

    var elLdapEmail = document.getElementById('scanLdapEmailDisplay');
    if (elLdapEmail) elLdapEmail.textContent = userEmail;

    var elUser = document.getElementById('scanStationUser');
    if (elUser) elUser.textContent = userName;

    var elCount = document.getElementById('scanStationCount');
    if (elCount) elCount.textContent = (state.currentUser && state.currentUser.scanCount) || 0;

    var elRecipient = document.getElementById('scanInputRecipient');
    if (elRecipient) elRecipient.value = userEmail;

    var elSubject = document.getElementById('scanInputSubject');
    if (elSubject) {
      var d = new Date();
      var dateStr = d.getFullYear() + '' +
        (d.getMonth() + 1 < 10 ? '0' : '') + (d.getMonth() + 1) + '' +
        (d.getDate() < 10 ? '0' : '') + d.getDate() + '_' +
        (d.getHours() < 10 ? '0' : '') + d.getHours() + '' +
        (d.getMinutes() < 10 ? '0' : '') + d.getMinutes();
      elSubject.value = 'Scan_' + ((state.currentUser && state.currentUser.username) || 'Document') + '_' + dateStr;
    }

    window.resetScanForm();
  };

  window.setScanColorMode = function (mode) {
    scanState.colorMode = mode;
    ['btnColorModeColor', 'btnColorModeGray', 'btnColorModeMono'].forEach(function (id) {
      var b = document.getElementById(id);
      if (b) b.className = 'scan-pill-btn';
    });
    var activeId = mode === 'GRAYSCALE' ? 'btnColorModeGray' : (mode === 'MONO' ? 'btnColorModeMono' : 'btnColorModeColor');
    var actEl = document.getElementById(activeId);
    if (actEl) actEl.className = 'scan-pill-btn active';
  };

  window.setScanResolution = function (res) {
    scanState.resolution = res;
    ['btnRes200', 'btnRes300', 'btnRes600'].forEach(function (id) {
      var b = document.getElementById(id);
      if (b) b.className = 'scan-pill-btn';
    });
    var activeId = res === '200DPI' ? 'btnRes200' : (res === '600DPI' ? 'btnRes600' : 'btnRes300');
    var actEl = document.getElementById(activeId);
    if (actEl) actEl.className = 'scan-pill-btn active';
  };

  window.setScanFormat = function (fmt) {
    scanState.fileFormat = fmt;
    ['btnFmtPdf', 'btnFmtTiff', 'btnFmtJpeg'].forEach(function (id) {
      var b = document.getElementById(id);
      if (b) b.className = 'scan-pill-btn';
    });
    var activeId = fmt === 'TIFF' ? 'btnFmtTiff' : (fmt === 'JPEG' ? 'btnFmtJpeg' : 'btnFmtPdf');
    var actEl = document.getElementById(activeId);
    if (actEl) actEl.className = 'scan-pill-btn active';
  };

  window.setScanDuplex = function (dup) {
    scanState.duplex = dup;
    ['btnDuplexSimplex', 'btnDuplexDuplex'].forEach(function (id) {
      var b = document.getElementById(id);
      if (b) b.className = 'scan-pill-btn';
    });
    var activeId = dup === 'DUPLEX' ? 'btnDuplexDuplex' : 'btnDuplexSimplex';
    var actEl = document.getElementById(activeId);
    if (actEl) actEl.className = 'scan-pill-btn active';
  };

  window.applyScanPreset = function (preset) {
    if (preset === 'COLOR_300') {
      window.setScanColorMode('COLOR');
      window.setScanResolution('300DPI');
      window.setScanFormat('PDF');
    } else if (preset === 'MONO_300') {
      window.setScanColorMode('MONO');
      window.setScanResolution('300DPI');
      window.setScanFormat('PDF');
    } else if (preset === 'PHOTO_600') {
      window.setScanColorMode('COLOR');
      window.setScanResolution('600DPI');
      window.setScanFormat('PDF');
    }
  };

  window.executeScanToEmail = function () {
    if (scanState.isScanning) return;

    var recipient = (state.currentUser && state.currentUser.email) ? state.currentUser.email.trim() : '';
    if (!recipient) {
      var recipientInput = document.getElementById('scanInputRecipient');
      recipient = recipientInput ? recipientInput.value.trim() : '';
    }
    if (!recipient && state.currentUser && state.currentUser.username) {
      recipient = state.currentUser.username + '@rrconstruction.local';
    }

    var subjectInput = document.getElementById('scanInputSubject');
    var subject = subjectInput ? subjectInput.value.trim() : 'Scanned Document';

    var stationCode = (state.station && state.station.printerCode) ? state.station.printerCode : null;
    var stationIp = (state.station && state.station.ipAddress) ? state.station.ipAddress : null;

    scanState.isScanning = true;
    state.inactivitySeconds = 120;

    var formView = document.getElementById('scanFormView');
    var progView = document.getElementById('scanProgressView');
    var succView = document.getElementById('scanSuccessView');

    if (formView) formView.style.display = 'none';
    if (succView) succView.style.display = 'none';
    if (progView) progView.style.display = 'block';

    var progText = document.getElementById('scanProgressStatusText');
    if (progText) progText.textContent = 'Scanning from Toshiba RADF / Glass...';

    // Step animation feedback for touch user
    setTimeout(function () {
      if (scanState.isScanning && progText) {
        progText.textContent = 'Encoding PDF and connecting to SMTP relay...';
      }
    }, 1200);

    apiRequest('POST', '/api/scan/email', {
      printerCode: stationCode,
      printerIp: stationIp,
      recipientEmail: recipient,
      subject: subject,
      fileName: subject.replace(/[^a-zA-Z0-9_-]/g, '_') + '.pdf',
      pages: 1,
      colorMode: scanState.colorMode,
      resolution: scanState.resolution,
      fileFormat: scanState.fileFormat,
      duplex: scanState.duplex
    }, function (status, data) {
      scanState.isScanning = false;

      if (status === 200 && data && data.success) {
        if (progView) progView.style.display = 'none';
        if (succView) succView.style.display = 'block';

        var elRecip = document.getElementById('scanSuccessRecipient');
        if (elRecip) elRecip.textContent = recipient;

        var elFile = document.getElementById('scanSuccessFileName');
        if (elFile && data.job) elFile.textContent = (data.job.fileName || subject + '.pdf') + ' (' + scanState.colorMode + ', ' + scanState.resolution + ')';

        // Update local user scan counter
        if (state.currentUser) {
          state.currentUser.scanCount = (state.currentUser.scanCount || 0) + 1;
          var elCount = document.getElementById('scanStationCount');
          if (elCount) elCount.textContent = state.currentUser.scanCount;
        }

        showAlert('✓ Scan sent successfully to ' + recipient, 'success');
      } else {
        if (progView) progView.style.display = 'none';
        if (formView) formView.style.display = 'block';
        var errMsg = (data && data.message) || 'Failed to dispatch scan to email.';
        showAlert('Scan Error: ' + errMsg, 'error');
      }
    });
  };

  window.toggleScanEmailForm = function () {
    var formView = document.getElementById('scanFormView');
    if (!formView) return;
    if (formView.style.display === 'none' || !formView.style.display) {
      formView.style.display = 'block';
    } else {
      formView.style.display = 'none';
    }
  };

  window.resetScanForm = function () {
    scanState.isScanning = false;
    var formView = document.getElementById('scanFormView');
    var progView = document.getElementById('scanProgressView');
    var succView = document.getElementById('scanSuccessView');

    if (progView) progView.style.display = 'none';
    if (succView) succView.style.display = 'none';
    if (formView) formView.style.display = 'block';
  };

  window.triggerToshibaScanNative = function () {
    showAlert('Scanner ready! Press [HOME] or [CLEAR] on panel', 'success');
    unlockDeviceTarget('SCAN');
  };
  window.triggerNativeCopyDirect = function (mode) {
    unlockDeviceTarget(mode || 'COPY');
  };

  window.showCopySessionModal = function () {
    window.showCopyScreen();
  };

  window.hideCopySessionModal = function () {};

  window.handleFinishCopying = function () {
    state.isNativeOperationActive = false;
    window.emergencyLockAndLogout('FINISH_DEVICE_SESSION_BUTTON');
  };

  function updateMenuJobCount() {
    var badge = document.getElementById('menuPrintBadge');
    if (!badge) return;
    var count = (state.jobs && state.jobs.length) || 0;
    if (count > 0) {
      badge.textContent = count + (count === 1 ? ' Job Waiting' : ' Jobs Waiting');
      badge.style.background = '#FEF3C7';
      badge.style.color = '#B45309';
      badge.style.borderColor = '#FCD34D';
    } else {
      badge.textContent = '0 Jobs';
      badge.style.background = '#EFF6FF';
      badge.style.color = '#1D4ED8';
      badge.style.borderColor = '#BFDBFE';
    }
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
    updateMenuJobCount();
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
    var csTime = document.getElementById('copyStationTime');
    if (csTime) {
      csTime.textContent = state.inactivitySeconds + 's remaining';
    }
    var scTime = document.getElementById('scanStationTime');
    if (scTime) {
      scTime.textContent = state.inactivitySeconds + 's remaining';
    }
  }

  /* ------------------------------------------------------------------ */
  /* Alert Banners                                                      */
  /* ------------------------------------------------------------------ */
  var alertDismissTimer = null;
  function showAlert(msg, type) {
    if (!alertBox) return;
    if (alertDismissTimer) {
      clearTimeout(alertDismissTimer);
      alertDismissTimer = null;
    }
    alertBox.className = 'pc-alert-strip pc-alert-' + (type || 'info');
    alertBox.textContent = msg;

    // Auto-hide alert after 3.5 seconds so it doesn't linger or cover controls
    if (type !== 'error') {
      alertDismissTimer = setTimeout(function () {
        hideAlert();
      }, 3500);
    }
  }

  function hideAlert() {
    if (!alertBox) return;
    if (alertDismissTimer) {
      clearTimeout(alertDismissTimer);
      alertDismissTimer = null;
    }
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
