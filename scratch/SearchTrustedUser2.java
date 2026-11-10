import java.nio.file.*;
import java.util.regex.*;

public class SearchTrustedUser2 {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        Matcher m = Pattern.compile("trusteduser|requiretrusted", Pattern.CASE_INSENSITIVE).matcher(content);
        while (m.find()) {
            int start = Math.max(0, m.start() - 100);
            int end = Math.min(content.length(), m.end() + 200);
            System.out.println(content.substring(start, end).replace('\n', ' '));
        }
    }
}
