# Documentazione delle modifiche - Task 0-6

Questo documento descrive il lavoro svolto su GymPlanner (repository
`antonio-de-santis-dev/Scheda-Palestre`, pubblicato su Northflank): allineamento di `main` e
Task 1-6. Le regole seguite sono quelle richieste:

- nessuna riscrittura;
- architettura e grafica invariate;
- solo migrazioni Flyway additive;
- nessun dato eliminato;
- nessun segreto nel repository;
- commit piccoli;
- nessun force push.

## 1. Situazione iniziale dei branch

| Branch | Stato iniziale |
| --- | --- |
| `main` (`8313bcf`) | Release 1.0.0. Molto indietro rispetto al codice pubblicato: mancavano `modifiche`, `fixV2`, `veloce`, `fix-cloude` e `musica`. |
| `delpy` | Linea più recente: include `modifiche`, `fixV2`, `veloce`, `fix-cloude` (`9d4c53a`) e `musica` (`e5b22c6`). Schema Flyway fino a V22. |
| `develop`, `feat/*`, `fix/*`, `perf/*`, `test/*`, `docs/*` | Branch storici degli incrementi, già confluiti. |
| `modifiche`, `fixV2`, `veloce`, `fix-cloude`, `musica` | Branch di funzionalità già integrati in `delpy`. |
| `SviluppoV2`, `desaig` | Linee parallele non integrate e non usate per il deploy. Non sono state toccate. |

## 2. Branch di deployment individuato

Il branch di deployment è **`delpy`**, per tre motivi:

- è l'unico con un nome riconducibile a "deploy" (fra deply, delpy e deploy esiste solo `delpy`);
- contiene i commit più recenti;
- è quello su cui sono state integrate le ultime modifiche pubblicate. Il riferimento a Northflank
  si trova in `docs/report-veloce.md`.

## 3. Procedura usata per allineare `main` (Task 0)

1. Backup del vecchio `main` nel branch remoto **`backup/main-pre-allineamento`** (`8313bcf`).
   Il push dei tag è bloccato dal proxy dell'ambiente, quindi è stato usato un branch.
2. Prima del merge, test su `delpy`: frontend 169/169, backend 275/275.
3. `git merge --no-ff delpy` in `main` → `416c47b`, senza conflitti, nessuna riscrittura di cronologia.
4. Test ripetuti su `main` (stessi risultati), poi `git push origin main`.

Da quel momento ogni task è partito dal `main` aggiornato ed è stato integrato con `--no-ff`
solo dopo i test.

## 4. Modifiche realizzate per ogni task

### Task 1 - Recupero tra esercizi (`task-1/recupero-tra-esercizi`, merge `41f5c48`)

- **Editor (ADMIN):** ogni esercizio ha il campo "Recupero prima del prossimo esercizio".
  - Si imposta in minuti e secondi, massimo 20 minuti.
  - La casella "Nessun recupero" lo azzera.
  - Il recupero fra le serie è stato rinominato "Recupero tra le serie".
- **Allenamento (USER):**
  - dopo l'ultima serie parte il timer del recupero tra esercizi;
  - un riquadro "Recupero tra esercizi" mostra cosa è "Appena concluso" e il "Prossimo esercizio";
  - "Fine serie" resta bloccato fino alla fine del recupero, come per le serie.
- **Compatibilità:** le schede esistenti ricevono come recupero tra esercizi quello dell'ultima serie,
  cioè il comportamento precedente. Gli allenamenti già salvati non cambiano.

### Task 2 - Nome della sessione (`task-2/nome-sessione`, merge `f6698fd`)

- **Diagnosi:** il titolo veniva salvato, ma la UI mostrava "Sessione A/B" e altre etichette generiche.
- Il nome scelto dall'ADMIN ora è il titolo ovunque:
  - schede dell'editor;
  - riepilogo delle schede (`sessionTitles`);
  - vista della scheda dell'utente;
  - Oggi, calendario, allenamento e storico.
