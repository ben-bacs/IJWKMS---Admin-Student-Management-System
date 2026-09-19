package edu.wvsu.ijwkms.academics;

import java.util.UUID;

public interface AcademicCalendarDirectory {

    boolean termExists(UUID termId);

    boolean termAcceptsEnrollment(UUID termId);
}
