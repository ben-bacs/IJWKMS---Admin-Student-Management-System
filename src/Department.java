
import java.util.*;

public class Department {

    private String departmentName;
    private List<Program> programs;

    public Department(String departmentName) {
        this.departmentName = departmentName;
        this.programs = new ArrayList<>();
    }

    public void addProgram(Program program) {
        programs.add(program);
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public List<Program> getPrograms() {
        return programs;
    }
}
