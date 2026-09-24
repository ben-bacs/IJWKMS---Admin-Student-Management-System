package edu.wvsu.ijwkms.grading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GwaCalculatorTests {

    @Test
    void weightsGradesByCourseUnitsAndRoundsHalfUp() {
        UUID studentId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();

        GwaSummary result = GwaCalculator.calculate(
                studentId,
                termId,
                List.of(
                        new GwaComponent(new BigDecimal("1.25"), new BigDecimal("3.00")),
                        new GwaComponent(new BigDecimal("2.00"), new BigDecimal("6.00"))));

        assertThat(result.studentId()).isEqualTo(studentId);
        assertThat(result.academicTermId()).isEqualTo(termId);
        assertThat(result.weightedGwa()).isEqualByComparingTo("1.75");
        assertThat(result.totalUnits()).isEqualByComparingTo("9.00");
        assertThat(result.eligibleGradeCount()).isEqualTo(2);
    }

    @Test
    void representsNoEligibleGradesWithoutAZeroGrade() {
        GwaSummary result = GwaCalculator.calculate(UUID.randomUUID(), null, List.of());

        assertThat(result.weightedGwa()).isNull();
        assertThat(result.totalUnits()).isEqualByComparingTo("0.00");
        assertThat(result.eligibleGradeCount()).isZero();
    }
}
