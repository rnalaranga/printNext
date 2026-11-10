import java.nio.file.Files;
import java.nio.file.Paths;

public class InspectZi {
    public static void main(String[] args) throws Exception {
        String text = Files.readString(Paths.get("scratch/topaccess_bundle.js"));
        String target = "function Zi(";
        int pos = text.indexOf(target);
        while (pos != -1) {
            int start = Math.max(0, pos - 100);
            int end = Math.min(text.length(), pos + 600);
            System.out.println("=== " + target + " ===");
            System.out.println(text.substring(start, end).replace('\n', ' '));
            pos = text.indexOf(target, pos + target.length());
        }
    }
}
