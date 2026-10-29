param([string]$ip = "192.168.1.15")

Add-Type -TypeDefinition @"
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
                udp.Client.ReceiveTimeout = 1500;
                udp.Client.SendTimeout = 1500;
                udp.Connect(ip, 161);
                byte[] oidBytes = EncodeOid(oid);
                byte[] commBytes = Encoding.ASCII.GetBytes(community);

                List<byte> pdu = new List<byte>();
                pdu.AddRange(new byte[] { 0x02, 0x01, 0x01, 0x02, 0x01, 0x00, 0x02, 0x01, 0x00 });
                List<byte> varbind = new List<byte>();
                varbind.AddRange(oidBytes);
                varbind.AddRange(new byte[] { 0x05, 0x00 });
                List<byte> varbindSeq = new List<byte>();
                varbindSeq.Add(0x30); varbindSeq.AddRange(EncodeLength(varbind.Count)); varbindSeq.AddRange(varbind);
                List<byte> varbindListSeq = new List<byte>();
                varbindListSeq.Add(0x30); varbindListSeq.AddRange(EncodeLength(varbindSeq.Count)); varbindListSeq.AddRange(varbindSeq);
                pdu.AddRange(varbindListSeq);

                byte pduType = isGetNext ? (byte)0xA1 : (byte)0xA0;
                List<byte> pduWrapper = new List<byte>();
                pduWrapper.Add(pduType); pduWrapper.AddRange(EncodeLength(pdu.Count)); pduWrapper.AddRange(pdu);
                List<byte> msg = new List<byte>();
                msg.AddRange(new byte[] { 0x02, 0x01, 0x01, 0x04 });
                msg.AddRange(EncodeLength(commBytes.Length)); msg.AddRange(commBytes); msg.AddRange(pduWrapper);
                List<byte> packet = new List<byte>();
                packet.Add(0x30); packet.AddRange(EncodeLength(msg.Count)); packet.AddRange(msg);
                byte[] pb = packet.ToArray();
                udp.Send(pb, pb.Length);
                IPEndPoint ep = new IPEndPoint(IPAddress.Any, 0);
                byte[] resp = udp.Receive(ref ep);
                return ParseResponse(resp);
            }
        } catch { return null; }
    }

    public static List<SnmpItem> Walk(string ip, string community, string rootOid, int maxCount) {
        var list = new List<SnmpItem>();
        string cur = rootOid;
        for (int i = 0; i < maxCount; i++) {
            var it = Query(ip, community, cur, true);
            if (it == null || string.IsNullOrEmpty(it.Oid) || !it.Oid.StartsWith(rootOid.TrimEnd('.') + ".")) break;
            if (list.Exists(x => x.Oid == it.Oid)) break;
            list.Add(it);
            cur = it.Oid;
        }
        return list;
    }

    private static SnmpItem ParseResponse(byte[] data) {
        try {
            int idx = 0;
            if (data[idx++] != 0x30) return null;
            idx = SkipLen(data, idx);
            if (data[idx++] != 0x02) return null;
            idx += data[idx++] + 1; // comm
            idx += data[idx++] + 1; // pdu
            idx = SkipLen(data, idx);
            idx += data[idx++] + 1; // req id
            int err = data[idx + 1];
            idx += data[idx++] + 1;
            idx += data[idx++] + 1; // err idx
            if (err != 0) return new SnmpItem("", "ERR_" + err, 0);
            if (data[idx++] != 0x30) return null;
            idx = SkipLen(data, idx);
            if (data[idx++] != 0x30) return null;
            idx = SkipLen(data, idx);
            if (data[idx++] != 0x06) return null;
            int oLen = ReadLen(data, ref idx);
            string oid = DecodeOid(data, idx, oLen);
            idx += oLen;
            byte vt = data[idx++];
            int vLen = ReadLen(data, ref idx);
            string val = "";
            if (vt == 0x02) {
                long iv = 0;
                for (int i = 0; i < vLen; i++) iv = (iv << 8) | (uint)data[idx + i];
                val = iv.ToString();
            } else if (vt == 0x04) {
                val = Encoding.UTF8.GetString(data, idx, vLen).Trim();
            } else if (vt == 0x43 || vt == 0x41 || vt == 0x42) {
                uint ticks = 0;
                for (int i = 0; i < vLen; i++) ticks = (ticks << 8) | (uint)data[idx + i];
                val = ticks.ToString();
            } else {
                val = BitConverter.ToString(data, idx, vLen);
            }
            return new SnmpItem(oid, val, vt);
        } catch { return null; }
    }

    private static int SkipLen(byte[] data, int idx) {
        if ((data[idx] & 0x80) == 0) return idx + 1;
        return idx + 1 + (data[idx] & 0x7F);
    }
    private static int ReadLen(byte[] data, ref int idx) {
        if ((data[idx] & 0x80) == 0) return data[idx++];
        int num = data[idx++] & 0x7F;
        int l = 0;
        for (int i = 0; i < num; i++) l = (l << 8) | (int)data[idx++];
        return l;
    }
    private static string DecodeOid(byte[] d, int s, int l) {
        var p = new List<string> { (d[s] / 40).ToString(), (d[s] % 40).ToString() };
        ulong v = 0;
        for (int i = 1; i < l; i++) {
            byte b = d[s + i];
            v = (v << 7) + (ulong)(b & 0x7F);
            if ((b & 0x80) == 0) { p.Add(v.ToString()); v = 0; }
        }
        return string.Join(".", p);
    }
    private static byte[] EncodeLength(int length) {
        if (length < 128) return new byte[] { (byte)length };
        var list = new List<byte>();
        int temp = length;
        while (temp > 0) { list.Insert(0, (byte)(temp & 0xFF)); temp >>= 8; }
        list.Insert(0, (byte)(0x80 | list.Count));
        return list.ToArray();
    }
    private static byte[] EncodeOid(string oidStr) {
        string[] parts = oidStr.Trim('.').Split('.');
        var body = new List<byte>();
        body.Add((byte)(int.Parse(parts[0]) * 40 + int.Parse(parts[1])));
        for (int i = 2; i < parts.Length; i++) {
            ulong val = ulong.Parse(parts[i]);
            var sub = new List<byte>();
            sub.Add((byte)(val & 0x7F)); val >>= 7;
            while (val > 0) { sub.Insert(0, (byte)((val & 0x7F) | 0x80)); val >>= 7; }
            body.AddRange(sub);
        }
        var res = new List<byte> { 0x06 };
        res.AddRange(EncodeLength(body.Count)); res.AddRange(body);
        return res.ToArray();
    }
}
"@ -Language CSharp

