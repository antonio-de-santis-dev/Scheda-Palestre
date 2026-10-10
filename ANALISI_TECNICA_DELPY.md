# Analisi tecnica del branch `delpy`

Data: 10 ottobre 2026 · Commit analizzato: `0dff2f0` (HEAD di `delpy`)
Ambito: tutto il codice del repository (backend Spring Boot, frontend React, infrastruttura Docker/Nginx/Caddy, migrazioni Flyway, test).

Le correzioni sono sul branch `fix-cloude`; il resoconto è in `RESOCONTO_FIX_CLOUDE.md`.

---

## 1. Sintesi

| Area | Esito generale |
| --- | --- |
| Architettura | Buona: monolite modulare con confini verificati da ArchUnit, DTO `record`, eventi sincroni, nessuna entità esposta. |
| Sicurezza | Solida di base: sessione server, CSRF SPA, BCrypt, blocco account, 404 sulle risorse altrui, CSP, cookie HttpOnly. Ci sono alcune lacune puntuali (sezione 3). |
| Bug | Pochi e circoscritti (sezione 4). Nessun bug bloccante sui flussi principali. |
| Prestazioni | Query aggregate ben fatte (storico, statistiche, record con una sola query SQL). Restano alcune N+1 su "Oggi" e sull'avvio dell'allenamento (sezione 5). |
| Clean code | Codice leggibile e commentato. Ci sono codice morto, codice irraggiungibile, commenti non aggiornati e messaggi in lingue diverse (sezione 6). |
| Warning | Compilazione e lint puliti; warning nei test (sezione 7). |

### Stato verificato prima delle correzioni

| Controllo | Risultato |
| --- | --- |
| `mvn verify` (Testcontainers + PostgreSQL 16) | **267/267** test superati, BUILD SUCCESS |
| `npm run lint` | 0 errori, 0 warning |
| `npm run typecheck` | 0 errori |
| `npm test` (Vitest) | **152/152** test superati |
| `npm audit` | 0 vulnerabilità |

---

## 2. Priorità

| ID | Tipo | Gravità | Titolo |
| --- | --- | --- | --- |
| S-01 | Sicurezza | Media | Nessun limite di frequenza per IP sul login (password spraying) |
| S-02 | Sicurezza / Bug | Media | La CSP di produzione blocca lo script inline del tema in `index.html` |
| S-03 | Sicurezza | Media | L'ADMIN iniziale protetto perde la protezione se viene rinominato |
| S-04 | Sicurezza | Bassa | Manca HSTS sul dominio HTTPS (Caddy) |
| S-05 | Sicurezza | Bassa | Manca `Permissions-Policy` fra gli header di sicurezza |
| S-06 | Sicurezza | Info | Il token CSRF non viene rigenerato al login |
| B-01 | Bug | Media | `POST /api/admin/assignments/{id}/activate` senza corpo copia i giorni (default documentato: `false`) |
| B-02 | Bug (concorrenza) | Bassa | Chiusura assegnazione: l'interruzione dell'allenamento non prende il lock del workout |
| B-03 | Bug (UX) | Bassa | Codici d'errore senza messaggio italiano nel frontend |
| P-01 | Prestazioni | Media | "Oggi": 3 query per ogni scheda attiva (N+1) |
| P-02 | Prestazioni | Media | Avvio allenamento: giorni/sessioni riletti due volte e scheda caricata due volte |
| P-03 | Prestazioni | Bassa | Audio di fine recupero scaricato anche con il suono disattivato |
| C-01 | Clean code | Bassa | Codice irraggiungibile in `AssignmentService.activate` |
| C-02 | Clean code | Bassa | Metodi di repository e metodi di dominio mai usati in produzione |
| C-03 | Clean code | Bassa | Javadoc non aggiornato in `ActivityReportService` |
| C-04 | Clean code | Bassa | Messaggi tecnici in italiano in `CalendarService` (altrove in inglese) |
| W-01 | Warning | Bassa | 7 warning `redundant cast` in `RecoveryResultsIntegrationTest` |
| W-02 | Warning | Bassa | Mockito/Byte Buddy caricato dinamicamente come agente (warning JDK 21) |
| W-03 | Warning | Bassa | jsdom: `scrollTo()` e `HTMLMediaElement.pause()` non implementati (rumore nei test) |
| D-01 | Debito | Info | Tabelle/colonne V15-V16 non usate dal codice (funzioni FUTURO) |

