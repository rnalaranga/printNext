import java.nio.file.Files;
import java.nio.file.Path;

public class SearchUsb {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = 0;
        int found = 0;
        while ((idx = content.indexOf("<DefaultScreen>", idx)) != -1 && found < 10) {
            int start = Math.max(0, idx - 100);
            int end = Math.min(content.length(), idx + 300);
            System.out.println("Match " + (++found) + ": " + content.substring(start, end).replace('\n', ' '));
            idx += 15;
        }
    }
}
