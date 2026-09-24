# API REST

Prefisso `/api`. Tutte le risposte d'errore sono Problem Details (RFC 9457,
`application/problem+json`) con il campo `code` (codice applicativo stabile) e, per gli errori
di validazione, `errors: [{field, message}]`.

Le richieste che modificano dati (`POST`, `PUT`, `DELETE`) richiedono l'header `X-XSRF-TOKEN`
uguale al cookie `XSRF-TOKEN` (ottenibile con `GET /api/auth/csrf`).

## Autenticazione

| Metodo | Endpoint | Accesso | Risposte |
| --- | --- | --- | --- |
| GET | `/api/auth/csrf` | pubblico | 200 `{headerName, token}` + cookie `XSRF-TOKEN` |
| POST | `/api/auth/login` | pubblico | 200 utente corrente; 401 `INVALID_CREDENTIALS` (sempre generico) |
| POST | `/api/auth/logout` | autenticato | 204 |
| GET | `/api/auth/me` | autenticato | 200 `{id, username, firstName, lastName, email, role, mustChangePassword}` |
| POST | `/api/auth/change-password` | autenticato | 200 utente; 400 `VALIDATION_ERROR` (`currentPassword`, `newPassword`) |

Con `mustChangePassword = true` gli altri endpoint rispondono 403 `PASSWORD_CHANGE_REQUIRED`.

## Codici HTTP e codici applicativi comuni

| HTTP | `code` |
| --- | --- |
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST` |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS` |
| 403 | `FORBIDDEN`, `PASSWORD_CHANGE_REQUIRED`, `CSRF_INVALID` |
| 404 | `NOT_FOUND` (risorsa inesistente **o** non accessibile) |
| 409 | `CONFLICT`, `CONCURRENT_MODIFICATION` e codici di duplicato specifici |
| 422 | codici di regola di dominio specifici |

## Account (ADMIN)

| Metodo | Endpoint | Funzione | Risposte |
| --- | --- | --- | --- |
| GET | `/api/admin/users?q=&role=&active=&page=&size=` | elenco paginato e ricerca (nome, username, email) | 200 `Page<User>` |
| POST | `/api/admin/users` | crea USER (ruolo sempre USER) | 201 `{user, temporaryPassword}`; 409 `USERNAME_TAKEN`/`EMAIL_TAKEN` |
| GET | `/api/admin/users/{id}` | dettaglio | 200; 404 |
| PUT | `/api/admin/users/{id}` | modifica nome, cognome, username, email, telefono | 200; 409 |
| POST | `/api/admin/users/{id}/activate` | riattiva | 200 |
| POST | `/api/admin/users/{id}/deactivate` | disattiva e invalida le sessioni | 200; 422 `CANNOT_DEACTIVATE_SELF`, `LAST_ACTIVE_ADMIN` |
| POST | `/api/admin/users/{id}/reset-password` | password temporanea mostrata una sola volta | 200 `{user, temporaryPassword}` |

Corpo di creazione/modifica:

```json
{ "firstName": "Mario", "lastName": "Rossi", "username": "mario", "email": "mario@example.test", "phone": null }
```

`User`: `{id, firstName, lastName, username, email, phone, role, active, mustChangePassword, locked, createdAt, updatedAt}`.
Il campo `passwordHash` non è mai esposto. `Page<T>`: `{content, page, size, totalElements, totalPages}`.

## Cataloghi (ADMIN)

Stessa forma per `muscle-groups` ed `exercises`:

| Metodo | Endpoint | Funzione | Risposte |
| --- | --- | --- | --- |
| GET | `/api/admin/{catalog}?q=&active=&page=&size=` | ricerca paginata (attivi e disattivati) | 200 `Page<Item>` |
| POST | `/api/admin/{catalog}` | crea `{name}` | 201; 409 `NAME_TAKEN` |
| PUT | `/api/admin/{catalog}/{id}` | rinomina `{name}` | 200; 409 `NAME_TAKEN`; 404 |
| POST | `/api/admin/{catalog}/{id}/activate` | riattiva | 200 |
| POST | `/api/admin/{catalog}/{id}/deactivate` | disattiva (cancellazione logica) | 200 |

`Item`: `{id, name, active, createdAt, updatedAt}`. I nomi sono univoci senza distinzione fra
maiuscole e minuscole e gli spazi multipli sono normalizzati. Un elemento disattivato resta visibile nelle schede
che lo usano, ma non può essere inserito in nuove configurazioni (`422 CATALOG_ITEM_INACTIVE`).

## Schede (ADMIN)

Tutte le operazioni di struttura restituiscono la **scheda completa aggiornata** (`PlanStructure`).