---

## 3. Vulnerabilità di sicurezza

### S-01 · Nessun rate limit per IP sul login (Media)

**Dove:** `frontend/nginx.conf`, `AuthService.authenticate`.

Il backend blocca un *account* per 15 minuti dopo 5 tentativi falliti. Questo protegge la singola
utenza ma non ferma un attacco **password spraying** (una password comune provata su molti username)
né un attaccante che blocca di proposito gli account altrui. Nessuno strato limita la frequenza delle
richieste per indirizzo IP.

**Correzione proposta:** `limit_req` di Nginx su `location = /api/auth/login` (10 richieste/minuto per
IP con burst di 10, risposta 429), con l'IP reale del client letto da `X-Forwarded-For` solo se la
richiesta arriva dalla rete Docker (Caddy). Il frontend mostra un messaggio italiano per il 429.

### S-02 · CSP che blocca lo script inline del tema (Media, bug funzionale)

**Dove:** `frontend/index.html`, `frontend/nginx-security-headers.conf`.

La CSP di Nginx è `default-src 'self'` senza `'unsafe-inline'` per gli script: lo `<script>` inline che
applica il tema salvato prima del render **viene bloccato in produzione**. Effetti: errore CSP in
console a ogni caricamento e "flash" del tema sbagliato finché React non monta. In sviluppo (Vite,
senza CSP) il problema non si vede.

**Correzione proposta:** spostare lo script in `public/theme-init.js` caricato con `<script src>`:
rispetta la CSP senza indebolirla (niente `'unsafe-inline'`, niente hash da mantenere).

### S-03 · Protezione dell'ADMIN iniziale aggirabile (Media)

**Dove:** `AdminUserService.isProtected/update`.

L'account ADMIN creato al bootstrap è "protetto" (non eliminabile), ma la protezione si basa sul
confronto fra lo **username** e `GYM_ADMIN_USERNAME`. Con `PUT /api/admin/users/{id}` un ADMIN può
rinominare quell'account: da quel momento `isProtected` restituisce `false` e l'account può essere
eliminato. La regola dell'ADR 0010 è quindi aggirabile in due passi.

**Correzione proposta:** rifiutare la modifica dello username dell'account protetto con 422
`PROTECTED_ACCOUNT` (gli altri dati restano modificabili).

### S-04 · HSTS assente (Bassa)

**Dove:** `deploy/Caddyfile`. Caddy fa il redirect HTTP→HTTPS ma non invia
`Strict-Transport-Security`: al primo accesso un attaccante sulla rete può forzare HTTP (SSL strip).
**Correzione proposta:** header HSTS di un anno sul sito.

### S-05 · `Permissions-Policy` assente (Bassa)

L'app non usa fotocamera, microfono, geolocalizzazione, pagamenti, USB. Dichiararlo riduce la
superficie in caso di XSS o di contenuti di terze parti. **Correzione proposta:** header
`Permissions-Policy` in `nginx-security-headers.conf`.

### S-06 · Token CSRF non rigenerato al login (Informativa)

Il login è un endpoint custom (`SessionAuthentication.login`): ruota correttamente l'id di sessione
(session fixation) ma non passa dalla `CsrfAuthenticationStrategy`, quindi il cookie `XSRF-TOKEN`
sopravvive al login. Con il repository a cookie il rischio pratico è basso (serve poter scrivere un
cookie sul dominio). Lasciato com'è e documentato: cambiarlo richiede di riallineare il client SPA.

### Controlli superati (nessuna azione)

