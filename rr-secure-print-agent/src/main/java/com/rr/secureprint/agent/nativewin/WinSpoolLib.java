package com.rr.secureprint.agent.nativewin;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import java.util.Arrays;
import java.util.List;

/**
 * JNA Mapping for Windows Spooler API (winspool.drv).
 * Used by RR Secure Print Windows Agent to query job status and manage spool queues.
 */
public interface WinSpoolLib extends Library {

    WinSpoolLib INSTANCE = Native.load("winspool.drv", WinSpoolLib.class);

    boolean OpenPrinterW(String pPrinterName, HANDLE[] phPrinter, Pointer pDefault);
    boolean ClosePrinter(HANDLE hPrinter);
    boolean GetJobW(HANDLE hPrinter, int JobId, int Level, Pointer pJob, int cbBuf, IntByReference pcbNeeded);
    boolean SetJobW(HANDLE hPrinter, int JobId, int Level, Pointer pJob, int Command);

    int JOB_CONTROL_PAUSE = 1;
    int JOB_CONTROL_RESUME = 2;
    int JOB_CONTROL_CANCEL = 3;
    int JOB_CONTROL_RESTART = 4;
    int JOB_CONTROL_DELETE = 5;

    class PRINTER_DEFAULTS extends Structure {
        public Pointer pDatatype;
        public Pointer pDevMode;
        public int DesiredAccess;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("pDatatype", "pDevMode", "DesiredAccess");
        }
    }
}
