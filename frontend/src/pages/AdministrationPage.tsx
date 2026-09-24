import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, apiRequest } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import {
  ADMIN_SECTIONS,
  type AdminField,
  type AdminOperation,
  type AdminRecord,
  type AdminSection,
} from './admin/adminConfig'

type PageEnvelope = { items: AdminRecord[]; totalElements?: number }

export function AdministrationPage() {
  const auth = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const visibleSections = useMemo(
    () => ADMIN_SECTIONS.filter((section) => section.permission === '*' || auth.hasPermission(section.permission)),
    [auth],
  )
  const requestedKey = location.pathname.split('/')[2] || 'overview'
  const section = visibleSections.find((candidate) => candidate.key === requestedKey) ?? visibleSections[0]

  useEffect(() => {
    if (section && requestedKey !== section.key) navigate(`/admin/${section.key}`, { replace: true })
  }, [navigate, requestedKey, section])

  if (!section) return <div className="page"><div className="notice">No administration workspace is assigned to this account.</div></div>

  return (
    <div className="admin-page">
      <header className="admin-hero">
        <div>
          <p className="eyebrow">Institutional workspace</p>
          <h1>Academic operations, one governed workspace.</h1>
          <p>{auth.user?.displayName} · {auth.user?.roles.join(', ')}</p>
        </div>
        <div className="permission-summary">
          <span>Effective access</span><strong>{auth.user?.permissions.length ?? 0} permissions</strong>
          <small>Actions are re-authorized by the server.</small>
        </div>
      </header>
      <div className="admin-layout">
        <nav className="admin-nav" aria-label="Administration portal">
          <p>Administration</p>
          {visibleSections.map((item) => <NavLink key={item.key} to={`/admin/${item.key}`}>{item.label}</NavLink>)}
        </nav>
        <main className="admin-content" key={section.key}>
          <SectionHeading section={section} />
          {section.key === 'overview' && <OverviewSection sections={visibleSections} />}
          {section.key === 'reports' && <ReportsSection />}
          {section.key === 'settings' && <SettingsSection />}
          {!['overview', 'reports', 'settings'].includes(section.key) && <ResourceSection section={section} />}
        </main>
      </div>
    </div>
  )
}

function SectionHeading({ section }: { section: AdminSection }) {
  return <div className="section-title"><p className="eyebrow">{section.label}</p><h2>{section.label}</h2><p>{section.description}</p></div>
}

function OverviewSection({ sections }: { sections: AdminSection[] }) {
  const auth = useAuth()
  const [counts, setCounts] = useState<Record<string, number | null>>({})
  const readable = useMemo(() => sections.filter((section) => section.endpoint && section.key !== 'audit'), [sections])

  useEffect(() => {
    let active = true
    Promise.all(readable.map(async (section) => {
      try {
        const result = await apiRequest<PageEnvelope | AdminRecord[]>(section.endpoint!)
        const count = Array.isArray(result) ? result.length : (result.totalElements ?? result.items.length)
        return [section.key, count] as const
      } catch {
        return [section.key, null] as const
      }
    })).then((entries) => { if (active) setCounts(Object.fromEntries(entries)) })
    return () => { active = false }
  }, [readable])

  return (
    <>
      <div className="metric-grid">
        <div className="metric-card"><span>Assigned roles</span><strong>{auth.user?.roles.length ?? 0}</strong><small>{auth.user?.roles.join(', ')}</small></div>
        <div className="metric-card"><span>Available workspaces</span><strong>{sections.length - 1}</strong><small>Derived from effective permissions</small></div>
        <div className="metric-card"><span>Security model</span><strong>Server</strong><small>Every request is independently authorized</small></div>
      </div>
      <div className="admin-card-grid">
        {sections.filter((section) => section.key !== 'overview').map((section) => (
          <NavLink className="admin-shortcut" key={section.key} to={`/admin/${section.key}`}>
            <span>{section.label}</span><strong>{section.endpoint ? (counts[section.key] ?? '—') : 'Open'}</strong><small>{section.description}</small>
          </NavLink>
        ))}
      </div>
    </>
  )
}

function ResourceSection({ section }: { section: AdminSection }) {
  const auth = useAuth()
  const [records, setRecords] = useState<AdminRecord[]>([])
  const [loading, setLoading] = useState(Boolean(section.endpoint))
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')

  const load = async () => {
    if (!section.endpoint) return
    setLoading(true); setError('')
    try { setRecords(normalizeRecords(await apiRequest<PageEnvelope | AdminRecord[]>(section.endpoint))) }
    catch (loadError) { setError(publicMessage(loadError)) }
    finally { setLoading(false) }
  }

  useEffect(() => {
    let active = true
    async function initialLoad() {
      if (!section.endpoint) return
      try {
        const result = await apiRequest<PageEnvelope | AdminRecord[]>(section.endpoint)
        if (active) setRecords(normalizeRecords(result))
      } catch (loadError) {
        if (active) setError(publicMessage(loadError))
      } finally {
        if (active) setLoading(false)
      }
    }
    void initialLoad()
    return () => { active = false }
  }, [section.endpoint])

  const onResult = (result: unknown, title: string) => {
    const next = normalizeRecords(result)
    if (next.length) setRecords(next)
    setFeedback(`${title} completed successfully.`)
    if (section.endpoint && !isReadResult(result)) void load()
  }
  const permittedOperations = section.operations?.filter((operation) => auth.hasPermission(operation.permission)) ?? []

  return (
    <>
      {feedback && <div className="success-banner" role="status">{feedback}</div>}
      {error && <div className="portal-error" role="alert">{error}</div>}
      {section.columns && <div className="resource-panel"><div className="resource-toolbar"><h3>Current records</h3>{section.endpoint && <button type="button" onClick={() => void load()}>Refresh</button>}</div>{loading ? <div className="empty-state" role="status">Loading authorized records…</div> : <ResourceTable records={records} columns={section.columns} />}</div>}
      {permittedOperations.length ? <div className="operation-grid">{permittedOperations.map((operation) => <OperationForm key={operation.title} operation={operation} onResult={onResult} />)}</div> : <div className="read-only-banner">This workspace is read-only for your assigned permissions.</div>}
    </>
  )
}

