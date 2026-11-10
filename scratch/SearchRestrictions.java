import java.nio.file.*;

public class SearchRestrictions {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        for (String node : new String[]{"Restrictions", "ScreenSettings", "DevAuthMgmtPlugIn", "SecuritySettings"}) {
            System.out.println("=== NODE: " + node + " ===");
            int idx = 0;
            int count = 0;
            while ((idx = content.indexOf("<commandNode>" + node, idx)) != -1 && count < 5) {
                int start = Math.max(0, idx - 150);
                int end = Math.min(content.length(), idx + 250);
                System.out.println("[" + count + "] " + content.substring(start, end).replace('\n', ' '));
                idx += 15;
                count++;
            }
        }
    }
}
