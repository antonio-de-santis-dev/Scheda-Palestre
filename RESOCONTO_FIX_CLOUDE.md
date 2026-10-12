# Resoconto del branch `fix-cloude`

Branch creato da `delpy` (`0dff2f0`) il 10 ottobre 2026. Contiene le correzioni dei problemi
descritti in `ANALISI_TECNICA_DELPY.md`. **Non è stato unito a nessun altro branch** e `delpy` non è
stato modificato.

## 1. Cosa è stato fatto

### Sicurezza

| ID | Correzione | File |
| --- | --- | --- |
| S-01 | Limite di frequenza sul login per IP: 20 tentativi/minuto con burst di 20; oltre, Nginx risponde **429** in formato Problem Details (`code: RATE_LIMITED`, `Retry-After: 60`) senza disturbare il backend. L'IP reale è letto da `X-Forwarded-For` solo se arriva dalle reti Docker private (Caddy). Le direttive `proxy_set_header` sono ora a livello di server, senza duplicazioni. | `frontend/nginx.conf` |
| S-02 | Lo script del tema non è più inline: `public/theme-init.js` rispetta la CSP `script-src 'self'` di produzione, quindi niente più errore CSP né "flash" del tema sbagliato. | `frontend/index.html`, `frontend/public/theme-init.js` |
| S-03 | Lo username dell'ADMIN iniziale non può più essere cambiato (422 `PROTECTED_ACCOUNT`): rinominarlo toglieva la protezione dall'eliminazione. Nome, cognome, email e telefono restano modificabili. | `AdminUserService` |
| S-04 | HSTS (`Strict-Transport-Security: max-age=31536000`) sul dominio servito da Caddy. | `deploy/Caddyfile` |
| S-05 | `Permissions-Policy` che disattiva fotocamera, microfono, geolocalizzazione, pagamenti e USB. | `frontend/nginx-security-headers.conf` |

### Bug

