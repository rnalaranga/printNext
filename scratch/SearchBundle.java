import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchBundle {
    public static void main(String[] args) throws Exception {
        String text = Files.readString(Paths.get("scratch/topaccess_bundle.js"));
        String[] keywords = { "ClearLog", "DeleteLog", "JobLog", "JobHistory", "ClearJob", "JobStatus", "LogClear", "PrintLog", "purge", "DeleteJobHistory", "LogAutoDelete", "LogSetting" };
        
        for (String kw : keywords) {
            Pattern p = Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            int count = 0;
            System.out.println("=== " + kw + " ===");
            while (m.find()) {
                count++;
                if (count <= 3) {
                    int start = Math.max(0, m.start() - 80);
                    int end = Math.min(text.length(), m.end() + 120);
                    System.out.println("  ..." + text.substring(start, end).replace('\n', ' ').trim() + "...");
                }
            }
            System.out.println("Total: " + count);
        }
    }
}
