import { jsonRequest } from '../../api/client'

export type AdminRecord = Record<string, unknown>

export type AdminField = {
  name: string
  label: string
  type?: 'text' | 'number' | 'date' | 'datetime-local' | 'password' | 'select' | 'checkbox' | 'textarea'
  required?: boolean
  placeholder?: string
  options?: string[]
}

export type AdminOperation = {
  title: string
  detail: string
  permission: string
  fields: AdminField[]
  request: (values: Record<string, FormDataEntryValue>) => { path: string; init?: RequestInit }
  confirm?: string
}

export type AdminSection = {
  key: string
  label: string
  description: string
  permission: string
  endpoint?: string
  columns?: Array<{ key: string; label: string }>
  operations?: AdminOperation[]
}

const value = (values: Record<string, FormDataEntryValue>, name: string) => String(values[name] ?? '').trim()
const optional = (values: Record<string, FormDataEntryValue>, name: string) => value(values, name) || null
const instant = (values: Record<string, FormDataEntryValue>, name: string) => value(values, name) ? new Date(value(values, name)).toISOString() : null
const number = (values: Record<string, FormDataEntryValue>, name: string) => Number(value(values, name))
const csv = (values: Record<string, FormDataEntryValue>, name: string) => value(values, name).split(',').map((item) => item.trim()).filter(Boolean)
const mutation = (method: string, body?: unknown): RequestInit => jsonRequest(method, body)
const encoded = (values: Record<string, FormDataEntryValue>, name: string) => encodeURIComponent(value(values, name))

export const ADMIN_ENTRY_PERMISSIONS = [
  'student.read', 'academics.read', 'curriculum.read', 'course.offering.read',
  'enrollment.read', 'grade.read', 'success.read', 'identity.users.read', 'audit.events.read',
]

