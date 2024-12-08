
import java.util.*;

public class AdminControl {

    private LinkedList<Student> studentList;

    public AdminControl() {
        this.studentList = new LinkedList<>();
    }

    public void addStudent(Student student) {
        studentList.add(student); // Efficient addition with LinkedList
    }

    public Student findStudentByID(String studentID) {
        for (Student student : studentList) {
            if (student.getId().equals(studentID)) {
                return student;
            }
        }
        return null; // Return null if no student found
    }

    public void viewAllStudents() {
        if (studentList.isEmpty()) {
            System.out.println("No students available.");
            return;
        }
        for (Student student : studentList) {
            System.out.printf("ID: %s, Name: %s, GWA: %.2f%n", student.getId(), student.getName(), student.getGWA());
        }
    }

    public void generateStudentReport(String studentID) {
        Student student = findStudentByID(studentID);

        if (student == null) {
            System.out.println("Student not found!");
            return;
        }

        System.out.println("\n=== Student Report ===");
        System.out.printf("ID: %s%n", student.getId());
        System.out.printf("Name: %s%n", student.getName());

        // Include Profile Information
        Profile profile = student.getProfile();
        if (profile != null) {
            System.out.println("Profile Information:");
            System.out.printf("- Address: %s%n", profile.getAddress());
            System.out.printf("- Contact Number: %s%n", profile.getContactNumber());
            System.out.printf("- Emergency Contact: %s%n", profile.getEmergencyContact());
        } else {
            System.out.println("Profile Information: Not Available");
        }

        System.out.println("Enrolled Courses:");
        List<Course> enrolledCourses = student.getEnrolledCourses();
        if (enrolledCourses.isEmpty()) {
            System.out.println("- No courses enrolled.");
        } else {
            for (Course course : enrolledCourses) {
                String courseCode = course.getCourseCode();
                String courseName = course.getCourseName();
                Double grade = student.getCourseGrades().get(courseCode);

                System.out.printf("- %s (%s): %s%n", courseName, courseCode,
                        grade == null ? "Not yet graded" : String.format("%.2f", grade));
            }
        }

        System.out.printf("Current GWA: %.2f%n", student.getGWA());
    }

    public void addOrUpdateProfile(String studentID, Profile profile) {
        Student student = findStudentByID(studentID);

        if (student == null) {
            System.out.println("Student not found!");
            return;
        }

        student.setProfile(profile);
        System.out.println("Profile added/updated successfully for " + student.getName());
    }

    public void sortStudents(Comparator<Student> comparator) {
        if (studentList.isEmpty()) {
            System.out.println("No students available to sort.");
            return;
        }

        studentList = new LinkedList<>(studentList.stream()
                .sorted(comparator)
                .toList());

        System.out.println("Students sorted successfully!");
    }

}
