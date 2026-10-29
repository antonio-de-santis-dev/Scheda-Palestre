/** Handles failed page downloads and render errors without exposing router stack traces. */
export function RouteErrorPage() {
  return <main className="container">
    <section className="card" role="alert">
      <h1>Impossibile aprire la pagina</h1>
      <p>Controlla la connessione e ricarica la pagina per riprovare.</p>
      <button className="btn btn--primary" type="button" onClick={() => window.location.reload()}>Ricarica pagina</button>
      <a className="btn btn--secondary" href="/">Torna alla Home</a>
    </section>
  </main>;
}
