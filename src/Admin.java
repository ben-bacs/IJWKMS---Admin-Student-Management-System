
public class Admin extends Person {

    private String department;

    public Admin(String id, String name, String department) {
        super(id, name);
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
        System.out.println("Name: " + getName());
        System.out.println("Department: " + department);
    }
}