| ID | Correzione | File |
| --- | --- | --- |
| B-01 | `POST /api/admin/assignments/{id}/activate` senza corpo **non copia più** i giorni (default `false` come da `docs/api.md`). | `AssignmentControllers` |
| B-02 | Quando l'ADMIN chiude un'assegnazione, l'allenamento in corso viene interrotto prendendo lo stesso lock di riga di "Fine serie": niente più sovrascritture concorrenti. | `WorkoutRepository`, `WorkoutService` |
| B-03 | Messaggi italiani per `INVALID_EXERCISE_ORDER`, `WORKOUT_STATE_CHANGED`, `SET_RESULTS_WINDOW_CLOSED`, `SCHEDULE_DAYS_REQUIRED`, `RATE_LIMITED` (429), `UNPROCESSABLE`, `METHOD_NOT_ALLOWED`, `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `REQUEST_ERROR`; messaggio di `PROTECTED_ACCOUNT` aggiornato. | `ApiError.ts`, `messages.ts` |

### Prestazioni

| ID | Correzione | File |
| --- | --- | --- |
| P-01 | "Oggi": giorni e piano del giorno letti in blocco (nuova `CalendarQueries.weekdays(Collection)` + `ranges`). Prima: 3 query per ogni scheda attiva; ora un numero costante. | `TodayService`, `CalendarQueries`, `CalendarService` |
| P-02 | Avvio allenamento: piano del giorno calcolato una sola volta; la scheda è caricata una sola volta e i controlli "eliminata/eseguibile" usano la struttura già letta (`PlanStructure.requireExecutable()`, stessi codici d'errore). | `WorkoutService`, `PlanStructure` |
| P-03 | L'audio di fine recupero viene scaricato solo se il suono è attivo. | `useRestAlert.ts` |

### Clean code e warning

| ID | Correzione |
| --- | --- |
| C-01 | `AssignmentService.activate`: `switch` espressione, rimosso il codice irraggiungibile. |
| C-02 | Rimossi `WorkoutRepository.findByPlanAssignmentIdAndScheduledDate`, `findByUserId(Pageable)`, `Workout.lastCompletedSet()`, `WorkoutSummary.of()` (mai usati in produzione). |
| C-03 | Javadoc di `ActivityReportService` allineato (peso e ripetizioni ora sono registrati). |
| C-04 | `CalendarService`: `detail` tecnici in inglese come nel resto del backend; la logica dei giorni in blocco è in un unico metodo riusato (niente duplicazione). |
| W-01 | Eliminati i 7 warning `redundant cast` (`.<Object>read(...)` al posto di `(Object)`). |
| W-02 | Mockito caricato come `-javaagent` da Surefire: sparito il warning JDK sugli agenti dinamici. |
| W-03 | Stub di `scrollTo` e dei metodi media in `src/test/setup.ts`: niente più "Not implemented" nell'output dei test. |

### Test aggiunti

- `CalendarIntegrationTest.activatingAPendingAssignmentWithoutBodyDoesNotCopyDays` (B-01).
- `UserDeletionIntegrationTest`: rinomina dell'ADMIN iniziale rifiutata, modifica degli altri dati consentita, protezione ancora attiva (S-03).
- `frontend/src/shared/errors/messages.test.ts`: codici tradotti, 429 → `RATE_LIMITED` (B-03).
- `scripts/check-frontend-container.py`: nessuno script inline, `theme-init.js` servito, `Permissions-Policy`, 429 del login in formato Problem Details con `Retry-After` (S-01, S-02, S-05).

### Documentazione

- `docs/api.md`: 429 sul login, `PROTECTED_ACCOUNT` sul `PUT` utente, corpo assente su `activate`.
- `ANALISI_TECNICA_DELPY.md`: analisi completa (Task 1).

### Lasciato volutamente invariato

- Tabelle/colonne V15-V16 non usate (funzioni FUTURO conservate per lo storico Flyway).
- Token CSRF non rigenerato al login (rischio basso, richiede modifiche al client).
- Nessuna migrazione nuova, nessuna migrazione esistente modificata.

## 2. Verifiche eseguite

| Controllo | Prima (`delpy`) | Dopo (`fix-cloude`) |
| --- | --- | --- |
| `mvn verify` (Java 21, PostgreSQL 16 Testcontainers) | 267/267, 7 warning javac + warning agente | **268/268**, nessun warning |
| `npm run lint` | pulito | pulito |
| `npm run typecheck` | pulito | pulito |
| `npm test` | 152/152, output con "Not implemented" | **155/155**, output pulito |
| `npm run build` | ok | ok, nessun warning |
| `npm audit` | 0 vulnerabilità | 0 vulnerabilità |
| Container Nginx + `scripts/check-frontend-container.py` | — | **OK** (incluso rate limit) |
| `caddy validate` sul Caddyfile | — | **Valid configuration** |

Nota: nell'ambiente di verifica Maven è stato lanciato con `mvn` di sistema (3.9.11, stessa versione
del wrapper) perché il download del wrapper era bloccato dalla rete; il comando consigliato resta
`./mvnw verify`.

## 3. Come testare manualmente

Preparazione (dalla radice del progetto, con `.env` configurato):

```bash
git fetch origin fix-cloude && git checkout fix-cloude
docker compose up -d postgres
cd backend && set -a && source ../.env && set +a && ./mvnw spring-boot:run   # terminale 1
cd frontend && npm install && npm run dev                                     # terminale 2
```

Aprire http://localhost:5173. Servono un ADMIN e almeno un USER con una scheda eseguibile.

### 3.1 Attivazione assegnazione senza copia dei giorni (B-01)

1. Da USER scegliere dei giorni per una scheda; da ADMIN chiudere quella assegnazione.
2. Da ADMIN assegnare una nuova scheda allo stesso utente **senza** attivarla.
3. Attivarla senza corpo (DevTools → Console, essendo loggati come ADMIN):
   ```js
   const t = document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1];
   fetch('/api/admin/assignments/<ID>/activate', {method:'POST', headers:{'X-XSRF-TOKEN': decodeURIComponent(t)}}).then(r => r.json()).then(console.log)
   ```
4. Atteso: `copiedWeekdays: []`; da USER, in *Giorni*, la nuova scheda non ha giorni.
   Dall'interfaccia, con la casella "copia giorni" spuntata, la copia continua a funzionare.

### 3.2 ADMIN iniziale protetto (S-03)

1. *Utenti* → aprire l'ADMIN iniziale (quello di `GYM_ADMIN_USERNAME`) → *Modifica*.
2. Cambiare lo username e salvare. Atteso: errore "L'account ADMIN iniziale è protetto: non può
   essere eliminato né cambiare username."
3. Cambiare solo il nome e salvare. Atteso: salvataggio riuscito; l'eliminazione resta impossibile.

### 3.3 Tema senza violazioni CSP (S-02) e header (S-05)

Serve lo stack Docker con Nginx (`docker compose --profile app up -d --build`, poi
http://localhost:8081):

1. *Profilo* → tema **Scuro**, poi ricaricare la pagina con DevTools aperti.
2. Atteso: la pagina nasce già scura (nessun lampo chiaro) e la Console **non** mostra
   "Refused to execute inline script … Content Security Policy".
3. DevTools → Network → documento → Response Headers: presente `Permissions-Policy`.

### 3.4 Limite tentativi di login (S-01)

Con lo stack Docker (http://localhost:8081):

```bash
for i in $(seq 1 45); do curl -s -o /dev/null -w "%{http_code} " -X POST -H 'Content-Type: application/json' \
  -d '{"username":"nessuno","password":"sbagliata1"}' http://localhost:8081/api/auth/login; done; echo
