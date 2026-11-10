import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class TestYaml {
    public static void main(String[] args) throws Exception {
        // Test jar entries
        try (JarFile jar = new JarFile("rr-secure-print-api/target/rr-secure-print-api-1.0.0-SNAPSHOT.jar")) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".yml") || entry.getName().endsWith(".yaml")) {
                    System.out.println("Checking jar entry: " + entry.getName());
                    try (InputStream is = jar.getInputStream(entry)) {
                        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                        for (Object doc : yaml.loadAll(is)) {
                            System.out.println("  Loaded doc: " + (doc != null ? doc.getClass().getName() : "null"));
                        }
                    } catch (Exception e) {
                        System.err.println("  FAILED: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
