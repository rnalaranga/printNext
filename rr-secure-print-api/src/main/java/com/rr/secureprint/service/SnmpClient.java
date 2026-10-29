package com.rr.secureprint.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SnmpClient {

    private static final AtomicInteger REQ_COUNTER = new AtomicInteger(1000);

    public static class SnmpItem {
        private final String oid;
        private final String value;
        private final int type;

        public SnmpItem(String oid, String value, int type) {
            this.oid = oid;
            this.value = value;
            this.type = type;
        }

        public String getOid() { return oid; }
        public String getValue() { return value; }
        public int getType() { return type; }

        @Override
        public String toString() {
            return oid + " = " + value + " (type 0x" + Integer.toHexString(type) + ")";
        }
    }

    public static SnmpItem query(String ip, String community, String oid, boolean isGetNext, int timeoutMs) {
        return query(ip, 161, community, oid, isGetNext, timeoutMs);
    }

    public static SnmpItem query(String ip, int port, String community, String oid, boolean isGetNext, int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs > 0 ? timeoutMs : 1500);
            InetAddress address = InetAddress.getByName(ip);

            byte[] requestPacket = buildSnmpPacket(community, oid, isGetNext, REQ_COUNTER.incrementAndGet());
            DatagramPacket sendPacket = new DatagramPacket(requestPacket, requestPacket.length, address, port);
            socket.send(sendPacket);

            byte[] buffer = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);

            byte[] responseData = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), responseData, 0, receivePacket.getLength());

            return parseResponse(responseData);
        } catch (SocketTimeoutException te) {
            return null; // device unreachable or timed out
        } catch (Exception ex) {
            return null;
        }
    }

    public static List<SnmpItem> walk(String ip, String community, String rootOid, int maxCount, int timeoutMs) {
        return walk(ip, 161, community, rootOid, maxCount, timeoutMs);
    }

    public static List<SnmpItem> walk(String ip, int port, String community, String rootOid, int maxCount, int timeoutMs) {
        List<SnmpItem> results = new ArrayList<>();
        String currentOid = rootOid;
        String cleanRoot = rootOid.endsWith(".") ? rootOid.substring(0, rootOid.length() - 1) : rootOid;

        for (int i = 0; i < maxCount; i++) {
            SnmpItem item = query(ip, port, community, currentOid, true, timeoutMs);
            if (item == null || item.getOid() == null || item.getOid().isEmpty()) {
                break;
            }
            if (item.getValue().startsWith("ERR:") || item.getValue().startsWith("SNMP_ERR_")) {
                break;
            }
            if (!item.getOid().startsWith(cleanRoot + ".") && !item.getOid().equals(cleanRoot)) {
                break; // walked past subtree
            }
            // detect loops
            boolean alreadyExists = false;
            for (SnmpItem ex : results) {
                if (ex.getOid().equals(item.getOid())) {
                    alreadyExists = true;
                    break;
                }
            }
            if (alreadyExists) {
                break;
            }

            results.add(item);
            currentOid = item.getOid();
        }

        return results;
    }

    /* =========================================================================
       ASN.1 ENCODING
       ========================================================================= */

    private static byte[] buildSnmpPacket(String community, String oid, boolean isGetNext, int reqId) throws IOException {
        byte[] oidBytes = encodeOid(oid);
        byte[] commBytes = community.getBytes(StandardCharsets.US_ASCII);

        // Varbind = SEQUENCE { OID, NULL }
        ByteArrayOutputStream varbind = new ByteArrayOutputStream();
        varbind.write(oidBytes);
        varbind.write(new byte[]{0x05, 0x00}); // NULL
        byte[] varbindBytes = wrapSequence(varbind.toByteArray());

        // VarbindList = SEQUENCE { Varbind }
        byte[] varbindList = wrapSequence(varbindBytes);

        // PDU
        ByteArrayOutputStream pdu = new ByteArrayOutputStream();
        // Request ID (Integer)
        byte[] reqIdBytes = encodeInteger(reqId);
        pdu.write(reqIdBytes);
        // Error status (0)
        pdu.write(new byte[]{0x02, 0x01, 0x00});
        // Error index (0)
        pdu.write(new byte[]{0x02, 0x01, 0x00});
        // VarbindList
        pdu.write(varbindList);

        byte pduTag = isGetNext ? (byte) 0xA1 : (byte) 0xA0;
        byte[] pduWrapped = wrapTag(pduTag, pdu.toByteArray());

        // SNMP Message: SEQUENCE { version (2c = 1), community, pdu }
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        msg.write(new byte[]{0x02, 0x01, 0x01}); // SNMP v2c (1)
        msg.write(0x04); // OCTET STRING
        msg.write(encodeLength(commBytes.length));
        msg.write(commBytes);
        msg.write(pduWrapped);

        return wrapSequence(msg.toByteArray());
    }

    private static byte[] wrapSequence(byte[] data) throws IOException {
        return wrapTag((byte) 0x30, data);
    }

    private static byte[] wrapTag(byte tag, byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        out.write(encodeLength(data.length));
        out.write(data);
        return out.toByteArray();
    }

    private static byte[] encodeLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        }
        List<Byte> bytes = new ArrayList<>();
        int temp = length;
        while (temp > 0) {
            bytes.add(0, (byte) (temp & 0xFF));
            temp >>= 8;
        }
        byte[] res = new byte[bytes.size() + 1];
        res[0] = (byte) (0x80 | bytes.size());
        for (int i = 0; i < bytes.size(); i++) {
            res[i + 1] = bytes.get(i);
        }
        return res;
    }

    private static byte[] encodeInteger(int val) {
        if (val >= -128 && val <= 127) {
            return new byte[]{0x02, 0x01, (byte) val};
        }
        if (val >= -32768 && val <= 32767) {
            return new byte[]{0x02, 0x02, (byte) ((val >> 8) & 0xFF), (byte) (val & 0xFF)};
        }
        return new byte[]{0x02, 0x04,
                (byte) ((val >> 24) & 0xFF),
                (byte) ((val >> 16) & 0xFF),
                (byte) ((val >> 8) & 0xFF),
                (byte) (val & 0xFF)};
    }

    private static byte[] encodeOid(String oidStr) throws IOException {
        String clean = oidStr.startsWith(".") ? oidStr.substring(1) : oidStr;
        String[] parts = clean.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid OID: " + oidStr);
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int first = Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]);
        body.write((byte) first);

        for (int i = 2; i < parts.length; i++) {
            long val = Long.parseLong(parts[i]);
            List<Byte> sub = new ArrayList<>();
            sub.add((byte) (val & 0x7F));
            val >>= 7;
            while (val > 0) {
                sub.add(0, (byte) ((val & 0x7F) | 0x80));
                val >>= 7;
            }
            for (byte b : sub) {
                body.write(b);
            }
        }

        byte[] bodyBytes = body.toByteArray();
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        res.write(0x06); // OBJECT IDENTIFIER tag
        res.write(encodeLength(bodyBytes.length));
        res.write(bodyBytes);
        return res.toByteArray();
    }

    /* =========================================================================
       ASN.1 DECODING
       ========================================================================= */

    private static SnmpItem parseResponse(byte[] data) {
        try {
            int idx = 0;
            if (data[idx++] != 0x30) return null; // Message SEQUENCE
            idx = skipLength(data, idx);

            if (data[idx++] != 0x02) return null; // version INTEGER
            int vLen = data[idx++];
            idx += vLen;

            if (data[idx++] != 0x04) return null; // community OCTET STRING
            int cLen = data[idx++];
            idx += cLen;

            byte pduType = data[idx++]; // PDU tag (0xA2 for GetResponse)
            idx = skipLength(data, idx);

            idx += 1; // ReqID tag
            int rLen = data[idx++];
            idx += rLen;

            idx += 1; // errStatus tag
            int eLen = data[idx++];
            int errStatus = data[idx];
            idx += eLen;

            idx += 1; // errIndex tag
            int eiLen = data[idx++];
            idx += eiLen;

            if (errStatus != 0) {
                return new SnmpItem("", "SNMP_ERR_" + errStatus, 0);
            }

            if (data[idx++] != 0x30) return null; // VarbindList SEQUENCE
            idx = skipLength(data, idx);

            if (data[idx++] != 0x30) return null; // Varbind SEQUENCE
            idx = skipLength(data, idx);

            if (data[idx++] != 0x06) return null; // OID tag
            int[] lenOut = new int[1];
            int oidLen = readLength(data, idx, lenOut);
            idx += lenOut[0];
            String retOid = decodeOid(data, idx, oidLen);
            idx += oidLen;

            int valType = data[idx++] & 0xFF;
            int valLen = readLength(data, idx, lenOut);
            idx += lenOut[0];

            String valStr = "";
            if (valType == 0x02) { // INTEGER
                long intVal = 0;
                boolean neg = (data[idx] & 0x80) != 0;
                for (int i = 0; i < valLen; i++) {
                    intVal = (intVal << 8) | (data[idx + i] & 0xFF);
                }
                if (neg && valLen == 4) intVal = (int) intVal;
                else if (neg && valLen == 1) intVal = (byte) intVal;
                else if (neg && valLen == 2) intVal = (short) intVal;
                valStr = String.valueOf(intVal);
            } else if (valType == 0x04) { // OCTET STRING
                valStr = new String(data, idx, valLen, StandardCharsets.ISO_8859_1).trim();
            } else if (valType == 0x43) { // TimeTicks
                long ticks = 0;
                for (int i = 0; i < valLen; i++) {
                    ticks = (ticks << 8) | (data[idx + i] & 0xFF);
                }
                long totalSeconds = ticks / 100;
                long days = totalSeconds / 86400;
                long hours = (totalSeconds % 86400) / 3600;
                long mins = (totalSeconds % 3600) / 60;
                long secs = totalSeconds % 60;
                if (days > 0) {
                    valStr = String.format("%dd %dh %dm %ds", days, hours, mins, secs);
                } else if (hours > 0) {
                    valStr = String.format("%dh %dm %ds", hours, mins, secs);
                } else {
                    valStr = String.format("%dm %ds", mins, secs);
                }
            } else if (valType == 0x41 || valType == 0x42) { // Counter32 / Gauge32
                long cnt = 0;
                for (int i = 0; i < valLen; i++) {
                    cnt = (cnt << 8) | (data[idx + i] & 0xFF);
                }
                valStr = String.valueOf(cnt);
            } else if (valType == 0x05) { // NULL
                valStr = "NULL";
            } else if (valType == 0x06) { // OID
                valStr = decodeOid(data, idx, valLen);
            } else {
                valStr = new String(data, idx, valLen, StandardCharsets.UTF_8).replaceAll("[^\\x20-\\x7E]", ".").trim();
            }

            return new SnmpItem(retOid, valStr, valType);
        } catch (Exception ex) {
            return null;
        }
    }

    private static int skipLength(byte[] data, int idx) {
        if ((data[idx] & 0x80) == 0) return idx + 1;
        int num = data[idx] & 0x7F;
        return idx + 1 + num;
    }

    private static int readLength(byte[] data, int idx, int[] bytesConsumed) {
        if ((data[idx] & 0x80) == 0) {
            bytesConsumed[0] = 1;
            return data[idx] & 0xFF;
        }
        int num = data[idx] & 0x7F;
        int len = 0;
        for (int i = 0; i < num; i++) {
            len = (len << 8) | (data[idx + 1 + i] & 0xFF);
        }
        bytesConsumed[0] = 1 + num;
        return len;
    }

    private static String decodeOid(byte[] data, int start, int len) {
        List<String> parts = new ArrayList<>();
        int first = data[start] & 0xFF;
        parts.add(String.valueOf(first / 40));
        parts.add(String.valueOf(first % 40));

        long val = 0;
        for (int i = 1; i < len; i++) {
            int b = data[start + i] & 0xFF;
            val = (val << 7) + (b & 0x7F);
            if ((b & 0x80) == 0) {
                parts.add(String.valueOf(val));
                val = 0;
            }
        }

        return String.join(".", parts);
    }
}
