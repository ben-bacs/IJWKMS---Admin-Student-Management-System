
import java.util.LinkedList;

public class Semester {

    private String semesterName;
    private LinkedList<Course> courses;

    public Semester(String semesterName) {
        this.semesterName = semesterName;
        this.courses = new LinkedList<>();
    }

    // Add a course to the semester
    public void addCourse(Course course) {
        courses.add(course);
    }

    // Get the semester's name
    public String getSemesterName() {
        return semesterName;
    }

    // Get the list of courses in the semester
    public LinkedList<Course> getCourses() {
        return courses;
    }
}
