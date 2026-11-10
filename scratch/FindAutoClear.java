import java.nio.file.*;
import java.util.regex.*;

public class FindAutoClear {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        Matcher m = Pattern.compile("(.{0,100}AutoClear.{0,100})").matcher(content);
        int count = 0;
        while (m.find() && count < 10) {
            System.out.println("Match " + (++count) + ": " + m.group(1).replace("\n", " "));
        }
    }
}
