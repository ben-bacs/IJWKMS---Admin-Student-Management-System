package edu.wvsu.ijwkms.grading;

import java.math.BigDecimal;
import java.util.UUID;

public interface GradeDirectory {

    AcademicPerformance academicPerformance(UUID studentId, UUID academicTermId);

    record AcademicPerformance(BigDecimal weightedGwa, BigDecimal totalUnits, int eligibleGradeCount) {}
}
