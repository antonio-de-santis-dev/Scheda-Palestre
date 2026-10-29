# Prima tranche Clean Code

Base: `delpy`, commit `74458100d3f64b6c26b48963cb485278ed5839fd`.
Branch: `fix/clean-code`. Il nome `fix` è incompatibile con il branch remoto già esistente `fix/nginx-forwarded-host`.

## Modifiche

- Lombok gestito dal BOM Spring Boot, annotation processor esplicito, escluso dal jar eseguibile. `@RequiredArgsConstructor` sostituisce 27 costruttori che assegnavano soltanto dipendenze, preservandone la visibilità. Costruttori con logica, ereditarietà ed entity restano espliciti. La constructor injection e i campi `final` rimangono; questa modifica riduce boilerplate, non il tempo di esecuzione.
- La form dei metadati mantiene i campi modificati quando una risposta aggiorna struttura/versione della scheda. Un cambio di scheda rimonta la form per ID.
- Gli errori di rinomina, aggiunta sezioni e riordino esercizi/sezioni vengono consumati dalle promise e mostrati dall'editor; la bozza della rinomina resta disponibile per riprovare.
- Il timer libera l'intervallo a scadenza, notifica una volta per recupero anche dopo un refetch e aggiorna lo stato solo quando cambia il secondo visualizzato.
- I filtri degli esercizi azzerano la pagina; il campo ricerca gruppi segue la URL anche tornando indietro.
- Le letture di catalogo e schede ADMIN inoltrano l'AbortSignal di TanStack Query fino a fetch.
- Il bootstrap CSRF ha un timeout di 15 secondi e verifica lo status HTTP; gli errori di bootstrap impediscono l'invio della mutazione e non bloccano le richieste successive. Le mutazioni conservano il proprio timeout separato.
- Entrambi i Compose assegnano al backend l'alias DNS atteso da Nginx (`gym-planner-backend`).
- Nuova lettura pubblica in batch delle sessioni, con proiezione dei soli campi necessari: elenco calendari e report caricano le sessioni con una query anziché una per scheda/assegnazione. Piani senza sessioni restituiscono liste vuote. Nessun cambiamento alle regole di rotazione.
- Workflow GitHub Actions: test backend completi con PostgreSQL Testcontainers, lint/test/build frontend e validazione Compose. Si attiva sui push `fix/**`, sulle PR verso `delpy` e manualmente. Non distribuisce l'applicazione.

## Verifiche

- Frontend: 85 test passati (13 file), ESLint passato, build TypeScript/Vite passata.
- YAML di entrambi i Compose letto e controllato per alias e dipendenze; validazione Docker completa demandata al workflow.
- Nuovi test backend: budget di una chiamata al repository per più piani, ordine delle sessioni, piani vuoti e richiesta vuota senza accesso al DB.
- Backend: `./mvnw --batch-mode verify` passato in GitHub Actions con Java 21: **208 test, 0 errori, 0 fallimenti, 0 saltati**, inclusi PostgreSQL/Testcontainers, migrazioni e ArchUnit. Run di verifica del codice: `36986341466`, commit `44833128666538f613b61f5d0aa5e10f34590656`. Il workflow di validazione Compose usa valori dimostrativi per tutte le variabili obbligatorie in entrambi gli ambienti.

Comandi:

```sh
cd frontend
npm ci
npm run lint
npm test -- --maxWorkers=2
npm run build
```

```sh
cd backend
./mvnw --batch-mode verify
```

## Lavoro successivo

| Area | Stato |
| --- | --- |
| Perdita bozza metadati, alias Compose, timer, filtri catalogo, bootstrap CSRF | Corretti in questa tranche |
| Query ripetute di calendario e report | Sessioni raggruppate; letture dei giorni e vista Oggi ancora da raggruppare |
| Constructor injection | Boilerplate sostituito con Lombok dove equivalente |
| Editor | Promise gestite nei percorsi individuati; blocco uniforme delle azioni durante il salvataggio da completare |
| Cancellazione richieste | Applicata alle query ADMIN di catalogo/schede; da estendere agli altri moduli |
| Concorrenza assegnazioni/allenamenti/ri-ancoraggio e tentativi di login | Da correggere con test concorrenti PostgreSQL |
| SQL di cancellazione catalogo che scrive nelle tabelle workoutplan | Da spostare nel modulo proprietario, preservando transazione e snapshot |
| Disattivazione multipla catalogo, selettori oltre 200 risultati | Da correggere |
| Liste che idratano aggregati, componenti grandi, infrastruttura multi-replica | Da affrontare separatamente con misure e test mirati |

Nessun benchmark end-to-end è stato eseguito: la riduzione delle chiamate per le sessioni non quantifica la latenza dell'intera applicazione. Non sono state modificate migrazioni o dati di produzione.
