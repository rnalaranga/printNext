(function () {
  'use strict';

  var state = {
    jobs: [],
    auditLogs: [],
    printers: []
  };

  window.onload = function () {
    // Authenticate as admin session for administrative view
    fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin: '1234' })
    }).then(function () {
      loadAll();
    });
  };

  window.loadAll = function () {
    loadJobs();
    loadAudit();
    loadPrinters();
  };

  window.loadJobs = function () {
    fetch('/api/admin/jobs')
      .then(function (res) { return res.json(); })
      .then(function (data) {
        state.jobs = data;
        renderJobs();
        updateStats();
      })
      .catch(function (err) { console.error('Jobs error:', err); });
  };

  window.loadAudit = function () {
    fetch('/api/admin/audit')
      .then(function (res) { return res.json(); })
      .then(function (data) {
        state.auditLogs = data;
        renderAudit();
      })
      .catch(function (err) { console.error('Audit error:', err); });
  };

  window.loadPrinters = function () {
    fetch('/api/admin/printers')
      .then(function (res) { return res.json(); })
      .then(function (data) {
        state.printers = data;
        renderPrinters();
      })
      .catch(function (err) { console.error('Printers error:', err); });
  };

  function updateStats() {
    var total = state.jobs.length;
    var held = 0;
    var completed = 0;
    for (var i = 0; i < state.jobs.length; i++) {
      if (state.jobs[i].status === 'HELD') held++;
      if (state.jobs[i].status === 'COMPLETED') completed++;
    }

    document.getElementById('statTotalJobs').textContent = total;
    document.getElementById('statHeldJobs').textContent = held;
    document.getElementById('statCompletedJobs').textContent = completed;
  }

  function renderJobs() {
    var tbody = document.getElementById('jobsTableBody');
    tbody.innerHTML = '';

    if (state.jobs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#8b949e;">No print jobs found.</td></tr>';
      return;
    }

    for (var i = 0; i < state.jobs.length; i++) {
      var job = state.jobs[i];
      var tr = document.createElement('tr');
      var badgeClass = 'badge-held';
      if (job.status === 'COMPLETED') badgeClass = 'badge-completed';
      if (job.status === 'CANCELLED') badgeClass = 'badge-cancelled';
      if (job.status === 'EXPIRED') badgeClass = 'badge-expired';

      tr.innerHTML =
        '<td>#' + (job.jobNumber || '-') + '</td>' +
        '<td><strong>' + escapeHtml(job.documentName) + '</strong></td>' +
        '<td>' + escapeHtml(job.username) + '<br><small style="color:#8b949e;">' + escapeHtml(job.userSid) + '</small></td>' +
        '<td>' + job.pageCount + ' p (' + (job.duplex ? '2-Sided' : '1-Sided') + ')</td>' +
        '<td>' + job.formattedFileSize + '</td>' +
        '<td><span class="badge ' + badgeClass + '">' + job.status + '</span></td>' +
        '<td>' + formatTime(job.submittedAt) + '</td>';

      tbody.appendChild(tr);
    }
  }

  function renderAudit() {
    var tbody = document.getElementById('auditTableBody');
    tbody.innerHTML = '';

    for (var i = 0; i < state.auditLogs.length; i++) {
      var log = state.auditLogs[i];
      var tr = document.createElement('tr');

      tr.innerHTML =
        '<td>' + formatTime(log.timestamp) + '</td>' +
        '<td><strong>' + escapeHtml(log.eventType) + '</strong></td>' +
        '<td>' + escapeHtml(log.username || 'System') + '</td>' +
        '<td>' + escapeHtml(log.actionDetails || '') + '</td>' +
        '<td>' + escapeHtml(log.ipAddress || '-') + '</td>' +
        '<td><span class="badge ' + (log.status === 'SUCCESS' ? 'badge-completed' : 'badge-cancelled') + '">' + log.status + '</span></td>';

      tbody.appendChild(tr);
    }
  }

  function renderPrinters() {
    var tbody = document.getElementById('printersTableBody');
    tbody.innerHTML = '';

    for (var i = 0; i < state.printers.length; i++) {
      var p = state.printers[i];
      var tr = document.createElement('tr');

      tr.innerHTML =
        '<td><strong>' + escapeHtml(p.name) + '</strong><br><small style="color:#8b949e;">' + escapeHtml(p.printerCode) + '</small></td>' +
        '<td>' + escapeHtml(p.ipAddress) + ':' + p.rawPort + '</td>' +
        '<td>' + escapeHtml(p.location) + '</td>' +
        '<td>' + escapeHtml(p.model) + '</td>' +
        '<td><span class="badge badge-completed">' + escapeHtml(p.status) + '</span></td>';

      tbody.appendChild(tr);
    }
  }

  window.submitSimulatedJob = function () {
    var docName = document.getElementById('simDocName').value || 'Drawing-Sheet-A101.pdf';
    var user = document.getElementById('simUser').value || 'jdoe';
    var pages = parseInt(document.getElementById('simPages').value, 10) || 5;

    fetch('/api/simulation/submit-job', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: user,
        documentName: docName,
        pageCount: pages,
        color: false,
        duplex: true,
        copies: 1,
        clientMachine: 'ENG-WS-SIM'
      })
    })
    .then(function () {
      loadAll();
      alert('Simulated job injected into queue for user: ' + user);
    });
  };

  window.triggerExpirySweep = function () {
    fetch('/api/simulation/expire-stale', { method: 'POST' })
      .then(function () {
        loadAll();
        alert('Job expiration sweep completed.');
      });
  };

  function formatTime(isoStr) {
    if (!isoStr) return '-';
    var d = new Date(isoStr);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' ' +
           d.toLocaleDateString();
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
