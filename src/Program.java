
import java.util.*;

public class Program {

    private String programName;
    private List<Specialization> specializations;

    public Program(String programName) {
        this.programName = programName;
        this.specializations = new ArrayList<>();
    }

    public void addSpecialization(Specialization specialization) {
        specializations.add(specialization);
    }

    public String getProgramName() {
        return programName;
    }

    public List<Specialization> getSpecializations() {
        return specializations;
    }
}
