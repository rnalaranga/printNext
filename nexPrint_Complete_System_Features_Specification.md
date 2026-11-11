![nexPrint Logo](nexprint_logo.png)

# nexPrint Enterprise Pull-Print & MFP Accounting System
**Comprehensive Enterprise Technical Specification & System Feature Manual**  
**Document Format:** Markdown (`.md`) & Microsoft Word (`.docx`)  
**Target Hardware:** Toshiba e-BRIDGE Next Fleets (e-STUDIO 08, 18, and 28 Series)  
**Engineered Scale:** 13,700+ Enterprise Active Directory Users across 100+ Multifunction Printers (MFPs)  
**Release Date:** November 2026 — Official Master Technical Release  

---

## 1. Executive Overview & Core Problem Solved

**nexPrint** is an enterprise-grade, high-security pull-printing, document release, and hardware accounting management platform developed specifically as a direct, zero-license-fee replacement for commercial suites like **PaperCut MF**, **MyQ Enterprise**, and **Canon uniFLOW**.

The system integrates natively with **Toshiba e-STUDIO multifunction printers (MFPs)** running the **e-BRIDGE Next controller firmware** without requiring:
* ❌ Proprietary embedded Java applets
* ❌ External appliance dongles / hardware boxes
* ❌ e-BRIDGE OpenPlatform developer runtime licenses

### The Core Problem Solved: Overcoming the 1,000-User Flash Limit
Standard Toshiba e-BRIDGE Next controllers possess an internal flash storage limit capable of hosting a maximum of **1,000 local user profiles and department codes**. In corporate enterprises possessing 13,000+ domain users, pushing accounts directly onto copier hard drives crashes controller memory or causes silent replication failure.

**The nexPrint Solution:**  
nexPrint decouples identity management from physical hardware:
1. Copiers run purely as lightweight thin-clients via their built-in WebKit/Chromium **Embedded Web Browser (EWB)**.
2. Zero user database replication is ever performed on physical copiers.
3. All accounts, PINs, RFID cards, quotas, and audit logs are resolved centrally in **under 10 milliseconds** against Active Directory and nexPrint's SQL database.

---

## 2. End-to-End System Architecture & Data Flow

```
[ Workstations (AutoCAD / Office / PDF / ERP) ]
                      │
                      │ 1. Print to "\\server\nexPrint_Queue" (TOSHIBA Universal 2 Driver)
                      ▼
     [ Windows Print Spooler (spoolsv.exe) ]
                      │
                      │ 2. Extracts .SHD (Active Directory User SID) & .SPL (PCL6/PS)
                      ▼
   [ nexPrint Agent (Spool Interceptor) ]
                      │
                      │ 3. Quarantines file -> spool_data/<uuid>.bin
                      │    Computes SHA-256 Checksum
                      │    Purges original job from Windows Spooler
                      ▼
    [ nexPrint Central API (Spring Boot 3.4.x) ] <=======> [ Active Directory / LDAP ]
    (Central Authentication, Quota & Accounting)           (13,700+ Domain Accounts)
                      │
                      │ 4. Kiosk EWB Session / PIN / RFID Card Auth
                      ▼
    [ nexPrint Touch Terminal (EWB UI) ]
    (1024x600 / 800x480 Toshiba EWB Browser)
                      │
                      │ 5. User taps "Release Selected" or "Release All"
                      ▼
    [ Direct Socket Service (Port 9100 RAW) ]
                      │
                      │ 6. Streams printer-ready byte stream directly to hardware engine
                      ▼
       [ Physical Toshiba e-STUDIO MFP ]
```