- I gruppi muscolari sono mostrati sotto il nome. Se il nome manca, il ripiego è "Giorno N".

### Task 3 - Riprogrammazione degli allenamenti saltati (`task-3/riprogrammazione-allenamenti`, merge `3a2a24a`)

- **Automatica:** un giorno di allenamento passato senza allenamento viene registrato come `MISSED`.
  - La sessione non va persa: viene proposta al prossimo giorno di allenamento e le successive
    slittano mantenendo l'ordine della scheda.
  - Il rilevamento avviene alla prima apertura di Oggi o del Calendario, oppure all'avvio di un
    allenamento. Non serve un job pianificato.
- **Manuale:** dal calendario o da Oggi l'utente può:
  - **saltare** un giorno: la sessione passa al prossimo giorno di allenamento;
  - **spostare** la sessione in un giorno libero prima del prossimo allenamento;
  - **annullare** la modifica.

  Prima di confermare, una tabella "Conseguenze sui prossimi giorni" mostra il prima e il dopo.
- **Calendario:**
  - i giorni saltati sono barrati, con la legenda "Saltato";
  - i giorni riprogrammati hanno il badge "Riprogrammato" e il testo "Recupera la sessione saltata il …".
- **Oggi:** stato "Allenamento di oggi saltato" con le azioni disponibili.

### Task 4 - Storico per scheda (`task-4/storico-per-scheda`, merge `3d2bbbf`)

- Rimossa la sezione "Record nei filtri selezionati", sia il frontend sia l'endpoint `/workout-records`.
- La pagina Storico mostra prima l'elenco "Schede con allenamenti". Sono incluse anche le schede
  chiuse, con il nome salvato nello snapshot.
- Il dettaglio di una scheda mostra:
  - un gruppo espandibile per sessione;
  - il riepilogo e i progressi limitati a quella scheda;
  - la paginazione (20 per pagina).
- Il filtro testuale "Scheda, sessione o esercizio" cerca anche nei nomi degli esercizi.

### Task 5 - Ultimo peso per serie (`task-5/ultimo-peso-serie`, merge `021da43`)

- Durante l'allenamento, sotto i dati della serie e nel modulo dei risultati compare
  "Ultima volta — Serie N: X kg", oppure "Nessun peso precedente".
- Il valore viene dall'ultimo allenamento **concluso** con lo stesso esercizio (stessa identità di
  catalogo, anche se rinominato) e dalla **stessa serie**.
- Non vengono considerati:
  - esercizi saltati;
  - serie non completate;
  - pesi non registrati.
- Il campo peso **non** viene precompilato: il valore è solo un promemoria.

### Task 6 - Ottimizzazione, bug e test (`task-6/ottimizzazione-test-bugfix`)

- **Bug catalogo:** l'elenco dei gruppi è paginato in ordine alfabetico, quindi un gruppo appena
  creato poteva finire su un'altra pagina senza alcun riscontro. Ora si apre subito il suo dettaglio.
- **Nuovo test E2E `layout.spec.ts`:** sulle schermate principali ADMIN e USER, a 360 px e con nomi
  lunghi, verifica che non ci sia scorrimento orizzontale e che nessun pulsante, link o campo sia
  tagliato. Copre anche l'allenamento durante il recupero.
- **Test E2E resi stabili su database con molti dati:** gli elenchi paginati di gruppi e utenti
  vengono usati tramite la ricerca.
- **Documentazione:** `docs/api.md` aggiornato con tutti gli endpoint e campi nuovi; aggiornato
  anche `frontend/e2e/README.md`.
- **Revisione** secondo la checklist richiesta: esito nella sezione 10.

## 5. File principali modificati

