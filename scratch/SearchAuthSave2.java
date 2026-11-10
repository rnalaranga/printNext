import java.nio.file.*;

public class SearchAuthSave2 {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = 4627661;
        int start = Math.max(0, idx - 1000);
        int end = Math.min(content.length(), idx + 200);
        System.out.println(content.substring(start, end));
    }
}
