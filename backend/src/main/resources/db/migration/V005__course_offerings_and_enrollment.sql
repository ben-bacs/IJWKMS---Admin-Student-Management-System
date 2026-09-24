CREATE TABLE course_offering (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES course(id) ON DELETE RESTRICT,
    academic_term_id UUID NOT NULL REFERENCES academic_term(id) ON DELETE RESTRICT,
    section_code VARCHAR(20) NOT NULL,
    capacity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT course_offering_scope_unique UNIQUE (course_id, academic_term_id, section_code),
    CONSTRAINT course_offering_section_check CHECK (section_code ~ '^[A-Z0-9][A-Z0-9_-]{0,19}$'),
    CONSTRAINT course_offering_capacity_check CHECK (capacity BETWEEN 1 AND 1000),
    CONSTRAINT course_offering_status_check
        CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX course_offering_term_status_idx
    ON course_offering (academic_term_id, status, course_id);

CREATE TABLE course_offering_schedule (
    id UUID PRIMARY KEY,
    course_offering_id UUID NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    location VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT course_offering_schedule_day_check CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT course_offering_schedule_time_check CHECK (end_time > start_time),
    CONSTRAINT course_offering_schedule_unique
        UNIQUE (course_offering_id, day_of_week, start_time, end_time)
);

CREATE INDEX course_offering_schedule_day_idx
    ON course_offering_schedule (day_of_week, start_time, end_time);

CREATE TABLE course_offering_instructor (
    course_offering_id UUID NOT NULL REFERENCES course_offering(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    instructor_role VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (course_offering_id, user_id),
    CONSTRAINT course_offering_instructor_role_check
        CHECK (instructor_role IN ('PRIMARY', 'CO_INSTRUCTOR', 'GRADER'))
);

CREATE INDEX course_offering_instructor_user_idx
    ON course_offering_instructor (user_id, course_offering_id);

CREATE TABLE enrollment (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES student(id) ON DELETE RESTRICT,
    course_offering_id UUID NOT NULL REFERENCES course_offering(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL,
    dropped_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT enrollment_student_offering_unique UNIQUE (student_id, course_offering_id),
    CONSTRAINT enrollment_status_check
        CHECK (status IN ('ENROLLED', 'DROPPED', 'WITHDRAWN', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT enrollment_lifecycle_check CHECK (
        (status = 'ENROLLED' AND dropped_at IS NULL AND withdrawn_at IS NULL
            AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'DROPPED' AND dropped_at IS NOT NULL AND withdrawn_at IS NULL
            AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'WITHDRAWN' AND dropped_at IS NULL AND withdrawn_at IS NOT NULL
            AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'COMPLETED' AND dropped_at IS NULL AND withdrawn_at IS NULL
            AND completed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND dropped_at IS NULL AND withdrawn_at IS NULL
            AND completed_at IS NULL AND cancelled_at IS NOT NULL)
    )
);

CREATE INDEX enrollment_student_status_idx ON enrollment (student_id, status, enrolled_at DESC);
CREATE INDEX enrollment_offering_status_idx ON enrollment (course_offering_id, status, enrolled_at);

CREATE TABLE enrollment_event (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES enrollment(id) ON DELETE RESTRICT,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    actor_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    correlation_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT enrollment_event_previous_status_check CHECK (
        previous_status IS NULL
        OR previous_status IN ('ENROLLED', 'DROPPED', 'WITHDRAWN', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT enrollment_event_new_status_check
        CHECK (new_status IN ('ENROLLED', 'DROPPED', 'WITHDRAWN', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX enrollment_event_enrollment_time_idx
    ON enrollment_event (enrollment_id, occurred_at, id);

INSERT INTO app_permission (id, code, description) VALUES
    ('10000000-0000-0000-0000-000000000013', 'course.offering.read', 'Read term course offerings, schedules, and instructors.'),
    ('10000000-0000-0000-0000-000000000014', 'course.offering.manage', 'Manage term course offerings, schedules, and instructors.'),
    ('10000000-0000-0000-0000-000000000015', 'enrollment.read', 'Read student and offering enrollment records.'),
    ('10000000-0000-0000-0000-000000000016', 'enrollment.create', 'Create authorized student enrollments.'),
    ('10000000-0000-0000-0000-000000000017', 'enrollment.drop', 'Drop or withdraw authorized student enrollments.');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, id
FROM app_permission
WHERE code IN (
    'course.offering.read', 'course.offering.manage', 'enrollment.read',
    'enrollment.create', 'enrollment.drop'
);

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002'::uuid, id
FROM app_permission
WHERE code IN ('course.offering.read', 'enrollment.read', 'enrollment.create', 'enrollment.drop');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003'::uuid, id
FROM app_permission
WHERE code IN ('course.offering.read', 'course.offering.manage', 'enrollment.read');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM app_role role
CROSS JOIN app_permission permission
WHERE role.code IN ('FACULTY', 'ADVISER', 'AUDITOR')
  AND permission.code IN ('course.offering.read', 'enrollment.read');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000006'::uuid, id
FROM app_permission
WHERE code IN ('course.offering.read', 'enrollment.create', 'enrollment.drop');

UPDATE app_metadata
SET metadata_value = 'enrollment', updated_at = CURRENT_TIMESTAMP
WHERE metadata_key = 'schema_version';