- Password: BCrypt delegante, limite 72 byte UTF-8, tempo costante con hash fittizio per username
  inesistenti, messaggio unico per credenziali errate/account disattivati/eliminati.
- Sessione: cookie `HttpOnly`, `SameSite=Lax`, `Secure` in produzione, `session_version` che invalida
  le sessioni a cambio password/disattivazione/eliminazione, ricontrollo ad ogni richiesta.
- Autorizzazione: regole per ruolo sugli URL e ownership in ogni servizio (`/api/me/**` usa sempre il
  principal); le risorse di altri utenti rispondono 404.
- SQL: tutte le query native usano parametri; il `WHERE` dinamico di storico/statistiche è costruito
  solo con frammenti costanti; i `LIKE` fanno l'escape di `%`, `_`, `\`.
- Errori: Problem Details senza stacktrace né messaggi interni; i log non contengono password.
- Frontend: nessun `dangerouslySetInnerHTML`, nessun `eval`, redirect post-login limitato alla propria
  area, URL delle API vincolate alla stessa origine, `npm audit` pulito.
- Container: backend eseguito come utente non root; database non esposto in produzione; nginx con
  `server_tokens off` e `client_max_body_size 256k`.

---

## 4. Bug

### B-01 · Attivazione senza corpo copia i giorni (Media)

**Dove:** `AdminAssignmentController.activate`.

```java
return service.activate(id, body == null || body.copy());
```

Se il corpo manca, `copySchedule` vale `true`. La documentazione (`docs/api.md`) e il DTO
`AssignRequest` dicono che il default è `false`. Un client diverso dalla SPA (o uno script) copierebbe
i giorni dell'ultima scheda chiusa senza averlo chiesto.

**Correzione:** `body != null && body.copy()` + test di integrazione.

### B-02 · Interruzione su chiusura assegnazione senza lock (Bassa)

**Dove:** `WorkoutService.onAssignmentClosed`.

Tutte le azioni sull'allenamento prendono un lock pessimistico sulla riga del workout; la reazione
alla chiusura dell'assegnazione no. Se l'ADMIN chiude la scheda mentre l'utente preme "Fine serie",
le due transazioni possono sovrascriversi (lost update: `Workout` non ha `@Version`).

**Correzione:** leggere gli allenamenti in corso con `PESSIMISTIC_WRITE`.

### B-03 · Codici d'errore senza traduzione (Bassa)

Codici restituiti dal backend ma assenti in `messages.ts`, che quindi mostrano all'utente il
`detail` tecnico in inglese: `INVALID_EXERCISE_ORDER`, `WORKOUT_STATE_CHANGED`,
`SET_RESULTS_WINDOW_CLOSED`, `SCHEDULE_DAYS_REQUIRED`, `METHOD_NOT_ALLOWED`, `PAYLOAD_TOO_LARGE`,
`UNSUPPORTED_MEDIA_TYPE`, `REQUEST_ERROR`, `UNPROCESSABLE`; serve anche il nuovo `RATE_LIMITED` (429).

**Correzione:** messaggi italiani + codice `RATE_LIMITED` per lo stato 429 e test.

---

## 5. Prestazioni e agilità

### P-01 · "Oggi" esegue query per ogni scheda attiva (Media)

`TodayService.today` per ogni assegnazione attiva chiama `weekdays(id)` e `dayPlan(...)`; `dayPlan`
a sua volta rilegge giorni e sessioni. Con *N* schede attive sono `3N` query solo per il calendario,
su una schermata che è la home dell'utente e viene ricaricata spesso.

**Correzione:** nuova query di massa `CalendarQueries.weekdays(Collection<UUID>)` e uso di
`ranges(active, date, date)` che già carica giorni e sessioni in blocco: numero di query costante.

### P-02 · Avvio dell'allenamento con letture duplicate (Media)

`WorkoutService.start` calcola `dayPlan` per ogni scheda attiva, poi lo ricalcola per la scheda
scelta; poi `requireExecutable` carica la scheda e subito dopo `getStructure` la ricarica tutta.

