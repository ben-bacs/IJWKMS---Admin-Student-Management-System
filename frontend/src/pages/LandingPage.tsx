import { Link } from 'react-router-dom'

const capabilities = [
  {
    title: 'Academic operations',
    body: 'One reliable source for programs, curricula, course offerings, enrollment, and grades.',
  },
  {
    title: 'Student success',
    body: 'Advising and academic-standing workflows designed to surface risk early and clearly.',
  },
  {
    title: 'Operational insight',
    body: 'Auditable reports and integrations built on stable, documented APIs.',
  },
]

export function LandingPage() {
  return (
    <div className="page">
      <section className="hero">
        <div>
          <p className="eyebrow">Academic operations, rebuilt</p>
          <h1>Clear records. Better decisions.</h1>
          <p className="hero-copy">
            IJWKMS Next is the modern foundation for student records, academic workflows,
            and timely support across the institution.
          </p>
          <div className="actions">
            <Link className="button" to="/login">Open the portal</Link>
            <a className="button secondary" href="/swagger-ui.html">Explore the API</a>
          </div>
        </div>

        <aside className="status-card" aria-labelledby="foundation-status">
          <h2 id="foundation-status">Foundation status</h2>
          <ul className="status-list">
            <li><span className="status-dot" /><span><strong>Modular backend</strong>Bounded domains on Spring Boot</span></li>
            <li><span className="status-dot" /><span><strong>Secure identity</strong>Revocable sessions and scoped permissions</span></li>
            <li><span className="status-dot" /><span><strong>Student success</strong>Explainable standing rules and auditable advising</span></li>
          </ul>
        </aside>
      </section>

      <section className="feature-grid" aria-label="Platform capabilities">
        {capabilities.map((capability) => (
          <article className="surface" key={capability.title}>
            <h2>{capability.title}</h2>
            <p>{capability.body}</p>
          </article>
        ))}
      </section>
    </div>
  )
}