| Metodo | Endpoint | Funzione | Errori principali |
| --- | --- | --- | --- |
| GET | `/api/admin/plans?q=&deleted=false&page=&size=` | elenco (attive o eliminate) | |
| POST | `/api/admin/plans` | crea `{name, description?, expiresOn?}` | 400 |
| GET | `/api/admin/plans/{id}` | struttura completa | 404 |
| PUT | `/api/admin/plans/{id}` | metadati `{name, description, expiresOn, version}` | 409 `CONCURRENT_MODIFICATION`, 422 `PLAN_DELETED` |
| DELETE | `/api/admin/plans/{id}` | eliminazione logica (chiude le assegnazioni attive) | 204 |
| POST | `/api/admin/plans/{id}/restore` | ripristino | |
| POST | `/api/admin/plans/{id}/duplicate` | copia profonda senza assegnazioni (`copiedFromPlanId`) | 201 |
| POST | `/api/admin/plans/{id}/sessions` | aggiunge sessione `{title}` | 201 |
| PUT | `/api/admin/sessions/{id}` | rinomina `{title}` | |
| DELETE | `/api/admin/sessions/{id}` | elimina (e rinumera) | |
| PUT | `/api/admin/plans/{id}/sessions/order` | riordino atomico `{ids:[...]}` | 422 `INVALID_ORDER` |
| POST | `/api/admin/sessions/{id}/sections` | aggiunge sezione `{muscleGroupId}` | 409 `MUSCLE_GROUP_ALREADY_IN_SESSION`, 422 `CATALOG_ITEM_INACTIVE` |
| DELETE | `/api/admin/sections/{id}` | elimina sezione | |
| PUT | `/api/admin/sessions/{id}/sections/order` | riordina sezioni | 422 `INVALID_ORDER` |
| POST | `/api/admin/sections/{id}/exercises` | aggiunge esercizio configurato | 400, 422 `CATALOG_ITEM_INACTIVE` |
| PUT | `/api/admin/plan-exercises/{id}` | sostituisce la configurazione completa (serie personalizzate incluse) | 400 `INVALID_CUSTOM_SETS` |
| DELETE | `/api/admin/plan-exercises/{id}` | elimina configurazione | |
| PUT | `/api/admin/sections/{id}/exercises/order` | riordina esercizi | 422 `INVALID_ORDER` |

Esercizio configurato:

```json
{
  "exerciseId": "uuid", "setsCount": 3, "reps": 10, "toFailure": false, "restSeconds": 90,
  "customSets": [
    {"setIndex": 1, "reps": 12, "toFailure": false, "restSeconds": 60},
    {"setIndex": 2, "reps": 10, "toFailure": false, "restSeconds": 90},
    {"setIndex": 3, "reps": 0,  "toFailure": true,  "restSeconds": 0}
  ]
}
```

Regole: `toFailure=true` implica `reps=0` (mostrato come **MAX**), altrimenti `reps` 1-100;
`setsCount` 1-20; `restSeconds` 0-600 (0 = nessun timer); `customSets` vuoto oppure
esattamente `setsCount` righe con indici da 1 a N.

`PlanStructure`: `{id, name, description, expiresOn, createdBy, copiedFromPlanId, createdAt,
updatedAt, deletedAt, version, executable, sessions:[{id, title, position, sections:[{id,
muscleGroupId, muscleGroupName, muscleGroupActive, position, exercises:[{id, exerciseId,
exerciseName, exerciseActive, position, setsCount, reps, toFailure, restSeconds, customized,
sets:[{setIndex, reps, toFailure, restSeconds}]}]}]}]}`. `sets` contiene sempre le N serie effettive.

## Assegnazioni (ADMIN)

| Metodo | Endpoint | Funzione | Errori principali |
| --- | --- | --- | --- |
| GET | `/api/admin/plans/{id}/assignments` | assegnatari della scheda | 404 |
| GET | `/api/admin/users/{id}/assignments` | assegnazioni dell'utente | 404 |
| POST | `/api/admin/assignments` | assegna a uno o più USER (tutto o niente) | 422 `USER_NOT_ASSIGNABLE`, `PLAN_NOT_EXECUTABLE`, `PLAN_DELETED`; 409 `ASSIGNMENT_ALREADY_ACTIVE` |
| POST | `/api/admin/assignments/{id}/activate` | attiva un'assegnazione in attesa `{copySchedule?}` | 422 `ASSIGNMENT_CLOSED`, `PLAN_NOT_EXECUTABLE` |
| POST | `/api/admin/assignments/{id}/close` | chiude (`active=false`, `endDate`) | |

```json
{ "planId": "uuid", "userIds": ["uuid-1", "uuid-2"], "startDate": "2026-10-01", "activate": true, "copySchedule": true }
```

