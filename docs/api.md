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
