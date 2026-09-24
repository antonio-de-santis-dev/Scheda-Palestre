# GymPlanner - Documentazione completa dell'implementazione

Versione 1.0.0 · 24 settembre 2026 · Specifica di riferimento:
`MEGA_DOCUMENTAZIONE_GYM_PLANNER_PER_CLAUDE_CODE.md` (v4.0)

Indice

1. Obiettivo dell'applicazione
2. Funzionalità implementate
3. Funzionalità non implementate
4. Architettura backend
5. Architettura frontend
6. Struttura delle directory
7. Moduli Spring Boot
8. Entità e relazioni
9. Migrazioni Flyway
10. Sicurezza e autenticazione
11. Ruoli e autorizzazioni
12. API REST
13. Schermate React
14. Rotazione del calendario
15. Snapshot degli allenamenti
16. Timer di recupero
17. Test creati
18. Risultati degli ultimi test e delle build
19. Branch, strategia di integrazione e commit principali
20. Decisioni tecniche
21. Problemi incontrati e soluzioni
22. Limiti conosciuti e sviluppi futuri
23. Variabili d'ambiente
24. Configurazione PostgreSQL e istruzioni Docker
25. Avvio, arresto ed esecuzione dei test
26. Credenziali iniziali dell'ADMIN
27. Esempi di utilizzo ADMIN e USER
28. Troubleshooting

---

## 1. Obiettivo dell'applicazione

GymPlanner è una web app **amministrata** per creare e assegnare schede di allenamento in palestra.
Non esiste registrazione pubblica: l'ADMIN crea gli account, gestisce i cataloghi di gruppi
muscolari ed esercizi, costruisce schede condivisibili e le assegna a uno o più utenti. Lo USER
sceglie soltanto i giorni della settimana in cui si allena, riceve automaticamente le sessioni
della scheda in rotazione, esegue l'allenamento serie per serie con il timer di recupero, può
saltare esercizi e consulta uno storico essenziale. L'interfaccia è pensata per l'uso in palestra
dal telefono (da 360 px) ed è accessibile (WCAG AA).

## 2. Funzionalità implementate

| User story | Descrizione | Stato |
| --- | --- | --- |
| US-01 | Creazione account USER da parte dell'ADMIN (password temporanea mostrata una volta) | ✅ |
| US-02 | Login/logout, messaggio generico, blocco dopo 5 tentativi per 15 minuti, account disattivati respinti | ✅ |
| US-03 | Profilo: telefono modificabile, cambio password con password attuale | ✅ |
| US-04 / US-05 | Consultazione e gestione cataloghi (gruppi muscolari, esercizi) con attivazione/disattivazione | ✅ |
| US-06 | Creazione scheda e assegnazione a più USER (un record per utente, attivazione atomica) | ✅ |
| US-07 | USER vede solo le proprie schede e riconosce quella attiva | ✅ |
| US-08 | Modifica, eliminazione logica (chiude le assegnazioni attive), ripristino, avviso assegnatari | ✅ |
| US-09 / US-10 / US-11 | Sessioni, sezioni muscolari, esercizi, con riordino atomico | ✅ |
| US-12 / US-13 | Serie, ripetizioni, recupero, cedimento (MAX) | ✅ |
| US-14 | Serie personalizzate tutto-o-niente, conferma alla riduzione delle serie | ✅ |
| US-15 | Giorni settimanali e rotazione automatica delle sessioni | ✅ |
| US-16 | Allenamento di oggi (sessione, gruppi, esercizi, valori; riposo; giorni mancanti; scheda in preparazione) | ✅ |
| US-17 | Avvio persistente e ripresa dopo ricaricamento | ✅ |
| US-18 | *Fine serie* idempotente e timer di recupero | ✅ |
| US-20 | Salto esercizio con conferma e conclusione | ✅ |
| US-21 | Autorizzazioni per ruolo e proprietà delle risorse (404 per risorse altrui) | ✅ |
| US-22 | Errori chiari senza perdita dei dati inseriti | ✅ |
| US-23 | Usabilità in palestra: mobile first, azioni grandi, azione principale senza scorrere | ✅ |
| US-24 | Storico essenziale con i valori dello snapshot | ✅ |
| US-25 | Modifica, disattivazione/riattivazione, reset password; invalidazione sessioni; almeno un ADMIN attivo | ✅ |
| US-26 | Duplicazione profonda senza assegnazioni | ✅ |

Inoltre: bootstrap sicuro del primo ADMIN, cambio password obbligatorio al primo accesso,
calendario con sessioni future ed esiti passati, gestione di un allenamento rimasto aperto oltre
la mezzanotte (O-04), temi chiaro/scuro automatici.

