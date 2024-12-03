
public abstract class Person {

    private String id;
    private String name;

    public Person(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // Concrete methods (shared by all subclasses)
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    // Abstract method to enforce specific implementation in subclasses
    public abstract void displayDetails();
}
