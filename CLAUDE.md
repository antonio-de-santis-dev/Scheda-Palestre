# CLAUDE.md - Contesto del progetto GymPlanner

Questo file spiega a Claude (e a qualsiasi sviluppatore) **cosa è GymPlanner, cosa è già stato
fatto e come lavorarci**. Leggilo prima di modificare il codice.

## 1. Cos'è il programma

GymPlanner è una web app per palestre, **amministrata**: non esiste registrazione pubblica.

- **ADMIN**: crea gli account USER, gestisce i cataloghi (gruppi muscolari, esercizi), costruisce le
  schede (sessioni → sezioni muscolari → esercizi con serie/ripetizioni/MAX/recupero, anche serie
  personalizzate), le duplica, le elimina logicamente e le ripristina, le assegna a uno o più utenti.
- **USER**: vede le proprie schede (una sola attiva), sceglie i giorni della settimana, riceve le
  sessioni **a rotazione** automatica, esegue l'allenamento serie per serie con **timer di recupero**,
  può saltare esercizi o interrompere, consulta lo **storico** e modifica telefono/password.

Fonte di verità funzionale: `MEGA_DOCUMENTAZIONE_GYM_PLANNER_PER_CLAUDE_CODE.md`.
Documentazione tecnica: `docs/architecture.md`, `docs/api.md`, `docs/decisions/` (ADR),
`docs/progress.md` (registro incrementi), `DOCUMENTAZIONE_IMPLEMENTAZIONE_COMPLETA.md`.

## 2. Stato del lavoro

| Incremento | Contenuto | Stato |
| --- | --- | --- |
| 0 | Fondamenta: struttura, sicurezza, bootstrap ADMIN, login/logout | Fatto |
| 1 | Account ADMIN, cataloghi | Fatto |
| 2 | Schede (editor completo, serie personalizzate, duplicazione, eliminazione logica), assegnazioni | Fatto |
| 3 | Giorni, rotazione, Oggi, esecuzione con timer, calendario | Fatto |
| 4 | Storico essenziale, profilo | Fatto |
| Finale | Test E2E Playwright, verifica finale, documentazione completa, merge su `main` | Vedi `docs/progress.md` |

Tutte le decisioni aperte O-01…O-07 della specifica sono state chiuse con le proposte consigliate
(`docs/decisions/0004-open-decisions.md`). Le funzionalità marcate FUTURO **non** vanno implementate.

## 3. Stack

- **Backend** (`backend/`): Java 21, Spring Boot 4.0.8, Maven Wrapper, Spring Security 7,
  Spring Data JPA/Hibernate 7, Bean Validation, Flyway, PostgreSQL 16, Testcontainers 2, ArchUnit.
- **Frontend** (`frontend/`): React 19, TypeScript strict, Vite 8, React Router 8, TanStack Query 5,
  React Hook Form + Zod 4, lucide-react, Vitest + Testing Library + MSW, Playwright.
- **Infrastruttura locale**: `compose.yaml` (PostgreSQL; profilo `app` per backend+nginx).

## 4. Architettura (regole da rispettare)

Monolite modulare in `backend/src/main/java/com/gymplanner/`:

| Modulo | Responsabilità | Può dipendere da |
| --- | --- | --- |
| `identity` | login, sessione, account, profilo, bootstrap ADMIN | shared |
| `catalog` | gruppi muscolari, esercizi | shared |
| `workoutplan` | schede e struttura | catalog |
| `assignment` | assegnazioni USER-scheda | identity, workoutplan |
| `calendar` | giorni settimanali, rotazione (`RotationCalculator`) | assignment, workoutplan |
| `execution` | allenamenti, snapshot, storico, viste Oggi/Calendario | calendar, assignment, workoutplan |
| `shared` | Problem Details, principal di sicurezza, tempo, paginazione | — |

- Ogni modulo: `api/` (pubblico: servizi, DTO, eventi) e `internal/` (entità, repository, servizi,
  controller). **Mai** importare `internal` di un altro modulo: `ArchitectureTest` lo verifica.
