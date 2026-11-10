import java.nio.file.*;

public class SearchSaveData {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = content.indexOf("GET_AUTHENTICATION_SETTING_SAVE_DATA");
        while (idx != -1) {
            int start = Math.max(0, idx - 100);
            int end = Math.min(content.length(), idx + 500);
            System.out.println("=== MATCH at " + idx + " ===");
            System.out.println(content.substring(start, end));
            idx = content.indexOf("GET_AUTHENTICATION_SETTING_SAVE_DATA", idx + 1);
        }
    }
}