**Correzione:** un solo `ranges(active, date, date)`; i controlli "eliminata" ed "eseguibile" sono
letti dalla struttura già caricata (stessi codici d'errore `PLAN_DELETED`/`PLAN_NOT_EXECUTABLE`).

### P-03 · Audio precaricato anche se disattivato (Bassa)

`useRestAlert` crea il player con `preload = 'auto'` sempre: su rete mobile scarica un MP3 che nella
maggior parte dei casi non verrà usato. **Correzione:** `preload` `'auto'` solo con il suono attivo.

### Già buono

- Storico, statistiche, grafici mensili e record: una query SQL aggregata ciascuno, senza caricare le
  entità; il riepilogo dello storico usa `WorkoutSummaryLoader` (una query per pagina).
- `default_batch_fetch_size=100`, `open-in-view=false`, indici parziali per lo stato `IN_PROGRESS`.
- Frontend: code splitting per rotta, TanStack Query con `staleTime`, retry solo su errori transitori,
  gzip e cache immutabile degli asset in Nginx.

### Suggerimenti non applicati (costo/beneficio)

- `SessionUserRefreshFilter` carica l'utente ad ogni richiesta: è voluto (revoca immediata). Si potrebbe
  usare una proiezione con i soli campi necessari, guadagno modesto.
- Vitest crea jsdom 27 volte (27% del tempo): `pool: 'vmThreads'` velocizzerebbe ma riduce
  l'isolamento; lasciato invariato.

---

## 6. Clean code

| ID | Dove | Problema | Intervento |
| --- | --- | --- | --- |
| C-01 | `AssignmentService.activate` | Dopo uno `switch` che copre tutti i casi con `return`/`throw` ci sono due righe mai eseguite. | Rimosse; `switch` espressione. |
| C-02 | `WorkoutRepository` | `findByPlanAssignmentIdAndScheduledDate` e `findByUserId(Pageable)` mai usati. `Workout.lastCompletedSet()` e `WorkoutDtos.WorkoutSummary.of()` usati solo dai test. | Rimossi; test adeguati. |
| C-03 | `ActivityReportService` | Il Javadoc dice che l'app "non registra i carichi", ma dal passo 5b peso e ripetizioni vengono registrati. | Javadoc aggiornato. |
| C-04 | `CalendarService.setNextSession` | `detail` in italiano mentre tutto il backend usa l'inglese (il testo per l'utente arriva dal codice nel frontend). | Uniformato all'inglese. |

---

## 7. Warning

| ID | Origine | Warning | Intervento |
| --- | --- | --- | --- |
| W-01 | `javac` (test) | 7 × `redundant cast to java.lang.Object` in `RecoveryResultsIntegrationTest`. | Cast rimossi. |
| W-02 | JDK 21 | "A Java agent has been loaded dynamically … Dynamic loading of agents will be disallowed". | Mockito caricato come `-javaagent` dal Surefire. |
| W-03 | jsdom | "Not implemented: Window's scrollTo()" / "HTMLMediaElement's pause()". | Stub nel setup dei test. |

---

## 8. Debito tecnico documentato (nessuna modifica)

- **D-01** `V15__password_recovery.sql` e `V16__push_subscriptions.sql` creano tabelle
  (`password_reset_tokens`, `recovery_rate_limits`, `push_subscriptions`), la colonna
  `workouts.rest_notified_version` e l'indice `ix_workouts_push_rest`, ma nessun codice li usa: sono
  funzioni FUTURO. Sono state conservate di proposito per lo storico Flyway (commit `b7d19b9`), quindi
  non vengono eliminate. Se si decide di non realizzarle mai, una futura `V22` può rimuoverle.
- Colonne `plan_exercises.planned_weight_kg`, `plan_sets.planned_weight_kg`,
  `workout_sets.weight_kg_planned` presenti ma non mappate: stesso discorso.
- `deploy/Caddyfile` e `compose.production.yaml` non impongono limiti di CPU/RAM ai container.
