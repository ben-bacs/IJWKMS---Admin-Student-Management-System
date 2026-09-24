package edu.wvsu.ijwkms.grading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

final class GwaCalculator {

    private static final int GWA_SCALE = 2;

    private GwaCalculator() {}

    static GwaSummary calculate(UUID studentId, UUID termId, List<GwaComponent> components) {
        BigDecimal totalUnits =
                components.stream().map(GwaComponent::courseUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalUnits.signum() == 0) {
            return new GwaSummary(studentId, termId, null, BigDecimal.ZERO.setScale(2), 0);
        }
        BigDecimal weightedTotal = components.stream()
                .map(component -> component.numericGrade().multiply(component.courseUnits()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GwaSummary(
                studentId,
                termId,
                weightedTotal.divide(totalUnits, GWA_SCALE, RoundingMode.HALF_UP),
                totalUnits.setScale(2, RoundingMode.UNNECESSARY),
                components.size());
    }
}
