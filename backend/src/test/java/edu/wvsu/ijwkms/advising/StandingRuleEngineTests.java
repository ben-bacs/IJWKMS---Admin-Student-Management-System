package edu.wvsu.ijwkms.advising;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StandingRuleEngineTests {
    private final StandingPolicyView policy = new StandingPolicyView(
            UUID.randomUUID(),
            "DEFAULT",
            "V1",
            "Default",
            2,
            new BigDecimal("2.50"),
            new BigDecimal("3.00"),
            2,
            "ACTIVE",
            null,
            0);

    @Test
    void reachesWatchAtBoundary() {
        var d = StandingRuleEngine.evaluate(
                policy, new StandingInputs(new BigDecimal("2.50"), new BigDecimal("2.50"), 0, 0));
        assertThat(d.status()).isEqualTo(StandingStatus.WATCH);
        assertThat(d.alerts()).extracting(RuleAlert::code).containsExactly("WATCH_GWA_THRESHOLD");
    }

    @Test
    void reachesProbationForFailuresAndGwa() {
        var d = StandingRuleEngine.evaluate(
                policy, new StandingInputs(new BigDecimal("3.00"), new BigDecimal("3.00"), 2, 0));
        assertThat(d.status()).isEqualTo(StandingStatus.PROBATION);
        assertThat(d.alerts())
                .extracting(RuleAlert::code)
                .containsExactly("FAILING_COURSE_THRESHOLD", "PROBATION_GWA_THRESHOLD");
    }

    @Test
    void remainsGoodBelowBoundaries() {
        assertThat(StandingRuleEngine.evaluate(
                                policy, new StandingInputs(new BigDecimal("2.49"), new BigDecimal("2.49"), 1, 1))
                        .status())
                .isEqualTo(StandingStatus.GOOD_STANDING);
    }

    @Test
    void isUndeterminedWithoutEligibleGrades() {
        assertThat(StandingRuleEngine.evaluate(policy, new StandingInputs(null, null, 0, 0))
                        .status())
                .isEqualTo(StandingStatus.UNDETERMINED);
    }
}
