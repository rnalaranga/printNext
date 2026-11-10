import java.nio.file.*;

public class SearchPanel {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = 0;
        int count = 0;
        while ((idx = content.indexOf("<commandNode>Panel", idx)) != -1 && count < 20) {
            int start = Math.max(0, idx - 100);
            int end = Math.min(content.length(), idx + 200);
            System.out.println("[" + count + "] " + content.substring(start, end).replace('\n', ' '));
            idx += 18;
            count++;
        }
        System.out.println("Total found: " + count);
    }
}
