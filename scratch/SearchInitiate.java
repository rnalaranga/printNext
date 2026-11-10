import java.nio.file.*;

public class SearchInitiate {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = content.indexOf("InitiateServerRequest =");
        if (idx == -1) idx = content.indexOf("InitiateServerRequest=");
        if (idx == -1) idx = content.indexOf("InitiateServerRequest: function");
        if (idx == -1) idx = content.indexOf("InitiateServerRequest:function");
        if (idx == -1) idx = content.indexOf("window.InitiateServerRequest");
        System.out.println("idx: " + idx);
        if (idx != -1) {
            int start = Math.max(0, idx - 100);
            int end = Math.min(content.length(), idx + 2000);
            System.out.println(content.substring(start, end));
        }
    }
}
