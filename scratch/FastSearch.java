import java.nio.file.*;

public class FastSearch {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js")).toLowerCase();
        int idx = 0;
        int count = 0;
        while ((idx = content.indexOf("autoclear", idx + 1)) != -1 && count < 20) {
            int start = Math.max(0, idx - 80);
            int end = Math.min(content.length(), idx + 100);
            System.out.println("Match " + (++count) + ": " + content.substring(start, end).replace("\n", " "));
        }
    }
}
