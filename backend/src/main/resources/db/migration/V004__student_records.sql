CREATE TABLE student (
    id UUID PRIMARY KEY,
    student_number VARCHAR(30) NOT NULL UNIQUE,
    user_id UUID UNIQUE REFERENCES app_user(id) ON DELETE RESTRICT,
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    preferred_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'APPLICANT',
    admission_date DATE NOT NULL,
    cohort_year SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT student_number_format_check
        CHECK (student_number ~ '^[A-Z0-9][A-Z0-9-]{3,29}$'),
    CONSTRAINT student_status_check
        CHECK (status IN ('APPLICANT', 'ACTIVE', 'LEAVE', 'SUSPENDED', 'GRADUATED', 'WITHDRAWN', 'INACTIVE')),
    CONSTRAINT student_cohort_year_check CHECK (cohort_year BETWEEN 1900 AND 2200)
);

CREATE INDEX student_name_search_idx ON student (last_name, first_name, student_number);
CREATE INDEX student_status_cohort_idx ON student (status, cohort_year);

CREATE TABLE student_profile (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL UNIQUE REFERENCES student(id) ON DELETE RESTRICT,
    address_line_1 VARCHAR(200),
    address_line_2 VARCHAR(200),
    city VARCHAR(100),
    province VARCHAR(100),
    postal_code VARCHAR(20),
    country_code CHAR(2),
    contact_number VARCHAR(30),
    emergency_contact_name VARCHAR(200),
    emergency_contact_number VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT student_profile_country_check
        CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT student_profile_contact_check
        CHECK (contact_number IS NULL OR contact_number ~ '^[+0-9() .-]{7,30}$'),
    CONSTRAINT student_profile_emergency_contact_check
        CHECK (emergency_contact_number IS NULL OR emergency_contact_number ~ '^[+0-9() .-]{7,30}$')
);

CREATE TABLE student_program (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE RESTRICT,
    program_id UUID NOT NULL REFERENCES academic_program(id) ON DELETE RESTRICT,
    curriculum_version_id UUID NOT NULL REFERENCES curriculum_version(id) ON DELETE RESTRICT,
    specialization_id UUID REFERENCES specialization(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_on DATE NOT NULL,
    ended_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT student_program_status_check CHECK (status IN ('ACTIVE', 'CHANGED', 'COMPLETED')),
    CONSTRAINT student_program_dates_check CHECK (ended_on IS NULL OR ended_on >= started_on),
    CONSTRAINT student_program_lifecycle_check CHECK (
        (status = 'ACTIVE' AND ended_on IS NULL)
        OR (status IN ('CHANGED', 'COMPLETED') AND ended_on IS NOT NULL)
    )
);

CREATE UNIQUE INDEX student_program_one_active_idx
    ON student_program (student_id)
    WHERE status = 'ACTIVE';
CREATE INDEX student_program_history_idx
    ON student_program (student_id, started_on DESC);
CREATE INDEX student_program_program_idx
    ON student_program (program_id, status, student_id);

INSERT INTO app_permission (id, code, description) VALUES
    ('10000000-0000-0000-0000-000000000009', 'student.read', 'Read student academic identity and assignment summaries.'),
    ('10000000-0000-0000-0000-000000000010', 'student.write', 'Create and manage student records and assignments.'),
    ('10000000-0000-0000-0000-000000000011', 'student.profile.read', 'Read private student contact and emergency profile data.'),
    ('10000000-0000-0000-0000-000000000012', 'student.profile.write', 'Manage private student contact and emergency profile data.');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, id
FROM app_permission
WHERE code LIKE 'student.%';

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002'::uuid, id
FROM app_permission
WHERE code LIKE 'student.%';

INSERT INTO app_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM app_role role
CROSS JOIN app_permission permission
WHERE role.code IN ('ACADEMIC_ADMIN', 'FACULTY', 'ADVISER', 'AUDITOR')
  AND permission.code = 'student.read';

UPDATE app_metadata
SET metadata_value = 'students', updated_at = CURRENT_TIMESTAMP
WHERE metadata_key = 'schema_version';
