import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class FindCommands {
    public static void main(String[] args) throws Exception {
        String content = Files.readString(Path.of("scratch/AjaxReqRespHandler.js"));
        Matcher m = Pattern.compile("<Command><([A-Za-z0-9_]+)>").matcher(content);
        Set<String> cmds = new TreeSet<>();
        while (m.find()) {
            cmds.add(m.group(1));
        }
        System.out.println("Commands in AjaxReqRespHandler: " + cmds);

        // Also search in topaccess_bundle.js
        if (Files.exists(Path.of("scratch/topaccess_bundle.js"))) {
            String bContent = Files.readString(Path.of("scratch/topaccess_bundle.js"));
            Matcher bm = Pattern.compile("<Command><([A-Za-z0-9_]+)>").matcher(bContent);
            Set<String> bcmds = new TreeSet<>();
            while (bm.find()) {
                bcmds.add(bm.group(1));
            }
            System.out.println("Commands in topaccess_bundle: " + bcmds);
        }
    }
}
