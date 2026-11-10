import java.nio.file.*;

public class SearchDefDE {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = content.indexOf("function dE(");
        if (idx == -1) idx = content.indexOf("var dE=");
        if (idx == -1) idx = content.indexOf("dE=function(");
        if (idx == -1) idx = content.indexOf("dE=/*#__PURE__*/");
        System.out.println("idx: " + idx);
        if (idx != -1) {
            int start = Math.max(0, idx - 100);
            int end = Math.min(content.length(), idx + 1000);
            System.out.println(content.substring(start, end));
        }
    }
}
