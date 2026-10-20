# RR Secure Print — Custom PaperCut Alternative
**Designed for RR Construction & Toshiba e-STUDIO 3518A Multifunction Printers**

An enterprise pull-printing system that enables secure print release on Toshiba e-STUDIO 3518A multifunction printers (MFPs) via their Embedded Web Browser (EWB) — **without PaperCut, without proprietary embedded Java applets, and without e-BRIDGE middleware**.

---

## 1. Project Overview & Architecture

RR Secure Print intercepts, holds, and securely releases print jobs submitted across RR Construction workstations. It separates privileged Windows print-spooler operations from the REST API and web presentation tier.

### System Architecture

```
[ Workstation (AutoCAD / PDF / Word) ]
                   │
                   │ (1) Normal Print to "RR Secure Pull Print"
                   ▼
    [ Windows Print Spooler (spoolsv.exe) ]
                   │
                   │ (2) Generates .SPL (PCL6) + .SHD (Active Directory User SID)
                   ▼
 [ rr-secure-print-agent (Spool Interceptor) ]
                   │
                   │ (3) Quarantines file -> spool_data/<uuid>.bin
                   │     Computes SHA-256 Checksum
                   │     Deletes original job from Windows spooler
                   ▼
  [ rr-secure-print-api (Spring Boot 3.4.x) ] ── (PostgreSQL / H2 Dev)
                   │
                   │ (4) REST API & Session Authorization
                   ▼
  [ rr-secure-print-web (Touchscreen Interface) ]
                   │
                   │ (5) Employee taps PIN at Toshiba e-STUDIO 3518A EWB Browser
                   │ (6) Selects documents & taps "Release Selected"
                   ▼
     [ Raw Socket Service (Port 9100) ]
                   │
                   │ (7) Streams printer-ready PCL6 stream to physical hardware
                   ▼
   [ Toshiba e-STUDIO 3518A Hardware Engine ]
```

### Module Breakdown

| Module | Technology | Responsibility |
| :--- | :--- | :--- |
| **`rr-secure-print-web`** | HTML5, CSS3, Vanilla ES5 JavaScript | High-contrast touch interface optimized for Toshiba EWB (1024x600 screen). Zero modern JS dependencies. |
| **`rr-secure-print-api`** | Java 21, Spring Boot 3.4.x, Spring Security | Concurrency-guarded REST endpoints, session rate limiting, SHA-256 verification, audit logging. |
| **`rr-secure-print-db`** | PostgreSQL 13+ / Flyway / H2 Mode | Schema migrations, user SID mapping, job lifecycle states, and security audit trail. |
| **`rr-secure-print-agent`** | PowerShell, JNA Win32 Spooler API | Virtual print queue interceptor; extracts Active Directory `pUserSid` without touching production hardware queues. |
| **`rr-secure-print-admin`** | HTML5, CSS, REST Dashboard | Web portal for queue audits, spool simulation, and Toshiba MFP status. |

---

## 2. Windows Prerequisites & Environment Check

Before running, execute the automated environment audit script:

```powershell
.\scripts\check-prerequisites.ps1
```

### System Requirements
- **OS:** Windows 10 / Windows 11 / Windows Server 2019 / 2022
- **Java:** JDK 21+ or JDK 25 (OpenJDK or Oracle JDK)
- **Build Tool:** Apache Maven 3.8+
- **Database:** PostgreSQL 13+ (or use the built-in embedded H2 PostgreSQL-mode for zero-setup local dev)
- **Local Network Test IP:** `192.168.1.6` (Wi-Fi/Ethernet)

### Quick Tool Installation via winget (if needed):
```powershell
winget install Oracle.JDK.21
winget install Apache.Maven
winget install PostgreSQL.PostgreSQL
```

---

## 3. How to Run Locally (Instant Dev Mode)

You can run the entire system **immediately** with preloaded test jobs and zero database configuration:

