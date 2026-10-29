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
    window.refreshNocFleet(false);
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
    nocState.countdownSecs = 15;
    updateCountdownBadge();

    nocState.pollTimer = setInterval(function () {
      if (state.currentPage !== 'monitoring') return;
      nocState.countdownSecs--;
      if (nocState.countdownSecs <= 0) {
        nocState.countdownSecs = 15;
        window.refreshNocFleet(false);
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

  window.refreshNocFleet = function (force) {
    if (force === undefined) force = false;
    var refreshBtn = document.getElementById('btnNocRefresh');
    if (refreshBtn && force) {
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
        if (refreshBtn && force) {
          refreshBtn.style.opacity = '1';
          refreshBtn.style.pointerEvents = 'auto';
        }
        nocState.fleetData = data;
        nocState.printers = data.printers || [];
        updateNocKpis(data);
        renderNocFleet();
        if (force) showToast('SNMP Telemetry refreshed from hardware!', 'success');
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
    if (badgeCount) badgeCount.textContent = count + (count === 1 ? ' Real MFP Active' : ' Real MFPs Active');
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
      var sHtml = '<option value="ALL">All Locations (' + count + ')</option>';
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
     VIEW 1: NOC MATRIX GRID (CRISP LIGHT THEME)
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

      html += '<div class="noc-tile ' + statClass + '" onclick="window.openNocDeviceModal(' + p.id + ')" title="' + escapeHtml(p.printerCode + ' - ' + p.name + ' (' + p.statusMessage + ')') + '">';
      html += '  <div class="noc-tile-head">';
      html += '    <span class="noc-tile-code">' + escapeHtml(p.printerCode) + '</span>';
      html += '    <div style="display:flex; align-items:center; gap:5px;">' + realBadge + '<span class="noc-tile-indicator ' + indicatorClass + '"></span></div>';
      html += '  </div>';
      html += '  <div class="noc-tile-loc">' + escapeHtml(p.name) + '</div>';
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
     VIEW 2: VISUAL FLEET EQUIPMENT CARDS (CRISP LIGHT THEME)
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

      // Toner Cylinders
      var tonerBarsHtml = '';
      if (p.toners && p.toners.length > 0) {
        for (var t = 0; t < p.toners.length; t++) {
          var tn = p.toners[t];
          var fillPct = Math.max(4, Math.min(100, tn.levelPercent));
          var fillBg = tn.color || '#2563EB';
          if (tn.name.toLowerCase().indexOf('black') !== -1) fillBg = '#1E293B';
          else if (tn.name.toLowerCase().indexOf('cyan') !== -1) fillBg = '#0284C7';
          else if (tn.name.toLowerCase().indexOf('magenta') !== -1) fillBg = '#DB2777';
          else if (tn.name.toLowerCase().indexOf('yellow') !== -1) fillBg = '#D97706';

          if (fillPct <= 10) fillBg = '#DC2626';
          else if (fillPct <= 20) fillBg = '#D97706';

          tonerBarsHtml += '<div class="noc-toner-cyl">';
          tonerBarsHtml += '  <div class="noc-toner-cyl-head">';
          tonerBarsHtml += '    <span>' + escapeHtml(tn.name) + '</span>';
          tonerBarsHtml += '    <span>' + fillPct + '%</span>';
          tonerBarsHtml += '  </div>';
          tonerBarsHtml += '  <div class="noc-toner-track">';
          tonerBarsHtml += '    <div class="noc-toner-fill" style="width:' + fillPct + '%; background:' + fillBg + ';"></div>';
          tonerBarsHtml += '  </div>';
          tonerBarsHtml += '</div>';
        }
      }

      // Trays
      var traysHtml = '';
      if (p.trays && p.trays.length > 0) {
        for (var ty = 0; ty < p.trays.length; ty++) {
          var tray = p.trays[ty];
          var emptyClass = tray.status === 'EMPTY' ? 'empty' : '';
          traysHtml += '<div class="noc-tray-chip ' + emptyClass + '">';
          traysHtml += '  <span>' + escapeHtml(tray.trayName) + ' (' + escapeHtml(tray.paperSize) + ')</span>';
          traysHtml += '  <span style="font-weight:700;">' + (tray.status === 'EMPTY' ? 'EMPTY' : tray.levelPercent + '%') + '</span>';
          traysHtml += '</div>';
        }
      }

      // Alert Banner if any
      var alertBanner = '';
      if (p.alerts && p.alerts.length > 0) {
        var isCrit = p.status === 'ERROR';
        alertBanner = '<div class="noc-card-alert ' + (isCrit ? 'critical' : '') + '"><span>⚠️</span> <span>' + escapeHtml(p.alerts[0]) + '</span></div>';
      }

      var badgeLabel = p.status;
      if (p.status === 'READY') badgeLabel = 'Idle / Ready';
      else if (p.status === 'PRINTING') badgeLabel = 'Printing Job';

      html += '<div class="noc-card">';
      html += '  <div class="noc-card-header">';
      html += '    <div class="noc-card-identity">';
      html += '      <div class="noc-card-icon-box ' + statKey + '">';
      html += '        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">';
      html += '          <polyline points="6 9 6 2 18 2 18 9"/>';
      html += '          <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/>';
      html += '          <rect x="6" y="14" width="12" height="8"/>';
      html += '        </svg>';
      html += '      </div>';
      html += '      <div>';
      html += '        <div class="noc-card-code">' + escapeHtml(p.printerCode) + ' <span class="noc-badge-real">LAN HARDWARE</span></div>';
      html += '        <div class="noc-card-name">' + escapeHtml(p.name) + '</div>';
      html += '        <div class="noc-card-meta">' + escapeHtml(p.ipAddress) + ' • ' + escapeHtml(p.location || 'Office') + '</div>';
      html += '      </div>';
      html += '    </div>';
      html += '    <span class="noc-status-badge ' + statKey + '">' + escapeHtml(badgeLabel) + '</span>';
      html += '  </div>';

      if (alertBanner) {
        html += '<div style="margin-top:10px;">' + alertBanner + '</div>';
      }

      html += '  <div class="noc-card-consumables">';
      html += '    <div class="noc-toners-row">' + tonerBarsHtml + '</div>';
      if (traysHtml) {
        html += '    <div class="noc-trays-row">' + traysHtml + '</div>';
      }
      html += '  </div>';

      html += '  <div class="noc-card-footer">';
      html += '    <div class="noc-odometer"><span>Life:</span> <span>' + Number(p.lifeCount || 0).toLocaleString() + ' clicks</span></div>';
      html += '    <button type="button" class="btn btn-sm btn-secondary" onclick="window.openNocDeviceModal(' + p.id + ')" style="padding:4px 12px; font-size:11px; font-weight:700;">SNMP Diagnostics</button>';
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
     DETAILED SNMP DEVICE DIAGNOSTICS MODAL (LIGHT THEME)
     ========================================================================= */
  window.openNocDeviceModal = function (printerId) {
    var p = nocState.printers.find(function (item) { return item.id === printerId; });
    if (!p) return;

    nocState.selectedPrinter = p;

    var modal = document.getElementById('modalNocDeviceDetail');
    if (!modal) return;

    document.getElementById('nocModalTitle').textContent = p.printerCode + ' - ' + p.name;
    document.getElementById('nocModalSub').textContent = (p.location || 'Office') + ' • ' + p.ipAddress + ' • ' + (p.model || 'Toshiba MFP');
    document.getElementById('nocModalUptime').textContent = p.uptime || '0s';
    document.getElementById('nocModalLifeCount').textContent = Number(p.lifeCount || 0).toLocaleString() + ' clicks';
    document.getElementById('nocModalPing').textContent = (p.pingMs || 1) + ' ms';
    document.getElementById('nocModalIp').textContent = p.ipAddress;
    document.getElementById('nocModalHardwareType').textContent = 'Physical Toshiba e-STUDIO MFP (LAN Hardware)';
    document.getElementById('nocModalLastPolled').textContent = p.lastPolled ? new Date(p.lastPolled).toLocaleTimeString() : new Date().toLocaleTimeString();

    var openWebBtn = document.getElementById('btnNocOpenWebUi');
    if (openWebBtn) {
      openWebBtn.href = 'https://' + p.ipAddress;
    }

    // Status Dot Color
    var dot = document.getElementById('nocModalStatusDot');
    if (dot) {
      var color = '#10B981';
      if (p.status === 'PRINTING') color = '#06B6D4';
      else if (p.status === 'WARNING') color = '#F59E0B';
      else if (p.status === 'ERROR') color = '#EF4444';
      else if (p.status === 'OFFLINE') color = '#94A3B8';
      dot.style.background = color;
      dot.style.boxShadow = '0 0 8px ' + color;
    }

    // Alerts
    var alertSec = document.getElementById('nocModalAlertSection');
    var alertList = document.getElementById('nocModalAlertList');
    if (alertSec && alertList) {
      if (p.alerts && p.alerts.length > 0) {
        alertSec.style.display = 'block';
        alertList.innerHTML = p.alerts.map(function (a) {
          return '<div style="background:#FFFBEB; border:1px solid #FDE68A; color:#B45309; padding:8px 12px; border-radius:6px; font-size:12px; font-weight:700;">⚠️ ' + escapeHtml(a) + '</div>';
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
          if (t.name.toLowerCase().indexOf('cyan') !== -1) barColor = '#0284C7';
          else if (t.name.toLowerCase().indexOf('magenta') !== -1) barColor = '#DB2777';
          else if (t.name.toLowerCase().indexOf('yellow') !== -1) barColor = '#D97706';

          if (pct <= 10) barColor = '#DC2626';
          else if (pct <= 20) barColor = '#D97706';

          tHtml += '<div style="background:#F8FAFC; border:1px solid #E2E8F0; border-radius:8px; padding:10px 14px;">';
          tHtml += '  <div style="display:flex; justify-content:space-between; font-size:12px; font-weight:700; margin-bottom:6px;">';
          tHtml += '    <span style="color:#0F172A;">' + escapeHtml(t.name) + ' Toner</span>';
          tHtml += '    <span style="font-family:\'JetBrains Mono\',monospace; color:' + barColor + ';">' + pct + '% (' + escapeHtml(t.status) + ')</span>';
          tHtml += '  </div>';
          tHtml += '  <div style="height:8px; background:#E2E8F0; border-radius:4px; overflow:hidden;">';
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
          trHtml += '<div style="background:#F8FAFC; border:1px solid ' + (isEmp ? '#FCA5A5' : '#E2E8F0') + '; border-radius:8px; padding:10px 14px;">';
          trHtml += '  <div style="font-size:10.5px; color:#64748B; text-transform:uppercase; font-weight:700;">' + escapeHtml(tr.trayName) + ' (' + escapeHtml(tr.paperSize) + ')</div>';
          trHtml += '  <div style="font-size:14px; font-weight:800; font-family:\'JetBrains Mono\',monospace; margin-top:3px; color:' + (isEmp ? '#DC2626' : '#0F172A') + ';">';
          trHtml += isEmp ? 'EMPTY' : tr.levelPercent + '% Ready';
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
