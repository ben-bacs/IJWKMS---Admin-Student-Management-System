package edu.wvsu.ijwkms.advising;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class AdvisingStore {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    AdvisingStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    boolean policyExists(String code, String version) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM standing_policy WHERE code=:code AND version_code=:version)")
                .param("code", code)
                .param("version", version)
                .query(Boolean.class)
                .single();
    }

    void createPolicy(StandingPolicyView p) {
        jdbc.sql("""
                INSERT INTO standing_policy (id,code,version_code,name,failed_course_threshold,
                watch_gwa_threshold,probation_gwa_threshold,consecutive_decline_terms,status,version)
                VALUES (:id,:code,:vc,:name,:failed,:watch,:probation,:decline,'DRAFT',0)
                """)
                .param("id", p.id())
                .param("code", p.code())
                .param("vc", p.versionCode())
                .param("name", p.name())
                .param("failed", p.failedCourseThreshold())
                .param("watch", p.watchGwaThreshold())
                .param("probation", p.probationGwaThreshold())
                .param("decline", p.consecutiveDeclineTerms())
                .update();
    }

    Optional<StandingPolicyView> findPolicy(UUID id) {
        return jdbc.sql("SELECT * FROM standing_policy WHERE id=:id")
                .param("id", id)
                .query(this::policy)
                .optional();
    }

    Optional<StandingPolicyView> activePolicy() {
        return jdbc.sql("SELECT * FROM standing_policy WHERE status='ACTIVE'")
                .query(this::policy)
                .optional();
    }

    int activatePolicy(UUID id, Instant now) {
        jdbc.sql("UPDATE standing_policy SET status='RETIRED',version=version+1 WHERE status='ACTIVE'")
                .update();
        return jdbc.sql(
                        "UPDATE standing_policy SET status='ACTIVE',activated_at=:now,version=version+1 WHERE id=:id AND status='DRAFT'")
                .param("now", utc(now))
                .param("id", id)
                .update();
    }

    boolean standingExists(UUID student, UUID term) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM academic_standing WHERE student_id=:s AND academic_term_id=:t)")
                .param("s", student)
                .param("t", term)
                .query(Boolean.class)
                .single();
    }

    int failedCourses(UUID student, UUID term) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM grade_record g JOIN grade_policy p ON p.id=g.grade_policy_id
                JOIN enrollment e ON e.id=g.enrollment_id JOIN course_offering o ON o.id=e.course_offering_id
                WHERE e.student_id=:s AND o.academic_term_id=:t AND g.status='FINAL'
                AND g.numeric_grade > p.passing_threshold
                """)
                .param("s", student)
                .param("t", term)
                .query(Integer.class)
                .single();
    }

    int declineCount(UUID student, BigDecimal current) {
        if (current == null) return 0;
        List<BigDecimal> prior =
                jdbc.sql("""
                SELECT cumulative_gwa FROM academic_standing
                WHERE student_id=:s AND cumulative_gwa IS NOT NULL ORDER BY evaluated_at DESC LIMIT 10
                """).param("s", student).query(BigDecimal.class).list();
        int count = 0;
        BigDecimal latest = current;
        for (BigDecimal previous : prior) {
            if (latest.compareTo(previous) > 0) {
                count++;
                latest = previous;
            } else break;
        }
        return count;
    }

    void createStanding(AcademicStandingView s, String facts) {
        jdbc.sql("""
                INSERT INTO academic_standing (id,student_id,academic_term_id,standing_policy_id,status,
                term_gwa,cumulative_gwa,failed_course_count,consecutive_decline_count,input_facts,evaluated_at,evaluated_by)
                VALUES (:id,:student,:term,:policy,:status,:termGwa,:cumulative,:failed,:decline,CAST(:facts AS jsonb),:at,:by)
                """)
                .param("id", s.id())
                .param("student", s.studentId())
                .param("term", s.academicTermId())
                .param("policy", s.standingPolicyId())
                .param("status", s.status().name())
                .param("termGwa", s.termGwa())
                .param("cumulative", s.cumulativeGwa())
                .param("failed", s.failedCourseCount())
                .param("decline", s.consecutiveDeclineCount())
                .param("facts", facts)
                .param("at", utc(s.evaluatedAt()))
                .param("by", s.evaluatedBy())
                .update();
    }

    Optional<AcademicStandingView> findStanding(UUID student, UUID term) {
        return jdbc.sql("SELECT * FROM academic_standing WHERE student_id=:s AND academic_term_id=:t")
                .param("s", student)
                .param("t", term)
                .query(this::standing)
                .optional();
    }

    int overrideStanding(UUID id, StandingStatus status, String reason, UUID actor, Instant now, long version) {
        return jdbc.sql("""
                UPDATE academic_standing SET status=:status,override_reason=:reason,overridden_by=:actor,
                overridden_at=:now,version=version+1 WHERE id=:id AND version=:version
                """)
                .param("status", status.name())
                .param("reason", reason)
                .param("actor", actor)
                .param("now", utc(now))
                .param("id", id)
                .param("version", version)
                .update();
    }

    void createAlert(AdvisingAlertView a, String facts) {
        jdbc.sql("""
                INSERT INTO advising_alert (id,student_id,academic_term_id,academic_standing_id,rule_code,
                rule_version,severity,status,input_facts,explanation) VALUES
                (:id,:student,:term,:standing,:rule,:rv,:severity,'OPEN',CAST(:facts AS jsonb),:explanation)
                """)
                .param("id", a.id())
                .param("student", a.studentId())
                .param("term", a.academicTermId())
                .param("standing", a.academicStandingId())
                .param("rule", a.ruleCode())
                .param("rv", a.ruleVersion())
                .param("severity", a.severity().name())
                .param("facts", facts)
                .param("explanation", a.explanation())
                .update();
    }

    List<AdvisingAlertView> alerts(UUID student) {
        return jdbc.sql("SELECT * FROM advising_alert WHERE student_id=:s ORDER BY created_at DESC,id")
                .param("s", student)
                .query(this::alertView)
                .list();
    }

    Optional<AdvisingAlertView> lockAlert(UUID id) {
        jdbc.sql("SELECT id FROM advising_alert WHERE id=:id FOR UPDATE")
                .param("id", id)
                .query(UUID.class)
                .optional();
        return jdbc.sql("SELECT * FROM advising_alert WHERE id=:id")
                .param("id", id)
                .query(this::alertView)
                .optional();
    }

    int transitionAlert(UUID id, AlertStatus next, UUID actor, Instant now, long version) {
        return jdbc.sql("""
                UPDATE advising_alert SET status=:next,updated_at=:now,
                resolved_at=CASE WHEN :next IN ('RESOLVED','DISMISSED') THEN :now ELSE NULL END,
                resolved_by=CASE WHEN :next IN ('RESOLVED','DISMISSED') THEN CAST(:actor AS uuid) ELSE NULL END,
                version=version+1 WHERE id=:id AND version=:version
                """)
                .param("next", next.name())
                .param("now", utc(now))
                .param("actor", actor)
                .param("id", id)
                .param("version", version)
                .update();
    }

    void alertEvent(UUID alert, AlertStatus from, AlertStatus to, String reason, UUID actor, Instant now) {
        jdbc.sql("""
                INSERT INTO advising_alert_event (id,advising_alert_id,previous_status,new_status,reason,actor_user_id,occurred_at)
                VALUES (:id,:alert,:from,:to,:reason,:actor,:now)
                """)
                .param("id", UUID.randomUUID())
                .param("alert", alert)
                .param("from", from.name())
                .param("to", to.name())
                .param("reason", reason)
                .param("actor", actor)
                .param("now", utc(now))
                .update();
    }

    void assign(AdviserAssignmentView a) {
        jdbc.sql("""
                INSERT INTO adviser_assignment (id,student_id,adviser_user_id,assigned_by)
                VALUES (:id,:student,:adviser,:by)
                """)
                .param("id", a.id())
                .param("student", a.studentId())
                .param("adviser", a.adviserUserId())
                .param("by", a.assignedBy())
                .update();
    }

    boolean assigned(UUID student, UUID adviser) {
        return jdbc.sql(
                        "SELECT EXISTS(SELECT 1 FROM adviser_assignment WHERE student_id=:s AND adviser_user_id=:a AND status='ACTIVE')")
                .param("s", student)
                .param("a", adviser)
                .query(Boolean.class)
                .single();
    }

    void note(AdvisingNoteView n) {
        jdbc.sql("""
                INSERT INTO advising_note (id,student_id,adviser_user_id,visibility,content)
                VALUES (:id,:student,:adviser,:visibility,:content)
                """)
                .param("id", n.id())
                .param("student", n.studentId())
                .param("adviser", n.adviserUserId())
                .param("visibility", n.visibility().name())
                .param("content", n.content())
                .update();
    }

    List<AdvisingNoteView> notes(UUID student, boolean includePrivate) {
        return jdbc.sql("""
                SELECT * FROM advising_note WHERE student_id=:s
                AND (:private OR visibility='STUDENT_VISIBLE') ORDER BY created_at DESC,id
                """)
                .param("s", student)
                .param("private", includePrivate)
                .query(this::noteView)
                .list();
    }

    private StandingPolicyView policy(ResultSet r, int n) throws SQLException {
        return new StandingPolicyView(
                r.getObject("id", UUID.class),
                r.getString("code"),
                r.getString("version_code"),
                r.getString("name"),
                r.getInt("failed_course_threshold"),
                r.getBigDecimal("watch_gwa_threshold"),
                r.getBigDecimal("probation_gwa_threshold"),
                r.getInt("consecutive_decline_terms"),
                r.getString("status"),
                instant(r, "activated_at"),
                r.getLong("version"));
    }

    private AcademicStandingView standing(ResultSet r, int n) throws SQLException {
        return new AcademicStandingView(
                r.getObject("id", UUID.class),
                r.getObject("student_id", UUID.class),
                r.getObject("academic_term_id", UUID.class),
                r.getObject("standing_policy_id", UUID.class),
                StandingStatus.valueOf(r.getString("status")),
                r.getBigDecimal("term_gwa"),
                r.getBigDecimal("cumulative_gwa"),
                r.getInt("failed_course_count"),
                r.getInt("consecutive_decline_count"),
                map(r.getString("input_facts")),
                instant(r, "evaluated_at"),
                r.getObject("evaluated_by", UUID.class),
                r.getString("override_reason"),
                r.getObject("overridden_by", UUID.class),
                instant(r, "overridden_at"),
                r.getLong("version"));
    }

    private AdvisingAlertView alertView(ResultSet r, int n) throws SQLException {
        return new AdvisingAlertView(
                r.getObject("id", UUID.class),
                r.getObject("student_id", UUID.class),
                r.getObject("academic_term_id", UUID.class),
                r.getObject("academic_standing_id", UUID.class),
                r.getString("rule_code"),
                r.getString("rule_version"),
                AlertSeverity.valueOf(r.getString("severity")),
                AlertStatus.valueOf(r.getString("status")),
                map(r.getString("input_facts")),
                r.getString("explanation"),
                instant(r, "created_at"),
                instant(r, "resolved_at"),
                r.getObject("resolved_by", UUID.class),
                r.getLong("version"));
    }

    private AdvisingNoteView noteView(ResultSet r, int n) throws SQLException {
        return new AdvisingNoteView(
                r.getObject("id", UUID.class),
                r.getObject("student_id", UUID.class),
                r.getObject("adviser_user_id", UUID.class),
                NoteVisibility.valueOf(r.getString("visibility")),
                r.getString("content"),
                instant(r, "created_at"),
                instant(r, "updated_at"),
                r.getLong("version"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(String value) {
        try {
            return json.readValue(value, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant instant(ResultSet r, String c) throws SQLException {
        Timestamp t = r.getTimestamp(c);
        return t == null ? null : t.toInstant();
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
