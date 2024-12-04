
import java.util.LinkedList;

public class Program {

    private String programName;
    private LinkedList<Specialization> specializations;

    public Program(String programName) {
        this.programName = programName;
        this.specializations = new LinkedList<>();
    }

    // Add a specialization to the program
    public void addSpecialization(Specialization specialization) {
        specializations.add(specialization);
    }

    // Get the program's name
    public String getProgramName() {
        return programName;
    }

    // Get the list of specializations in the program
    public LinkedList<Specialization> getSpecializations() {
        return specializations;
    }
}
