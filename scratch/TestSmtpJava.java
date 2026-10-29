import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TestSmtpJava {
    public static void main(String[] args) throws Exception {
        String host = "qoa.lk";
        int port = 465;
        String user = "nexprint@qoa.lk";
        String pass = "0715719676Bcg";
        String to = "nalaranga@gmail.com";
        String from = "nexprint@qoa.lk";

        System.out.println("Connecting to " + host + ":" + port + " with SSL...");
        SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) ssf.createSocket();
        socket.connect(new InetSocketAddress(host, port), 10000);
        socket.startHandshake();
        socket.setSoTimeout(10000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

        // Read multiline greeting
        readMultiline(reader, 220);

        send(writer, "EHLO test.local");
        readMultiline(reader, 250);

        send(writer, "AUTH LOGIN");
        readMultiline(reader, 334);

        send(writer, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
        readMultiline(reader, 334);

        send(writer, Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)));
        readMultiline(reader, 235);

        send(writer, "MAIL FROM:<" + from + ">");
        readMultiline(reader, 250);

        send(writer, "RCPT TO:<" + to + ">");
        readMultiline(reader, 250);

        send(writer, "DATA");
        readMultiline(reader, 354);

        String msg = "From: \"nexPrint Scanner\" <" + from + ">\r\n" +
                     "To: <" + to + ">\r\n" +
                     "Subject: nexPrint Test Scan Document\r\n" +
                     "MIME-Version: 1.0\r\n" +
                     "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                     "Hello,\r\n\r\nThis is a test scan delivery from nexPrint Secure Print & Scan system.\r\n" +
                     "Sent via qoa.lk:465 (SSL).\r\n";
        writer.write(msg);
        writer.write("\r\n.\r\n");
        writer.flush();

        readMultiline(reader, 250);

        send(writer, "QUIT");
        readMultiline(reader, 221);

        socket.close();
        System.out.println(">>> EMAIL SENT SUCCESSFULLY TO " + to + " <<<");
    }

    private static void send(BufferedWriter w, String s) throws IOException {
        System.out.println("C: " + s);
        w.write(s + "\r\n");
        w.flush();
    }

    private static String readMultiline(BufferedReader r, int expected) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            System.out.println("S: " + line);
            sb.append(line).append("\n");
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }
        if (!sb.toString().startsWith(String.valueOf(expected))) {
            throw new IOException("Expected " + expected + " but got: " + sb);
        }
        return sb.toString();
    }
}
