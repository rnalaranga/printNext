param(
    [string]$ip = "192.168.1.15",
    [string]$comm = "public"
)

$code = @'
using System;
using System.Net;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Text;

public class SnmpItem {
    public string Oid;
    public string Value;
    public byte Type;
    public SnmpItem(string o, string v, byte t) { Oid = o; Value = v; Type = t; }
}

public class SnmpClient {
    public static SnmpItem Query(string ip, string community, string oid, bool isGetNext) {
        try {
            using (var udp = new UdpClient()) {
                udp.Client.ReceiveTimeout = 2000;
                udp.Client.SendTimeout = 2000;
                udp.Connect(ip, 161);

                byte[] oidBytes = EncodeOid(oid);
                byte[] commBytes = Encoding.ASCII.GetBytes(community);

                List<byte> pdu = new List<byte>();
                pdu.AddRange(new byte[] { 0x02, 0x01, 0x01 }); // req id
                pdu.AddRange(new byte[] { 0x02, 0x01, 0x00 }); // err status
                pdu.AddRange(new byte[] { 0x02, 0x01, 0x00 }); // err idx

                List<byte> varbind = new List<byte>();
                varbind.AddRange(oidBytes);
                varbind.AddRange(new byte[] { 0x05, 0x00 }); // null

                List<byte> varbindSeq = new List<byte>();
                varbindSeq.Add(0x30);
                varbindSeq.AddRange(EncodeLength(varbind.Count));
                varbindSeq.AddRange(varbind);

                List<byte> varbindListSeq = new List<byte>();
                varbindListSeq.Add(0x30);
                varbindListSeq.AddRange(EncodeLength(varbindSeq.Count));
                varbindListSeq.AddRange(varbindSeq);

                pdu.AddRange(varbindListSeq);

                byte pduType = isGetNext ? (byte)0xA1 : (byte)0xA0;
                List<byte> pduWrapper = new List<byte>();
                pduWrapper.Add(pduType);
                pduWrapper.AddRange(EncodeLength(pdu.Count));
                pduWrapper.AddRange(pdu);

                List<byte> msg = new List<byte>();
                msg.AddRange(new byte[] { 0x02, 0x01, 0x01 }); // SNMP v2c
                msg.Add(0x04);
                msg.AddRange(EncodeLength(commBytes.Length));
                msg.AddRange(commBytes);
                msg.AddRange(pduWrapper);

                List<byte> packet = new List<byte>();
                packet.Add(0x30);
                packet.AddRange(EncodeLength(msg.Count));
                packet.AddRange(msg);

                byte[] packetBytes = packet.ToArray();
                udp.Send(packetBytes, packetBytes.Length);

                IPEndPoint ep = new IPEndPoint(IPAddress.Any, 0);
                byte[] resp = udp.Receive(ref ep);
                return ParseResponse(resp);
            }
        } catch (Exception ex) {
            return new SnmpItem(oid, "ERR: " + ex.Message, 0);
        }
    }

    public static List<SnmpItem> Walk(string ip, string community, string rootOid, int maxCount) {
        var list = new List<SnmpItem>();
        string currentOid = rootOid;

        for (int i = 0; i < maxCount; i++) {
            SnmpItem item = Query(ip, community, currentOid, true);
            if (item == null || string.IsNullOrEmpty(item.Oid)) break;
            if (item.Value.StartsWith("ERR:")) break;
            if (!item.Oid.StartsWith(rootOid.TrimEnd('.') + ".") && item.Oid != rootOid) break;
            if (list.Exists(x => x.Oid == item.Oid)) break;
            list.Add(item);
            currentOid = item.Oid;
        }
        return list;
    }