### Step 1: Start the Spring Boot Server
```powershell
.\scripts\run-dev.ps1 -Port 8080
```
*(Or if you wish to run directly on port 8000: `.\scripts\run-dev.ps1 -Port 8000`)*

### Step 2: Open the Toshiba EWB Touchscreen Interface
Open your browser (or point the Toshiba e-STUDIO 3518A EWB browser) to:
- **Local Browser:** [http://localhost:8080/](http://localhost:8080/)
- **Toshiba MFP LAN Address:** [http://192.168.1.6:8080/](http://192.168.1.6:8080/)

### Step 3: Log In Using Demo Accounts
Use the on-screen touchscreen keypad or tap the quick demo chips:
- **Site Engineer (John Doe):** PIN `1234` (Username: `jdoe`, SID: `S-1-5-21-3623811015-3361044348-30300820-1001`)
- **Project Director (Mary Smith):** PIN `1234` (Username: `msmith`, SID: `S-1-5-21-3623811015-3361044348-30300820-1002`)
- **Administrator:** PIN `1234` (Username: `admin`, SID: `S-1-5-21-3623811015-3361044348-30300820-1000`)

### Step 4: Test Pull-Print Operations
1. View the list of held documents (`Site-Office-Permits-2026.pdf`, `Foundation-Inspection-Report.docx`).
2. Select one or more documents using the large touch checkboxes.
3. Tap **Release Selected**. The system atomically locks the job, validates the SHA-256 checksum, and processes the release.
4. Open the **Administrator Portal** at [http://localhost:8080/admin.html](http://localhost:8080/admin.html) to view the global queue and real-time audit trail.

---

## 4. Running with Production PostgreSQL

### Step 1: Initialize Database & User
```powershell
.\scripts\setup-postgres.ps1 -Method status
.\scripts\setup-db.ps1
```

### Step 2: Start with the PostgreSQL Profile
```powershell
.\scripts\run-postgres.ps1 -Port 8080 -DbHost localhost -DbName rr_secure_print -DbUser postgres -DbPassword postgres
```
Flyway automatically applies `V1__init_schema.sql` on startup.

---

## 5. Simulating Workstation Spool Jobs

You can inject simulated print jobs from PowerShell at any time:

```powershell
.\scripts\simulate-job.ps1 -Username "jdoe" -DocumentName "Highrise-Structural-Drawings-Rev4.pdf" -Pages 16
```
The job will immediately appear on John Doe's touchscreen terminal.

---

## 6. Toshiba e-STUDIO 3518A EWB Compatibility Details

The Toshiba e-STUDIO 3518A Embedded Web Browser operates on an embedded WebKit rendering engine:

1. **Strict ES5 Vanilla JavaScript:**
   - No ES6+ arrow functions (`() =>`), `let`, `const`, or template literals.
   - All HTTP requests use standard `XMLHttpRequest`.
   - Touch click handlers use standard DOM event binding.
2. **Touchscreen Ergonomics:**
   - Touch buttons have a minimum height of `52px` with `touch-action: manipulation;` and `user-select: none;` to prevent zoom latency.
   - High-contrast color palette (dark navy background with high-visibility white, green, and orange buttons) engineered for office/construction trailer ambient light.
3. **Session Auto-Logout:**
   - An active 120-second inactivity timer counts down in the top-right header and resets upon any screen tap.
   - The session automatically terminates if an employee leaves the printer without tapping logout.

---

## 7. Security Architecture & Controls

- **Active Directory Immutable SID:** The primary ownership key is the Windows Security Identifier (`S-1-5-21-...`). A user can never access, view, or release another user's spool files.
- **Isolated Spool Storage:** Spool files are written to `spool_data/` with randomly generated UUID filenames. Spool directory traversal (`../`) is strictly rejected by the storage service.
- **Cryptographic Integrity:** Every job calculates a SHA-256 hash upon ingestion. Before release, the hash is re-verified to prevent local file tampering.
- **Atomic Compare-And-Swap (CAS):** To prevent double-printing from multiple rapid touchscreen taps, the database executes atomic conditional transitions (`UPDATE print_jobs SET status = 'RELEASING' WHERE id = :id AND status = 'HELD'`).
- **Authentication Rate Limiting:** Brute-force PIN attacks are blocked after 5 failed attempts within 60 seconds per IP.
- **Physical Output Guard:** Governed by `app.printer.simulation-mode: true` by default. No bytes are sent to Port 9100 during development.

---

## 8. Status: Implemented vs Not-Yet-Implemented

### Completed & Fully Operational (MVP)
- [x] **Stage 1 (UI Prototype):** Toshiba EWB-compatible HTML5 touchscreen interface with on-screen numeric PIN pad, job selection, release, cancel, auto-logout, and responsive WSVGA layout.
- [x] **Stage 2 (REST API):** Full Spring Boot 3.4.x API (`/api/health`, `/api/auth/login`, `/api/auth/logout`, `/api/jobs`, `/api/jobs/{id}/release`, `DELETE /api/jobs/{id}`, `/api/admin/jobs`, `/api/admin/audit`).
- [x] **Stage 3 (Secure Spool Storage & Simulation):** Isolated binary storage in `spool_data/`, SHA-256 cryptographic verification, simulated PCL/PJL generator, and automated expiry sweep.
- [x] **Stage 4 (Windows Spooler Agent Architecture):** Native Win32 architecture documentation, virtual pull-queue installer script (`install-virtual-printer.ps1`), and background spool monitor daemon (`spool-monitor.ps1`).
- [x] **Stage 5 (Identity & AD Architecture):** Windows User SID primary ownership binding, LDAPS Active Directory service integration, and local verified PIN fallback.
- [x] **Stage 6 (Toshiba EWB Optimization):** Pure ES5 JavaScript, zero modern JS framework overhead, conservative CSS2.1/CSS3 styling, and high-contrast touch buttons.
- [x] **Automated Testing:** 100% passing test suite across unit, storage integrity, and end-to-end MockMvc integration tests.

### Required Before Real Physical Print Release
To safely switch from simulation to live physical output on the Toshiba printer:
1. **Network Reachability Test:** Verify port 9100 reachability using PowerShell:
   ```powershell
   Test-NetConnection -ComputerName 192.168.1.150 -Port 9100
   ```
2. **Driver Verification:** Verify the "RR Secure Pull Print" queue is using the official **Toshiba e-STUDIO Universal PCL6 or PostScript driver** so that generated `.SPL` files match the printer's rasterization engine.
3. **Safety Switch Toggle:** Update `.env` or `application.yml`:
   ```yaml
   app:
     printer:
       simulation-mode: false
   ```
4. **Active Directory LDAPS:** If binding to live domain controllers, enable `app.ad.enabled: true` and configure domain controller LDAPS certificates.

---

## 9. Troubleshooting Guide

| Issue | Root Cause | Resolution |
| :--- | :--- | :--- |
| **Port 8000 already in use** | A Python HTTP test server was previously running on port 8000. | Run the server on port 8080: `.\scripts\run-dev.ps1 -Port 8080`, or terminate the python process: `Stop-Process -Name python` and run `.\scripts\run-dev.ps1 -Port 8000`. |
| **Toshiba EWB shows blank screen** | Incompatible JavaScript syntax or HTTPS certificate error. | Ensure you are serving `index.html` via HTTP or a trusted HTTPS certificate. Verify no ES6+ syntax is in the browser console. |
| **"Invalid credentials or PIN code"** | Rate limiting triggered or wrong PIN entered. | Default PIN for demo users (`jdoe`, `msmith`, `admin`) is `1234`. Wait 60 seconds if rate-limited. |
| **PostgreSQL Connection Refused** | PostgreSQL Windows service is stopped. | Start the service via PowerShell: `Start-Service *postgres*`, or run in dev mode (`.\scripts\run-dev.ps1`). |