### Complete End-to-End Steps
1. **Universal Virtual Queue Submission:** Employees print to a single shared network printer queue (`\\server\nexPrint_Queue`) using the certified TOSHIBA Universal 2 PostScript/PCL6 Driver. Users never need to select a physical device.
2. **Privileged Spool Interception:** The Windows Spool Monitor Agent reads the `.SHD` shadow metadata header, extracts the Active Directory User SID (`pUserSid`) and document name, and quarantines the `.SPL` raw print file.
3. **Cryptographic Spool Quarantine:** Spool files are isolated in `spool_data/<uuid>.bin`, hashed with SHA-256, and marked as `HELD`. The Windows print job is immediately purged, preventing premature output.
4. **Walk-Up Authentication at MFP:** The employee walks up to ANY Toshiba copier, views the EWB touch screen, and taps their contactless RFID card or enters their 4-digit PIN.
5. **High-Speed Central Identity Resolution:** Spring Boot validates credentials against PostgreSQL/H2 tables or queries Active Directory via LDAP/LDAPS in under 10ms.
6. **Secure Document Release via Port 9100:** The server locks the selected jobs optimistically, verifies the SHA-256 hash, and streams the raw byte stream directly to the MFP over Port 9100 RAW socket.
7. **Real-Time Accounting & Audit:** Print pages are atomically incremented on the user's account, an immutable audit event is written, and the spool file is cleaned up per retention rules.

---

## 3. Toshiba Hardware Integration & Device Control

nexPrint interacts directly with the Toshiba e-BRIDGE Next controller architecture through TopAccess Content Web Server (CWS), SNMP MIBs, and native OpenPlatform browser hooks:

* **CWS Panel Command Engine:** Programmatically locks and unlocks physical copier glass and buttons via TopAccess XML endpoints (`/contentwebserver/DeviceManagement/DeviceControl`).
* **Optimized CWS Toggle (Sub-Second Unlock):** Utilizes targeted CWS panel toggle commands, unlocking the photocopier in approximately **2.5 seconds** (bypassing legacy 15–20s full web server reloads).
* **Multi-Series Controller Compatibility:** Automatically detects and handles differences between Toshiba **08 Series** (Qt WebKit engine) and **18/28 Series** (Chromium OpenPlatform engine), adjusting screen transition commands accordingly.
* **Inactivity Auto-Lock Watchdog:** Dual-layered protection: an active **20-second inactivity countdown timer** on the kiosk frontend, coupled with a **25-second server-side watchdog daemon** (`CopyTrackingService.java`) that automatically re-locks the physical copier if left unattended.
* **Hardware Deep Sleep Watchdog:** Detects energy-saver deep sleep cycles (>3500ms JS suspension). If the copier sleeps while an operator is logged in, the terminal immediately enforces hardware re-lock upon wake.
* **Automated Kiosk Provisioning:** Administrators can push EWB browser settings (Kiosk URL, Kiosk Mode, Default Home Screen) to any Toshiba MFP in the fleet directly from the Admin Console via `ToshibaEwbService.java`.

---

## 4. Touchscreen Terminal Interface (EWB UI)

The kiosk frontend (`nexPrint Touch Terminal`) is engineered specifically for Toshiba 1024x600 and 800x480 resistive and capacitive touch screens:

1. **Top Header Bar:**
   * Modern brand monogram (`nP`) and title (`nexPrint Secure Touch Terminal`).
   * Live pulsing green status indicator (`● Terminal Ready`).
   * Active User Profile Pill with user avatar, name, and department badge.
   * Real-time **20s Auto-lock Countdown** badge.
   * Instant **HOME** button to access native Toshiba hardware settings.
   * High-contrast Red **LOGOUT** button.
2. **Dual Authentication Modes:**
   * Segmented pill dock to switch between **PIN CODE** and **USERNAME** (Active Directory credentials) views.
3. **Contactless RFID Card Reader Catcher:**
   * Auto-focused hidden listener (`#pcCardCatcher`) captures fast-burst USB/HID RFID card swipes (Elatec, RF IDeas pcProx, Toshiba TWN4) without triggering on-screen keyboard popups.
4. **Self-Service Card Registration:**
   * When an unrecognized RFID card is tapped, an alert banner smoothly appears displaying the Card ID and instructing the employee to enter their 4-digit PIN once to pair the card permanently.
5. **Tactile Touch Keypad:**
   * 3x4 grid with finger-optimized 46px tap targets, 22px high-contrast numbers, active touch compression (`transform: scale(0.96)`), clean backspace (`⌫ CLR`), and enterprise blue `LOG IN →` button.
6. **Dynamic PIN Glow Display:**
   * Displays radial gradient glowing blue dots that illuminate as digits are entered.
