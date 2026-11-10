import java.nio.file.Files;
import java.nio.file.Paths;

public class InspectOu {
    public static void main(String[] args) throws Exception {
        String text = Files.readString(Paths.get("scratch/topaccess_bundle.js"));
        String target = "function ou(";
        int pos = text.indexOf(target);
        if (pos == -1) target = "ou=";
        pos = text.indexOf(target);
        while (pos != -1) {
            int start = Math.max(0, pos - 100);
            int end = Math.min(text.length(), pos + 1000);
            System.out.println("=== " + target + " ===");
            System.out.println(text.substring(start, end).replace('\n', ' '));
            pos = text.indexOf(target, pos + target.length());
        }
    }
}
