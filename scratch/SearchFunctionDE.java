import java.nio.file.*;

public class SearchFunctionDE {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = 1293978;
        int start = Math.max(0, idx - 1500);
        int end = Math.min(content.length(), idx + 100);
        System.out.println(content.substring(start, end));
    }
}
