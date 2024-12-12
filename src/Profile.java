
public class Profile {

    private String address;
    private String contactNumber;
    private String emergencyContact;

    public Profile(String address, String contactNumber, String emergencyContact) {
        this.address = address;
        this.contactNumber = contactNumber;
        this.emergencyContact = emergencyContact;
    }

    // Getters and Setters
    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

}
