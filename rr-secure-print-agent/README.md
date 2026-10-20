# RR Secure Print — Windows Print Spooler Architecture & Integration Guide

**Author:** Senior Windows Print-Server Engineer & Security Architect  
**Target Device:** Toshiba e-STUDIO 3518A Multifunction Printer  
**Module:** `rr-secure-print-agent`

---

## 1. Executive Summary & Design Goals

The objective of RR Secure Print is to provide enterprise-grade secure pull-printing (Hold & Release) without relying on third-party proprietary software (e.g., PaperCut MF, Equitrac, or e-BRIDGE Open Platform embedded middleware).

### Critical Architectural Principle:
> **The system must hold jobs *before* they reach the physical printer. It must NOT implement holding by simply pausing jobs inside the physical production queue.**

Pausing jobs in an active hardware queue is unsafe because:
1. If the Windows Print Spooler service restarts or crashes, paused jobs can inadvertently auto-resume and print sensitive documents without employee authentication.
2. In-queue pausing locks the physical printer port, preventing other jobs from streaming.
3. Spooler corruption in one job blocks all queued traffic for that device.

---

## 2. Technical Evaluation of Windows Print Subsystem Integration Points

Windows Print Subsystem (`spoolsv.exe`) offers several architectural integration levels. We evaluated the three primary approaches:

| Integration Architecture | Implementation Mechanism | Pros | Cons / Verdict |
| :--- | :--- | :--- | :--- |
| **A. Custom Print Processor** | DLL implementing `PrintDocumentOnPrintProcessor` | Intercepts spool data before rendering | High risk of spooler crash; requires kernel/driver signing; complex C++ debugging. |
| **B. Custom Port Monitor** | DLL implementing `InitializePrintMonitor2`, `WritePort` | Intercepts raw printer-ready bytes during transmission | Runs as `SYSTEM` inside `spoolsv.exe`. Any unhandled exception halts all printing across the server. |
| **C. Virtual "Pull Queue" with File/Null Port & Spool Interceptor** *(Recommended Standard)* | Dedicated Windows Print Queue configured with Toshiba Universal Driver + Port Redirection | **100% stable; completely isolated from production queue; zero risk of spooler crash; extracts immutable Active Directory user SID from `JOB_INFO_2`.** | Highly recommended for production pull printing. |

---

## 3. The RR Secure Print Architecture

```
[ Workstation / CAD / PDF ]
           │
           │  (1) Standard SMB / RPC Print Job
           ▼
[ Windows Print Spooler (spoolsv.exe) ]
   └── Queue: "RR Secure Pull Print" (Virtual Queue)
   └── Driver: Toshiba e-STUDIO Universal PCL6 / PS Driver
           │
           │  (2) Spooler writes printer-ready .SPL (PCL6) and .SHD (Shadow Metadata)
           ▼
[ C:\Windows\System32\spool\PRINTERS\ ]
           │
           │  (3) RR Secure Print Agent intercepts & quarantines:
           │      - Reads pUserSid (Active Directory SID) from JOB_INFO_2
           │      - Moves .SPL to protected spool_data/<uuid>.bin
           │      - Computes SHA-256 cryptographic checksum
           │      - Cancels/purges job from Windows Spooler
           ▼
[ RR Secure Print Server (PostgreSQL + Local Spool Storage) ]
   └── Job State: HELD
           │
           │  (4) Employee taps PIN / badge on Toshiba e-STUDIO 3518A EWB Touchscreen
           │  (5) Server verifies ownership via AD User SID
           │  (6) Concurrency-guarded state transition: HELD -> RELEASING -> COMPLETED
           ▼
[ Raw Socket Streaming (Port 9100 / JetDirect) ]
           │
           │  (7) Streams verified PCL6 bytes directly to printer TCP buffer
           ▼
[ Toshiba e-STUDIO 3518A Physical Print Engine ]
```

---

## 4. Capturing User SID and Print Settings

When a user prints from Windows:
1. The print job contains the caller's Windows Security Identifier (SID) in `JOB_INFO_2.pUserSid`.
2. This SID (`S-1-5-21-...`) is immutable and cryptographically bound to the Kerberos/NTLM authentication token of the Windows session.
3. RR Secure Print uses this SID as the **primary ownership identifier**. The browser-submitted username is never trusted for authorization.
4. Document settings (Duplex, Page Count, Color Mode, Paper Size) are parsed from the PCL6/PJL header embedded in the spool file.

---

## 5. Concurrency & Re-entrancy Safety

To prevent double-printing or race conditions when users tap "Release Selected" multiple times rapidly:
1. **Database Level:** Atomic CAS (Compare-And-Swap) `UPDATE print_jobs SET status = 'RELEASING' WHERE id = :id AND status = 'HELD'`.
2. **JPA Level:** `@Version` optimistic locking.
3. **UI Level:** Touch action buttons enter `isBusy` lock immediately on touch.

---

## 6. Real vs Simulated Print Release

In **Simulation Mode** (`app.printer.simulation-mode: true`):
- Spool data is verified and processed through the complete lifecycle.
- Database records transition to `COMPLETED`.
- Audit logs record simulated transmission.
- **No TCP socket connection is opened to port 9100**, ensuring total safety during development.

In **Live Production Mode** (`app.printer.simulation-mode: false`):
- The server opens a standard TCP socket to `192.168.1.150:9100`.
- Spool data is streamed directly to the Toshiba print engine.