| Area | File |
| --- | --- |
| Migrazioni | `backend/src/main/resources/db/migration/V23__rest_between_exercises.sql`, `V24__schedule_events.sql`, `V25__previous_weights_index.sql` |
| Schede (Task 1-2) | `workoutplan/internal/PlanExercise.java`, `ExerciseConfig`, `PlanDtos`, `PlanService`, `api/PlanStructure` |
| Esecuzione (Task 1) | `execution/internal/Workout.java`, `WorkoutExercise.java`, `WorkoutService.java`, `WorkoutDtos.java` |
| Calendario (Task 3) | `calendar/internal/SlotPlanner.java`, `ScheduleChangeService.java`, `ScheduleEvent*.java`, `ScheduleTracking*.java`, `ScheduleController.java`, `CalendarService.java`, `calendar/api/ScheduleMaintenance.java`, `execution/internal/TodayService.java` |
| Storico (Task 4) | `execution/internal/HistoryPlansService.java`, `WorkoutHistoryFilter`, `WorkoutController.java`; rimosso `WorkoutRecordsService.java` |
| Peso precedente (Task 5) | `execution/internal/PreviousWeightsService.java`, `WorkoutController.java` |
| Frontend | `admin/plans/ExerciseEditor.tsx`, `PlanEditorPage.tsx`, `SessionTabs.tsx`, `planTypes.ts`; `user/workout/WorkoutPage.tsx`, `RecoveryResultsForm.tsx`, `api.ts`; `user/schedule/ScheduleChangePanel.tsx`, `changes.ts`; `user/calendar/CalendarPage.tsx`; `user/today/TodayPage.tsx`; `user/history/HistoryPages.tsx`; `admin/catalog/CatalogPage.tsx`; `shared/components/PlanSessionView.tsx`; `shared/styles/global.css` |
| Test | backend: `RestBetweenExercisesIntegrationTest`, `SessionNamesIntegrationTest`, `SlotPlannerTest`, `ScheduleChangesIntegrationTest`, `HistoryByPlanIntegrationTest`, `PreviousWeightsIntegrationTest`, `migration/TaskMigrationsTest`, `ExerciseConfigValidatorTest`; frontend: `restBetweenExercises`, `sessionNames`, `scheduleChanges`, `previousWeights`, `catalog` e altri `*.test.tsx`; E2E: `task-flows.spec.ts`, `layout.spec.ts`, `main-flows.spec.ts`, `api.ts` |

## 6. Modifiche al database e migrazioni Flyway aggiunte

Tutte le migrazioni sono **additive**:

- nessun `DROP`, `DELETE` o `TRUNCATE`;
- nessuna modifica alle migrazioni esistenti (V1-V22);
- Hibernate resta in `validate`.

| Migrazione | Contenuto | Dati esistenti |
| --- | --- | --- |
| **V23** `rest_between_exercises` | `plan_exercises.rest_after_seconds` NOT NULL con CHECK 0-1200; `workout_exercises.rest_after_seconds` nullable con CHECK | Le schede esistenti vengono valorizzate con il recupero dell'ultima serie, cioè il comportamento precedente. Gli allenamenti storici restano `NULL`. |
| **V24** `schedule_events` | Tabella `schedule_events` (giorno saltato o spostato, univoca per assegnazione e data, FK `ON DELETE CASCADE` verso `plan_assignments`); tabella `schedule_tracking` (fino a che giorno sono stati controllati gli allenamenti saltati) | Per le assegnazioni attive `checked_until` = ieri, quindi **nessun** allenamento passato viene riprogrammato retroattivamente. |
| **V25** `previous_weights_index` | Indice parziale `ix_workout_exercises_catalog` su `workout_exercises(catalog_exercise_id)` | Nessun dato modificato. |

`ON DELETE CASCADE` riguarda solo le nuove tabelle, e le assegnazioni non vengono mai cancellate
fisicamente dall'applicazione.

## 7. API nuove o modificate

Il dettaglio è in `docs/api.md`, sezione "Modifiche Task 1-6". In sintesi:

- **Nuove:**
  - `GET/POST /api/me/assignments/{id}/schedule-changes`
  - `GET .../schedule-changes/options?date=`
  - `DELETE .../schedule-changes/{changeId}`
  - `GET /api/me/workout-plans`
  - `GET /api/me/workouts/{id}/previous-weights`
