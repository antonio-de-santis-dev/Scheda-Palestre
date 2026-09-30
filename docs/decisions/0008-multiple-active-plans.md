# ADR 0008 - Più schede attive per utente, giorni senza sovrapposizione

**Problema.** Fino a V7 la regola era "una sola scheda attiva per USER" (indice
`ux_plan_assignments_active_user`): attivare una nuova scheda chiudeva la precedente e interrompeva
l'allenamento in corso. Ora uno USER può seguire più schede insieme (es. "Forza" lunedì e venerdì,
"Cardio" martedì e giovedì). Vincoli richiesti:

- nessuna seconda assegnazione **attiva** della **stessa scheda** allo stesso utente;
- per un utente, ogni giorno della settimana appartiene a **una sola** scheda attiva;
- i giorni si configurano **per assegnazione**;
- resta **un solo allenamento in corso** per utente;
- il controllo deve reggere richieste simultanee (più schede del browser, più dispositivi).

**Decisione sul database (V8).** `ux_plan_assignments_active_user` è sostituito da
`ux_plan_assignments_active_user_plan (user_id, workout_plan_id) WHERE active`. La migrazione
verifica prima che non esistano duplicati: non possono esistere, perché l'indice precedente era più
restrittivo, ma se ci fossero la migrazione si fermerebbe con un messaggio esplicito invece di
fallire a metà. `ux_workouts_user_in_progress` resta invariato.

**Concorrenza sui giorni: alternative.**
1. **Lock per utente nella transazione** — **scelta.** `pg_advisory_xact_lock` su una chiave
   derivata dall'id utente (`shared.concurrency.UserLock`), poi rilettura delle schede attive e dei
   loro giorni dentro il lock, verifica e scrittura. Il lock è rilasciato da commit o rollback, è
   rientrante nella stessa transazione e non blocca gli altri utenti.
2. Denormalizzare `user_id` e un flag "attiva" in `weekly_schedules` con un indice unico parziale
   `(user_id, weekday) WHERE attiva`. È più forte (lo garantisce il DB), ma obbliga a tenere il flag
   coerente a ogni attivazione, chiusura ed eliminazione della scheda, in un altro modulo.
   Scartata per semplicità. Resta un'evoluzione possibile.

Un controllo `SELECT` seguito da `INSERT` senza lock non basta: due richieste leggono entrambe
"giorno libero" e scrivono entrambe. `CalendarIntegrationTest.concurrentRequestsCannotBothTakeTheSameDay`
lancia due thread in parallelo per 5 volte. Verifica ogni volta un 200 e un 409 e una sola riga nel
DB. Senza il lock lo stesso test fallisce (controprova fatta durante lo sviluppo).

Lo stesso lock protegge l'attivazione, che rifiuta una seconda attivazione della stessa scheda con
`409 ASSIGNMENT_ALREADY_ACTIVE`. L'indice unico resta comunque come rete di sicurezza.

**API.**
- `GET /api/me/schedules`: tutte le schede attive con i loro giorni, così l'interfaccia mostra
  quale scheda occupa quale giorno.
- `PUT /api/me/assignments/{id}/schedule {weekdays}`. Risposte possibili:
  - 404 se l'assegnazione non è dell'utente;
  - `422 ASSIGNMENT_NOT_ACTIVE` se l'assegnazione è chiusa o in attesa;
  - `409 SCHEDULE_DAY_CONFLICT` con `conflicts: [{weekday, assignmentId, planName}]`.
- Il vecchio `GET/PUT /api/me/schedule` è rimosso (unico client: il frontend).

**`copySchedule` (sostituisce O-07).** Non esiste più una scheda "precedente" sostituita.
Il comportamento diventa:
- **Default `false`**: non si copia nulla.
- Con `true`, si copiano solo i giorni dell'**ultima scheda chiusa** dell'utente che sono
  **ancora liberi**. I giorni occupati non vengono mai copiati e sono riportati in
  `skippedWeekdays` della risposta di assegnazione (`copiedWeekdays` per quelli copiati).
- La copia passa da una porta (`assignment.api.ScheduleCopyPort`) implementata dal modulo
  calendar, così `assignment` non dipende da `calendar` (ArchUnit invariato).

**Servizi.**
- `AssignmentQueries.listActiveForUser` restituisce una lista; l'`Optional` che nascondeva
  l'assunzione è stato eliminato.
- "Oggi", calendario e avvio dell'allenamento risolvono la scheda **che allena in quella data** a
  partire dai giorni. Al massimo una lo fa, per la regola sui giorni.
- Senza scheda per la data, "Oggi" riporta lo stato più informativo: riposo, poi non ancora
  iniziata, poi in preparazione, poi senza giorni. Riporta anche il prossimo allenamento fra tutte
  le schede e l'elenco `plansWithoutDays`.
- Rotazione e storico sono per assegnazione: l'ancora di rotazione vive in `plan_assignments`.
- Chiudere o eliminare una scheda chiude solo le sue assegnazioni e interrompe solo i suoi
  allenamenti.

**Modifica dei giorni durante un allenamento in corso.** L'allenamento appartiene alla propria
assegnazione e prosegue normalmente. I nuovi giorni valgono per le date future: la rotazione è
riancorata come prima (spec 10.6). Lo verifica
`ExecutionIntegrationTest.changingDaysDuringAWorkoutKeepsTheWorkoutRunning`.

**Conseguenze.**
- La scelta dei giorni non è ottimistica: decide il server, il frontend invalida calendario,
  "Oggi" e schede.
- I giorni occupati da un'altra scheda restano raggiungibili da tastiera (`aria-disabled`) e
  riportano per iscritto il motivo ("Occupato da …").
