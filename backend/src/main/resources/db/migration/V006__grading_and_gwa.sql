CREATE TABLE grade_policy (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    minimum_value NUMERIC(6, 2) NOT NULL,
    maximum_value NUMERIC(6, 2) NOT NULL,
    passing_threshold NUMERIC(6, 2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    include_in_gwa BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT grade_policy_values_check CHECK (
        maximum_value > minimum_value
        AND passing_threshold BETWEEN minimum_value AND maximum_value
    ),
    CONSTRAINT grade_policy_dates_check
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT grade_policy_code_check
        CHECK (code ~ '^[A-Z][A-Z0-9_-]{1,49}$')
);

CREATE INDEX grade_policy_effective_dates_idx
    ON grade_policy (effective_from, effective_to);

CREATE TABLE grade_record (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL UNIQUE REFERENCES enrollment(id) ON DELETE RESTRICT,
    grade_policy_id UUID REFERENCES grade_policy(id) ON DELETE RESTRICT,
    numeric_grade NUMERIC(6, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_GRADED',
    submitted_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    submitted_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT grade_record_status_check
        CHECK (status IN ('NOT_GRADED', 'DRAFT', 'FINAL', 'INCOMPLETE', 'WITHDRAWN')),
    CONSTRAINT grade_record_value_state_check CHECK (
        (status = 'NOT_GRADED' AND numeric_grade IS NULL AND grade_policy_id IS NULL)
        OR (status IN ('DRAFT', 'FINAL') AND numeric_grade IS NOT NULL AND grade_policy_id IS NOT NULL)
        OR (status = 'INCOMPLETE' AND numeric_grade IS NULL AND grade_policy_id IS NOT NULL)
        OR (status = 'WITHDRAWN' AND numeric_grade IS NULL)
    ),
    CONSTRAINT grade_record_submission_check CHECK (
        (status = 'FINAL' AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL)
        OR (status <> 'FINAL' AND submitted_at IS NULL)
    )
);

CREATE INDEX grade_record_status_idx ON grade_record (status, enrollment_id);

CREATE TABLE grade_revision (
    id UUID PRIMARY KEY,
    grade_record_id UUID NOT NULL REFERENCES grade_record(id) ON DELETE RESTRICT,
    previous_value NUMERIC(6, 2),
    new_value NUMERIC(6, 2),
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_by UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    approved_by UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT grade_revision_previous_status_check
        CHECK (previous_status IN ('NOT_GRADED', 'DRAFT', 'FINAL', 'INCOMPLETE', 'WITHDRAWN')),
    CONSTRAINT grade_revision_new_status_check
        CHECK (new_status IN ('NOT_GRADED', 'DRAFT', 'FINAL', 'INCOMPLETE', 'WITHDRAWN'))
);

CREATE INDEX grade_revision_record_time_idx
    ON grade_revision (grade_record_id, occurred_at, id);

INSERT INTO grade_record (id, enrollment_id)
SELECT md5(enrollment.id::text || ':grade')::uuid, enrollment.id
FROM enrollment;

INSERT INTO app_permission (id, code, description) VALUES
    ('10000000-0000-0000-0000-000000000018', 'grade.read', 'Read authorized grade records, history, and GWA.'),
    ('10000000-0000-0000-0000-000000000019', 'grade.submit', 'Submit grades for assigned course offerings.'),
    ('10000000-0000-0000-0000-000000000020', 'grade.revise', 'Approve and apply revisions to final grades.'),
    ('10000000-0000-0000-0000-000000000021', 'grade.policy.manage', 'Configure effective-dated grading policies.');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, id
FROM app_permission
WHERE code LIKE 'grade.%';

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002'::uuid, id
FROM app_permission
WHERE code IN ('grade.read', 'grade.revise');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003'::uuid, id
FROM app_permission
WHERE code IN ('grade.read', 'grade.revise', 'grade.policy.manage');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000004'::uuid, id
FROM app_permission
WHERE code IN ('grade.read', 'grade.submit');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM app_role role
CROSS JOIN app_permission permission
WHERE role.code IN ('ADVISER', 'STUDENT', 'AUDITOR')
  AND permission.code = 'grade.read';

UPDATE app_metadata
SET metadata_value = 'grading', updated_at = CURRENT_TIMESTAMP
WHERE metadata_key = 'schema_version';