- **Modificate (solo campi aggiunti, retrocompatibili):**
  - `restAfterSeconds` (richiesta e risposta delle schede, `WorkoutState`) e `restKind`;
  - `sessionTitles` in `GET /api/admin/plans`;
  - `rescheduledFrom` e `skip`, stato `SKIPPED_DAY` e tipo `SKIPPED` in Oggi e Calendario;
  - parametro `plan` su `/workouts`, `/workout-stats` e `/workout-progress`; `q` cerca anche gli esercizi.
- **Rimossa:** `GET /api/me/workout-records`, usata solo dalla sezione record eliminata dal Task 4.

## 8. Decisioni tecniche

Per le ambiguità è stata scelta la soluzione più coerente con il funzionamento attuale:

1. **Recupero tra esercizi per esercizio:** si configura sull'esercizio che finisce, perché lì si
   trovano già i recuperi delle serie. Il valore predefinito replica il comportamento precedente.
2. **Rilevamento "pigro" degli allenamenti saltati:** avviene alla lettura di Oggi o del Calendario e
   all'avvio. Niente scheduler, adatto a un singolo container su Northflank. È serializzato per utente
   con `UserLock` e idempotente grazie al vincolo unico.
3. **Rotazione a slot:** un giorno saltato non consuma una sessione, quindi le successive slittano.
   Le assegnazioni esistenti non vengono ricalcolate all'indietro.
4. **Annullamento:**
   - un salto annullato su un giorno passato accetta la perdita: la sessione non viene ri-proposta
     né rilevata di nuovo;
   - una modifica già allenata non si può annullare (`SCHEDULE_CHANGE_LOCKED`).
5. **Spostamento solo nei giorni liberi** prima del prossimo allenamento e mai su giorni di un'altra
   scheda attiva. Così l'ordine delle sessioni resta prevedibile.
6. **Storico per scheda** raggruppato per `plan_id` dello snapshot e con il nome dello snapshot:
   restano visibili anche le schede eliminate o rinominate.
7. **Peso precedente** fornito da un solo endpoint per allenamento, con una query, e mostrato senza
   precompilare il campo, per non registrare per errore pesi non usati.
8. **Gruppo appena creato aperto subito** (Task 6), coerente con la navigazione esistente via `?group=`.

## 9. Problemi trovati e correzioni applicate

| Problema | Correzione |
| --- | --- |
| Suite E2E non allineate alla grafica attuale: schede "Sessione A/B", login "Bentornato", utenti paginati, griglia del calendario | E2E aggiornati (Task 2) |
| Testo duplicato "Esercizio completato" accanto alla celebrazione | Riquadro rinominato "Appena concluso" |
| Etichetta lunga che usciva dall'anello del timer | Mantenuta l'etichetta corta "Recupero" |
| "Nessun recupero" lasciava minuti non validi nel modulo | Azzeramento automatico dei campi |
| Text block SQL Java: lo spazio finale veniva rimosso (`whereuser_id`) | Concatenazione esplicita |
| Nuovo gruppo non visibile se finiva su un'altra pagina del catalogo | Apertura automatica del gruppo creato |
| E2E dipendenti dalla prima pagina di elenchi paginati | Ricerca del nome creato |
| Tipi DOM mancanti per `page.evaluate` nel nuovo E2E (rompeva `npm run build`) | Riferimento `lib="dom"` limitato al file |

## 10. Revisione Task 6 (checklist)

**Backend:**

- **Query e N+1:** le nuove funzionalità usano una query per richiesta: storico per scheda,
  statistiche, peso precedente e rilevamento dei giorni saltati. Nel caso comune quest'ultimo è una
  sola lettura, senza lock.
- **Paginazione dello storico:** presente (20 per pagina); riepilogo e progressi sono aggregati in SQL.
- **Transazioni e race condition:**
  - le modifiche del calendario e il rilevamento avvengono sotto `UserLock` con rilettura dentro il lock;
  - "Fine serie" usa il lock pessimistico, come prima;
  - il timer è derivato da `restEndsAt`/`serverTime`.
