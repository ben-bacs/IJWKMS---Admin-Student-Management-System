
import java.util.*;

public class Student extends Person {

    private String password;
    private Profile profile; // One-to-One relationship
    private List<Course> enrolledCourses;
    private TreeMap<String, Double> courseGrades;
    private double gwa; // Holds the computed GWA

    public Student(String id, String password, String name) {
        super(id, name);
        this.password = password;
        this.profile = null; // Initialize as null until set
        this.enrolledCourses = new ArrayList<>();
        this.courseGrades = new TreeMap<>();
        this.gwa = 0.0; // Initialize GWA to 0
        StudentLogin.addStudentAccount(id, password); // Sync with login system
    }

    @Override
    public void displayDetails() {
        System.out.println("Student ID: " + getId());
        System.out.println("Name: " + getName());
        System.out.printf("GWA: %.2f%n", gwa);
        if (profile != null) {
            System.out.println("Profile:");
            System.out.println("- Address: " + profile.getAddress());
            System.out.println("- Contact Number: " + profile.getContactNumber());
            System.out.println("- Emergency Contact: " + profile.getEmergencyContact());
        } else {
            System.out.println("Profile: Not available");
        }
    }

    // Getter and Setter for Profile
    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    // Other methods remain unchanged...
    public void enrollCourse(Course course) {
        enrolledCourses.add(course);
    }

    public void calculateGWA() {
        double totalGradePoints = 0;
        int totalUnits = 0;

        for (Course course : enrolledCourses) {
            Double grade = courseGrades.get(course.getCourseCode());
            if (grade != null) {
                totalGradePoints += grade * course.getUnits();
                totalUnits += course.getUnits();
            }
        }

        this.gwa = totalUnits > 0 ? totalGradePoints / totalUnits : 0.0;
    }

    public void viewGrades() {
        System.out.println("Enrolled Courses and Grades:");
        for (Course course : enrolledCourses) {
            String courseCode = course.getCourseCode();
            String courseName = course.getCourseName();
            Double grade = courseGrades.get(courseCode);

            System.out.printf("- %s (%s): %s%n", courseName, courseCode, grade == null ? "Not yet graded" : String.format("%.2f", grade));
        }
        System.out.printf("Current GWA: %.2f%n", gwa);
    }

    public List<Course> getEnrolledCourses() {
        return enrolledCourses;
    }

    public TreeMap<String, Double> getCourseGrades() {
        return courseGrades;
    }

    public double getGWA() {
        return gwa;
    }

    public void evaluateAcademicStanding() {
        if (gwa == 0) {
            System.out.println("No grades recorded yet.");
            return;
        }

        System.out.println("\n=== Academic Standing ===");
        if (gwa >= 3.0) {
            System.out.println("Status: Good Standing");
        } else {
            System.out.println("Status: At Risk (Consider Academic Advising)");
        }
        System.out.printf("Current GWA: %.2f%n", gwa);
    }

}