- Fra moduli si usano **UUID** (niente associazioni JPA cross-modulo) ed **eventi sincroni**
  (`WorkoutPlanEvents`, `AssignmentEvents`) per le reazioni "all'indietro" (ADR 0002).
- Nessuna entità JPA esce dai controller: sempre DTO (`record`).
- Errori: eccezioni di `shared.error` (`NotFoundException`, `ConflictException`,
  `BusinessRuleException` 422, `BadRequestException`, …) → RFC 9457 con campo `code`.
  Risorse di altri utenti → **404**, mai 403.
- `/api/me/**`: l'utente si ricava **sempre** da `@AuthenticationPrincipal AuthenticatedUser`.
- Schema DB solo tramite **Flyway** (`backend/src/main/resources/db/migration`, V1…V6).
  **Non modificare migrazioni esistenti**: aggiungi `V7__...sql`. Hibernate è in `validate`.
- Tabelle al plurale (`users`, mai `user`); nessun `@ManyToMany` utente-scheda.

Punti delicati già risolti (non romperli):

- vincoli unici di posizione `DEFERRABLE` → i riordini sono atomici;
- indici unici parziali: una sola assegnazione attiva per USER, un solo allenamento `IN_PROGRESS`
  per USER, un solo esercizio `IN_PROGRESS` per allenamento (per questo `advance()` fa `flush()`);
- "Fine serie" idempotente con lock pessimistico sul workout;
- timer derivato da `restEndsAt`/`serverTime` (mai un contatore), `BusinessCalendar.now()` troncato ai ms;
- sessione server + CSRF SPA (`XSRF-TOKEN` → `X-XSRF-TOKEN`), `users.session_version` invalida le sessioni.

Frontend (`frontend/src/`): `app/` (router, provider, layout), `auth/`, `admin/`, `user/`,
`shared/` (client `http.ts` con CSRF e Problem Details, `errors/messages.ts` con i messaggi italiani,
componenti, `styles/tokens.css` + `global.css`). I dati server stanno solo in TanStack Query.
Mobile first da 360 px, target ≥ 44 px, stati espressi con testo + icona.

## 5. Comandi

```bash
# Database locale
docker compose up -d postgres

# Backend (Java 21 obbligatorio; variabili da .env)
cd backend
set -a && source ../.env && set +a
./mvnw spring-boot:run          # http://localhost:8080
./mvnw verify                    # tutti i test (serve Docker per Testcontainers)

# Frontend
cd frontend
npm install
npm run dev                      # http://localhost:5173 (proxy /api -> 8080)
npm test                         # Vitest
npm run lint && npm run build
npm run e2e                      # Playwright (vedi frontend/e2e/README.md)
```

`.env` (copiato da `.env.example`): `POSTGRES_PASSWORD` = `DB_PASSWORD`; `GYM_ADMIN_PASSWORD`
deve avere **almeno 8 caratteri con lettere e cifre**, altrimenti il backend non parte.

## 6. Convenzioni Git

- `main`: solo codice verificato. `develop`: integrazione. Un branch per funzionalità da `develop`,
  merge con `--no-ff`. Conventional Commits (`feat(execution): ...`, `fix(...)`, `test(...)`, `docs:`).
- Mai force-push, mai committare `.env`, `node_modules`, `target`, `dist`.
- Prima di ogni merge: `./mvnw verify`, `npm test`, `npm run lint`, `npm run build`.

## 7. Problemi già incontrati sul PC dello sviluppatore (Ubuntu)

- `release version 21 not supported` → Java 17 attivo: installare `openjdk-21-jdk` ed esportare
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` e `PATH="$JAVA_HOME/bin:$PATH"`.
- `required variable POSTGRES_PASSWORD is missing` → `.env` vuoto o comando lanciato fuori dalla
  cartella del progetto.
- `GYM_ADMIN_PASSWORD does not satisfy the password policy` → password senza cifre.
