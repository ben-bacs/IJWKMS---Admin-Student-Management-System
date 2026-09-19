CREATE TABLE standing_policy (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    version_code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    failed_course_threshold INTEGER NOT NULL,
    watch_gwa_threshold NUMERIC(6, 2) NOT NULL,
    probation_gwa_threshold NUMERIC(6, 2) NOT NULL,
    consecutive_decline_terms INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (code, version_code),
    CONSTRAINT standing_policy_threshold_check CHECK (
        failed_course_threshold BETWEEN 1 AND 20
        AND probation_gwa_threshold >= watch_gwa_threshold
        AND consecutive_decline_terms BETWEEN 1 AND 10
    ),
    CONSTRAINT standing_policy_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT standing_policy_lifecycle_check CHECK (
        (status = 'DRAFT' AND activated_at IS NULL)
        OR (status IN ('ACTIVE', 'RETIRED') AND activated_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX standing_policy_one_active_idx ON standing_policy ((status)) WHERE status = 'ACTIVE';

CREATE TABLE academic_standing (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE RESTRICT,
    academic_term_id UUID NOT NULL REFERENCES academic_term(id) ON DELETE RESTRICT,
    standing_policy_id UUID NOT NULL REFERENCES standing_policy(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL,
    term_gwa NUMERIC(6, 2),
    cumulative_gwa NUMERIC(6, 2),
    failed_course_count INTEGER NOT NULL,
    consecutive_decline_count INTEGER NOT NULL,
    input_facts JSONB NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    evaluated_by UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    override_reason VARCHAR(1000),
    overridden_by UUID REFERENCES app_user(id) ON DELETE RESTRICT,
    overridden_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (student_id, academic_term_id),
    CONSTRAINT academic_standing_status_check CHECK (
        status IN ('GOOD_STANDING', 'WATCH', 'PROBATION', 'SUSPENSION_REVIEW', 'UNDETERMINED')
    ),
    CONSTRAINT academic_standing_counts_check CHECK (
        failed_course_count >= 0 AND consecutive_decline_count >= 0
    ),
    CONSTRAINT academic_standing_override_check CHECK (
        (override_reason IS NULL AND overridden_by IS NULL AND overridden_at IS NULL)
        OR (override_reason IS NOT NULL AND overridden_by IS NOT NULL AND overridden_at IS NOT NULL)
    )
);

CREATE INDEX academic_standing_student_time_idx
    ON academic_standing (student_id, evaluated_at DESC);

CREATE TABLE adviser_assignment (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE RESTRICT,
    adviser_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMPTZ,
    assigned_by UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT adviser_assignment_status_check CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT adviser_assignment_lifecycle_check CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL) OR (status = 'ENDED' AND ended_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX adviser_assignment_one_active_idx
    ON adviser_assignment (student_id) WHERE status = 'ACTIVE';
CREATE INDEX adviser_assignment_adviser_idx
    ON adviser_assignment (adviser_user_id, status, student_id);

CREATE TABLE advising_alert (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE RESTRICT,
    academic_term_id UUID NOT NULL REFERENCES academic_term(id) ON DELETE RESTRICT,
    academic_standing_id UUID NOT NULL REFERENCES academic_standing(id) ON DELETE RESTRICT,
    rule_code VARCHAR(100) NOT NULL,
    rule_version VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    input_facts JSONB NOT NULL,
    explanation VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES app_user(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT advising_alert_severity_check CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT advising_alert_status_check CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED')
    ),
    CONSTRAINT advising_alert_resolution_check CHECK (
        (status IN ('RESOLVED', 'DISMISSED') AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
        OR (status NOT IN ('RESOLVED', 'DISMISSED') AND resolved_at IS NULL AND resolved_by IS NULL)
    )
);

CREATE INDEX advising_alert_student_status_idx ON advising_alert (student_id, status, created_at DESC);

CREATE TABLE advising_alert_event (
    id UUID PRIMARY KEY,
    advising_alert_id UUID NOT NULL REFERENCES advising_alert(id) ON DELETE RESTRICT,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(1000),
    actor_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX advising_alert_event_alert_time_idx
    ON advising_alert_event (advising_alert_id, occurred_at, id);

CREATE TABLE advising_note (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE RESTRICT,
    adviser_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    visibility VARCHAR(30) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT advising_note_visibility_check CHECK (visibility IN ('ADVISER_ONLY', 'STUDENT_VISIBLE'))
);

CREATE INDEX advising_note_student_time_idx ON advising_note (student_id, created_at DESC);

INSERT INTO app_permission (id, code, description) VALUES
    ('10000000-0000-0000-0000-000000000022', 'success.read', 'Read authorized standing, alert, and advising information.'),
    ('10000000-0000-0000-0000-000000000023', 'success.evaluate', 'Evaluate deterministic academic standing rules.'),
    ('10000000-0000-0000-0000-000000000024', 'success.manage', 'Manage standing policies, assignments, alerts, and overrides.'),
    ('10000000-0000-0000-0000-000000000025', 'advising.note.write', 'Create authorized advising notes.');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, id FROM app_permission WHERE code LIKE 'success.%' OR code = 'advising.note.write';
INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003'::uuid, id FROM app_permission WHERE code IN ('success.read', 'success.evaluate', 'success.manage');
INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000005'::uuid, id FROM app_permission WHERE code IN ('success.read', 'success.evaluate', 'success.manage', 'advising.note.write');
INSERT INTO app_role_permission (role_id, permission_id)
SELECT role.id, permission.id FROM app_role role CROSS JOIN app_permission permission
WHERE role.code IN ('REGISTRAR', 'FACULTY', 'AUDITOR') AND permission.code = 'success.read';
INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000006'::uuid, id FROM app_permission WHERE code = 'success.read';

UPDATE app_metadata SET metadata_value = 'student-success', updated_at = CURRENT_TIMESTAMP
WHERE metadata_key = 'schema_version';
