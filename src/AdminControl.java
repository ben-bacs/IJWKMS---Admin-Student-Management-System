
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

    public void bubbleSortByName() {
        if (studentList.isEmpty()) {
            System.out.println("No students available to sort.");
            return;
        }

        int n = studentList.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (studentList.get(j).getName().compareTo(studentList.get(j + 1).getName()) > 0) {
                    Collections.swap(studentList, j, j + 1);
                }
            }
        }

        System.out.println("Students sorted by Name:");
        for (Student student : studentList) {
            System.out.printf("%s - GWA: %.2f%n", student.getName(), student.getGWA());
        }
    }

    public void mergeSortByGWA() {
        if (studentList.isEmpty()) {
            System.out.println("No students available to sort.");
            return;
        }

        studentList = (LinkedList<Student>) mergeSort(studentList);

        System.out.println("Students sorted by GWA:");
        for (Student student : studentList) {
            System.out.printf("%s - GWA: %.2f%n", student.getName(), student.getGWA());
        }
    }

    private List<Student> mergeSort(List<Student> list) {
        if (list.size() <= 1) {
            return list;
        }

        int mid = list.size() / 2;
        List<Student> left = mergeSort(new ArrayList<>(list.subList(0, mid)));
        List<Student> right = mergeSort(new ArrayList<>(list.subList(mid, list.size())));

        return merge(left, right);
    }

    private List<Student> merge(List<Student> left, List<Student> right) {
        List<Student> merged = new ArrayList<>();
        int i = 0, j = 0;

        while (i < left.size() && j < right.size()) {
            if (left.get(i).getGWA() <= right.get(j).getGWA()) {
                merged.add(left.get(i++));
            } else {
                merged.add(right.get(j++));
            }
        }

        while (i < left.size()) {
            merged.add(left.get(i++));
        }

        while (j < right.size()) {
            merged.add(right.get(j++));
        }

        return merged;
    }

    public void sortStudentsByGWA() {
        mergeSortByGWA(); // Call the actual Merge Sort implementation
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

}
