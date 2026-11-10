import java.nio.file.*;
import java.util.regex.*;

public class FindAuthPage {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        Matcher m = Pattern.compile("(.{0,150}Authentication\\.html.{0,300})").matcher(content);
        while (m.find()) {
            System.out.println("Match: " + m.group(1).replace("\n", " "));
        }
    }
}
