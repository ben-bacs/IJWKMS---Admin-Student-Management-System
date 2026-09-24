import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { ApiError, apiRequest, jsonRequest } from '../api/client'
import { useAuth } from '../auth/AuthContext'

type Assignment = { curriculumVersionId: string; curriculumVersionCode: string; programCode: string }
type Student = { id: string; studentNumber: string; firstName: string; middleName?: string; lastName: string; preferredName?: string; status: string; cohortYear: number; programAssignment?: Assignment }
type Profile = { addressLine1?: string; city?: string; province?: string; contactNumber?: string }
type Enrollment = { id: string; courseCode: string; academicTermId: string; academicTermCode: string; sectionCode: string; status: string }
type Grade = { id: string; courseCode: string; academicTermId: string; academicTermCode: string; numericGrade: number | null; status: string; courseUnits: number }
type Gwa = { weightedGwa: number | null; totalUnits: number; eligibleGradeCount: number }
type Standing = { status: string; termGwa: number | null; cumulativeGwa: number | null; evaluatedAt: string }
type Alert = { id: string; ruleCode: string; severity: string; status: string; explanation: string; createdAt: string }
type Note = { id: string; content: string; createdAt: string }
type Session = { id: string; userAgent?: string; createdAt: string; lastSeenAt: string; expiresAt: string; current: boolean }
type Requirement = { id: string; courseId: string; courseCode: string; requirementType: string; recommendedYear?: number; recommendedTerm?: number }
type Page<T> = { items: T[]; totalElements: number }
type PortalData = { student: Student; profile: Profile; enrollments: Enrollment[]; grades: Grade[]; gwa: Gwa; standing: Standing | null; alerts: Alert[]; notes: Note[]; sessions: Session[]; requirements: Requirement[] }

const sections = [
  ['dashboard', 'Dashboard'], ['profile', 'Profile'], ['enrollment', 'Current enrollment'], ['grades', 'Grades'],
  ['history', 'Academic history'], ['curriculum', 'Curriculum progress'], ['standing', 'Academic standing'],
  ['notifications', 'Notifications'], ['privacy', 'Privacy & data'], ['sessions', 'Active sessions'], ['reports', 'Report download'],
] as const

