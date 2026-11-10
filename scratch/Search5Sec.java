import java.nio.file.*;

public class Search5Sec {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(".{0,50}(?:AutoScreenChange|AutoScreen|DisplayTiming|SessionClear|timeOut|timeout).{0,50}", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(content);
        int count = 0;
        while (m.find() && count < 15) {
            System.out.println("[" + count + "] " + m.group().replace('\n', ' '));
            count++;
        }
    }
}
