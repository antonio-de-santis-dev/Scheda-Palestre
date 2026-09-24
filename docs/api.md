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