export function StudentPortalPage() {
  const auth = useAuth()
  const location = useLocation()
  const section = location.pathname.split('/')[2] || 'dashboard'
  const [data, setData] = useState<PortalData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busySession, setBusySession] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    async function load() {
      try {
        const student = await apiRequest<Student>('/api/v1/students/me')
        const [profile, enrollmentPage, gradePage, gwa, alerts, notes, sessions, requirements] = await Promise.all([
          apiRequest<Profile>(`/api/v1/students/${student.id}/profile`),
          apiRequest<Page<Enrollment>>(`/api/v1/enrollments/students/${student.id}?page=0&size=100`),
          apiRequest<Page<Grade>>(`/api/v1/students/${student.id}/grades?page=0&size=100`),
          apiRequest<Gwa>(`/api/v1/students/${student.id}/gwa`),
          apiRequest<Alert[]>(`/api/v1/students/${student.id}/advising-alerts`),
          apiRequest<Note[]>(`/api/v1/students/${student.id}/advising-notes`),
          apiRequest<Session[]>('/api/v1/auth/sessions'),
          student.programAssignment?.curriculumVersionId
            ? apiRequest<Page<Requirement>>(`/api/v1/curriculum/versions/${student.programAssignment.curriculumVersionId}/requirements?page=0&size=100`)
            : Promise.resolve({ items: [], totalElements: 0 }),
        ])
        const latestTerm = enrollmentPage.items[0]?.academicTermId ?? gradePage.items[0]?.academicTermId
        let standing: Standing | null = null
        if (latestTerm) {
          try { standing = await apiRequest<Standing>(`/api/v1/students/${student.id}/standing/${latestTerm}`) }
          catch (standingError) { if (!(standingError instanceof ApiError && standingError.status === 404)) throw standingError }
        }
        if (active) setData({ student, profile, enrollments: enrollmentPage.items, grades: gradePage.items, gwa, standing, alerts, notes, sessions, requirements: requirements.items })
      } catch (loadError) {
        if (active) setError(loadError instanceof ApiError ? loadError.message : 'The portal could not be loaded safely.')
      }
    }
    void load()
    return () => { active = false }
  }, [])

  const current = useMemo(() => data?.enrollments.filter((item) => item.status === 'ENROLLED') ?? [], [data])
  async function revoke(session: Session) {
    setBusySession(session.id)
    try {
      await apiRequest<void>(`/api/v1/auth/sessions/${session.id}/revoke`, jsonRequest('POST'))
      if (session.current) await auth.logout()
      else setData((existing) => existing ? { ...existing, sessions: existing.sessions.filter((item) => item.id !== session.id) } : existing)
    } catch (revokeError) { setError(revokeError instanceof ApiError ? revokeError.message : 'Session revocation failed.') }
    finally { setBusySession(null) }
  }
  function downloadReport() {
    if (!data) return
    const report = { generatedAt: new Date().toISOString(), student: data.student, enrollments: data.enrollments, grades: data.grades, gwa: data.gwa, standing: data.standing }
    const url = URL.createObjectURL(new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' }))
    const link = document.createElement('a'); link.href = url; link.download = `academic-record-${data.student.studentNumber}.json`; link.click(); URL.revokeObjectURL(url)
  }

  if (error && !data) return <PortalState title="Portal unavailable" detail={error} />
  if (!data) return <PortalState title="Loading your portal" detail="Retrieving your authorized academic record…" />
  const name = data.student.preferredName || data.student.firstName
  const openAlerts = data.alerts.filter((alert) => !['RESOLVED', 'DISMISSED'].includes(alert.status))

  return <div className="portal-page">
    <header className="portal-hero"><div><p className="eyebrow">Student portal</p><h1>Welcome back, {name}.</h1><p>{data.student.studentNumber} · {data.student.programAssignment?.programCode ?? 'Program not assigned'}</p></div><Metric label="Cumulative GWA" value={formatGrade(data.gwa.weightedGwa)} detail={`${data.gwa.eligibleGradeCount} eligible grades`} /></header>
    {error && <div className="portal-error" role="alert">{error}</div>}
    <div className="portal-layout"><nav className="portal-nav" aria-label="Student portal"><p>My record</p>{sections.map(([path, label]) => <NavLink key={path} to={`/student/${path}`}>{label}</NavLink>)}</nav>
      <section className="portal-content" aria-live="polite">
        {section === 'dashboard' && <><SectionTitle title="Dashboard" detail="A current view of your academic path." /><div className="metric-grid"><Metric label="Active courses" value={current.length} /><Metric label="Standing" value={friendly(data.standing?.status ?? 'UNDETERMINED')} /><Metric label="Open alerts" value={openAlerts.length} /></div><Card title="What needs attention">{openAlerts.slice(0, 3).map((alert) => <Item key={alert.id} title={friendly(alert.ruleCode)} detail={alert.explanation} />)}{!openAlerts.length && <Empty text="No advising alerts are open." />}</Card></>}
        {section === 'profile' && <><SectionTitle title="Profile" detail="Your academic identity and contact record." /><Definition rows={[["Student number", data.student.studentNumber], ["Full name", [data.student.firstName, data.student.middleName, data.student.lastName].filter(Boolean).join(' ')], ["Status", friendly(data.student.status)], ["Cohort", String(data.student.cohortYear)], ["Contact", data.profile.contactNumber || 'Not provided'], ["Address", [data.profile.addressLine1, data.profile.city, data.profile.province].filter(Boolean).join(', ') || 'Not provided']]} /></>}
        {section === 'enrollment' && <><SectionTitle title="Current enrollment" detail="Courses in your active load." /><Card title={`${current.length} active courses`}>{current.map((item) => <Item key={item.id} title={item.courseCode} detail={`${item.academicTermCode} · Section ${item.sectionCode}`} />)}{!current.length && <Empty text="You have no active enrollment." />}</Card></>}
        {section === 'grades' && <><SectionTitle title="Grades" detail="Explicit grade states; a blank grade never means zero." /><DataTable headers={['Course', 'Term', 'Units', 'Status', 'Grade']} rows={data.grades.map((grade) => [grade.courseCode, grade.academicTermCode, String(grade.courseUnits), friendly(grade.status), formatGrade(grade.numericGrade)])} /></>}
        {section === 'history' && <><SectionTitle title="Academic history" detail="Completed, withdrawn, and historical course activity." /><DataTable headers={['Course', 'Term', 'Enrollment', 'Grade state']} rows={data.enrollments.map((enrollment) => { const grade = data.grades.find((item) => item.academicTermId === enrollment.academicTermId && item.courseCode === enrollment.courseCode); return [enrollment.courseCode, enrollment.academicTermCode, friendly(enrollment.status), friendly(grade?.status ?? 'NOT_GRADED')] })} /></>}
        {section === 'curriculum' && <><SectionTitle title="Curriculum progress" detail={data.student.programAssignment?.curriculumVersionCode ? `Curriculum ${data.student.programAssignment.curriculumVersionCode}` : 'No active curriculum assignment.'} /><DataTable headers={['Course', 'Type', 'Recommended year', 'Term']} rows={data.requirements.map((item) => [item.courseCode, friendly(item.requirementType), item.recommendedYear?.toString() ?? '—', item.recommendedTerm?.toString() ?? '—'])} /></>}
        {section === 'standing' && <><SectionTitle title="Academic standing" detail="A deterministic result based on recorded academic facts." />{data.standing ? <Definition rows={[["Status", friendly(data.standing.status)], ["Term GWA", formatGrade(data.standing.termGwa)], ["Cumulative GWA", formatGrade(data.standing.cumulativeGwa)], ["Evaluated", formatDate(data.standing.evaluatedAt)]]} /> : <Empty text="Standing has not yet been evaluated for your latest term." />}</>}
        {section === 'notifications' && <><SectionTitle title="Notifications" detail="Explainable advising alerts and shared adviser notes." /><Card title="Advising alerts">{data.alerts.map((alert) => <Item key={alert.id} title={`${friendly(alert.severity)} · ${friendly(alert.status)}`} detail={alert.explanation} />)}{!data.alerts.length && <Empty text="No alerts." />}</Card><Card title="Notes shared with you">{data.notes.map((note) => <Item key={note.id} title={formatDate(note.createdAt)} detail={note.content} />)}{!data.notes.length && <Empty text="No adviser notes have been shared." />}</Card></>}
        {section === 'privacy' && <><SectionTitle title="Privacy & data" detail="What this service stores and how this page handles it." /><Card title="Data categories"><p>Academic identity, contact details, curriculum assignments, enrollment, grades, standing, advising alerts, and session metadata.</p><p>This portal keeps data in memory only. It does not copy your academic record into localStorage or sessionStorage.</p></Card></>}
        {section === 'sessions' && <><SectionTitle title="Active sessions" detail="Review devices and revoke access you no longer recognize." /><Card title={`${data.sessions.length} active sessions`}>{data.sessions.map((session) => <div className="session-row" key={session.id}><div><strong>{session.current ? 'This session' : 'Other session'}</strong><span>{session.userAgent || 'Unknown browser'} · Last active {formatDate(session.lastSeenAt)}</span></div><button disabled={busySession === session.id} onClick={() => void revoke(session)}>Revoke</button></div>)}</Card></>}
        {section === 'reports' && <><SectionTitle title="Report download" detail="Generate a point-in-time copy from data you are authorized to view." /><Card title="Personal academic record"><p>The JSON download is created in your browser and is not retained by the portal.</p><button className="button" onClick={downloadReport}>Download my record</button></Card></>}
      </section></div>
  </div>
}