7. **PaperCut 3-Action Launcher (Main Menu):**
   * **Print Release Tile:** Cobalt Blue accent with live badge showing held jobs count (e.g. `3 Jobs Waiting`).
   * **Photocopy Tile:** Amber Gold accent with walk-up copier unlock trigger.
   * **Scan to Email Tile:** Emerald Green accent for direct-to-inbox scanning.
   * **Device Home Button:** Quick access to native Toshiba copy/fax functions.
8. **Held Print Release Queue Screen:**
   * High-contrast sticky header table.
   * Document metadata: Title, Submission Time, Page Count, Attributes (`[B&W]`/`[Color]`, `[1-Sided]`/`[2-Sided]`), Cost, and Status badge.
   * Tap-to-select rows with instant blue highlight (`.pc-selected`).
   * Top bar with `[Release All]`, `[Refresh]`, and `[Select All]`.
9. **Floating Bottom Action Dock:**
   * Summary bar: `X selected | Y pgs | LKR Z.00`.
   * Action buttons: `[← Apps]`, `[Delete]`, and prominent `[Print Selected →]`.
10. **Walk-Up Photocopy Screen:**
    * 3 clear illustrated visual steps:
      1. Press **[ HOME ]** on physical panel.
      2. Place paper & press **[ START ]** key.
      3. Press **[ SLEEP ]** or tap the large red **[ 🔒 Finish & Lock Copier ]** button.
11. **Walk-Up Scan Screen:**
    * Step-by-step guidance and prominent **[ 🔒 Finish & Lock Scanner ]** exit button.

---

## 5. Active Directory & LDAP Enterprise Directory Synchronization

* **13,700+ Enterprise Scale:** Uses LDAP `PagedResultsControl` to page through massive user directories without memory spikes or LDAP timeouts.
* **Complete Attribute Mapping:**
  * `sAMAccountName` ➔ `username`
  * `displayName` ➔ `fullName`
  * `mail` ➔ `email`
  * `department` ➔ `department`
  * `objectSid` ➔ `userSid`
* **PaperCut-Style JIT Self-Enrollment:** When an employee prints to the virtual queue for the first time, nexPrint auto-provisions their user record with a default PIN (e.g., `1234`), enabling immediate walk-up release without administrator intervention.
* **Automated & On-Demand Sync:** Background cron-based periodic synchronization (e.g., every 60 minutes) alongside manual on-demand sync from the Web Admin Console.
* **LDAPS & StartTLS Encryption:** Full support for LDAPS (Port 636) and StartTLS with custom truststore certificate handling.

---

## 6. Hardware Walk-Up Photocopy Accounting Engine

* **TopAccess XML Log Ingestion:** Background polling daemon queries active Toshiba copiers every 60 seconds via `/contentwebserver/JobManagement/JobLog`, filtering for `<jobType>COPY</jobType>` records.
* **Domain User Normalization:** Strips domain prefixes and suffixes (`CORP\jdoe` or `jdoe@corp.local` ➔ `jdoe`) to accurately attribute copy counts.
* **Color vs. Monochrome Breakdown:** Records total copy pages, mono copy pages, and color copy pages individually per job.
* **Idempotent Accounting Guarantee:** Unique database constraint on `(printer_ip, printer_job_id)` prevents duplicate accounting across polling cycles.
* **Selective Accounting (Scan Exclusion):** In accordance with operational policy, scan jobs are excluded from billable copy quotas, preventing document archival from exhausting print budgets.

---

## 7. Scan-to-Email & Integrated SMTP Daemon

* **Built-In Inbound SMTP Server:** SubEthaSMTP daemon running on port 2525 / port 25 receives scanned multi-page PDF documents directly from Toshiba copiers.
* **Dynamic User Routing:** When an employee initiates a scan session at the MFP, the system configures the scan destination to their verified domain email address.
* **Outbound Relay Engine:** Relays captured scans through corporate mail servers (Microsoft 365, Google Workspace, Exchange Server) with retry mechanisms.
* **Scan Job History Tracking:** Every scan event is logged in `scan_jobs` with sender IP, recipient, page count, document size, and delivery status.

---

