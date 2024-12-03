
import java.util.*;

public class Specialization {

    private String specializationName;
    private List<Course> courses;

    public Specialization(String specializationName) {
        this.specializationName = specializationName;
        this.courses = new ArrayList<>();
    }

    public void addCourse(Course course) {
        courses.add(course);
    }

    public String getSpecializationName() {
        return specializationName;
    }

    public List<Course> getCourses() {
        return courses;
    }
}