    private static SnmpItem ParseResponse(byte[] data) {
        int idx = 0;
        if (data[idx++] != 0x30) return null;
        idx = SkipLength(data, idx);

        if (data[idx++] != 0x02) return null;
        int vLen = data[idx++];
        idx += vLen;

        if (data[idx++] != 0x04) return null;
        int cLen = data[idx++];
        idx += cLen;

        byte pduType = data[idx++];
        idx = SkipLength(data, idx);

        idx += 1;
        int rLen = data[idx++];
        idx += rLen;

        idx += 1;
        int eLen = data[idx++];
        int errStatus = data[idx];
        idx += eLen;

        idx += 1;
        int eiLen = data[idx++];
        idx += eiLen;

        if (errStatus != 0) return new SnmpItem("", "SNMP_ERR_" + errStatus, 0);

        if (data[idx++] != 0x30) return null;
        idx = SkipLength(data, idx);

        if (data[idx++] != 0x30) return null;
        idx = SkipLength(data, idx);

        if (data[idx++] != 0x06) return null;
        int oidLen = ReadLength(data, ref idx);
        string retOid = DecodeOid(data, idx, oidLen);
        idx += oidLen;

        byte valType = data[idx++];
        int valLen = ReadLength(data, ref idx);
        string valStr = "";

        if (valType == 0x02) {
            long intVal = 0;
            bool neg = (data[idx] & 0x80) != 0;
            for (int i = 0; i < valLen; i++) intVal = (intVal << 8) | (uint)data[idx + i];
            if (neg && valLen == 4) intVal = (int)intVal;
            else if (neg && valLen == 1) intVal = (sbyte)intVal;
            else if (neg && valLen == 2) intVal = (short)intVal;
            valStr = intVal.ToString();
        } else if (valType == 0x04) {
            valStr = Encoding.UTF8.GetString(data, idx, valLen).Trim();
        } else if (valType == 0x43) {
            uint ticks = 0;
            for (int i = 0; i < valLen; i++) ticks = (ticks << 8) | (uint)data[idx + i];
            TimeSpan s = TimeSpan.FromMilliseconds(ticks * 10);
            valStr = s.Days + "d " + s.Hours + "h " + s.Minutes + "m " + s.Seconds + "s (" + ticks + " ticks)";
        } else if (valType == 0x41 || valType == 0x42) {
            uint cnt = 0;
            for (int i = 0; i < valLen; i++) cnt = (cnt << 8) | (uint)data[idx + i];
            valStr = cnt.ToString();
        } else {
            valStr = "[Type 0x" + valType.ToString("X2") + " Len " + valLen + "]: " + BitConverter.ToString(data, idx, valLen);
        }

        return new SnmpItem(retOid, valStr, valType);
    }

    private static int SkipLength(byte[] data, int idx) {
        if ((data[idx] & 0x80) == 0) return idx + 1;
        int num = data[idx] & 0x7F;
        return idx + 1 + num;
    }

    private static int ReadLength(byte[] data, ref int idx) {
        if ((data[idx] & 0x80) == 0) return data[idx++];
        int num = data[idx++] & 0x7F;
        int l = 0;
        for (int i = 0; i < num; i++) l = (l << 8) | (int)data[idx++];
        return l;
    }

    private static string DecodeOid(byte[] data, int start, int len) {
        List<string> parts = new List<string>();
        int first = data[start];
        parts.Add((first / 40).ToString());
        parts.Add((first % 40).ToString());

        ulong val = 0;
        for (int i = 1; i < len; i++) {
            byte b = data[start + i];
            val = (val << 7) + (ulong)(b & 0x7F);
            if ((b & 0x80) == 0) {
                parts.Add(val.ToString());
                val = 0;
            }
        }
        return string.Join(".", parts);
    }

    private static byte[] EncodeLength(int length) {
        if (length < 128) return new byte[] { (byte)length };
        List<byte> list = new List<byte>();
        int temp = length;
        while (temp > 0) {
            list.Insert(0, (byte)(temp & 0xFF));
            temp >>= 8;
        }
        list.Insert(0, (byte)(0x80 | list.Count));
        return list.ToArray();
    }