L'attivazione avviene in un'unica transazione: chiude l'eventuale assegnazione attiva precedente
(e interrompe l'allenamento in corso collegato), attiva la nuova, imposta l'ancoraggio della rotazione
al giorno più tardo fra `startDate` e oggi e, con `copySchedule` (O-07, default `true`), copia i giorni
settimanali della precedente.

`Assignment`: `{id, userId, userFullName, username, planId, planName, planDeleted, startDate, endDate, active, status, createdAt}`
con `status` ∈ `PENDING`, `ACTIVE`, `CLOSED`.

## Area USER - schede

| Metodo | Endpoint | Funzione |
| --- | --- | --- |
| GET | `/api/me/assignments` | le proprie assegnazioni (attiva riconoscibile da `status`) |
| GET | `/api/me/assignments/{id}/plan` | struttura della scheda in sola lettura; 404 se l'assegnazione non è propria |

## Area USER - giorni di allenamento

| Metodo | Endpoint | Funzione | Errori |
| --- | --- | --- | --- |
| GET | `/api/me/schedule` | giorni correnti `{assignmentId, weekdays}` (`assignmentId` null senza scheda attiva) | |
| PUT | `/api/me/schedule` | sostituisce i giorni `{weekdays:[1,3,5]}` (ISO: 1=lunedì … 7=domenica) e ri-ancora la rotazione | 400 `VALIDATION_ERROR`, 422 `NO_ACTIVE_ASSIGNMENT` |

### Rotazione e ri-ancoraggio

La sessione di un giorno `d` è `S[(i0 + k) mod N]`, dove `k` è il numero di giorni pianificati fra
l'ancora `a` (inclusa) e `d` (esclusa), calcolato come settimane intere × giorni scelti più il resto
(`RotationCalculator`). Un giorno pianificato consuma la sessione anche se non viene svolto (O-01).

Il ri-ancoraggio (cambio dei giorni, sessioni aggiunte/eliminate/riordinate, nuova attivazione)
sposta l'ancora a oggi, oppure a domani se oggi era già un giorno di allenamento, e mantiene come
prossima la stessa sessione che sarebbe stata proposta. Gli allenamenti esistenti non cambiano.

## Area USER - oggi, calendario ed esecuzione

| Metodo | Endpoint | Funzione | Errori principali |
| --- | --- | --- | --- |
| GET | `/api/me/today?date=YYYY-MM-DD` | giornata: `status` ∈ `NO_ACTIVE_ASSIGNMENT`, `NOT_STARTED_YET`, `NO_SCHEDULE`, `PLAN_NOT_READY`, `REST_DAY`, `TRAINING_DAY`; anteprima `session`, `workout` del giorno, `pendingWorkout` (O-04), `nextTraining`, `canStart` | |
| GET | `/api/me/calendar?from=&to=` | giorni (`TRAINING`/`REST`/`NONE`) con sessione ed esito; massimo 62 giorni | 400 `RANGE_TOO_LARGE`, `VALIDATION_ERROR` |
| POST | `/api/me/workouts` | avvia l'allenamento pianificato `{date}` e crea lo snapshot | 422 `NO_ACTIVE_ASSIGNMENT`, `DATE_NOT_ALLOWED`, `NOT_A_TRAINING_DAY`, `PLAN_NOT_EXECUTABLE`; 409 `WORKOUT_ALREADY_EXISTS`, `WORKOUT_ALREADY_IN_PROGRESS` |
| GET | `/api/me/workouts/current` | allenamento in corso (204 se nessuno) | |
| GET | `/api/me/workouts/{id}` | stato completo di un proprio allenamento | 404 |
| POST | `/api/me/workouts/{id}/sets/{setId}/complete` | **Fine serie**, idempotente | 422 `SET_NOT_CURRENT`, `WORKOUT_NOT_IN_PROGRESS`; 404 |
| POST | `/api/me/workouts/{id}/exercises/{exerciseId}/skip` | salta l'esercizio in corso (idempotente) | 422 `EXERCISE_NOT_IN_PROGRESS` |
| POST | `/api/me/workouts/{id}/interrupt` | interrompe (idempotente) | 422 `WORKOUT_NOT_IN_PROGRESS` |

Ogni risposta di esecuzione restituisce lo stato completo:

```json
{
  "workoutId": "uuid", "status": "IN_PROGRESS", "scheduledDate": "2026-10-05",
  "planName": "Scheda principianti", "sessionTitle": "Giorno 1",
  "startedAt": "...", "finishedAt": null,
  "exercises": [{"id": "uuid", "position": 1, "status": "IN_PROGRESS", "exerciseName": "Panca piana",
                 "muscleGroupName": "Petto", "setsPlanned": 3, "setsCompleted": 1,
                 "sets": [{"id": "uuid", "setIndex": 1, "repsPlanned": 10, "toFailure": false,
                           "restSeconds": 90, "completedAt": "2026-10-05T17:29:30Z"}]}],
  "currentExerciseId": "uuid", "currentSetId": "uuid",
  "restEndsAt": "2026-10-05T17:31:00Z", "restSeconds": 90,
  "serverTime": "2026-10-05T17:30:15Z", "nextAction": "WAIT_FOR_REST"
}
```

`nextAction` ∈ `COMPLETE_SET`, `WAIT_FOR_REST` (il timer è informativo, O-06), `FINISHED`.