export const ADMIN_SECTIONS: AdminSection[] = [
  {
    key: 'overview', label: 'Overview', permission: '*',
    description: 'Permission-scoped operational coverage and shortcuts for today’s work.',
  },
  {
    key: 'students', label: 'Students', permission: 'student.read', endpoint: '/api/v1/students?size=25',
    description: 'Search the student registry, create records, and make audited lifecycle changes.',
    columns: [{ key: 'studentNumber', label: 'Student' }, { key: 'firstName', label: 'First name' }, { key: 'lastName', label: 'Last name' }, { key: 'status', label: 'Status' }, { key: 'cohortYear', label: 'Cohort' }],
    operations: [
      {
        title: 'Create student', detail: 'Use existing program and curriculum-version identifiers.', permission: 'student.write',
        fields: [
          { name: 'studentNumber', label: 'Student number', required: true }, { name: 'userId', label: 'Linked user ID' },
          { name: 'firstName', label: 'First name', required: true }, { name: 'middleName', label: 'Middle name' },
          { name: 'lastName', label: 'Last name', required: true }, { name: 'preferredName', label: 'Preferred name' },
          { name: 'status', label: 'Status', type: 'select', options: ['APPLICANT', 'ACTIVE'], required: true },
          { name: 'admissionDate', label: 'Admission date', type: 'date', required: true },
          { name: 'cohortYear', label: 'Cohort year', type: 'number', required: true },
          { name: 'programId', label: 'Program ID', required: true }, { name: 'curriculumVersionId', label: 'Curriculum version ID', required: true },
          { name: 'specializationId', label: 'Specialization ID' }, { name: 'programStartedOn', label: 'Program start', type: 'date', required: true },
        ],
        request: (v) => ({ path: '/api/v1/students', init: mutation('POST', {
          studentNumber: value(v, 'studentNumber'), userId: optional(v, 'userId'), firstName: value(v, 'firstName'), middleName: optional(v, 'middleName'),
          lastName: value(v, 'lastName'), preferredName: optional(v, 'preferredName'), status: value(v, 'status'), admissionDate: value(v, 'admissionDate'),
          cohortYear: number(v, 'cohortYear'), programId: value(v, 'programId'), curriculumVersionId: value(v, 'curriculumVersionId'),
          specializationId: optional(v, 'specializationId'), programStartedOn: value(v, 'programStartedOn'),
        }) }),
      },
      {
        title: 'Change student status', detail: 'Status changes are audited and use optimistic locking.', permission: 'student.write', confirm: 'Confirm this student lifecycle change.',
        fields: [{ name: 'studentId', label: 'Student ID', required: true }, { name: 'status', label: 'New status', type: 'select', options: ['ACTIVE', 'LEAVE', 'SUSPENDED', 'GRADUATED', 'WITHDRAWN', 'INACTIVE'], required: true }, { name: 'version', label: 'Current version', type: 'number', required: true }],
        request: (v) => ({ path: `/api/v1/students/${encoded(v, 'studentId')}/status`, init: mutation('PATCH', { status: value(v, 'status'), version: number(v, 'version') }) }),
      },
    ],
  },
  {
    key: 'organizations', label: 'Organizations', permission: 'academics.read', endpoint: '/api/v1/academics/organization-units?size=25',
    description: 'Maintain the institution, college, and department hierarchy.',
    columns: [{ key: 'code', label: 'Code' }, { key: 'name', label: 'Name' }, { key: 'type', label: 'Type' }, { key: 'status', label: 'Status' }],
    operations: [{
      title: 'Create organization unit', detail: 'Parent is required for colleges and departments.', permission: 'academics.manage',
      fields: [{ name: 'parentId', label: 'Parent ID' }, { name: 'type', label: 'Type', type: 'select', options: ['INSTITUTION', 'COLLEGE', 'DEPARTMENT'], required: true }, { name: 'code', label: 'Code', required: true }, { name: 'name', label: 'Name', required: true }],
      request: (v) => ({ path: '/api/v1/academics/organization-units', init: mutation('POST', { parentId: optional(v, 'parentId'), type: value(v, 'type'), code: value(v, 'code'), name: value(v, 'name') }) }),
    }],
  },
  {
    key: 'programs', label: 'Programs', permission: 'curriculum.read', endpoint: '/api/v1/curriculum/programs?size=25',
    description: 'Configure degree programs and their department ownership.',
    columns: [{ key: 'code', label: 'Code' }, { key: 'name', label: 'Name' }, { key: 'degreeType', label: 'Degree' }, { key: 'status', label: 'Status' }],
    operations: [{
      title: 'Create program', detail: 'Programs begin in draft for review.', permission: 'curriculum.manage',
      fields: [{ name: 'departmentId', label: 'Department ID', required: true }, { name: 'code', label: 'Code', required: true }, { name: 'name', label: 'Name', required: true }, { name: 'degreeType', label: 'Degree type', type: 'select', options: ['CERTIFICATE', 'DIPLOMA', 'ASSOCIATE', 'BACHELOR', 'MASTER', 'DOCTORATE'], required: true }],
      request: (v) => ({ path: '/api/v1/curriculum/programs', init: mutation('POST', { departmentId: value(v, 'departmentId'), code: value(v, 'code'), name: value(v, 'name'), degreeType: value(v, 'degreeType') }) }),
    }],
  },
  {
    key: 'curricula', label: 'Curricula', permission: 'curriculum.read', endpoint: '/api/v1/curriculum/curricula?size=25',
    description: 'Create immutable curriculum lines and explicitly version their requirements.',
    columns: [{ key: 'code', label: 'Code' }, { key: 'name', label: 'Name' }, { key: 'programId', label: 'Program ID' }, { key: 'version', label: 'Version' }],
    operations: [
      {
        title: 'Create curriculum', detail: 'Attach a named curriculum to a program.', permission: 'curriculum.manage',
        fields: [{ name: 'programId', label: 'Program ID', required: true }, { name: 'code', label: 'Code', required: true }, { name: 'name', label: 'Name', required: true }],
        request: (v) => ({ path: '/api/v1/curriculum/curricula', init: mutation('POST', { programId: value(v, 'programId'), code: value(v, 'code'), name: value(v, 'name') }) }),
      },
      {
        title: 'Add curriculum version', detail: 'Historical versions stay intact.', permission: 'curriculum.manage',
        fields: [{ name: 'curriculumId', label: 'Curriculum ID', required: true }, { name: 'versionCode', label: 'Version code', required: true }, { name: 'effectiveFrom', label: 'Effective from', type: 'date', required: true }, { name: 'effectiveTo', label: 'Effective to', type: 'date' }],
        request: (v) => ({ path: `/api/v1/curriculum/curricula/${encoded(v, 'curriculumId')}/versions`, init: mutation('POST', { versionCode: value(v, 'versionCode'), effectiveFrom: value(v, 'effectiveFrom'), effectiveTo: optional(v, 'effectiveTo') }) }),
      },
    ],
  },
  {
    key: 'courses', label: 'Courses', permission: 'academics.read', endpoint: '/api/v1/academics/courses?size=25',
    description: 'Manage the governed course catalog and lifecycle.',
    columns: [{ key: 'code', label: 'Code' }, { key: 'name', label: 'Name' }, { key: 'units', label: 'Units' }, { key: 'status', label: 'Status' }],
    operations: [
      {
        title: 'Create course', detail: 'New catalog entries begin as drafts.', permission: 'academics.manage',
        fields: [{ name: 'code', label: 'Code', required: true }, { name: 'name', label: 'Name', required: true }, { name: 'description', label: 'Description', type: 'textarea' }, { name: 'units', label: 'Units', type: 'number', required: true }],
        request: (v) => ({ path: '/api/v1/academics/courses', init: mutation('POST', { code: value(v, 'code'), name: value(v, 'name'), description: optional(v, 'description'), units: number(v, 'units') }) }),
      },
      {
        title: 'Change course status', detail: 'Activation and retirement require explicit confirmation.', permission: 'academics.manage', confirm: 'Confirm this catalog lifecycle change.',
        fields: [{ name: 'courseId', label: 'Course ID', required: true }, { name: 'status', label: 'Status', type: 'select', options: ['ACTIVE', 'INACTIVE'], required: true }, { name: 'version', label: 'Current version', type: 'number', required: true }],
        request: (v) => ({ path: `/api/v1/academics/courses/${encoded(v, 'courseId')}/status`, init: mutation('PATCH', { status: value(v, 'status'), version: number(v, 'version') }) }),
      },
    ],
  },
  {
    key: 'terms', label: 'Terms', permission: 'academics.read', endpoint: '/api/v1/academics/academic-years?size=25',
    description: 'Configure academic years, term windows, and controlled lifecycle states.',
    columns: [{ key: 'code', label: 'Academic year' }, { key: 'label', label: 'Label' }, { key: 'startDate', label: 'Starts' }, { key: 'endDate', label: 'Ends' }, { key: 'status', label: 'Status' }],
    operations: [
      {
        title: 'Create academic year', detail: 'Dates are validated by the server.', permission: 'academics.manage',
        fields: [{ name: 'code', label: 'Code (YYYY-YYYY)', required: true }, { name: 'label', label: 'Label', required: true }, { name: 'startDate', label: 'Start date', type: 'date', required: true }, { name: 'endDate', label: 'End date', type: 'date', required: true }],
        request: (v) => ({ path: '/api/v1/academics/academic-years', init: mutation('POST', { code: value(v, 'code'), label: value(v, 'label'), startDate: value(v, 'startDate'), endDate: value(v, 'endDate') }) }),
      },
      {
        title: 'Create term', detail: 'Configure enrollment and grading windows.', permission: 'academics.manage',
        fields: [{ name: 'academicYearId', label: 'Academic year ID', required: true }, { name: 'code', label: 'Code', required: true }, { name: 'name', label: 'Name', required: true }, { name: 'startDate', label: 'Start date', type: 'date', required: true }, { name: 'endDate', label: 'End date', type: 'date', required: true }, { name: 'enrollmentOpenAt', label: 'Enrollment opens', type: 'datetime-local' }, { name: 'enrollmentCloseAt', label: 'Enrollment closes', type: 'datetime-local' }, { name: 'gradeSubmissionDeadline', label: 'Grade deadline', type: 'datetime-local' }],
        request: (v) => ({ path: `/api/v1/academics/academic-years/${encoded(v, 'academicYearId')}/terms`, init: mutation('POST', { code: value(v, 'code'), name: value(v, 'name'), startDate: value(v, 'startDate'), endDate: value(v, 'endDate'), enrollmentOpenAt: instant(v, 'enrollmentOpenAt'), enrollmentCloseAt: instant(v, 'enrollmentCloseAt'), gradeSubmissionDeadline: instant(v, 'gradeSubmissionDeadline') }) }),
      },
    ],
  },
  {
    key: 'offerings', label: 'Offerings', permission: 'course.offering.read', endpoint: '/api/v1/course-offerings?size=25',
    description: 'Publish sections, capacities, schedules, instructors, and offering status.',
    columns: [{ key: 'courseCode', label: 'Course' }, { key: 'academicTermCode', label: 'Term' }, { key: 'sectionCode', label: 'Section' }, { key: 'enrolledCount', label: 'Enrolled' }, { key: 'capacity', label: 'Capacity' }, { key: 'status', label: 'Status' }],
    operations: [
      {
        title: 'Create offering', detail: 'Create a draft course section.', permission: 'course.offering.manage',
        fields: [{ name: 'courseId', label: 'Course ID', required: true }, { name: 'academicTermId', label: 'Term ID', required: true }, { name: 'sectionCode', label: 'Section code', required: true }, { name: 'capacity', label: 'Capacity', type: 'number', required: true }],
        request: (v) => ({ path: '/api/v1/course-offerings', init: mutation('POST', { courseId: value(v, 'courseId'), academicTermId: value(v, 'academicTermId'), sectionCode: value(v, 'sectionCode'), capacity: number(v, 'capacity') }) }),
      },
      {
        title: 'Change offering status', detail: 'Opening, completing, or cancelling a section affects enrollment.', permission: 'course.offering.manage', confirm: 'Confirm this offering lifecycle change.',
        fields: [{ name: 'offeringId', label: 'Offering ID', required: true }, { name: 'status', label: 'Status', type: 'select', options: ['OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'], required: true }, { name: 'version', label: 'Current version', type: 'number', required: true }],
        request: (v) => ({ path: `/api/v1/course-offerings/${encoded(v, 'offeringId')}/status`, init: mutation('PATCH', { status: value(v, 'status'), version: number(v, 'version') }) }),
      },
    ],
  },
  {
    key: 'enrollments', label: 'Enrollments', permission: 'enrollment.read',
    description: 'Review rosters and perform audited enrollment, drop, and withdrawal operations.',
    columns: [{ key: 'courseCode', label: 'Course' }, { key: 'academicTermCode', label: 'Term' }, { key: 'sectionCode', label: 'Section' }, { key: 'status', label: 'Status' }, { key: 'enrolledAt', label: 'Enrolled at' }],
    operations: [
      { title: 'Load student enrollments', detail: 'Retrieve the authorized enrollment history.', permission: 'enrollment.read', fields: [{ name: 'studentId', label: 'Student ID', required: true }], request: (v) => ({ path: `/api/v1/enrollments/students/${encoded(v, 'studentId')}?size=100` }) },
      { title: 'Enroll student', detail: 'Capacity, duplicates, term state, and prerequisites are enforced server-side.', permission: 'enrollment.create', fields: [{ name: 'studentId', label: 'Student ID', required: true }, { name: 'courseOfferingId', label: 'Offering ID', required: true }], request: (v) => ({ path: '/api/v1/enrollments', init: mutation('POST', { studentId: value(v, 'studentId'), courseOfferingId: value(v, 'courseOfferingId') }) }) },
      { title: 'Drop or withdraw', detail: 'This writes an immutable enrollment event.', permission: 'enrollment.drop', confirm: 'Confirm this enrollment status change.', fields: [{ name: 'enrollmentId', label: 'Enrollment ID', required: true }, { name: 'transition', label: 'Transition', type: 'select', options: ['drop', 'withdraw'], required: true }, { name: 'reason', label: 'Reason', type: 'textarea' }, { name: 'version', label: 'Current version', type: 'number', required: true }], request: (v) => ({ path: `/api/v1/enrollments/${encoded(v, 'enrollmentId')}/${encoded(v, 'transition')}`, init: mutation('POST', { reason: optional(v, 'reason'), version: number(v, 'version') }) }) },
    ],
  },
  {
    key: 'grading', label: 'Grading oversight', permission: 'grade.read', endpoint: '/api/v1/grade-policies',
    description: 'Inspect gradebooks, submit grades, and preserve revision history.',
    columns: [{ key: 'code', label: 'Policy' }, { key: 'name', label: 'Name' }, { key: 'minimumValue', label: 'Minimum' }, { key: 'maximumValue', label: 'Maximum' }, { key: 'passingThreshold', label: 'Passing' }],
    operations: [
      { title: 'Load offering gradebook', detail: 'Faculty scope and oversight permissions remain enforced.', permission: 'grade.read', fields: [{ name: 'offeringId', label: 'Offering ID', required: true }], request: (v) => ({ path: `/api/v1/course-offerings/${encoded(v, 'offeringId')}/grades?size=100` }) },
      { title: 'Submit enrollment grade', detail: 'Final submissions require confirmation and are audited.', permission: 'grade.submit', confirm: 'Confirm this grade submission. Final grades require a revision reason to change later.', fields: [{ name: 'enrollmentId', label: 'Enrollment ID', required: true }, { name: 'gradePolicyId', label: 'Grade policy ID' }, { name: 'numericGrade', label: 'Numeric grade', type: 'number' }, { name: 'status', label: 'Status', type: 'select', options: ['DRAFT', 'FINAL', 'INCOMPLETE', 'WITHDRAWN'], required: true }, { name: 'version', label: 'Current version (0 if new)', type: 'number', required: true }], request: (v) => ({ path: `/api/v1/enrollments/${encoded(v, 'enrollmentId')}/grade`, init: mutation('PUT', { gradePolicyId: optional(v, 'gradePolicyId'), numericGrade: value(v, 'numericGrade') ? number(v, 'numericGrade') : null, status: value(v, 'status'), version: number(v, 'version') }) }) },
    ],
  },
  {
    key: 'advising', label: 'Advising', permission: 'success.read',
    description: 'Evaluate standing, review alerts, and record privacy-aware advising notes.',
    columns: [{ key: 'severity', label: 'Severity' }, { key: 'status', label: 'Status' }, { key: 'ruleCode', label: 'Rule' }, { key: 'explanation', label: 'Explanation' }, { key: 'createdAt', label: 'Created' }],
    operations: [
      { title: 'Load student alerts', detail: 'Only authorized student-success records are returned.', permission: 'success.read', fields: [{ name: 'studentId', label: 'Student ID', required: true }], request: (v) => ({ path: `/api/v1/students/${encoded(v, 'studentId')}/advising-alerts` }) },
      { title: 'Evaluate academic standing', detail: 'Evaluation uses the active versioned policy and records its facts.', permission: 'success.evaluate', confirm: 'Confirm standing evaluation for this student and term.', fields: [{ name: 'studentId', label: 'Student ID', required: true }, { name: 'termId', label: 'Term ID', required: true }], request: (v) => ({ path: `/api/v1/students/${encoded(v, 'studentId')}/standing/${encoded(v, 'termId')}/evaluate`, init: mutation('POST') }) },
      { title: 'Add advising note', detail: 'Choose student-visible or adviser-only visibility deliberately.', permission: 'advising.note.write', fields: [{ name: 'studentId', label: 'Student ID', required: true }, { name: 'visibility', label: 'Visibility', type: 'select', options: ['ADVISER_ONLY', 'STUDENT_VISIBLE'], required: true }, { name: 'content', label: 'Note', type: 'textarea', required: true }], request: (v) => ({ path: `/api/v1/students/${encoded(v, 'studentId')}/advising-notes`, init: mutation('POST', { visibility: value(v, 'visibility'), content: value(v, 'content') }) }) },
    ],
  },
  {
    key: 'users', label: 'Users & roles', permission: 'identity.users.read', endpoint: '/api/v1/identity/users?size=100',
    description: 'Provision accounts and make explicit, audited role or account-status changes.',
    columns: [{ key: 'username', label: 'Username' }, { key: 'displayName', label: 'Name' }, { key: 'status', label: 'Status' }, { key: 'roles', label: 'Roles' }],
    operations: [
      { title: 'Create user', detail: 'Passwords are validated and stored only as secure hashes.', permission: 'identity.users.manage', fields: [{ name: 'username', label: 'Username', required: true }, { name: 'displayName', label: 'Display name', required: true }, { name: 'password', label: 'Temporary password', type: 'password', required: true }, { name: 'roles', label: 'Roles (comma-separated)', placeholder: 'STUDENT', required: true }], request: (v) => ({ path: '/api/v1/identity/users', init: mutation('POST', { username: value(v, 'username'), displayName: value(v, 'displayName'), password: value(v, 'password'), roles: csv(v, 'roles') }) }) },
      { title: 'Change account status', detail: 'Disabling an account revokes its active sessions.', permission: 'identity.users.manage', confirm: 'Confirm this account status change.', fields: [{ name: 'userId', label: 'User ID', required: true }, { name: 'status', label: 'Status', type: 'select', options: ['ACTIVE', 'LOCKED', 'DISABLED'], required: true }], request: (v) => ({ path: `/api/v1/identity/users/${encoded(v, 'userId')}/status`, init: mutation('PATCH', { status: value(v, 'status') }) }) },
      { title: 'Assign or remove role', detail: 'Role changes take effect on the next authorization check.', permission: 'identity.roles.manage', confirm: 'Confirm this role assignment change.', fields: [{ name: 'userId', label: 'User ID', required: true }, { name: 'roleCode', label: 'Role code', required: true }, { name: 'action', label: 'Action', type: 'select', options: ['assign', 'remove'], required: true }], request: (v) => ({ path: `/api/v1/identity/users/${encoded(v, 'userId')}/roles/${encoded(v, 'roleCode')}`, init: mutation(value(v, 'action') === 'assign' ? 'PUT' : 'DELETE') }) },
    ],
  },
  {
    key: 'reports', label: 'Reports', permission: 'student.read',
    description: 'Prepare scoped operational views before official PDF and CSV export.',
  },
  {
    key: 'audit', label: 'Audit', permission: 'audit.events.read', endpoint: '/api/v1/audit/events?size=50',
    description: 'Review immutable security and business events without exposing secret data.',
    columns: [{ key: 'occurredAt', label: 'Time' }, { key: 'actorDisplayName', label: 'Actor' }, { key: 'action', label: 'Action' }, { key: 'targetType', label: 'Target' }, { key: 'outcome', label: 'Outcome' }, { key: 'correlationId', label: 'Correlation' }],
    operations: [{ title: 'Filter audit events', detail: 'Search by action and optional outcome.', permission: 'audit.events.read', fields: [{ name: 'action', label: 'Action contains' }, { name: 'outcome', label: 'Outcome', type: 'select', options: ['', 'SUCCESS', 'FAILURE', 'DENIED'] }], request: (v) => ({ path: `/api/v1/audit/events?size=50&action=${encoded(v, 'action')}${value(v, 'outcome') ? `&outcome=${encoded(v, 'outcome')}` : ''}` }) }],
  },
  {
    key: 'settings', label: 'Settings', permission: 'identity.users.read',
    description: 'Inspect your effective access and security-governed platform defaults.',
  },
]
