
import java.util.*;

public class StudentLogin {

    private static Map<String, String> studentAccounts = new HashMap<>();

    public static void addStudentAccount(String studentID, String password) {
        studentAccounts.put(studentID, password); // Store credentials
    }

    public static boolean login(String studentID, String password) {
        return studentAccounts.containsKey(studentID) && studentAccounts.get(studentID).equals(password);
    }
}