## 3. Funzionalità non implementate

Per scelta della specifica (FUTURO o fuori ambito): US-19 controlli avanzati del timer (pausa,
aggiunta tempo, salto recupero), pesi previsti/usati, ripetizioni effettive, statistiche e grafici,
record personali, durata aggregata, RPE/RIR, superset/circuiti/drop set, foto e video, notifiche
push, PWA/app nativa, recupero password via email, sessioni fuori calendario, storico consultabile
dall'ADMIN, microservizi, code di messaggi, cache distribuite.

## 4. Architettura backend

Monolite modulare Spring Boot 4.0.8 su Java 21 (ADR 0001, 0002):

- un solo processo, un solo database PostgreSQL, schema gestito solo da Flyway
  (`spring.jpa.hibernate.ddl-auto=validate`, `open-in-view=false`);
- moduli di dominio con package `api` (pubblico) e `internal` (entità, repository, servizi,
  controller); confini e dipendenze verificati da ArchUnit;
- riferimenti fra moduli tramite UUID (le foreign key restano nel database);
- reazioni "all'indietro" tramite **eventi di dominio sincroni** nella stessa transazione
  (`PlanDeleted`, `PlanSessionsChanged`, `AssignmentActivated`, `AssignmentClosed`);
- DTO `record` separati dalle entità, Bean Validation su tutti gli input, transazioni esplicite
  (`@Transactional`) nei servizi, errori RFC 9457 (Problem Details) con `code` applicativo;
- orologio iniettabile (`Clock`, `BusinessCalendar` con fuso `Europe/Rome` per le date di calendario).

## 5. Architettura frontend

React 19 + TypeScript strict + Vite 8 (ADR 0005):

- **React Router 8** (data router) con guardie `RequireAuth` e caricamento lazy delle pagine;
- **TanStack Query** come unica fonte dei dati server (nessuno store globale duplicato),
  retry automatico solo per errori transitori e, fra le mutazioni, solo per quelle idempotenti;
- **React Hook Form + Zod** con le stesse regole del backend;
- client `shared/api/http.ts`: cookie di sessione, CSRF double-submit, timeout 15 s, costruzione
  di `ApiError` dai Problem Details, notifica globale dei 401;
- messaggi d'errore italiani centralizzati (`shared/errors/messages.ts`), errori di campo riportati
  sul form mantenendo i dati;
- design system a token (`tokens.css`, `global.css`) generato con la skill *ui-ux-pro-max*:
  mobile first, target ≥ 44 px (64 px per *Fine serie*), contrasto AA, focus visibile,
  stati sempre con testo + icona, tema scuro, `prefers-reduced-motion`.

## 6. Struttura delle directory

```text
Scheda-Palestre/
├── README.md, CLAUDE.md, GUIDA_TEST_MANUALE.md, DOCUMENTAZIONE_IMPLEMENTAZIONE_COMPLETA.md
├── MEGA_DOCUMENTAZIONE_GYM_PLANNER_PER_CLAUDE_CODE.md   (specifica)
├── compose.yaml, .env.example, .gitignore
├── docs/
│   ├── architecture.md, api.md, progress.md, design-system.md
│   └── decisions/ (ADR 0001-0006)
├── backend/
│   ├── pom.xml, mvnw, mvnw.cmd, .mvn/, Dockerfile
│   └── src/
│       ├── main/java/com/gymplanner/
│       │   ├── GymPlannerApplication.java
│       │   ├── identity/{api,internal}
│       │   ├── catalog/{api,internal}
│       │   ├── workoutplan/{api,internal}
│       │   ├── assignment/{api,internal}
│       │   ├── calendar/{api,internal}
│       │   ├── execution/internal
│       │   └── shared/{error,security,time,web}
│       ├── main/resources/{application.properties, db/migration/V1..V6}
│       └── test/java/com/gymplanner/ (unit, integrazione, ArchUnit, support/)
└── frontend/
    ├── package.json, vite.config.ts, tsconfig*.json, eslint.config.js
    ├── playwright.config.ts, e2e/, Dockerfile, nginx.conf
    └── src/
        ├── app/ (router, providers, layouts)
        ├── auth/ (login, cambio password, guardie)
        ├── admin/ (users, catalog, plans, assignments)
        ├── user/ (today, workout, calendar, plans, schedule, history, profile)
        ├── shared/ (api, components, errors, styles, utils)
        └── test/ (setup Vitest, MSW, fixture)
```

## 7. Moduli Spring Boot

