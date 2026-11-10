import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class FindAuthNodes {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        Matcher m = Pattern.compile("<commandNode>([^<]*Authentication[^<]*)</commandNode>").matcher(content);
        Set<String> nodes = new TreeSet<>();
        while (m.find()) {
            nodes.add(m.group(1));
        }
        System.out.println("Authentication commandNodes: " + nodes);

        Matcher m2 = Pattern.compile("<Command><([A-Za-z0-9_]+)><commandNode>([^<]*Authentication[^<]*)</commandNode>").matcher(content);
        while (m2.find()) {
            System.out.println("Command: <" + m2.group(1) + "> for node: " + m2.group(2));
        }
    }
}