Write-Host "Probing SNMP Error States & Engine on $ip..."
# hrPrinterStatus: 1=other, 2=unknown, 3=idle, 4=printing, 5=warmup
$prs = [SnmpClient]::Query($ip, "public", "1.3.6.1.2.1.25.3.5.1.1.1", $false)
Write-Host "hrPrinterStatus: $($prs.Value) (3=idle, 4=printing, 5=warmup)"

# hrPrinterDetectedErrorState
$errBits = [SnmpClient]::Query($ip, "public", "1.3.6.1.2.1.25.3.5.1.2.1", $false)
Write-Host "hrPrinterDetectedErrorState: $($errBits.Value) (Hex: $(if ($errBits.Value) { $errBits.Value } else { 'none' }))"

Write-Host "`n--- prtAlertTable Full Walk ---"
$alerts = [SnmpClient]::Walk($ip, "public", "1.3.6.1.2.1.43.18.1.1", 30)
$alerts | ForEach-Object { Write-Host "  $($_.Oid) = $($_.Value)" }

Write-Host "`n--- prtMarkerSuppliesTable (All Consumables: Toner, Waste, Drum) ---"
$sup = [SnmpClient]::Walk($ip, "public", "1.3.6.1.2.1.43.11.1.1", 30)
$sup | ForEach-Object { Write-Host "  $($_.Oid) = $($_.Value)" }

Write-Host "`n--- prtGeneralTable & Current Job ---"
$gen = [SnmpClient]::Walk($ip, "public", "1.3.6.1.2.1.43.5.1.1", 20)
$gen | ForEach-Object { Write-Host "  $($_.Oid) = $($_.Value)" }
