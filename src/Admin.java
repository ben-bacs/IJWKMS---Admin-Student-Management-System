
public class Admin extends Person {

    private String department;

    public Admin(String id, String firstName, String lastName, String department) {
        super(id, firstName, lastName);
        this.department = department;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    @Override
    public void displayDetails() {
        System.out.println("Admin ID: " + getId());
        System.out.println("First Name: " + getFirstName());
        System.out.println("Last Name: " + getLastName());
        System.out.println("Department: " + department);
    }

}