## 8. SNMP Real-Time Fleet Telemetry & Monitoring

Powered by `SnmpMonitoringService.java` and asynchronous SNMP v1/v2c polling:

* **Standard Printer MIB (RFC 3805 / RFC 1759) & Toshiba MIB Support:** Queries standard and vendor-specific OIDs for live device telemetrics.
* **Consumables Level Tracking:** Reads exact percentage levels for Black, Cyan, Magenta, and Yellow toners, rendering color-coded progress bars in the admin console.
* **Paper Tray Capacity Monitoring:** Monitors paper levels and status for all physical drawers (Tray 1, Tray 2, Large Capacity Feeder, Bypass).
* **Hardware Error & Alarm Detection:** Real-time alerts for Paper Jams, Cover Open / Door Ajar, Out of Paper, Toner Empty, and Offline reachability.
* **Automated Fleet Polling Daemon:** Background worker polls all registered copiers on configurable cycles (default 60s) and caches health states for instant dashboard display.

---

## 9. Web Administration Console Modules

Accessible via `http://localhost:8080/admin.html`:

| Module | Features & Capabilities |
| :--- | :--- |
| **1. Dashboard Overview** | Real-time KPI cards (Total Jobs Processed, Currently Held, Completed/Released, Managed Printers, Registered Users), quick-action buttons, and recent print activity feed. |
| **2. Virtual Print Queue** | Configuration interface for Windows spool shares (`\\server\nexPrint_Queue`), driver bindings (`TOSHIBA Universal 2`), and release station pool assignments. |
| **3. Printer Devices** | Fleet inventory of 100+ Toshiba copiers showing IP, Port, Model, Online/Offline status, TopAccess CWS links, "Poll Walk-Up Copy Jobs" trigger, and automated EWB kiosk configuration tools. |
| **4. User Management** | Searchable employee directory displaying Username, Display Name, Department, Identity Source (AD/Local), PIN status, Badge ID, Print Pages, Copy Pages, Total Pages, and Page Quotas with Reset PIN and Edit actions. |
| **5. Active Directory & LDAP** | Connection parameters (Host, Port, SSL/TLS, Base DN, Bind DN, Password), Test Connection utility, manual synchronization trigger, and sync status history. |
| **6. Global Print Queue** | Enterprise-wide view of all print jobs with status filters (`HELD`, `COMPLETED`, `CANCELLED`, `EXPIRED`), document names, owners, page counts, submission timestamps, and administrative release/purge actions. |
| **7. Photocopy Logs** | Dedicated hardware copy accounting ledger displaying Timestamp, User Account, MFP Device, IP, Total Pages, Mono Pages, Color Pages, and Copier Job ID with instant "🔄 Poll MFPs Now" button. |
| **8. Security Audit Trail** | Regulatory compliance log recording all authentications (`LOGIN_SUCCESS`, `LOGIN_FAILED`), releases, cancellations, copies, and admin changes with Actor SID, Client IP, and Forensic Metadata. |
| **9. System Settings & Custom Logo** | Global server configurations: custom organization logo upload (displayed in sidebar and kiosk headers), default PIN for self-enrolled users, session timeouts, cost per mono/color page, and data retention windows. |
| **10. System Health & SNMP** | Live visual grid displaying toner gauges, tray capacities, paper jam alarms, and controller connection statuses across all physical MFPs in the fleet. |

---

## 10. Security Architecture & Regulatory Compliance

### Live Document Encryption (AES-256-GCM Authenticated Encryption)
* **Pull Print Spool Files Encryption at Rest:** Every intercepted print job stored on disk (spool_data/<uuid>.bin) is automatically encrypted with **AES-256-GCM** using a random 12-byte IV per file. Raw document bytes are unreadable on the server disk.
* **On-the-Fly RAM Decryption:** When the user enters their PIN/Badge at the Toshiba copier to release jobs, the server decrypts the PCL6 stream entirely in memory and streams it directly to Port 9100.
* **Scan Ingest Encryption at Rest:** Scanned documents captured by the inbound SMTP server from Toshiba MFPs (data/scans/<user>/) are encrypted with AES-256 before disk writing.
* **Backward Compatible NEXP Header:** Encrypted payloads feature a 4-byte NEXP magic header, preserving seamless backward compatibility with any legacy unencrypted archives.