function PortalState({ title, detail }: { title: string; detail: string }) { return <div className="page"><div className="surface portal-state"><h1>{title}</h1><p>{detail}</p></div></div> }
function SectionTitle({ title, detail }: { title: string; detail: string }) { return <header className="section-title"><p className="eyebrow">Student self-service</p><h2>{title}</h2><p>{detail}</p></header> }
function Metric({ label, value, detail }: { label: string; value: string | number; detail?: string }) { return <div className="metric-card"><span>{label}</span><strong>{value}</strong>{detail && <small>{detail}</small>}</div> }
function Card({ title, children }: { title: string; children: ReactNode }) { return <article className="portal-card"><h3>{title}</h3>{children}</article> }
function Item({ title, detail }: { title: string; detail: string }) { return <div className="record-item"><strong>{title}</strong><span>{detail}</span></div> }
function Empty({ text }: { text: string }) { return <p className="empty-state">{text}</p> }
function Definition({ rows }: { rows: string[][] }) { return <dl className="definition-card">{rows.map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl> }
function DataTable({ headers, rows }: { headers: string[]; rows: string[][] }) { return rows.length ? <div className="table-wrap"><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody></table></div> : <Empty text="No records are available." /> }
function formatGrade(value: number | null | undefined) { return value == null ? 'Not available' : Number(value).toFixed(2) }
function friendly(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (character) => character.toUpperCase()) }
function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