| Modulo | Contenuto principale | API pubblica |
| --- | --- | --- |
| `identity` | `User`, login (`AuthService`), `SecurityConfig`, filtri di sessione, bootstrap ADMIN, gestione account, profilo | `UserDirectory`, `UserSummary`, `UserRole` |
| `catalog` | `MuscleGroup`, `Exercise`, servizio generico `CatalogService` | `CatalogLookup`, `CatalogItemView` |
| `workoutplan` | `WorkoutPlan` → `PlanSession` → `MuscleSection` → `PlanExercise` → `PlanSet`; editor, duplicazione | `WorkoutPlanQueries`, `PlanStructure`, `WorkoutPlanEvents` |
| `assignment` | `PlanAssignment`, assegnazione multipla, attivazione/chiusura | `AssignmentQueries`, `AssignmentView`, `AssignmentEvents` |
| `calendar` | `WeeklySchedule`, `CalendarService` (giorni, ri-ancoraggio) | `CalendarQueries`, `DayPlan`, `RotationCalculator` |
| `execution` | `Workout` → `WorkoutExercise` → `WorkoutSet`, `WorkoutService`, `TodayService` | — (nessun modulo dipende da execution) |
| `shared` | eccezioni e `GlobalExceptionHandler`, `AuthenticatedUser`, `BusinessCalendar`, `PageResponse` | — |

Dipendenze ammesse: `workoutplan→catalog`, `assignment→identity,workoutplan`,
`calendar→assignment,workoutplan`, `execution→calendar,assignment,workoutplan`.

## 8. Entità e relazioni

```text
users 1─* workout_plans (created_by)          workout_plans 1─* plan_sessions 1─* muscle_sections 1─* plan_exercises 1─* plan_sets
muscle_groups 1─* muscle_sections             exercises 1─* plan_exercises
users 1─* plan_assignments *─1 workout_plans  plan_assignments 1─* weekly_schedules
plan_assignments 1─* workouts 1─* workout_exercises 1─* workout_sets
plan_sessions 0..1─* workouts (ON DELETE SET NULL)   plan_exercises 0..1─* workout_exercises (ON DELETE SET NULL)
```

Punti salienti: tabella `users` (mai `user`); nessun `@ManyToMany` utente-scheda (`PlanAssignment` è
un'entità con date, stato e ancora della rotazione); nessun campo `customSets` (la
personalizzazione è la presenza di righe `plan_sets`); `WorkoutSet.completedAt` unica fonte di verità
del completamento; stringhe per gli enum (`ADMIN/USER`, `IN_PROGRESS/COMPLETED/INTERRUPTED`,
`TODO/IN_PROGRESS/COMPLETED/SKIPPED`); `WorkoutPlan.version` per il controllo ottimistico.

## 9. Migrazioni Flyway

| Versione | File | Contenuto |
| --- | --- | --- |
| V1 | `V1__create_users.sql` | `users`, unicità case-insensitive su username/email, `session_version` |
| V2 | `V2__create_catalog.sql` | `muscle_groups`, `exercises`, nomi univoci case-insensitive |
| V3 | `V3__create_workout_plans.sql` | schede e figli; check MAX/ripetizioni; unicità di posizione `DEFERRABLE INITIALLY DEFERRED` |
| V4 | `V4__create_plan_assignments.sql` | assegnazioni; indice unico parziale "una sola attiva per USER"; check sulle date |
| V5 | `V5__create_weekly_schedules.sql` | giorni ISO 1-7, unici per assegnazione |
| V6 | `V6__create_workouts.sql` | allenamenti e snapshot; unico (assegnazione, data); indice parziale "un solo IN_PROGRESS per USER" e "un solo esercizio IN_PROGRESS per allenamento"; FK storiche `ON DELETE SET NULL` |

Regola: le migrazioni pubblicate non si modificano; ogni cambio richiede una nuova `V7__…`.

## 10. Sicurezza e autenticazione

(ADR 0003)

- Login JSON `POST /api/auth/login`; sessione server nel cookie `GYMSESSION` (HttpOnly,
  SameSite=Lax, Secure configurabile), rotazione dell'id di sessione al login, scadenza per
  inattività 60 minuti.
- CSRF attivo in modalità SPA (cookie `XSRF-TOKEN` → header `X-XSRF-TOKEN`).
- Password con BCrypt (`DelegatingPasswordEncoder`); policy 8-128 caratteri con lettere e cifre.
- Blocco di 15 minuti dopo 5 tentativi falliti; messaggio sempre generico; confronto con hash
  fittizio per utenti inesistenti/bloccati (niente enumerazione).
- `users.session_version`: disattivazione, reset e cambio password invalidano le sessioni aperte
  (verifica a ogni richiesta in `SessionUserRefreshFilter`).