* **Cryptographic Spool Quarantine:** Spool files are isolated in `spool_data/<uuid>.bin` and verified via SHA-256 before socket transmission to prevent file tampering.
* **Credential Protection:** PINs and passwords are protected using industry-standard BCrypt and SHA hashing; plaintext PINs are never stored in database tables.
* **Brute-Force & Rate-Limiting Defense:** Throttles failed authentication attempts, triggering exponential backoff and temporary terminal lockout.
* **Concurrency-Guarded Print Release:** Database row-level optimistic locking guarantees that a held job can never be released simultaneously at two different printers.
* **Role-Based Access Control (RBAC):** Strict separation between Standard Touchscreen Users (PIN/Card release only) and Administrators (Full console access protected by Spring Security).
* **Immutable Audit Trail:** All security and lifecycle events are written to an append-only audit log capturing actor SID, workstation/copier IP, timestamp, action, and outcome.

---

## 11. Database Schema & Data Models

Managed via Flyway versioned migrations (PostgreSQL 13+ production / embedded H2 development):

1. **`users`:** `id`, `username`, `full_name`, `email`, `pin_hash`, `card_id`, `user_sid`, `department`, `role`, `print_count`, `copy_count`, `page_quota`, `identity_source`, `is_active`, `created_at`, `updated_at`.
2. **`print_jobs`:** `id`, `job_uuid`, `user_id`, `document_name`, `page_count`, `color`, `duplex`, `paper_size`, `file_size_bytes`, `sha256_hash`, `spool_file_path`, `status` (`HELD`, `PRINTING`, `COMPLETED`, `CANCELLED`, `EXPIRED`), `cost`, `target_printer_id`, `submitted_at`, `released_at`.
3. **`printers`:** `id`, `printer_code`, `name`, `ip_address`, `port`, `model`, `location`, `is_active`, `last_seen_at`, `toner_levels_json`, `error_status`, `created_at`, `updated_at`.
4. **`virtual_queues`:** `id`, `queue_name`, `share_path`, `driver_name`, `is_default`, `is_active`, `release_pool_json`, `created_at`, `updated_at`.
5. **`copy_jobs`:** `id`, `printer_ip`, `printer_job_id`, `user_id`, `username_raw`, `total_pages`, `mono_pages`, `color_pages`, `status`, `started_at`, `completed_at`, `created_at`.
6. **`scan_jobs`:** `id`, `printer_ip`, `user_id`, `recipient_email`, `page_count`, `file_size_bytes`, `file_format`, `status`, `created_at`.
7. **`directory_configs`:** `id`, `server_type` (`AD`/`LDAP`), `host`, `port`, `use_ssl`, `base_dn`, `bind_dn`, `bind_password`, `sync_interval_minutes`, `last_sync_at`, `last_sync_status`, `created_at`, `updated_at`.
8. **`system_settings`:** `id`, `setting_key`, `setting_value`, `description`, `updated_at`.
9. **`audit_logs`:** `id`, `timestamp`, `event_type`, `actor_username`, `actor_sid`, `target_printer`, `client_ip`, `action_details`, `result_status`.

---

## 12. Complete REST API Catalog

