package scratch;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SnmpProbe {

    public static class SnmpEntry {
        public String oid;
        public String value;
        public SnmpEntry(String oid, String value) { this.oid = oid; this.value = value; }
    }

    public static void main(String[] args) throws Exception {
        String ip = args.length > 0 ? args[0] : "192.168.1.15";
        String community = args.length > 1 ? args[1] : "public";

        System.out.println("=================================================");
        System.out.println(" PROBING PRINTER VIA SNMP v2c: " + ip);
        System.out.println("=================================================");

        querySingle(ip, community, "1.3.6.1.2.1.1.1.0", "sysDescr (Device Model/Firmware)");
        querySingle(ip, community, "1.3.6.1.2.1.1.3.0", "sysUpTime (Device Uptime)");
        querySingle(ip, community, "1.3.6.1.2.1.1.5.0", "sysName (Printer Hostname)");
        querySingle(ip, community, "1.3.6.1.2.1.1.6.0", "sysLocation (Device Location)");
        querySingle(ip, community, "1.3.6.1.2.1.25.3.2.1.5.1", "hrDeviceStatus (1=unk, 2=running, 3=warning, 5=down)");
        querySingle(ip, community, "1.3.6.1.2.1.25.3.5.1.1.1", "hrPrinterStatus (1=other, 2=unk, 3=idle, 4=printing, 5=warmup)");
        querySingle(ip, community, "1.3.6.1.2.1.25.3.5.1.2.1", "hrPrinterDetectedErrorState (Error bitmask)");
        querySingle(ip, community, "1.3.6.1.2.1.43.10.2.1.4.1.1", "prtMarkerLifeCount (Total Lifetime Engine Counter)");

        System.out.println("\n--- WALKING TONER & SUPPLIES (1.3.6.1.2.1.43.11.1.1) ---");
        walk(ip, community, "1.3.6.1.2.1.43.11.1.1");

        System.out.println("\n--- WALKING PAPER INPUT TRAYS (1.3.6.1.2.1.43.8.2.1) ---");
        walk(ip, community, "1.3.6.1.2.1.43.8.2.1");

        System.out.println("\n--- WALKING PRINTER ALERTS & CODES (1.3.6.1.2.1.43.18.1.1) ---");
        walk(ip, community, "1.3.6.1.2.1.43.18.1.1");
    }

    private static void querySingle(String ip, String community, String oid, String label) {
        SnmpEntry res = send(ip, community, oid, 0xA0);
        if (res != null) {
            System.out.printf("%-40s = %s%n", label, res.value);
        } else {
            System.out.printf("%-40s = (No response)%n", label);
        }
    }

    private static void walk(String ip, String community, String rootOid) {
        String curOid = rootOid;
        for (int i = 0; i < 40; i++) {
            SnmpEntry res = send(ip, community, curOid, 0xA1);
            if (res == null || res.oid == null) break;
            if (!res.oid.startsWith(rootOid.trim() + ".")) break;
            System.out.println("  " + res.oid + " = " + res.value);
            curOid = res.oid;
        }
    }

    private static SnmpEntry send(String ip, String community, String oid, int pduType) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2000);
            InetAddress addr = InetAddress.getByName(ip);

            byte[] oidBytes = encodeOid(oid);
            byte[] commBytes = community.getBytes(StandardCharsets.US_ASCII);

            ByteArray pdu = new ByteArray();
            pdu.add(0x02, 0x01, 0x05); // req id
            pdu.add(0x02, 0x01, 0x00); // err status
            pdu.add(0x02, 0x01, 0x00); // err idx

            ByteArray vb = new ByteArray();
            vb.add(oidBytes);
            vb.add(0x05, 0x00); // null

            ByteArray vbSeq = new ByteArray();
            vbSeq.add(0x30);
            vbSeq.addLength(vb.size());
            vbSeq.add(vb.toArray());

            ByteArray vbListSeq = new ByteArray();
            vbListSeq.add(0x30);
            vbListSeq.addLength(vbSeq.size());
            vbListSeq.add(vbSeq.toArray());

            pdu.add(vbListSeq.toArray());

            ByteArray pduWrap = new ByteArray();
            pduWrap.add(pduType);
            pduWrap.addLength(pdu.size());
            pduWrap.add(pdu.toArray());

            ByteArray msg = new ByteArray();
            msg.add(0x02, 0x01, 0x01); // v2c
            msg.add(0x04);
            msg.addLength(commBytes.length);
            msg.add(commBytes);
            msg.add(pduWrap.toArray());

            ByteArray pkt = new ByteArray();
            pkt.add(0x30);
            pkt.addLength(msg.size());
            pkt.add(msg.toArray());

            byte[] req = pkt.toArray();
            socket.send(new DatagramPacket(req, req.length, addr, 161));

            byte[] buf = new byte[4096];
            DatagramPacket respPkt = new DatagramPacket(buf, buf.length);
            socket.receive(respPkt);

            return parse(buf, respPkt.getLength());
        } catch (Exception ex) {
            return null;
        }
    }

    private static SnmpEntry parse(byte[] data, int len) {
        try {
            int idx = 0;
            if (data[idx++] != 0x30) return null;
            idx += skipLen(data, idx);

            // version
            if (data[idx++] != 0x02) return null;
            idx += data[idx++] + 0;

            // comm
            if (data[idx++] != 0x04) return null;
            int cLen = readLen(data, idx);
            idx += skipLen(data, idx) + cLen;

            // pdu
            byte pduType = data[idx++];
            idx += skipLen(data, idx);

            // req id
            if (data[idx++] != 0x02) return null;
            idx += data[idx++] + 0;

            // err status
            if (data[idx++] != 0x02) return null;
            idx += data[idx++] + 0;

            // err idx
            if (data[idx++] != 0x02) return null;
            idx += data[idx++] + 0;

            // vb list
            if (data[idx++] != 0x30) return null;
            idx += skipLen(data, idx);

            // vb
            if (data[idx++] != 0x30) return null;
            idx += skipLen(data, idx);

            // oid
            if (data[idx++] != 0x06) return null;
            int oLen = readLen(data, idx);
            idx += skipLen(data, idx);
            String retOid = decodeOid(data, idx, oLen);
            idx += oLen;

            // val
            byte valType = data[idx++];
            int vLen = readLen(data, idx);
            idx += skipLen(data, idx);

            String valStr = "";
            if (valType == 0x02) {
                long iv = 0;
                boolean neg = (data[idx] & 0x80) != 0;
                for (int i = 0; i < vLen; i++) iv = (iv << 8) | (data[idx + i] & 0xFF);
                if (neg && vLen == 4) iv = (int) iv;
                valStr = String.valueOf(iv);
            } else if (valType == 0x04) {
                valStr = new String(data, idx, vLen, StandardCharsets.UTF_8).trim();
            } else if (valType == 0x43 || valType == 0x41 || valType == 0x42) {
                long c = 0;
                for (int i = 0; i < vLen; i++) c = (c << 8) | (data[idx + i] & 0xFF);
                valStr = String.valueOf(c);
            } else {
                valStr = "[Type 0x" + Integer.toHexString(valType & 0xFF) + ", len " + vLen + "]";
            }

            return new SnmpEntry(retOid, valStr);
        } catch (Exception e) {
            return null;
        }
    }

    private static int skipLen(byte[] data, int idx) {
        if ((data[idx] & 0x80) == 0) return 1;
        return 1 + (data[idx] & 0x7F);
    }

    private static int readLen(byte[] data, int idx) {
        if ((data[idx] & 0x80) == 0) return data[idx] & 0xFF;
        int num = data[idx] & 0x7F;
        int l = 0;
        for (int i = 1; i <= num; i++) l = (l << 8) | (data[idx + i] & 0xFF);
        return l;
    }

    private static String decodeOid(byte[] data, int start, int len) {
        StringBuilder sb = new StringBuilder("1.3");
        for (int i = 1; i < len; i++) {
            long val = 0;
            while (i < len) {
                byte b = data[start + i];
                val = (val << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) break;
                i++;
            }
            sb.append('.').append(val);
        }
        return sb.toString();
    }

    private static byte[] encodeOid(String oidStr) {
        String[] parts = oidStr.trim().split("\\.");
        ByteArray body = new ByteArray();
        body.add(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            long val = Long.parseLong(parts[i]);
            List<Byte> sub = new ArrayList<>();
            sub.add((byte) (val & 0x7F));
            val >>= 7;
            while (val > 0) {
                sub.add(0, (byte) ((val & 0x7F) | 0x80));
                val >>= 7;
            }
            for (byte b : sub) body.add(b & 0xFF);
        }
        ByteArray res = new ByteArray();
        res.add(0x06);
        res.addLength(body.size());
        res.add(body.toArray());
        return res.toArray();
    }

    private static class ByteArray {
        private final List<Byte> list = new ArrayList<>();
        public void add(int... bytes) { for (int b : bytes) list.add((byte) b); }
        public void add(byte[] bytes) { for (byte b : bytes) list.add(b); }
        public void addLength(int len) {
            if (len < 128) { list.add((byte) len); return; }
            List<Byte> temp = new ArrayList<>();
            int t = len;
            while (t > 0) { temp.add(0, (byte) (t & 0xFF)); t >>= 8; }
            list.add((byte) (0x80 | temp.size()));
            list.addAll(temp);
        }
        public int size() { return list.size(); }
        public byte[] toArray() {
            byte[] arr = new byte[list.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
            return arr;
        }
    }
}
