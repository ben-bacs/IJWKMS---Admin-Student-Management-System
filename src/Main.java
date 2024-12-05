
import java.util.*;

public class Main {

    private static AdminControl adminControl = new AdminControl();
    private static List<Department> departments = new ArrayList<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        initializeSampleData(); // Initialize departments, programs, courses, etc.

        int mainChoice;

        do {
            displayMainMenu();
            mainChoice = safeInputInt(scanner, "Enter your choice: ");
            scanner.nextLine(); // Clear newline character

            switch (mainChoice) {
                case 1 -> {
                    if (adminLogin(scanner)) {
                        adminMenu(scanner);
                    }
                }
                case 2 ->
                    studentMenu(scanner);
                case 3 ->
                    System.out.println("Exiting the system. Goodbye!");
                default ->
                    System.out.println("Invalid choice. Please try again.");
            }
        } while (mainChoice != 3);

        scanner.close();
    }

    public static void displayMainMenu() {
        System.out.println("\n=== Main Menu ===");
        System.out.println("1. Admin Login");
        System.out.println("2. Student Login");
        System.out.println("3. Exit");
    }

    public static void adminMenu(Scanner scanner) {
        int adminChoice;

        do {
            System.out.println("\n=== Admin Menu ===");
            System.out.println("1. Add Student");
            System.out.println("2. View All Students");
            System.out.println("3. Update Grades");
            System.out.println("4. Sort Students by GWA");
            System.out.println("5. Generate Student Report");
            System.out.println("6. Add/Update Student Profile");
            System.out.println("7. Back to Main Menu");
            adminChoice = safeInputInt(scanner, "Enter your choice: ");

            switch (adminChoice) {
                case 1 ->
                    addStudent(scanner);
                case 2 -> {
                    System.out.println("\nView All Students:");
                    System.out.println("1. Original Order");
                    System.out.println("2. Sorted by Name");
                    int viewChoice = safeInputInt(scanner, "Enter your choice: ");

                    switch (viewChoice) {
                        case 1 ->
                            adminControl.viewAllStudents(); // Original order
                        case 2 -> {
                            adminControl.mergeSortByName();
                            adminControl.viewAllStudents(); // Display after sorting
                        }
                        default ->
                            System.out.println("Invalid choice. Returning to Admin Menu.");
                    }
                }
                case 3 ->
                    updateStudentGrades(scanner);
                case 4 -> {
                    adminControl.sortStudentsByGWA();
                    System.out.println("Sorting completed successfully!");
                }
                case 5 -> {
                    String studentID = safeInputString(scanner, "Enter Student ID to generate report: ");
                    adminControl.generateStudentReport(studentID);
                }
                case 6 -> {
                    String studentID = safeInputString(scanner, "Enter Student ID to add/update profile: ");
                    String address = safeInputString(scanner, "Enter Address: ");
                    String contactNumber = safeInputString(scanner, "Enter Contact Number: ");
                    String emergencyContact = safeInputString(scanner, "Enter Emergency Contact: ");

                    Profile profile = new Profile(address, contactNumber, emergencyContact);
                    adminControl.addOrUpdateProfile(studentID, profile);
                }
                case 7 ->
                    System.out.println("Returning to Main Menu...");
                default ->
                    System.out.println("Invalid choice. Please try again.");
            }
        } while (adminChoice != 7);
    }

    public static void studentMenu(Scanner scanner) {
        System.out.print("Enter your Student ID: ");
        String studentID = scanner.nextLine().trim();

        System.out.print("Enter your Password: ");
        String password = scanner.nextLine().trim();

        if (StudentLogin.login(studentID, password)) {
            Student student = adminControl.findStudentByID(studentID);
            if (student != null) {
                int studentChoice;
                do {
                    System.out.println("\n=== Student Menu ===");
                    System.out.println("1. View Grades and Courses");
                    System.out.println("2. Evaluate Academic Standing");
                    System.out.println("3. Back to Main Menu");
                    studentChoice = safeInputInt(scanner, "Enter your choice: ");

                    switch (studentChoice) {
                        case 1 ->
                            student.viewGrades();
                        case 2 ->
                            student.evaluateAcademicStanding();
                        case 3 ->
                            System.out.println("Returning to Main Menu...");
                        default ->
                            System.out.println("Invalid choice. Please try again.");
                    }
                } while (studentChoice != 3);
            } else {
                System.out.println("Student not found.");
            }
        } else {
            System.out.println("Invalid Student ID or Password.");
        }
    }

    public static void addStudent(Scanner scanner) {
        String id = safeInputString(scanner, "Enter Student ID: ");
        String password = safeInputString(scanner, "Enter Password: ");
        String name = safeInputString(scanner, "Enter Name: ");

        System.out.println("Available Departments:");
        for (int i = 0; i < departments.size(); i++) {
            System.out.printf("%d. %s%n", i + 1, departments.get(i).getDepartmentName());
        }
        int departmentChoice = safeInputInt(scanner, "Select a Department: ") - 1;

        if (departmentChoice < 0 || departmentChoice >= departments.size()) {
            System.out.println("Invalid choice.");
            return;
        }

        Department selectedDepartment = departments.get(departmentChoice);
        System.out.println("Available Programs:");
        for (int i = 0; i < selectedDepartment.getPrograms().size(); i++) {
            System.out.printf("%d. %s%n", i + 1, selectedDepartment.getPrograms().get(i).getProgramName());
        }
        int programChoice = safeInputInt(scanner, "Select a Program: ") - 1;

        if (programChoice < 0 || programChoice >= selectedDepartment.getPrograms().size()) {
            System.out.println("Invalid choice.");
            return;
        }

        Program selectedProgram = selectedDepartment.getPrograms().get(programChoice);
        System.out.println("Available Specializations:");
        for (int i = 0; i < selectedProgram.getSpecializations().size(); i++) {
            System.out.printf("%d. %s%n", i + 1, selectedProgram.getSpecializations().get(i).getSpecializationName());
        }
        int specializationChoice = safeInputInt(scanner, "Select a Specialization: ") - 1;

        if (specializationChoice < 0 || specializationChoice >= selectedProgram.getSpecializations().size()) {
            System.out.println("Invalid choice.");
            return;
        }

        Specialization selectedSpecialization = selectedProgram.getSpecializations().get(specializationChoice);
        Student student = new Student(id, password, name);

        System.out.println("\nEnrolling in the following courses:");
        for (Course course : selectedSpecialization.getCourses()) {
            student.enrollCourse(course);
            System.out.printf("- %s (%s)%n", course.getCourseName(), course.getCourseCode());
        }

        adminControl.addStudent(student);
        System.out.println("Student added successfully and enrolled in courses for " + selectedSpecialization.getSpecializationName() + ".");
    }

    public static void updateStudentGrades(Scanner scanner) {
        String studentID = safeInputString(scanner, "Enter Student ID: ");
        Student student = adminControl.findStudentByID(studentID);

        if (student == null) {
            System.out.println("Student not found!");
            return;
        }

        for (Course course : student.getEnrolledCourses()) {
            double grade;
            do {
                grade = safeInputDouble(scanner, "Enter grade for " + course.getCourseName() + " (0.0-5.0): ");
                if (grade < 0 || grade > 5.0) {
                    System.out.println("Invalid grade. Please enter a value between 0 and 100.");
                }
            } while (grade < 0 || grade > 5.0);

            student.getCourseGrades().put(course.getCourseCode(), grade);
        }

        student.calculateGWA();
        System.out.println("Grades updated successfully. Current GWA: " + String.format("%.2f", student.getGWA()));
    }

    private static void initializeSampleData() {
        // === College of Information Technology (CIT) ===
        Course citCourse1 = new Course("IT101", "Intro to Computing", 3);
        Course citCourse2 = new Course("IT102", "Object-Oriented Programming", 4);
        Course citCourse3 = new Course("IT103", "Software Engineering", 4);
        Course citCourse4 = new Course("IT104", "Data Structures", 4);
        Course citCourse5 = new Course("IT105", "Network Fundamentals", 3);
        Course citGeneral1 = new Course("ITG01", "Programming Fundamentals", 3);
        Course citGeneral2 = new Course("ITG02", "Computer Organization", 3);

        Specialization citSpec1 = new Specialization("Software Development");
        citSpec1.addCourse(citCourse1);
        citSpec1.addCourse(citCourse2);
        citSpec1.addCourse(citCourse3);

        Specialization citSpec2 = new Specialization("Network Engineering");
        citSpec2.addCourse(citCourse4);
        citSpec2.addCourse(citCourse5);

        Program bsit = new Program("BSIT");
        bsit.addSpecialization(citSpec1);
        bsit.addSpecialization(citSpec2);

        Program bscs = new Program("BSCS");
        bscs.addSpecialization(citSpec1);

        Department cit = new Department("College of Information Technology");
        cit.addProgram(bsit);
        cit.addProgram(bscs);

        // === College of Business and Management (CBM) ===
        Course cbmCourse1 = new Course("BM101", "Marketing Principles", 3);
        Course cbmCourse2 = new Course("BM102", "Advertising", 3);
        Course cbmCourse3 = new Course("BM103", "Digital Marketing", 3);
        Course cbmCourse4 = new Course("BM104", "Financial Accounting", 4);
        Course cbmCourse5 = new Course("BM105", "Cost Accounting", 3);
        Course cbmGeneral1 = new Course("BMG01", "Business Ethics", 3);
        Course cbmGeneral2 = new Course("BMG02", "Statistics", 3);

        Specialization cbmSpec1 = new Specialization("Marketing");
        cbmSpec1.addCourse(cbmCourse1);
        cbmSpec1.addCourse(cbmCourse2);
        cbmSpec1.addCourse(cbmCourse3);

        Specialization cbmSpec2 = new Specialization("Accounting");
        cbmSpec2.addCourse(cbmCourse4);
        cbmSpec2.addCourse(cbmCourse5);

        Program bsba = new Program("BSBA");
        bsba.addSpecialization(cbmSpec1);

        Program bsa = new Program("BSA");
        bsa.addSpecialization(cbmSpec2);

        Department cbm = new Department("College of Business and Management");
        cbm.addProgram(bsba);
        cbm.addProgram(bsa);

        // === College of Engineering (COE) ===
        Course coeCourse1 = new Course("ENG101", "Circuit Theory", 3);
        Course coeCourse2 = new Course("ENG102", "Electronics 1", 3);
        Course coeCourse3 = new Course("ENG103", "Embedded Systems", 4);
        Course coeCourse4 = new Course("ENG104", "Thermodynamics", 3);
        Course coeCourse5 = new Course("ENG105", "Machine Design", 4);
        Course coeGeneral1 = new Course("ENGG01", "Physics", 3);
        Course coeGeneral2 = new Course("ENGG02", "Engineering Mathematics", 3);

        Specialization coeSpec1 = new Specialization("Electronics Engineering");
        coeSpec1.addCourse(coeCourse1);
        coeSpec1.addCourse(coeCourse2);
        coeSpec1.addCourse(coeCourse3);

        Specialization coeSpec2 = new Specialization("Mechanical Engineering");
        coeSpec2.addCourse(coeCourse4);
        coeSpec2.addCourse(coeCourse5);

        Program bsee = new Program("BSEE");
        bsee.addSpecialization(coeSpec1);

        Program bsme = new Program("BSME");
        bsme.addSpecialization(coeSpec2);

        Department coe = new Department("College of Engineering");
        coe.addProgram(bsee);
        coe.addProgram(bsme);

        // Add all departments to the global list
        departments.add(cit);
        departments.add(cbm);
        departments.add(coe);
    }

    // Helper methods for safe input handling
    private static int safeInputInt(Scanner scanner, String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                return scanner.nextInt();
            } catch (InputMismatchException e) {
                System.out.println("Invalid input. Please enter a number.");
                scanner.nextLine(); // Clear invalid input
            }
        }
    }

    private static double safeInputDouble(Scanner scanner, String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                return scanner.nextDouble();
            } catch (InputMismatchException e) {
                System.out.println("Invalid input. Please enter a valid number.");
                scanner.nextLine(); // Clear invalid input
            }
        }
    }

    private static String safeInputString(Scanner scanner, String prompt) {
        String input;
        do {
            System.out.print(prompt);
            input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.out.println("Input cannot be empty. Please try again.");
            }
        } while (input.isEmpty());
        return input;
    }

    public static boolean adminLogin(Scanner scanner) {
        final String ADMIN_USERNAME = "admin";  // Hardcoded admin username
        final String ADMIN_PASSWORD = "password123";  // Hardcoded admin password

        System.out.println("\n--- Admin Login ---");

        System.out.print("Enter Admin Username: ");
        String username = scanner.nextLine().trim(); // Use trim() to handle leading/trailing spaces

        System.out.print("Enter Admin Password: ");
        String password = scanner.nextLine().trim();

        if (username.equals(ADMIN_USERNAME) && password.equals(ADMIN_PASSWORD)) {
            System.out.println("Login successful. Welcome, Admin!");
            return true;
        } else {
            System.out.println("Invalid credentials. Access denied.");
            return false;
        }
    }

}
