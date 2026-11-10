import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchJobStatusSettings {
    public static void main(String[] args) throws Exception {
        String text = Files.readString(Paths.get("scratch/topaccess_bundle.js"));
        String[] targets = { "NumberOfDisplay", "JobStatus", "JobHistory", "LogSetting", "AutoDelete", "CompletedJobs" };
        for (String target : targets) {
            Pattern p = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            int count = 0;
            while (m.find() && count < 3) {
                count++;
                int start = Math.max(0, m.start() - 100);
                int end = Math.min(text.length(), m.end() + 200);
                System.out.println("=== TARGET: " + target + " ===");
                System.out.println(text.substring(start, end).replace('\n', ' '));
            }
        }
    }
}
