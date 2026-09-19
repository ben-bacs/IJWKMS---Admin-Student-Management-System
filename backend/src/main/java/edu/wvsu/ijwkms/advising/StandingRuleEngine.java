package edu.wvsu.ijwkms.advising;

import java.util.ArrayList;

final class StandingRuleEngine {
    private StandingRuleEngine() {}

    static StandingDecision evaluate(StandingPolicyView policy, StandingInputs inputs) {
        var alerts = new ArrayList<RuleAlert>();
        if (inputs.failedCourses() >= policy.failedCourseThreshold()) {
            alerts.add(new RuleAlert(
                    "FAILING_COURSE_THRESHOLD",
                    AlertSeverity.HIGH,
                    "Failed courses " + inputs.failedCourses() + " met threshold " + policy.failedCourseThreshold()
                            + "."));
        }
        if (inputs.termGwa() != null && inputs.termGwa().compareTo(policy.probationGwaThreshold()) >= 0) {
            alerts.add(new RuleAlert(
                    "PROBATION_GWA_THRESHOLD",
                    AlertSeverity.HIGH,
                    "Term GWA " + inputs.termGwa() + " met probation threshold " + policy.probationGwaThreshold()
                            + "."));
        } else if (inputs.termGwa() != null && inputs.termGwa().compareTo(policy.watchGwaThreshold()) >= 0) {
            alerts.add(new RuleAlert(
                    "WATCH_GWA_THRESHOLD",
                    AlertSeverity.MEDIUM,
                    "Term GWA " + inputs.termGwa() + " met watch threshold " + policy.watchGwaThreshold() + "."));
        }
        if (inputs.declineCount() >= policy.consecutiveDeclineTerms()) {
            alerts.add(new RuleAlert(
                    "CONSECUTIVE_DECLINE",
                    AlertSeverity.MEDIUM,
                    "GWA declined for " + inputs.declineCount() + " consecutive evaluated terms."));
        }
        StandingStatus status;
        if (inputs.termGwa() == null) status = StandingStatus.UNDETERMINED;
        else if (alerts.stream().anyMatch(a -> a.severity() == AlertSeverity.HIGH)) status = StandingStatus.PROBATION;
        else if (!alerts.isEmpty()) status = StandingStatus.WATCH;
        else status = StandingStatus.GOOD_STANDING;
        return new StandingDecision(status, java.util.List.copyOf(alerts));
    }
}
