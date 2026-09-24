# Registro di avanzamento degli incrementi

| Incremento | Branch | Stato | User story | Verifica |
| --- | --- | --- | --- | --- |
| 0 - Fondamenta | `chore/increment-0-foundation` | Completato | — | Backend 19 test, frontend 10 test, build OK, login/logout manuale con curl |

## Incremento 0 - Fondamenta

- Repository con `backend/` (Spring Boot 4.0.8, Java 21, Maven Wrapper) e `frontend/`
  (React 19 + Vite 8 + TypeScript strict).
- PostgreSQL in `compose.yaml`; migrazione `V1__create_users.sql`.
- Moduli `identity` e `shared`; Problem Details RFC 9457 con codice applicativo.
- Spring Security: sessione server, CSRF SPA, blocco dopo 5 tentativi per 15 minuti,
  cambio password obbligatorio, invalidazione delle sessioni tramite `session_version`.
- Bootstrap del primo ADMIN da `GYM_ADMIN_*` (avvio interrotto se mancano le variabili).
- Frontend: login, cambio password, guardie di routing, layout ADMIN/USER, client HTTP con CSRF.
- Test: ArchUnit (confini modulari), integrazione con Testcontainers (auth, blocco, CSRF, 401/403).

**Prova manuale.** Avviare PostgreSQL e il backend con `GYM_ADMIN_*`, poi il frontend.
Accedere come `admin`: viene chiesto il cambio password; dopo il cambio si arriva alla dashboard.