- **Validazione ed errori:** Bean Validation sui nuovi DTO, eccezioni di `shared.error` con `code`
  (RFC 9457), risorse di altri utenti → 404.
- **Autorizzazioni:** i nuovi endpoint sono in `/api/me/**`, con l'utente preso dal principal.
  I test verificano che altri utenti ricevano 404 e che l'ADMIN venga rifiutato.
- **Endpoint inutilizzati:** rimosso `/workout-records`.
- **Indici:** V25 per il peso precedente; vincoli unici su `schedule_events`.
- **Log:** nessun log aggiunto, actuator limitato a `health`.
- **Migrazioni:** testate da `TaskMigrationsTest` su un database con dati V22 reali (V22→V25).

**Frontend:**

- Nessuna dipendenza aggiunta; caricamento per route già suddiviso in chunk.
- Dati server solo in TanStack Query; il peso precedente usa `staleTime` di 30 minuti.
- Stati loading, empty ed error tramite `QueryState` ed `EmptyState`, come nel resto dell'app.
- Modali di conferma per saltare il recupero e modificare il calendario.
- Accessibilità:
  - etichette su tutti i campi;
  - stati espressi con testo e icona;
  - target da 44 px.
- Smartphone verificato automaticamente a 360 px (`layout.spec.ts`) e con le schermate controllate a vista.

## 11. Test eseguiti e risultati

| Suite | Prima (Task 0) | Finale |
| --- | --- | --- |
| Backend `mvn verify` (unit, integrazione Testcontainers, ArchUnit, migrazioni) | 275/275 | **298/298**, 0 falliti |
| Frontend Vitest | 169/169 | **181/181** (34 file), 0 falliti |
| Frontend `npm run lint`, `tsc -b`, `npm run build` | ok | ok |
| E2E Playwright (`main-flows`, `task-flows`, `layout`) a 360 px su sistema reale | - | **14/14** |

I 15 flussi principali sono stati verificati sul sistema reale (backend, PostgreSQL e Vite) con
Playwright a 360 px:

| # | Flusso | Coperto da |
| --- | --- | --- |
| 1 | login ADMIN | `main-flows` 1 |
| 2 | creazione utente | `main-flows` 1 |
| 3 | creazione scheda | `main-flows` 2 |
| 4 | creazione e denominazione sessioni | `task-flows` Task 2 |
| 5 | assegnazione | `main-flows` 2, 5b |
| 6 | programmazione dei giorni | `main-flows` 3, 5b |
| 7 | avvio allenamento | `main-flows` 4-5 |
| 8 | completamento serie | `main-flows` 4-5 |
| 9 | recupero tra serie | `main-flows` 4-5 |
| 10 | recupero tra esercizi | `task-flows` Task 1 |
| 11 | salto di un esercizio | `main-flows` 4-5 |
| 12 | completamento allenamento | `main-flows` 4-5, `task-flows` Task 5 |
| 13 | storico | `main-flows` 4-5, 6, `task-flows` Task 4 |
| 14 | allenamento saltato e riprogrammazione | `task-flows` Task 3 e `ScheduleChangesIntegrationTest` |
| 15 | modifica manuale del calendario | `task-flows` Task 3 |

## 12. Limitazioni rimaste

- Le date in cui una sessione è stata spostata non vengono ricontrollate se in seguito un'altra
  scheda occupa quel giorno della settimana. Cambiare i giorni di una scheda annulla comunque le sue
  modifiche future.
- Il rilevamento degli allenamenti saltati guarda al massimo 62 giorni indietro. Nella migrazione V24
  "ieri" è calcolato con la data del database (UTC): la differenza con Europe/Rome è al massimo di un
  giorno ed è solo prudenziale.
