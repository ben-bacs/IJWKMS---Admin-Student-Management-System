CREATE TABLE organization_unit (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES organization_unit(id) ON DELETE RESTRICT,
    unit_type VARCHAR(20) NOT NULL,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT organization_unit_type_check
        CHECK (unit_type IN ('INSTITUTION', 'COLLEGE', 'DEPARTMENT')),
    CONSTRAINT organization_unit_status_check
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT organization_unit_code_check
        CHECK (code ~ '^[A-Z][A-Z0-9_-]{1,29}$'),
    CONSTRAINT organization_unit_not_own_parent_check
        CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX organization_unit_parent_idx ON organization_unit (parent_id, unit_type, status);

CREATE TABLE academic_program (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES organization_unit(id) ON DELETE RESTRICT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    degree_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT academic_program_degree_type_check
        CHECK (degree_type IN ('CERTIFICATE', 'DIPLOMA', 'ASSOCIATE', 'BACHELOR', 'MASTER', 'DOCTORATE')),
    CONSTRAINT academic_program_status_check
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT academic_program_code_check
        CHECK (code ~ '^[A-Z][A-Z0-9_-]{1,49}$')
);

CREATE INDEX academic_program_department_idx ON academic_program (department_id, status);

CREATE TABLE specialization (
    id UUID PRIMARY KEY,
    program_id UUID NOT NULL REFERENCES academic_program(id) ON DELETE RESTRICT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT specialization_program_code_unique UNIQUE (program_id, code),
    CONSTRAINT specialization_status_check
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT specialization_code_check
        CHECK (code ~ '^[A-Z][A-Z0-9_-]{1,49}$')
);

CREATE INDEX specialization_program_idx ON specialization (program_id, status);

CREATE TABLE course (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    units NUMERIC(4,1) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT course_status_check
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT course_code_check
        CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{1,29}$'),
    CONSTRAINT course_units_check
        CHECK (units > 0 AND units <= 12)
);

CREATE INDEX course_status_code_idx ON course (status, code);

CREATE TABLE course_prerequisite (
    course_id UUID NOT NULL REFERENCES course(id) ON DELETE RESTRICT,
    prerequisite_course_id UUID NOT NULL REFERENCES course(id) ON DELETE RESTRICT,
    minimum_grade_rule VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (course_id, prerequisite_course_id),
    CONSTRAINT course_prerequisite_not_self_check CHECK (course_id <> prerequisite_course_id)
);

CREATE INDEX course_prerequisite_reverse_idx ON course_prerequisite (prerequisite_course_id, course_id);

CREATE TABLE curriculum (
    id UUID PRIMARY KEY,
    program_id UUID NOT NULL REFERENCES academic_program(id) ON DELETE RESTRICT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT curriculum_code_check
        CHECK (code ~ '^[A-Z][A-Z0-9_-]{1,49}$')
);

CREATE INDEX curriculum_program_idx ON curriculum (program_id);

CREATE TABLE curriculum_version (
    id UUID PRIMARY KEY,
    curriculum_id UUID NOT NULL REFERENCES curriculum(id) ON DELETE RESTRICT,
    version_code VARCHAR(30) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT curriculum_version_code_unique UNIQUE (curriculum_id, version_code),
    CONSTRAINT curriculum_version_status_check
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT curriculum_version_code_check
        CHECK (version_code ~ '^[A-Z0-9][A-Z0-9._-]{0,29}$'),
    CONSTRAINT curriculum_version_dates_check
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT curriculum_version_lifecycle_check CHECK (
        (status = 'DRAFT' AND activated_at IS NULL AND retired_at IS NULL)
        OR (status = 'ACTIVE' AND activated_at IS NOT NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND activated_at IS NOT NULL AND retired_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX curriculum_one_active_version_idx
    ON curriculum_version (curriculum_id)
    WHERE status = 'ACTIVE';

CREATE INDEX curriculum_version_history_idx
    ON curriculum_version (curriculum_id, effective_from DESC);

CREATE TABLE curriculum_requirement (
    id UUID PRIMARY KEY,
    curriculum_version_id UUID NOT NULL REFERENCES curriculum_version(id) ON DELETE RESTRICT,
    course_id UUID NOT NULL REFERENCES course(id) ON DELETE RESTRICT,
    specialization_id UUID REFERENCES specialization(id) ON DELETE RESTRICT,
    requirement_type VARCHAR(20) NOT NULL,
    recommended_year SMALLINT,
    recommended_term SMALLINT,
    minimum_grade_rule VARCHAR(100),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT curriculum_requirement_type_check
        CHECK (requirement_type IN ('CORE', 'ELECTIVE')),
    CONSTRAINT curriculum_requirement_year_check
        CHECK (recommended_year IS NULL OR recommended_year BETWEEN 1 AND 8),
    CONSTRAINT curriculum_requirement_term_check
        CHECK (recommended_term IS NULL OR recommended_term BETWEEN 1 AND 4),
    CONSTRAINT curriculum_requirement_order_check CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX curriculum_requirement_scope_unique
    ON curriculum_requirement (curriculum_version_id, course_id, specialization_id) NULLS NOT DISTINCT;

CREATE INDEX curriculum_requirement_version_idx
    ON curriculum_requirement (curriculum_version_id, display_order, course_id);

CREATE TABLE academic_year (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT academic_year_code_check CHECK (code ~ '^[0-9]{4}-[0-9]{4}$'),
    CONSTRAINT academic_year_dates_check CHECK (end_date > start_date),
    CONSTRAINT academic_year_status_check CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED'))
);

CREATE UNIQUE INDEX academic_year_one_active_idx ON academic_year ((status)) WHERE status = 'ACTIVE';
CREATE INDEX academic_year_dates_idx ON academic_year (start_date DESC, end_date DESC);

CREATE TABLE academic_term (
    id UUID PRIMARY KEY,
    academic_year_id UUID NOT NULL REFERENCES academic_year(id) ON DELETE RESTRICT,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    enrollment_open_at TIMESTAMPTZ,
    enrollment_close_at TIMESTAMPTZ,
    grade_submission_deadline TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT academic_term_year_code_unique UNIQUE (academic_year_id, code),
    CONSTRAINT academic_term_code_check CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,19}$'),
    CONSTRAINT academic_term_dates_check CHECK (end_date > start_date),
    CONSTRAINT academic_term_enrollment_window_check CHECK (
        enrollment_open_at IS NULL
        OR enrollment_close_at IS NULL
        OR enrollment_close_at > enrollment_open_at
    ),
    CONSTRAINT academic_term_status_check CHECK (
        status IN ('PLANNED', 'ENROLLMENT_OPEN', 'IN_PROGRESS', 'GRADING', 'CLOSED')
    )
);

CREATE INDEX academic_term_year_dates_idx
    ON academic_term (academic_year_id, start_date, end_date);

INSERT INTO app_permission (id, code, description) VALUES
    ('10000000-0000-0000-0000-000000000005', 'academics.read', 'Read academic organization, catalog, and calendar data.'),
    ('10000000-0000-0000-0000-000000000006', 'academics.manage', 'Manage academic organization, catalog, and calendar data.'),
    ('10000000-0000-0000-0000-000000000007', 'curriculum.read', 'Read programs, curricula, versions, and requirements.'),
    ('10000000-0000-0000-0000-000000000008', 'curriculum.manage', 'Manage programs, curricula, versions, and requirements.');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, id
FROM app_permission
WHERE code IN ('academics.read', 'academics.manage', 'curriculum.read', 'curriculum.manage');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003'::uuid, id
FROM app_permission
WHERE code IN ('academics.read', 'academics.manage', 'curriculum.read', 'curriculum.manage');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM app_role role
CROSS JOIN app_permission permission
WHERE role.code IN ('REGISTRAR', 'FACULTY', 'ADVISER', 'STUDENT', 'AUDITOR')
  AND permission.code IN ('academics.read', 'curriculum.read');

UPDATE app_metadata
SET metadata_value = 'academics', updated_at = CURRENT_TIMESTAMP
WHERE metadata_key = 'schema_version';
