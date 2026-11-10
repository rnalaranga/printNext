import java.nio.file.*;

public class SearchApply {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        String[] terms = {"SessionClear", "AppliedTiming", "RestartUI", "ResetPanel", "FunctionClear", "fnReload", "saveAuthenticationSettings"};
        for (String term : terms) {
            System.out.println("=== TERM: " + term + " ===");
            int idx = 0;
            int count = 0;
            while ((idx = content.indexOf(term, idx)) != -1 && count < 5) {
                int start = Math.max(0, idx - 100);
                int end = Math.min(content.length(), idx + 200);
                System.out.println("[" + count + "] " + content.substring(start, end).replace('\n', ' '));
                idx += term.length();
                count++;
            }
        }
    }
}
