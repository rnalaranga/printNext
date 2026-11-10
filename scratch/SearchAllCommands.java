import java.nio.file.*;
import java.util.*;

public class SearchAllCommands {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<commandNode>([^<]+)</commandNode>");
        java.util.regex.Matcher m = p.matcher(content);
        Set<String> nodes = new TreeSet<>();
        while (m.find()) {
            nodes.add(m.group(1));
        }
        for (String n : nodes) {
            System.out.println("CommandNode: " + n);
        }
    }
}
