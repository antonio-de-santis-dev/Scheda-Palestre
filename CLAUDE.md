# CLAUDE.md - Contesto del progetto GymPlanner

Questo file spiega a Claude (e a qualsiasi sviluppatore) **cosa è GymPlanner, cosa è già stato
fatto e come lavorarci**. Leggilo prima di modificare il codice.

## 1. Cos'è il programma

GymPlanner è una web app per palestre, **amministrata**: non esiste registrazione pubblica.

- **ADMIN**:
  - crea gli account USER; li elimina in modo logico con anonimizzazione e ne consulta il **report
    attività**;
  - gestisce un **catalogo unico**, in cui ogni esercizio appartiene a un solo gruppo muscolare;
  - costruisce le schede (sessioni → sezioni muscolari → esercizi con
    serie/ripetizioni/MAX/recupero, anche serie personalizzate, con una durata consigliata);
  - le duplica, le elimina logicamente e le ripristina, le assegna a uno o più utenti.
- **USER**:
  - può avere **più schede attive**; per ciascuna sceglie i giorni della settimana, e ogni giorno
    appartiene a una sola scheda;
  - riceve le sessioni **a rotazione** automatica, per scheda;
  - esegue l'allenamento serie per serie con **timer di recupero**: "Fine serie" è bloccato finché
    il recupero non finisce; celebrazioni e avviso sonoro di fine recupero sono opzionali;
  - può saltare esercizi o interrompere;
  - registra facoltativamente peso usato e ripetizioni effettive per serie; consulta lo **storico** e modifica telefono e password;
  - riceve un avviso quando la durata consigliata di una scheda è terminata.

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
| Finale | Test E2E Playwright, verifica finale, documentazione completa, merge su `main` | Fatto |
| Modifiche A-F | Branch `modifiche`: notifiche ADMIN, catalogo unificato (V7), più schede attive (V8), durata consigliata, eliminazione utente + report (V9), recupero/celebrazioni/audio | Fatto, PR aperta verso `main` |

Tutte le decisioni aperte O-01…O-07 della specifica sono state chiuse con le proposte consigliate
(`docs/decisions/0004-open-decisions.md`). **O-06** (timer informativo) e **O-07** (copia dei giorni)
sono state superate dal branch `modifiche`: vedi ADR 0004, 0008 e ADR 0007-0010. Le funzionalità marcate FUTURO erano escluse dalla V1. Il proprietario ha autorizzato
la V2 sul branch `SviluppoV2`: seguire le fasi e lo stato di `docs/sviluppo-v2.md`.

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
| `assignment` | assegnazioni USER-scheda (più schede attive) | identity, workoutplan |
| `calendar` | giorni per assegnazione, conflitti fra schede, rotazione (`RotationCalculator`) | assignment, workoutplan |
| `execution` | allenamenti, snapshot, storico, viste Oggi/Calendario, report attività | identity, calendar, assignment, workoutplan |
| `shared` | Problem Details, principal di sicurezza, tempo, paginazione, `UserLock` | — |

- Ogni modulo: `api/` (pubblico: servizi, DTO, eventi) e `internal/` (entità, repository, servizi,
  controller). **Mai** importare `internal` di un altro modulo: `ArchitectureTest` lo verifica.
- Fra moduli si usano **UUID** (niente associazioni JPA cross-modulo) ed **eventi sincroni**
  (`WorkoutPlanEvents`, `AssignmentEvents`, `UserEvents`) per le reazioni "all'indietro" (ADR 0002);
  per le richieste "in avanti" una porta in `api/` (es. `ScheduleCopyPort`).
- Nessuna entità JPA esce dai controller: sempre DTO (`record`).
- Errori: eccezioni di `shared.error` (`NotFoundException`, `ConflictException`,
  `BusinessRuleException` 422, `BadRequestException`, …) → RFC 9457 con campo `code`.
  Risorse di altri utenti → **404**, mai 403.
- `/api/me/**`: l'utente si ricava **sempre** da `@AuthenticationPrincipal AuthenticatedUser`.
- Schema DB solo tramite **Flyway** (`backend/src/main/resources/db/migration`, V1…V13).
  **Non modificare migrazioni esistenti**: aggiungi una nuova migrazione. Hibernate è in `validate`.
- Tabelle al plurale (`users`, mai `user`); nessun `@ManyToMany` utente-scheda.

Punti delicati già risolti (non romperli):

- vincoli unici di posizione `DEFERRABLE` → i riordini sono atomici;
- indici unici parziali:
  - una sola assegnazione attiva per `(user_id, workout_plan_id)` (V8, al posto della vecchia
    "una sola per USER");
  - un solo allenamento `IN_PROGRESS` per USER;
  - un solo esercizio `IN_PROGRESS` per allenamento (per questo `advance()` fa `flush()`);
- un giorno della settimana appartiene a una sola scheda attiva dell'utente. Il controllo è sotto
  `UserLock` (`pg_advisory_xact_lock`) con rilettura dentro il lock (ADR 0008);
- ogni esercizio ha `muscle_group_id NOT NULL`; l'editor rifiuta esercizi di un altro gruppo
  (`EXERCISE_GROUP_MISMATCH`), le righe storiche restano salvabili (ADR 0007);
- durante il recupero la serie successiva è rifiutata (`REST_NOT_FINISHED`), ma ripetere una serie
  già completata resta idempotente;
- utenti mai cancellati fisicamente: `deleted_at` + anonimizzazione (ADR 0010);
- `duration_weeks` è la durata **consigliata**, calcolata dalla data di inizio di ogni assegnazione: lo stato è calcolato dal server con `BusinessCalendar`
  e non blocca nulla (ADR 0009);
- "Fine serie" idempotente con lock pessimistico sul workout;
- timer V2 persistente con pausa/ripresa/estensione/salto esplicito, derivato da `restEndsAt`/`serverTime` (mai un contatore), `BusinessCalendar.now()` troncato ai ms;
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
- **Deroga consapevole:** il branch `modifiche` è stato creato da `main` aggiornato, su richiesta
  del proprietario. Viene integrato in `main` solo dopo la sua revisione della PR.
- Mai force-push, mai committare `.env`, `node_modules`, `target`, `dist`.
- Prima di ogni merge: `./mvnw verify`, `npm test`, `npm run lint`, `npm run build`.

## 7. Problemi già incontrati sul PC dello sviluppatore (Ubuntu)

- `release version 21 not supported` → Java 17 attivo: installare `openjdk-21-jdk` ed esportare
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` e `PATH="$JAVA_HOME/bin:$PATH"`.
- `required variable POSTGRES_PASSWORD is missing` → `.env` vuoto o comando lanciato fuori dalla
  cartella del progetto.
- `GYM_ADMIN_PASSWORD does not satisfy the password policy` → password senza cifre.
