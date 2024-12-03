
public class Course {

    private String courseCode;
    private String courseName;
    private int units;

    public Course(String courseCode, String courseName, int units) {
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.units = units;
    }

    // Getters
    public String getCourseCode() {
        return courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public int getUnits() {
        return units;
    }
}
