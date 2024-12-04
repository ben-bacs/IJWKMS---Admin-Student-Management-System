
import java.util.LinkedList;

public class Specialization {

    private String specializationName;
    private LinkedList<Course> courses;

    public Specialization(String specializationName) {
        this.specializationName = specializationName;
        this.courses = new LinkedList<>();
    }

    // Add a course to the specialization
    public void addCourse(Course course) {
        courses.add(course);
    }

    // Get the specialization's name
    public String getSpecializationName() {
        return specializationName;
    }

    // Get the list of courses in the specialization
    public LinkedList<Course> getCourses() {
        return courses;
    }
}
