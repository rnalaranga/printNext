import java.nio.file.*;

public class SearchTrustedUser {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        int idx = 0;
        while ((idx = content.indexOf("requireTrustedUser", idx)) != -1) {
            int start = Math.max(0, idx - 100);
            int end = Math.min(content.length(), idx + 200);
            System.out.println(content.substring(start, end).replace('\n', ' '));
            idx += 18;
        }
    }
}
