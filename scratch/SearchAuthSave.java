import java.nio.file.*;

public class SearchAuthSave {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = content.indexOf("<AuthSettings>");
        while (idx != -1) {
            int start = Math.max(0, idx - 300);
            int end = Math.min(content.length(), idx + 500);
            System.out.println("=== MATCH at " + idx + " ===");
            System.out.println(content.substring(start, end));
            idx = content.indexOf("<AuthSettings>", idx + 1);
        }
    }
}