```

Atteso: prima una serie di `401` (o `403` CSRF), poi `429`. Dalla pagina di login, dopo il blocco,
compare "Troppi tentativi in poco tempo. Attendi un minuto e riprova."; dopo un minuto il login torna
disponibile. In produzione l'header HSTS si verifica con `curl -sI https://<dominio> | grep -i strict`.

### 3.5 Messaggi in italiano (B-03)

1. Da USER avviare un allenamento e aprirlo in **due schede** del browser.
2. Nella scheda A riordinare gli esercizi; nella scheda B (vecchia) riordinarli di nuovo.
   Atteso: messaggio italiano "La schermata è stata aggiornata…" e lista riallineata, mai testo inglese.

### 3.6 Chiusura assegnazione durante l'allenamento (B-02)

1. Da USER avviare l'allenamento e completare una serie.
2. Da ADMIN chiudere l'assegnazione di quella scheda.
3. Da USER premere "Fine serie" o ricaricare. Atteso: l'allenamento risulta **Interrotto**, con le
   serie già fatte salvate; nessun errore 500.

### 3.7 "Oggi", avvio e audio (P-01, P-02, P-03)

1. Utente con **due o tre schede attive** su giorni diversi: *Oggi* mostra la scheda giusta per il
   giorno, il prossimo allenamento e gli avvisi di durata come prima.
2. Avviare l'allenamento del giorno: si apre regolarmente. Con una scheda svuotata di sessioni
   l'avvio risponde "La scheda non è eseguibile…" come prima.
3. In allenamento, con il suono **disattivato**, DevTools → Network → filtro "mp3": nessun download.
   Attivando il suono il file viene scaricato e suona.

### 3.8 Controlli automatici

```bash
cd backend && ./mvnw verify          # 268 test, nessun warning di compilazione
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
# Container Nginx (vedi docs/report-veloce.md):
docker build -t gym-frontend-check frontend
docker run -d --name gym-frontend-check --add-host gym-planner-backend:127.0.0.1 -p 127.0.0.1:18080:80 gym-frontend-check
python3 scripts/check-frontend-container.py && docker rm -f gym-frontend-check
```