- `mustChangePassword`: solo login, logout, `me`, `csrf` e `change-password` sono consentiti;
  il resto risponde 403 `PASSWORD_CHANGE_REQUIRED`.
- Header di sicurezza (CSP `default-src 'none'` sulle API, `X-Frame-Options: DENY`; CSP dedicata in nginx).
- Actuator: solo `/actuator/health` senza dettagli. Nessuna password, hash o cookie nei log.
- CORS limitato a `GYM_CORS_ALLOWED_ORIGINS` (la SPA è normalmente servita sulla stessa origine).

## 11. Ruoli e autorizzazioni

| Area | Regola |
| --- | --- |
| `/api/admin/**` | solo ADMIN (USER → 403) |
| `/api/me/profile` | ogni utente autenticato |
| altri `/api/me/**` | solo USER; l'utente è sempre ricavato dalla sessione, mai dal client |
| risorse di altri utenti | 404 identico a "inesistente" (assegnazioni, schede in lettura, allenamenti) |
| creazione account | il ruolo non è accettato dal client: sempre USER |
| ADMIN | non può disattivare sé stesso; deve restare almeno un ADMIN attivo |

## 12. API REST

Il dettaglio completo (endpoint, corpi, codici d'errore) è in `docs/api.md`. In sintesi:
`/api/auth/*` (csrf, login, logout, me, change-password), `/api/admin/users/**`,
`/api/admin/{muscle-groups,exercises}/**`, `/api/admin/plans/**`, `/api/admin/sessions/**`,
`/api/admin/sections/**`, `/api/admin/plan-exercises/**`, `/api/admin/assignments/**`,
`/api/me/{assignments,schedule,today,calendar,workouts,profile}`.
Codici HTTP: 200, 201, 204, 400 (`VALIDATION_ERROR`), 401, 403, 404, 409, 422.

## 13. Schermate React

| Rotta | Schermata |
| --- | --- |
| `/login`, `/change-password` | accesso e cambio password (obbligatorio al primo accesso) |
| `/admin` | dashboard con collegamenti rapidi |
| `/admin/users`, `/admin/users/:id` | elenco con ricerca/filtri/paginazione, creazione, dettaglio (modifica, disattiva/riattiva, reset password, schede assegnate) |
| `/admin/catalog/muscle-groups`, `/admin/catalog/exercises` | cataloghi |
| `/admin/plans`, `/admin/plans/:id/edit` | elenco (attive/eliminate, duplica, elimina, ripristina) ed editor completo |
| `/admin/plans/:id/assignments` | assegnazione multipla e gestione assegnatari |
| `/admin/profile`, `/app/profile` | profilo |
| `/app/today` | allenamento di oggi / riposo / giorni mancanti / scheda in preparazione / allenamento aperto |
| `/app/workout/:id` | esecuzione guidata con timer |
| `/app/calendar` | 4 settimane: sessioni future ed esiti passati |
| `/app/plans`, `/app/plans/:assignmentId` | schede assegnate (sola lettura) |
| `/app/schedule` | sette selettori lunedì-domenica |
| `/app/history`, `/app/history/:workoutId` | storico e dettaglio |

## 14. Rotazione del calendario

Implementata in `calendar/api/RotationCalculator` (funzione pura) e `CalendarService`.

- Per una data `d`: se l'assegnazione non è attiva nel periodo, nessuna sessione; se mancano i
  giorni o le sessioni, nessuna sessione; se il giorno della settimana non è scelto, riposo;
  altrimenti `k` = giorni pianificati fra l'ancora `a` (inclusa) e `d` (esclusa) e la sessione è
  `S[(i0 + k) mod N]`.
- `k` è calcolato in tempo costante: settimane intere × numero di giorni scelti + resto
  (verificato contro il conteggio giorno per giorno su 50 casi casuali).
- O-01: un giorno pianificato consuma la sessione anche se non viene svolto.
- Ancora iniziale all'attivazione: il giorno più tardo fra data di inizio e oggi, indice 0.
- **Ri-ancoraggio** (cambio dei giorni; sessioni aggiunte, eliminate o riordinate): l'ancora si sposta
  a oggi (o a domani se oggi era già giorno di allenamento) e l'indice diventa quello della
  sessione che sarebbe stata proposta, mappata per identità se le sessioni sono state riordinate.
  Gli allenamenti esistenti non cambiano.
- O-07: all'attivazione di una nuova scheda i giorni della precedente vengono copiati (opzionale).

Esempio della specifica (Giorno 1/Giorno 2, lunedì-mercoledì-venerdì): lun 1 → G1, mer 3 → G2,
ven 5 → G1, lun 8 → G2 (test `specExampleTwoSessionsOnMondayWednesdayFriday`).

## 15. Snapshot degli allenamenti

All'avvio (`WorkoutService.start`, una transazione) vengono verificati: assegnazione attiva,
data consentita (O-05: solo oggi, ±1 giorno di tolleranza per i fusi), giorno pianificato, scheda
eseguibile, nessun duplicato per (assegnazione, data), nessun altro allenamento in corso. Poi
vengono copiati nome della scheda, titolo della sessione e, nell'ordine globale sessione → sezione
→ esercizio, nomi di esercizio e gruppo e ogni serie (ripetizioni, MAX, recupero) presa dalle
serie personalizzate o dai valori generali. Il primo esercizio è `IN_PROGRESS`, gli altri `TODO`.
Da quel momento l'esecuzione non legge più la configurazione: modifiche, disattivazioni o
cancellazioni della scheda non alterano allenamenti avviati o conclusi (le FK di origine
diventano `NULL`). Verificato da `snapshotIsNotAffectedByLaterPlanChanges`,
`historyKeepsSnapshotValuesAfterPlanChanges` ed E2E n. 6.

## 16. Timer di recupero

Il timer non è un'entità: `restEndsAt = completedAt + restSeconds` dell'ultima serie completata,
restituito con `serverTime` in ogni risposta di esecuzione (`nextAction` = `WAIT_FOR_REST`). Il
frontend calcola il residuo come differenza di istanti corretta per lo scarto fra orologio del
server e del client (`useRestTimer`), aggiorna la vista ogni 250 ms e si riallinea con il server
su `visibilitychange`. Recupero 0 = nessun timer. O-03: il recupero parte anche fra esercizi, ma
non dopo l'ultima serie dell'ultimo esercizio. O-06: il timer è informativo, *Fine serie* resta
sempre disponibile. A fine recupero: annuncio accessibile e vibrazione, se supportata.
*Fine serie* è idempotente: la richiesta blocca la riga dell'allenamento (`SELECT … FOR UPDATE`) e
una serie già completata restituisce lo stato corrente; il frontend ripete automaticamente la
richiesta solo in caso di errore di rete.

## 17. Test creati

Backend (JUnit 5, AssertJ, MockMvc, Testcontainers PostgreSQL 16, ArchUnit):

| Classe | Tipo | Copertura |
| --- | --- | --- |
| `ArchitectureTest` | architettura | cicli, dipendenze ammesse, `internal` non accessibili, entità solo in `internal` |
| `UserAndPasswordPolicyTest` | unit | blocco tentativi, versione sessione, policy password |
| `AuthIntegrationTest` | integrazione | bootstrap ADMIN, login, messaggio generico, blocco 15 min, logout, CSRF, 401/403, cambio password obbligatorio, invalidazione sessione |
| `AdminUserIntegrationTest` | integrazione | creazione, duplicati case-insensitive, validazione, ricerca/paginazione, disattivazione, reset, autorizzazioni |
| `CatalogIntegrationTest` | integrazione | CRUD cataloghi, unicità anche a livello DB, elementi disattivati |
| `ExerciseConfigValidatorTest` | unit | MAX/ripetizioni, serie personalizzate complete |
| `PlanIntegrationTest` | integrazione | struttura, riordino atomico, eliminazione, sezioni univoche, serie personalizzate, locking ottimistico, eliminazione logica, duplicazione profonda, ruoli |
| `AssignmentIntegrationTest` | integrazione | assegnazione multipla, chiusura precedente, indice parziale, attivazione/chiusura, atomicità, accesso incrociato USER A/B |
| `RotationCalculatorTest` | unit | esempio della specifica, riposo, O-01, ancora, calcolo O(1) vs ingenuo |
| `CalendarIntegrationTest` | integrazione | giorni, rotazione, ri-ancoraggio (giorni e sessioni), copia giorni, periodo |
| `WorkoutTransitionsTest` | unit | transizioni di stato, timer |
| `ExecutionIntegrationTest` | integrazione | flusso completo con timer, idempotenza anche concorrente (4 thread), serie corrente, salto, ripresa, avvio, indice parziale, snapshot invariato, accesso incrociato, chiusura assegnazione, Oggi, Calendario |
| `HistoryIntegrationTest` | integrazione | storico, snapshot, privacy, profilo |

Frontend (Vitest + Testing Library + MSW, 9 file): guardie di routing, login e cambio password,
gestione utenti, cataloghi, editor della scheda (validazione, MAX, riordino, conferme, serie
personalizzate), assegnazioni, schede USER, giorni, timer riallineato, schermata allenamento
(idempotenza con retry, risincronizzazione, salto, riepilogo), Oggi, Calendario, Storico, Profilo.

End-to-end (Playwright, `frontend/e2e/main-flows.spec.ts`, viewport 360×740): i sei flussi della §16
della specifica.

## 18. Risultati degli ultimi test e delle build

Esecuzione del 24/09/2026 su `develop`:

| Verifica | Risultato |
| --- | --- |
| Database ricreato da zero solo con Flyway | 6 migrazioni applicate, `ddl-auto=validate` OK |
| `./mvnw clean verify` | **189 test, 0 fallimenti**, compilazione senza warning, jar prodotto |
| `npm run typecheck` / `npm run lint` | OK, 0 errori, 0 warning |
| `npm test` | **51 test in 9 file, tutti verdi** |
| `npm run build` | OK; bundle iniziale 368 kB (114 kB gzip), pagine caricate su richiesta |
| `npm run e2e` su Vite + backend (database nuovo e ripetuto) | **5/5 superati** (6 flussi) |
| `npm run e2e` sullo stack Docker (nginx + backend + PostgreSQL) | **5/5 superati** |
| Verifica manuale nel browser a 360 px | *Fine serie* visibile senza scorrere, nessuno scorrimento orizzontale |
| Ricerca di segreti nella cronologia Git | nessun `.env`, chiave o token versionato |

## 19. Branch, strategia di integrazione e commit principali

Strategia (ADR 0006): `main` stabile ← `develop` (integrazione) ← un branch per incremento o area,
integrato con `merge --no-ff` dopo test e build verdi; Conventional Commits; nessun force-push.

| Branch | Contenuto |
| --- | --- |
| `chore/increment-0-foundation` | struttura, sicurezza, bootstrap ADMIN, scheletro frontend, Docker, ADR |
| `feat/identity-accounts` | gestione account ADMIN |
| `feat/catalog-management` | cataloghi |
| `feat/workout-plans` | schede, editor, serie personalizzate, duplicazione |
| `feat/plan-assignments` | assegnazioni e schede USER |
| `feat/calendar-rotation` | giorni e rotazione |
| `feat/workout-execution` | esecuzione, timer, Oggi, Calendario |
| `feat/workout-history` | storico e profilo |
| `test/integration-e2e` | suite Playwright |
| `fix/nginx-forwarded-host` | correzione 403 dietro nginx |
| `perf/route-code-splitting` | caricamento lazy delle pagine |
| `docs/final-documentation` | CLAUDE.md, guida di test, documentazione finale |

I branch `feat/frontend-admin` e `feat/frontend-user`, citati come esempi nelle istruzioni,
non sono stati creati. Il frontend di ogni user story è stato sviluppato nello stesso branch del
suo backend (slice verticali), così ogni merge in `develop` era completo e verificabile.

Commit principali (estratto):

```text
80f8d28 feat(identity): add session security, login lockout, forced password change and admin bootstrap
dfce159 feat(identity): add admin user creation, editing, activation and password reset
acfb9ae feat(catalog): add muscle groups and exercises with case-insensitive unique names
85c6efe feat(workoutplan): add shared plans with sessions, sections, configured exercises and custom sets
3c02c37 feat(assignment): assign shared plans to many users with a single active assignment
99381f4 feat(calendar): add weekly schedule, constant-time session rotation and re-anchoring
df48eb9 feat(execution): add workout snapshot, idempotent set completion, skip, today and calendar views
07d3340 feat(frontend): add today, guided workout with rest timer and calendar screens
f363f51 feat(execution,identity): add essential workout history and user profile endpoints
114fffe test(e2e): add Playwright suite for the six main flows at 360 px
add2ce4 fix(docker): forward the original Host with port so same-origin requests pass CORS behind nginx
330ca09 perf(frontend): lazy-load pages per route to shrink the initial bundle
```

## 20. Decisioni tecniche

Registrate in `docs/decisions/` con problema, alternative, decisione, motivazione, conseguenze:

- **0001** Stack: Spring Boot 4.0.8 (linea supportata) invece di 3.5; React 19, React Router 8, Vite 8.
- **0002** Confini modulari: riferimenti per UUID ed eventi sincroni; viste Oggi e Calendario nel modulo `execution`.
- **0003** Sicurezza: sessione server, CSRF SPA, `session_version`, filtro per il cambio password obbligatorio.
- **0004** Decisioni aperte O-01…O-07 chiuse con le proposte consigliate.
- **0005** Design system CSS a token (skill ui-ux-pro-max), font locali, tema scuro.
- **0006** Strategia Git.

Altre scelte documentate nel codice: vincoli unici `DEFERRABLE` per i riordini atomici;
`flush()` esplicito prima di attivare l'esercizio successivo (indice parziale); lock pessimistico per
*Fine serie*; istanti troncati ai millisecondi; tolleranza di ±1 giorno all'avvio (fusi orari);
caricamento lazy delle pagine.

## 21. Problemi incontrati e soluzioni

| Problema | Soluzione |
| --- | --- |
| start.spring.io bloccato, Docker Hub con limite di download (429) | `pom.xml` scritto a mano; immagini da `mirror.gcr.io` |
| MockMvc sostituisce il repository CSRF | intestazione verificata con curl sul backend reale; test adattato |
| Riordino con vincoli di posizione unici | vincoli `DEFERRABLE INITIALLY DEFERRED` e aggiornamento delle sole posizioni |
| Evento di ri-ancoraggio non emesso dopo il riordino | confronto degli id ordinati per posizione, non per ordine in memoria |
| `LazyInitializationException` nell'elenco schede | mappatura dei DTO dentro la transazione del servizio |
| Form ricreati dopo il salvataggio (messaggio di conferma perso) | `key` legata all'identità (assegnazione/utente), non ai valori |
| Regole del linter React 19 sul timer | hook riscritto senza setState sincroni né lettura di ref durante il render |
| 403 al login dietro nginx | `proxy_set_header Host $http_host` (porta inclusa) |
| Bundle da 585 kB | caricamento lazy per rotta (368 kB iniziali) |
| PC Ubuntu con Java 17, `.env` vuoto, password ADMIN d'esempio senza cifre | istruzioni in README/CLAUDE.md; `.env.example` corretto |

## 22. Limiti conosciuti e sviluppi futuri

- Il timer non può garantire suono o vibrazione con lo schermo bloccato (limite del browser).
- Il calendario ricostruisce le sessioni dei giorni passati non svolti con le regole correnti:
  dopo un ri-ancoraggio il titolo mostrato per un giorno passato *non svolto* può differire da
  quello previsto all'epoca (gli allenamenti svolti restano esatti).
- L'avvio è consentito per la data odierna ±1 giorno rispetto al fuso del server (`GYM_TIME_ZONE`).
- Le sessioni HTTP sono in memoria: un riavvio del backend richiede un nuovo login e più istanze
  richiederebbero Spring Session (JDBC/Redis).
- Nessuna documentazione OpenAPI generata: le API sono descritte in `docs/api.md`.
- Sviluppi futuri previsti dalla specifica: controlli avanzati del timer (US-19), storico avanzato
  e statistiche, carichi e ripetizioni effettive, PWA/notifiche, recupero password via email.

## 23. Variabili d'ambiente

| Variabile | Uso | Esempio (dimostrativo) |
| --- | --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | container PostgreSQL | `gymplanner`, `gymplanner`, `change-me`, `5432` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | connessione del backend | `jdbc:postgresql://localhost:5432/gymplanner` |
| `GYM_ADMIN_USERNAME`, `GYM_ADMIN_EMAIL`, `GYM_ADMIN_PASSWORD` | primo ADMIN (solo se non esiste alcun ADMIN) | `admin`, `admin@example.test`, `ChangeMe2026` |
| `GYM_COOKIE_SECURE` | cookie `Secure` (true dietro HTTPS) | `false` |
| `GYM_CORS_ALLOWED_ORIGINS` | origini CORS consentite | `http://localhost:5173` |
| `GYM_TIME_ZONE` | fuso per le date decise dal server | `Europe/Rome` |

`POSTGRES_PASSWORD` e `DB_PASSWORD` devono coincidere. `GYM_ADMIN_PASSWORD`: 8-128 caratteri con
lettere e cifre, altrimenti l'avvio si interrompe con un messaggio chiaro. Il file `.env` non va
mai versionato; `.env.example` contiene solo valori dimostrativi.

## 24. Configurazione PostgreSQL e istruzioni Docker

- PostgreSQL 16 (`postgres:16-alpine`) in `compose.yaml`, porta pubblicata solo su `127.0.0.1`,
  dati nel volume `pgdata`, healthcheck `pg_isready`. Il database e l'utente vengono creati dal
  container al primo avvio usando `.env`; le tabelle le crea Flyway all'avvio del backend.
- `docker compose up -d postgres`: solo database (sviluppo con backend e frontend locali).
- `docker compose --profile app up -d --build`: database + backend (immagine Java 21 JRE, utente
  non root, healthcheck) + frontend (nginx che serve la SPA e inoltra `/api`) su
  **http://localhost:8081**.
- `docker compose down` ferma i container; `docker compose down -v` cancella anche i dati.

## 25. Avvio, arresto ed esecuzione dei test

Avvio (sviluppo):

```bash
cp .env.example .env               # e personalizza le password
docker compose up -d postgres
cd backend && set -a && source ../.env && set +a && ./mvnw spring-boot:run   # :8080
cd frontend && npm install && npm run dev                                     # :5173
```

Su Windows (PowerShell) il caricamento del `.env` è descritto nel `README.md`.

Arresto: `Ctrl+C` nei terminali di backend e frontend, poi `docker compose down`.

Test:

```bash
cd backend && ./mvnw verify                 # unit + integrazione (richiede Docker) + ArchUnit
cd frontend && npm test                     # Vitest
cd frontend && npm run lint && npm run typecheck && npm run build
cd frontend && npx playwright install chromium && npm run e2e   # con backend e frontend avviati
```

## 26. Credenziali iniziali dell'ADMIN

Non esistono credenziali predefinite nel codice. Il primo ADMIN viene creato all'avvio dai
valori di `GYM_ADMIN_USERNAME`, `GYM_ADMIN_EMAIL` e `GYM_ADMIN_PASSWORD`, solo se nel database non
esiste ancora nessun ADMIN, con `mustChangePassword = true`: al primo accesso l'applicazione impone
di scegliere una nuova password. La password non viene mai scritta nei log. Per ricreare l'ADMIN
iniziale in locale, azzera il database con `docker compose down -v`.

## 27. Esempi di utilizzo

**ADMIN**

1. Accede e cambia la password iniziale.
2. *Gruppi*: crea "Petto", "Dorso". *Esercizi*: crea "Panca piana", "Trazioni", "Rematore".
3. *Schede → Nuova scheda* "Principianti"; aggiunge "Giorno 1" (Petto: Panca 3×10 recupero 60",
   Trazioni 2×MAX) e "Giorno 2" (Dorso: Rematore 3×12). Il badge passa da "Incompleta" a "Pronta".
4. *Assegna*: seleziona più utenti, data di inizio, *Attiva subito*.
5. *Utenti*: crea un account e comunica la password temporanea; se serve, *Reset password* o *Disattiva*.
6. Per personalizzare una scheda per un solo utente: *Duplica*, modifica la copia, assegnala.

**USER**

1. Accede con la password temporanea e ne sceglie una nuova.
2. *Giorni*: seleziona lunedì, mercoledì e venerdì.
3. *Oggi*: vede la sessione del giorno e preme *Inizia allenamento*.
4. Per ogni serie preme *Fine serie*: parte il timer; al termine un avviso segnala la fine del recupero.
5. Se un esercizio non si può fare: *Salta esercizio*. A fine sessione: riepilogo.
6. *Calendario*: vede le prossime sessioni e gli esiti; *Storico*: rivede gli allenamenti svolti.

La guida completa per verificare tutte le funzioni è in `GUIDA_TEST_MANUALE.md`.

## 28. Troubleshooting

| Sintomo | Causa probabile | Soluzione |
| --- | --- | --- |
| `no configuration file provided` | comando fuori dalla cartella del progetto o branch senza `compose.yaml` | `cd` nella root del progetto, usare `develop`/`main` |
| `required variable POSTGRES_PASSWORD is missing` | `.env` vuoto o assente | `cp .env.example .env` e compilarlo |
| `release version 21 not supported` | Maven usa Java 17 | installare `openjdk-21-jdk`, `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| `GYM_ADMIN_PASSWORD does not satisfy the password policy` | password senza cifre o troppo corta | es. `Admin12345` |
| `No ADMIN account exists ... missing environment variable(s)` | variabili non caricate nel terminale | `set -a && source ../.env && set +a` |
| `password authentication failed for user "gymplanner"` | volume creato con un'altra password | `docker compose down -v`, poi di nuovo `up -d postgres` |
| porta 5432 occupata | altro PostgreSQL locale | `POSTGRES_PORT=5433` e `DB_URL=...:5433/...` |
| test backend falliscono subito | Docker non attivo (Testcontainers) | avviare Docker |
| dal frontend "Connessione assente o server non raggiungibile" | backend spento | avviare il backend su 8080 |
| "Credenziali non valide" dopo molti tentativi | account bloccato 15 minuti | attendere o far resettare la password dall'ADMIN |
| 403 al login dietro un proxy diverso da quello fornito | l'header `Host` perde la porta | inoltrare `Host` completo o impostare `GYM_CORS_ALLOWED_ORIGINS` |