    private static byte[] EncodeOid(string oidStr) {
        string[] parts = oidStr.Trim('.').Split('.');
        List<byte> body = new List<byte>();
        int first = int.Parse(parts[0]) * 40 + int.Parse(parts[1]);
        body.Add((byte)first);
        for (int i = 2; i < parts.Length; i++) {
            ulong val = ulong.Parse(parts[i]);
            List<byte> sub = new List<byte>();
            sub.Add((byte)(val & 0x7F));
            val >>= 7;
            while (val > 0) {
                sub.Insert(0, (byte)((val & 0x7F) | 0x80));
                val >>= 7;
            }
            body.AddRange(sub);
        }
        List<byte> res = new List<byte>();
        res.Add(0x06);
        res.AddRange(EncodeLength(body.Count));
        res.AddRange(body);
        return res.ToArray();
    }
}
'@

Add-Type -TypeDefinition $code -Language CSharp

Write-Host "`n===========================================================" -ForegroundColor Green
Write-Host " REAL LIVE SNMP QUERY FOR TOSHIBA MFP: $ip" -ForegroundColor Green
Write-Host "===========================================================" -ForegroundColor Green

Write-Host "--- 1. BASIC DEVICE IDENTITY ---" -ForegroundColor Cyan
Write-Host "Model / sysDescr:         $([SnmpClient]::Query($ip, $comm, '1.3.6.1.2.1.1.1.0', $false).Value)"
Write-Host "Uptime / sysUpTime:        $([SnmpClient]::Query($ip, $comm, '1.3.6.1.2.1.1.3.0', $false).Value)"
Write-Host "Hostname / sysName:        $([SnmpClient]::Query($ip, $comm, '1.3.6.1.2.1.1.5.0', $false).Value)"
Write-Host "Location / sysLocation:    $([SnmpClient]::Query($ip, $comm, '1.3.6.1.2.1.1.6.0', $false).Value)"

Write-Host "`n--- 2. PRINTER STATUS & STATE ---" -ForegroundColor Cyan
# hrDeviceStatus: 1=unknown, 2=running, 3=warning, 4=testing, 5=down
$devStat = [SnmpClient]::Query($ip, $comm, '1.3.6.1.2.1.25.3.2.1.5.1', $false).Value
Write-Host "hrDeviceStatus:            $devStat (2=Running/Healthy, 3=Warning, 5=Down)"
# hrPrinterStatus: 1=other, 2=unknown, 3=idle, 4=printing, 5=warmup
$prnStat = [SnmpClient]::Query($ip, $comm, '1.3.6.1.2.1.25.3.5.1.1.1', $false).Value
Write-Host "hrPrinterStatus:           $prnStat (3=Idle/Ready, 4=Printing, 5=Warmup)"
# hrPrinterDetectedErrorState
$errBits = [SnmpClient]::Query($ip, $comm, '1.3.6.1.2.1.25.3.5.1.2.1', $false).Value
Write-Host "hrPrinterDetectedErrors:   $errBits"

Write-Host "`n--- 3. LIFETIME ENGINE TOTAL COUNTER ---" -ForegroundColor Cyan
$lifeCounter = [SnmpClient]::Query($ip, $comm, '1.3.6.1.2.1.43.10.2.1.4.1.1', $false).Value
Write-Host "Total Life Page Count:     $lifeCounter"

Write-Host "`n--- 4. TONER & CONSUMABLES WALK (prtMarkerSuppliesTable) ---" -ForegroundColor Cyan
$supplies = [SnmpClient]::Walk($ip, $comm, '1.3.6.1.2.1.43.11.1.1', 30)
$supplies | ForEach-Object {
    Write-Host "  $($_.Oid) = $($_.Value)"
}

Write-Host "`n--- 5. INPUT TRAYS WALK (prtInputTable) ---" -ForegroundColor Cyan
$trays = [SnmpClient]::Walk($ip, $comm, '1.3.6.1.2.1.43.8.2.1', 30)
$trays | ForEach-Object {
    Write-Host "  $($_.Oid) = $($_.Value)"
}

Write-Host "`n--- 6. PRINTER ALERTS WALK (prtAlertTable) ---" -ForegroundColor Cyan
$alerts = [SnmpClient]::Walk($ip, $comm, '1.3.6.1.2.1.43.18.1.1', 20)
$alerts | ForEach-Object {
    Write-Host "  $($_.Oid) = $($_.Value)"
}
