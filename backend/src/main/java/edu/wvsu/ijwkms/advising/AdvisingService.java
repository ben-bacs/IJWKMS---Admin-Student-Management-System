package edu.wvsu.ijwkms.advising;

import edu.wvsu.ijwkms.audit.AuditOutcome;
import edu.wvsu.ijwkms.audit.AuditService;
import edu.wvsu.ijwkms.grading.GradeDirectory;
import edu.wvsu.ijwkms.identity.IdentityDirectory;
import edu.wvsu.ijwkms.shared.AcademicCode;
import edu.wvsu.ijwkms.shared.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class AdvisingService {
    private final AdvisingStore store;
    private final GradeDirectory grades;
    private final IdentityDirectory identities;
    private final AuditService audit;
    private final JsonMapper json;
    private final Clock clock;

    AdvisingService(
            AdvisingStore store,
            GradeDirectory grades,
            IdentityDirectory identities,
            AuditService audit,
            JsonMapper json,
            Clock clock) {
        this.store = store;
        this.grades = grades;
        this.identities = identities;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    StandingPolicyView createPolicy(
            UUID actor,
            String code,
            String version,
            String name,
            int failed,
            BigDecimal watch,
            BigDecimal probation,
            int decline) {
        String c = AcademicCode.normalize(code), v = AcademicCode.normalize(version);
        if (store.policyExists(c, v)) throw conflict("STANDING_POLICY_EXISTS", "Policy version already exists.");
        if (probation.compareTo(watch) < 0)
            throw bad("STANDING_THRESHOLDS_INVALID", "Probation threshold must be at least watch threshold.");
        StandingPolicyView p = new StandingPolicyView(
                UUID.randomUUID(), c, v, name.trim(), failed, watch, probation, decline, "DRAFT", null, 0);
        store.createPolicy(p);
        audit(actor, "STANDING_POLICY_CREATED", p.id(), Map.of("code", c, "version", v));
        return p;
    }

    @Transactional
    StandingPolicyView activate(UUID actor, UUID id) {
        StandingPolicyView p = requirePolicy(id);
        if (store.activatePolicy(id, clock.instant()) == 0)
            throw conflict("STANDING_POLICY_NOT_DRAFT", "Only draft policies can activate.");
        audit(actor, "STANDING_POLICY_ACTIVATED", id, Map.of("version", p.versionCode()));
        return requirePolicy(id);
    }

    @Transactional
    AcademicStandingView evaluate(UUID actor, UUID student, UUID term) {
        if (store.standingExists(student, term))
            throw conflict("STANDING_ALREADY_EVALUATED", "Standing already exists for this term.");
        StandingPolicyView p = store.activePolicy()
                .orElseThrow(() -> conflict("ACTIVE_STANDING_POLICY_REQUIRED", "No active policy exists."));
        var termPerf = grades.academicPerformance(student, term);
        var cumulative = grades.academicPerformance(student, null);
        int failed = store.failedCourses(student, term);
        int decline = store.declineCount(student, cumulative.weightedGwa());
        StandingInputs inputs = new StandingInputs(termPerf.weightedGwa(), cumulative.weightedGwa(), failed, decline);
        StandingDecision decision = StandingRuleEngine.evaluate(p, inputs);
        Instant now = clock.instant();
        Map<String, Object> facts = facts(inputs);
        AcademicStandingView s = new AcademicStandingView(
                UUID.randomUUID(),
                student,
                term,
                p.id(),
                decision.status(),
                inputs.termGwa(),
                inputs.cumulativeGwa(),
                failed,
                decline,
                facts,
                now,
                actor,
                null,
                null,
                null,
                0);
        String encoded = encode(facts);
        store.createStanding(s, encoded);
        for (RuleAlert rule : decision.alerts())
            store.createAlert(
                    new AdvisingAlertView(
                            UUID.randomUUID(),
                            student,
                            term,
                            s.id(),
                            rule.code(),
                            p.versionCode(),
                            rule.severity(),
                            AlertStatus.OPEN,
                            facts,
                            rule.explanation(),
                            now,
                            null,
                            null,
                            0),
                    encoded);
        audit(
                actor,
                "ACADEMIC_STANDING_EVALUATED",
                s.id(),
                Map.of("status", decision.status(), "alerts", decision.alerts().size()));
        return s;
    }

    @Transactional(readOnly = true)
    AcademicStandingView standing(UUID student, UUID term) {
        return store.findStanding(student, term)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "STANDING_NOT_FOUND", "Standing was not found."));
    }

    @Transactional
    AcademicStandingView override(
            UUID actor, UUID student, UUID term, StandingStatus status, String reason, long version) {
        AcademicStandingView s = standing(student, term);
        if (store.overrideStanding(s.id(), status, reason.trim(), actor, clock.instant(), version) == 0) throw stale();
        audit(actor, "ACADEMIC_STANDING_OVERRIDDEN", s.id(), Map.of("from", s.status(), "to", status));
        return standing(student, term);
    }

    @Transactional
    AdviserAssignmentView assign(UUID actor, UUID student, UUID adviser) {
        if (!identities.userHasRole(adviser, "ADVISER"))
            throw bad("ADVISER_ROLE_REQUIRED", "Assigned user must be an adviser.");
        AdviserAssignmentView a = new AdviserAssignmentView(
                UUID.randomUUID(), student, adviser, "ACTIVE", clock.instant(), null, actor, 0);
        store.assign(a);
        audit(actor, "ADVISER_ASSIGNED", a.id(), Map.of("studentId", student, "adviserId", adviser));
        return a;
    }

    @Transactional
    AdvisingNoteView note(UUID actor, UUID student, NoteVisibility visibility, String content) {
        if (!store.assigned(student, actor))
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "ADVISER_NOT_ASSIGNED", "The adviser is not assigned to this student.");
        AdvisingNoteView n = new AdvisingNoteView(
                UUID.randomUUID(), student, actor, visibility, content.trim(), clock.instant(), clock.instant(), 0);
        store.note(n);
        audit(actor, "ADVISING_NOTE_CREATED", n.id(), Map.of("studentId", student, "visibility", visibility));
        return n;
    }

    @Transactional(readOnly = true)
    List<AdvisingNoteView> notes(UUID student, boolean includePrivate) {
        return store.notes(student, includePrivate);
    }

    @Transactional(readOnly = true)
    List<AdvisingAlertView> alerts(UUID student) {
        return store.alerts(student);
    }

    @Transactional
    AdvisingAlertView transition(UUID actor, UUID id, AlertStatus next, String reason, long version) {
        AdvisingAlertView current = store.lockAlert(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "Alert was not found."));
        boolean allowed =
                switch (current.status()) {
                    case OPEN -> next == AlertStatus.ACKNOWLEDGED || next == AlertStatus.DISMISSED;
                    case ACKNOWLEDGED -> next == AlertStatus.IN_PROGRESS || next == AlertStatus.DISMISSED;
                    case IN_PROGRESS -> next == AlertStatus.RESOLVED || next == AlertStatus.DISMISSED;
                    case RESOLVED, DISMISSED -> false;
                };
        if (!allowed) throw conflict("ALERT_TRANSITION_INVALID", "Alert transition is invalid.");
        Instant now = clock.instant();
        if (store.transitionAlert(id, next, actor, now, version) == 0) throw stale();
        store.alertEvent(id, current.status(), next, reason, actor, now);
        audit(actor, "ADVISING_ALERT_STATUS_CHANGED", id, Map.of("from", current.status(), "to", next));
        return store.alerts(current.studentId()).stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private StandingPolicyView requirePolicy(UUID id) {
        return store.findPolicy(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "STANDING_POLICY_NOT_FOUND", "Policy not found."));
    }

    private Map<String, Object> facts(StandingInputs i) {
        return Map.of(
                "termGwa",
                i.termGwa() == null ? "UNAVAILABLE" : i.termGwa(),
                "cumulativeGwa",
                i.cumulativeGwa() == null ? "UNAVAILABLE" : i.cumulativeGwa(),
                "failedCourses",
                i.failedCourses(),
                "consecutiveDeclineTerms",
                i.declineCount());
    }

    private String encode(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void audit(UUID actor, String action, UUID id, Map<String, ?> detail) {
        audit.record(actor, action, "STUDENT_SUCCESS", id.toString(), AuditOutcome.SUCCESS, detail);
    }

    private static ApiException conflict(String c, String m) {
        return new ApiException(HttpStatus.CONFLICT, c, m);
    }

    private static ApiException bad(String c, String m) {
        return new ApiException(HttpStatus.BAD_REQUEST, c, m);
    }

    private static ApiException stale() {
        return conflict("OPTIMISTIC_LOCK_CONFLICT", "The record changed; reload and retry.");
    }
}
