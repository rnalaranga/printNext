import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class FixChiranPrint {
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:file:./data/rr_secure_print", "SA", "")) {
            // Update print_jobs where username like '%slt ebill%'
            String sql1 = "UPDATE print_jobs SET user_id = 65, username = 'chiran', user_sid = 'S-1-5-21-3231730075-2149119002-3047611012-1000' WHERE LOWER(username) LIKE '%slt%ebill%'";
            try (PreparedStatement ps = conn.prepareStatement(sql1)) {
                int count = ps.executeUpdate();
                System.out.println("Updated " + count + " print jobs to chiran");
            }
            // Update user print_count
            String sql2 = "UPDATE users SET print_count = 1 WHERE username = 'chiran'";
            try (PreparedStatement ps = conn.prepareStatement(sql2)) {
                int count = ps.executeUpdate();
                System.out.println("Updated " + count + " user chiran print_count to 1");
            }
        }
    }
}