| Endpoint | Method | Description |
| :--- | :--- | :--- |
| `/api/station/auth/pin` | `POST` | PIN authentication at Toshiba kiosk terminal; returns AuthResponse with token and user details. |
| `/api/station/auth/user-pass` | `POST` | Username & Password authentication against Active Directory. |
| `/api/station/auth/card-tap` | `POST` | Automatic RFID card swipe ingest; authenticates user or triggers new card pairing prompt. |
| `/api/station/auth/associate-card` | `POST` | Permanently pairs an unrecognized RFID badge ID with an employee's PIN. |
| `/api/station/logout` | `POST` | Terminates kiosk session and clears terminal state. |
| `/api/station/jobs` | `GET` | Retrieves active held print jobs for authenticated kiosk session. |
| `/api/station/release` | `POST` | Releases selected print jobs to the current physical copier over Port 9100 RAW socket. |
| `/api/station/release-all` | `POST` | Releases all held print jobs for the authenticated user with a single tap. |
| `/api/station/cancel` | `POST` | Cancels and purges selected held jobs. |
| `/api/station/device-functions` | `POST` | Unlocks Toshiba physical copier hardware (`COPY` / `SCAN` / `HOME` mode) via TopAccess CWS. |
| `/api/station/emergency-lock` | `POST` | Immediately enforces hardware panel re-lock upon session finish or idle timeout. |
| `/api/station/status` | `GET` | Terminal heartbeat and printer operational status check. |
| `/api/admin/dashboard/stats` | `GET` | Aggregated KPI counters for Admin Console dashboard. |
| `/api/admin/users` | `GET` | Paginated, searchable list of enterprise users with print/copy totals. |
| `/api/admin/users` | `POST` | Creates or updates an enterprise user profile. |
| `/api/admin/users/{id}/reset-pin` | `POST` | Resets an employee's walk-up PIN code. |
| `/api/admin/printers` | `GET` | Retrieves all registered Toshiba MFPs. |
| `/api/admin/printers` | `POST` | Registers or updates a Toshiba MFP device in the fleet. |
| `/api/admin/printers/{id}/test` | `POST` | Tests network reachability, Port 9100 socket, and TopAccess CWS. |
| `/api/admin/printers/{id}/configure-ewb` | `POST` | Remotely provisions Toshiba EWB kiosk browser settings via TopAccess. |
| `/api/admin/copy-tracking/poll` | `POST` | Manually triggers immediate TopAccess XML photocopy log ingestion. |
| `/api/admin/copy-tracking/logs` | `GET` | Retrieves walk-up photocopy accounting logs. |
| `/api/admin/directory/config` | `GET` | Retrieves Active Directory / LDAP connection parameters. |
| `/api/admin/directory/config` | `POST` | Saves Active Directory / LDAP connection parameters. |
| `/api/admin/directory/sync` | `POST` | Triggers manual Active Directory synchronization. |
| `/api/admin/audit-logs` | `GET` | Searchable, filterable security and operational audit trail. |
| `/api/admin/fleet/snmp` | `GET` | Real-time SNMP telemetry for toners, paper trays, and hardware alerts. |
| `/api/admin/settings` | `GET` | Retrieves system branding, custom logo, and operational parameters. |
| `/api/admin/settings` | `POST` | Updates system branding, custom logo, and operational parameters. |
| `/api/health` | `GET` | System health, database connectivity, and spool storage status. |

---

## 13. Deployment, Automation Scripts & Operations Guide

nexPrint includes enterprise automation scripts in the project root and `scripts/` directory:

* **`start-printnext.bat`:** Single-click Windows batch launcher that compiles and starts the entire production server.
* **`scripts/run-dev.ps1`:** Launches local development server on Port 8080 with embedded H2 database and preloaded demo data.
* **`scripts/run-postgres.ps1`:** Launches production server connected to enterprise PostgreSQL 13+ with Flyway migrations.
* **`scripts/check-prerequisites.ps1`:** Automated environment auditor checking Windows OS version, Java 21+ JDK, Maven, and network adapter IP.
* **`scripts/setup-db.ps1 & setup-postgres.ps1`:** Automates PostgreSQL database creation, user permissions, and schema initialization.
* **`scripts/install-virtual-printer.ps1`:** Creates Windows Virtual Spooler printer (`nexPrint_Queue`) bound to the Toshiba Universal 2 Driver.
* **`scripts/start-spool-monitor.ps1`:** Launches background Windows Spooler file interceptor service.
* **`scripts/simulate-job.ps1`:** Developer utility that injects simulated print jobs for any user (e.g. `jdoe`) to test kiosk release without physical workstations.
* **`scripts/configure-printer-kiosk.ps1`:** Automates TopAccess configuration across a subnet of Toshiba copiers.

---
*Official Technical Specification produced in project root: `H:\ANTIGRAVITY\QCUT\` as `nexPrint_Complete_System_Features_Specification.docx` and `nexPrint_Complete_System_Features_Specification.md` with embedded nexPrint logo (`nexprint_logo.png`).*
