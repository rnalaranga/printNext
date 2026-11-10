import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class FindUserCredCmds {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/topaccess_bundle.js"));
        Matcher m = Pattern.compile("<([A-Za-z0-9_]+)><commandNode>Authentication/UserCredential</commandNode>").matcher(content);
        Set<String> cmds = new TreeSet<>();
        while (m.find()) {
            cmds.add(m.group(1));
        }
        System.out.println("UserCredential commands: " + cmds);
    }
}
