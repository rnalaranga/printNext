import java.nio.file.*;

public class SearchMatch3 {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = 0;
        for (int i = 0; i < 4; i++) {
            idx = content.indexOf("saveAuthenticationSettings", idx + 1);
        }
        int start = Math.max(0, idx - 800);
        int end = Math.min(content.length(), idx + 300);
        System.out.println(content.substring(start, end));
    }
}
