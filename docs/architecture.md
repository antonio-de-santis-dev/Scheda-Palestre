# Architettura di GymPlanner

## Visione d'insieme

```text
Browser (React SPA) ──same origin──▶ /api/**  Spring Boot (monolite modulare) ──▶ PostgreSQL
        │                                      │
   Vite dev proxy / nginx                 Flyway (unico responsabile dello schema)
```

- **Monolite modulare** (non microservizi): un solo processo e un solo database, diviso in
  moduli con confini verificati da ArchUnit (`backend/src/test/java/com/gymplanner/ArchitectureTest.java`).
- **SPA sullo stesso dominio:** in sviluppo il dev server Vite inoltra `/api` al backend; con Docker
  lo fa nginx. Così il cookie di sessione resta `SameSite=Lax` e CORS non serve.

## Moduli backend

| Modulo | Responsabilità | Dipende da |
| --- | --- | --- |
| `identity` | login, sessione, account, profilo, bootstrap ADMIN | `shared` |
| `catalog` | gruppi muscolari ed esercizi | `shared` |
| `workoutplan` | schede, sessioni, sezioni, esercizi configurati, serie personalizzate | `catalog` |
| `assignment` | assegnazioni USER-scheda (più schede attive), attivazione e chiusura | `identity`, `workoutplan` |
| `calendar` | giorni settimanali per assegnazione, conflitti fra schede, rotazione e ri-ancoraggio | `assignment`, `workoutplan` |
| `execution` | allenamenti, snapshot, serie, storico, viste Oggi/Calendario, report attività ADMIN | `identity`, `calendar`, `assignment`, `workoutplan` |
| `shared` | Problem Details, principal di sicurezza, tempo, paginazione, lock per utente | — |

Ogni modulo ha:

- `api/`: servizi pubblici, DTO ed eventi usabili dagli altri moduli;
- `internal/`: entità JPA, repository, servizi applicativi e controller REST.

I riferimenti fra moduli passano per UUID. Le reazioni fra moduli avvengono tramite eventi di
dominio sincroni nella stessa transazione (ADR 0002):
- `UserDeleted`: `identity` → `assignment`;
- `AssignmentClosed`: `assignment` → `execution`;
- `PlanDeleted` e `PlanSessionsChanged`: `workoutplan` → `assignment` / `calendar`.

Quando un modulo a monte deve **chiedere** qualcosa a uno a valle, usa una porta: la copia dei
giorni liberi all'attivazione passa da `assignment.api.ScheduleCopyPort`, implementata da
`calendar` (ADR 0008).

## Concorrenza

- Lock pessimistico sul workout: "Fine serie" è idempotente.
- `shared.concurrency.UserLock` (`pg_advisory_xact_lock` per utente) serializza attivazioni e
  scelta dei giorni dello stesso utente. Dopo il lock, dati riletti e verificati (ADR 0008).
- Indici unici parziali come rete di sicurezza:
  - una sola assegnazione attiva per `(utente, scheda)`;
  - un solo allenamento in corso per utente;
  - un solo esercizio in corso per allenamento.

## Persistenza

- Schema creato solo da Flyway (`backend/src/main/resources/db/migration`), Hibernate in modalità `validate`.
- Tabelle al plurale (`users`, non `user`), UUID generati dal backend, istanti `timestamptz` in UTC,
  date di calendario `date`.
- Unicità case-insensitive con indici su `lower(...)`. Indici unici parziali per "una sola
  assegnazione attiva per utente e scheda" (V8) e "un solo allenamento in corso per USER".
- Migrazioni: V1–V6 della prima versione (mai modificate), poi:
  - V7: esercizio → gruppo muscolare;
  - V8: più schede attive;
  - V9: eliminazione logica degli utenti.

## Sicurezza

Vedi ADR 0003: sessione server con cookie HttpOnly, CSRF per SPA, blocco dei tentativi,
`session_version` per invalidare le sessioni, 404 per risorse di altri utenti.

## Frontend

```text
src/
├── app/        router, provider (TanStack Query), layout
├── auth/       login, cambio password, guardie
├── admin/      area ADMIN (utenti + report, catalogo unificato, schede, assegnazioni)
├── user/       area USER (oggi, allenamento, calendario, schede, giorni, storico, profilo)
├── shared/     client API, errori, componenti, stili
└── test/       setup Vitest + MSW
```

- Le pagine sono caricate su richiesta (`lazy` di React Router): lo USER non scarica l'editor ADMIN.
- I dati del server vivono solo nella cache di TanStack Query, senza uno store globale duplicato.
- I moduli usano React Hook Form + Zod con le stesse regole del backend.
- Le notifiche dopo una navigazione (es. "Nuova scheda creata") viaggiano nello state di React
  Router e sono mostrate e consumate da `shared/flash/FlashOutlet` nel layout.
- Componenti accessibili condivisi:
  - `Combobox`: pattern ARIA 1.2;
  - `ConfirmDialog` e `DeleteUserDialog`: `<dialog>` nativo;
  - `RecommendedDurationNotice`.
- Gli errori sono gestiti in un unico punto: `ApiError` costruito dai Problem Details, messaggi
  italiani in `shared/errors/messages.ts`, errori di campo riportati sul form.

## Esecuzione

```text
Sviluppo:   browser → Vite :5173 ──/api──▶ Spring Boot :8080 ──▶ PostgreSQL :5432 (Docker)
Docker app: browser → nginx :8081 ──/api──▶ backend :8080 ──▶ postgres (rete compose)
```

Test: unit e integrazione con Testcontainers (backend), Vitest + MSW (frontend), Playwright
(sistema completo). Dettagli in `DOCUMENTAZIONE_IMPLEMENTAZIONE_COMPLETA.md`.