- Un giorno saltato non si può avviare direttamente: prima va annullato il salto.
- Le tabelle create da V15/V16 non sono usate dal codice attuale (lasciate per compatibilità).
- La riproduzione YouTube del modulo musica non è stata provata dal vivo (rete dell'ambiente di test).
- V25 crea l'indice senza `CONCURRENTLY` (Flyway lo esegue in transazione): per pochi istanti blocca
  le scritture su `workout_exercises` durante il primo avvio. È trascurabile con i volumi di una palestra.

## 13. Variabili d'ambiente necessarie

Sono invariate rispetto a `delpy`: nessuna variabile nuova. Vedi `.env.example`.

| Variabile | Uso |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | connessione PostgreSQL del backend |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | solo per il PostgreSQL di `compose.yaml` in locale |
| `GYM_ADMIN_USERNAME`, `GYM_ADMIN_EMAIL`, `GYM_ADMIN_PASSWORD` | bootstrap dell'ADMIN, solo su database vuoto. La password deve avere almeno 8 caratteri con lettere e cifre. |
| `GYM_COOKIE_SECURE` | `true` in produzione (HTTPS) |
| `GYM_CORS_ALLOWED_ORIGINS` | origine del frontend |
| `GYM_TIME_ZONE` | fuso del calendario (predefinito Europe/Rome) |

## 14. Avvio di frontend e backend

```bash
cp .env.example .env              # e compila i valori (mai committare .env)
docker compose up -d postgres

cd backend
set -a && source ../.env && set +a
./mvnw spring-boot:run            # http://localhost:8080 (Flyway applica V23-V25 all'avvio)

cd ../frontend
npm install
npm run dev                       # http://localhost:5173 (proxy /api -> 8080)
```

## 15. Esecuzione dei test

```bash
cd backend && ./mvnw verify                         # serve Docker per Testcontainers
cd frontend && npm test && npm run lint && npm run build
# E2E: backend, PostgreSQL e Vite avviati
cd frontend && E2E_ADMIN_PASSWORD=<password admin iniziale> npm run e2e
```

## 16. Deployment su Northflank

Nessuna modifica a Dockerfile, `nginx.conf`, compose, dipendenze o variabili. È stato verificato che:

- frontend e backend compilano (`mvn verify`, `npm run build`);
- le migrazioni partono da sole all'avvio del backend (`spring.flyway.enabled=true`) e sono
  additive: il database esistente **non** viene azzerato e i dati restano;
- le route del frontend funzionano dopo un refresh diretto (`try_files ... /index.html` in
  `nginx.conf`, verificato anche dagli E2E sui deep link);
- health check (`/actuator/health`, probe liveness e readiness) e porta 8080 del backend sono invariati;
  nginx continua a inoltrare `/api/` a `gym-planner-backend:8080`;
- nel diff non sono stati introdotti segreti (solo password di utenti di test usa e getta negli E2E).

Per pubblicare:

1. Il servizio Northflank oggi compila da **`delpy`**. Ci sono due strade:
   - portare `main` in `delpy` (`git checkout delpy && git merge --no-ff main && git push origin delpy`);
   - oppure impostare `main` come branch del servizio nelle impostazioni di build di Northflank.
2. Northflank ricompila le immagini di backend e frontend e riavvia i servizi.
3. All'avvio il backend applica V23, V24 e V25 (controllabile nei log: `Successfully applied 3 migrations`).
4. Controlla `/actuator/health` e un login ADMIN.

Si consiglia uno snapshot o backup del database da Northflank prima del rilascio, come buona pratica.

## 17. Attività future consigliate

- Ricontrollare le date spostate quando i giorni di un'altra scheda cambiano.
- Rate limiting per IP al bordo di Northflank (vedi `docs/report-veloce.md`).
- Rotazione del token CSRF al login.
- Rimozione, con una migrazione dedicata e dopo verifica, delle tabelle V15/V16 non usate.
- Aggiungere `e2e/layout.spec.ts` anche in tema scuro e a 768 px.
- Eseguire gli E2E nella CI.
