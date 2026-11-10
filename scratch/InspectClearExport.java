import java.nio.file.Files;
import java.nio.file.Paths;

public class InspectClearExport {
    public static void main(String[] args) throws Exception {
        String text = Files.readString(Paths.get("scratch/topaccess_bundle.js"));
        String target = "getClearExportLogs";
        int pos = text.indexOf(target);
        while (pos != -1) {
            int start = Math.max(0, pos - 200);
            int end = Math.min(text.length(), pos + 800);
            System.out.println("---");
            System.out.println(text.substring(start, end).replace('\n', ' '));
            pos = text.indexOf(target, pos + target.length());
        }
    }
}