function ResourceTable({ records, columns }: { records: AdminRecord[]; columns: NonNullable<AdminSection['columns']> }) {
  if (!records.length) return <div className="empty-state">No authorized records found.</div>
  return <div className="table-wrap"><table><thead><tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr></thead><tbody>{records.map((record, index) => <tr key={String(record.id ?? index)}>{columns.map((column) => <td key={column.key}>{displayValue(record[column.key])}</td>)}</tr>)}</tbody></table></div>
}

function OperationForm({ operation, onResult }: { operation: AdminOperation; onResult: (result: unknown, title: string) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [pending, setPending] = useState<Record<string, FormDataEntryValue> | null>(null)

  const execute = async (values: Record<string, FormDataEntryValue>) => {
    setBusy(true); setError('')
    try {
      const request = operation.request(values)
      const result = await apiRequest<unknown>(request.path, request.init)
      onResult(result, operation.title); setPending(null)
    } catch (operationError) { setError(publicMessage(operationError)); setPending(null) }
    finally { setBusy(false) }
  }
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const values = Object.fromEntries(new FormData(event.currentTarget))
    if (operation.confirm) setPending(values); else void execute(values)
  }

  return (
    <section className="operation-card">
      <h3>{operation.title}</h3><p>{operation.detail}</p>
      {error && <div className="form-error" role="alert">{error}</div>}
      <form className="operation-form" onSubmit={submit}>{operation.fields.map((field) => <OperationField key={field.name} field={field} prefix={operation.title} />)}<button className="button" type="submit" disabled={busy}>{busy ? 'Working…' : operation.title}</button></form>
      {pending && <div className="confirmation-panel" role="alertdialog" aria-label="Confirm sensitive action"><strong>{operation.confirm}</strong><p>This action is permission-checked and recorded in the audit trail.</p><div className="actions"><button className="button" type="button" disabled={busy} onClick={() => void execute(pending)}>Confirm action</button><button className="button secondary" type="button" onClick={() => setPending(null)}>Cancel</button></div></div>}
    </section>
  )
}

function OperationField({ field, prefix }: { field: AdminField; prefix: string }) {
  const id = `admin-${prefix}-${field.name}`.replaceAll(' ', '-').toLowerCase()
  return <label htmlFor={id}>{field.label}{field.type === 'select' ? <select id={id} name={field.name} required={field.required}>{field.options?.map((option) => <option key={option} value={option}>{option || 'Any'}</option>)}</select> : field.type === 'textarea' ? <textarea id={id} name={field.name} required={field.required} placeholder={field.placeholder} rows={3} /> : <input id={id} name={field.name} type={field.type ?? 'text'} required={field.required} placeholder={field.placeholder} step={field.type === 'number' ? 'any' : undefined} />}</label>
}

function ReportsSection() {
  const reports = [['Student academic record', 'Students'], ['Enrollment report', 'Enrollments'], ['Class list', 'Offerings'], ['Grade report', 'Grading oversight'], ['Program / cohort summary', 'Programs'], ['Academic-standing report', 'Advising']]
  return <><div className="notice">Official PDF and CSV generation is delivered in Phase 10. These permission-scoped operational views are ready for review now.</div><div className="admin-card-grid">{reports.map(([name, source]) => <div className="admin-shortcut" key={name}><span>{name}</span><strong>Preview</strong><small>Source workspace: {source}</small></div>)}</div></>
}

function SettingsSection() {
  const auth = useAuth()
  return <><div className="definition-card"><div><dt>Account</dt><dd>{auth.user?.username}</dd></div><div><dt>Status</dt><dd>{auth.user?.status}</dd></div><div><dt>Roles</dt><dd>{auth.user?.roles.join(', ')}</dd></div><div><dt>Session storage</dt><dd>Secure HttpOnly cookie</dd></div></div><div className="resource-panel"><div className="resource-toolbar"><h3>Effective permission matrix</h3></div><ul className="permission-list">{auth.user?.permissions.map((permission) => <li key={permission}>{permission}</li>)}</ul></div></>
}

function normalizeRecords(result: unknown): AdminRecord[] {
  if (Array.isArray(result)) return result as AdminRecord[]
  if (result && typeof result === 'object' && 'items' in result && Array.isArray((result as PageEnvelope).items)) return (result as PageEnvelope).items
  if (result && typeof result === 'object') return [result as AdminRecord]
  return []
}

function isReadResult(result: unknown) { return Array.isArray(result) || Boolean(result && typeof result === 'object' && 'items' in result) }
function displayValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '—'
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'object') return JSON.stringify(value)
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value)) return new Date(value).toLocaleString()
  return String(value)
}
function publicMessage(error: unknown) { return error instanceof ApiError ? error.message : 'The operation could not be completed. Try again or contact an administrator.' }
