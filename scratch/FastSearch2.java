import java.nio.file.*;

public class FastSearch2 {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        String lower = content.toLowerCase();
        int idx = lower.indexOf("selenergyclear");
        if (idx != -1) {
            int start = Math.max(0, idx - 200);
            int end = Math.min(content.length(), idx + 300);
            System.out.println("selenergyclear context: " + content.substring(start, end).replace("\n", " "));
        }
    }
}
