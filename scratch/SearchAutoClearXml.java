import java.nio.file.*;
import java.util.regex.*;

public class SearchAutoClearXml {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        Matcher m = Pattern.compile("(<[^>]*AutoClear[^>]*>)").matcher(content);
        int count = 0;
        while (m.find() && count < 20) {
            System.out.println("Tag: " + m.group(1));
            count++;
        }

        // Search for context around AutoClear
        Matcher m2 = Pattern.compile("(.{0,80}AutoClear.{0,80})").matcher(content);
        count = 0;
        while (m2.find() && count < 15) {
            System.out.println("Context: " + m2.group(1).replace("\n", " "));
            count++;
        }
    }
}
