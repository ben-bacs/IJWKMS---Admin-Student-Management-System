
import java.util.*;

public class Semester {

    private String semesterName;
    private List<Course> courses;

    public Semester(String semesterName) {
        this.semesterName = semesterName;
        this.courses = new ArrayList<>();
    }

    public void addCourse(Course course) {
        courses.add(course);
    }

    public String getSemesterName() {
        return semesterName;
    }

    public List<Course> getCourses() {
        return courses;
    }
}
