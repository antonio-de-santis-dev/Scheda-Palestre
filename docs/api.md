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
