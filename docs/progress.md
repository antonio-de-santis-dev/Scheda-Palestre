# Registro di avanzamento degli incrementi

| Incremento | Branch | Stato | User story | Verifica |
| --- | --- | --- | --- | --- |
| 0 - Fondamenta | `chore/increment-0-foundation` | Completato | — | Backend 19 test, frontend 10 test, build OK, login/logout manuale con curl |
| 1 - Account e cataloghi | `feat/identity-accounts`, `feat/catalog-management` | Completato | US-01, US-02, US-25, US-04, US-05, US-21 (ruoli), US-22 | Backend 67 test, frontend 19 test, lint/build OK |
| 2 - Schede e assegnazioni | `feat/workout-plans`, `feat/plan-assignments` | Completato | US-06, US-07, US-08, US-09, US-10, US-11, US-12, US-13, US-21 (assegnazioni); anticipate US-14 e US-26 lato backend/editor | Backend 96 test, frontend 33 test, lint/build OK |

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

## Incremento 1 - Account e cataloghi

- ADMIN: elenco utenti paginato con ricerca e filtro di stato, creazione di account USER con
  password temporanea mostrata una sola volta, modifica, disattivazione (con invalidazione
  immediata delle sessioni), riattivazione e reset password. Non può disattivare sé stesso e
  deve sempre restare un ADMIN attivo.
- Cataloghi di gruppi muscolari ed esercizi: ricerca, creazione, rinomina, attivazione e
  disattivazione, con unicità dei nomi senza distinzione fra maiuscole e minuscole (anche a livello di database).
- Migrazione `V2__create_catalog.sql`.
- Errori chiari (US-22): codici applicativi tradotti in italiano ed errori mostrati accanto ai
  campi, conservando i dati inseriti.

**Prova manuale.** Da ADMIN: *Utenti → Nuovo utente*, annotare la password temporanea, uscire,
accedere con il nuovo utente e cambiare la password. Da ADMIN: *Gruppi* ed *Esercizi*, creare
"Petto" e "Panca piana", poi provare a ricrearli in minuscolo (errore "Nome già in uso").

## Incremento 2 - Schede e assegnazioni

- Migrazioni `V3__create_workout_plans.sql` e `V4__create_plan_assignments.sql`.
- Editor ADMIN completo: sessioni, sezioni muscolari, esercizi configurati (serie, ripetizioni,
  MAX, recupero), riordino atomico con pulsanti accessibili, eliminazione con conferma, avviso
  sugli assegnatari attivi, controllo ottimistico della versione sui metadati.
- Serie personalizzate (US-14) e duplicazione profonda (US-26) sono state implementate insieme
  all'editor perché fanno parte dello stesso modello (`PlanExercise`/`PlanSet`); sono verificate
  da test dedicati. Restano registrate nell'Incremento 4 della specifica.
- Eliminazione logica e ripristino: l'eliminazione chiude le assegnazioni attive.
- Assegnazione a più utenti (un record per utente, tutto o niente), attivazione con chiusura
  della precedente, una sola assegnazione attiva per USER garantita anche dal database.
- USER: "Le mie schede" e dettaglio in sola lettura; le risorse di altri utenti rispondono 404.

**Prova manuale.** Creare gruppi ed esercizi, poi *Schede → Nuova scheda*, aggiungere "Giorno 1"
e "Giorno 2" con almeno un esercizio ciascuno (uno a cedimento: compare "MAX"). *Assegna* a due
utenti. Accedere come uno dei due utenti: *Schede* mostra la scheda attiva in sola lettura.
