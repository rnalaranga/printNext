import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InspectJobHistory {
    public static void main(String[] args) throws Exception {
        String text = Files.readString(Paths.get("scratch/topaccess_bundle.js"));
        String[] targets = { "getJobHistoryListCommand", "fnCreateAndClearLog", "DeleteAll", "ClearHistory", "DeleteHistory" };
        for (String target : targets) {
            int pos = text.indexOf(target);
            System.out.println("=== TARGET: " + target + " (found=" + (pos != -1) + ") ===");
            while (pos != -1) {
                int start = Math.max(0, pos - 150);
                int end = Math.min(text.length(), pos + 350);
                System.out.println("---");
                System.out.println(text.substring(start, end).replace('\n', ' '));
                pos = text.indexOf(target, pos + target.length());
            }
        }
    }
}
