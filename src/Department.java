
import java.util.LinkedList;

public class Department {

    private String departmentName;
    private LinkedList<Program> programs;

    public Department(String departmentName) {
        this.departmentName = departmentName;
        this.programs = new LinkedList<>();
    }

    // Add a program to the department
    public void addProgram(Program program) {
        programs.add(program);
    }

    // Get the department's name
    public String getDepartmentName() {
        return departmentName;
    }

    // Get the list of programs in the department
    public LinkedList<Program> getPrograms() {
        return programs;
    }
}
