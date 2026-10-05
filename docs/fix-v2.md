# FixV2 — miglioramenti per passi

Base richiesta: `fixV2` da `4f1f997`. Ogni passo viene provato dal proprietario prima di iniziare il successivo. Nessun deploy prima della fine.

| Passo | Funzione | Stato |
|---|---|---|
| 1 | Riordinare gli esercizi durante l’allenamento | Implementato, in verifica |
| 2 | Esercizi completati in fondo alla lista | Da fare dopo la prova del passo 1 |
| 3 | Durata allenamento da timestamp backend, nello storico | Da fare |
| 4 | Recupero persistente: pausa/ripresa, +30s, salto confermato, controllo versione | Da fare |
| 5 | Storico filtrabile con filtri URL, risultati peso/ripetizioni, statistiche e record | Da fare |
| Finale | Verifiche complete e deploy | Da fare |

## Passo 1: ordine durante l’esecuzione

Nel percorso Esercizi compaiono Su/Giù sugli esercizi ancora da svolgere. Il primo fra gli incompleti diventa quello corrente. È possibile cambiare prima di iniziare oppure fra le serie, anche durante il recupero. Cambiare ordine non sospende il recupero e non cancella alcuna serie. Tornando a un esercizio parzialmente svolto si riparte dalla prima serie incompleta. Completati/saltati restano chiusi.

Il cambiamento riguarda soltanto questo allenamento: la scheda condivisa dell’ADMIN resta invariata. Le posizioni degli incompleti vengono scambiate negli spazi già disponibili; i completati non vengono automaticamente spostati in questa tranche (passo 2).

### API e persistenza

`POST /api/me/workouts/{id}/exercises/reorder`:

```json
{"exerciseIds":["UUID-B","UUID-A"],"expectedVersion":0}
```

La lista contiene esattamente tutti gli esercizi TODO/IN_PROGRESS, senza duplicati. `WorkoutState.executionVersion` aumenta con riordino, completamento serie, salto, interruzione o chiusura assegnazione. La revisione è controllata sotto lock pessimistico del workout. Una replica della stessa ultima modifica non modifica nuovamente l’ordine; uno stato superato restituisce 409 `WORKOUT_STATE_CHANGED` e il frontend risincronizza. Ordini non validi restituiscono 400 `INVALID_EXERCISE_ORDER`. Le risorse altrui restano 404, ADMIN non può usare gli endpoint USER. CSRF resta obbligatorio.

Migrazione additiva `V12__workout_execution_order.sql`: revisioni iniziali 0 e vincolo posizione DEFERRABLE per scambi atomici. Prima di attivare il nuovo esercizio viene eseguito un flush del precedente, conservando il vincolo “un solo IN_PROGRESS”.

**V12 di SviluppoV2 è un’altra migrazione.** I prossimi passi non devono copiare ciecamente migrazioni V12–V16 da quel branch: su fixV2 aggiungere versioni successive, senza modificare V12 dopo la sua applicazione. Questa tranche va provata su un database della linea delpy/fixV2 arrivato a V11, non su un DB già migrato con SviluppoV2.

### Verifica automatica

- Frontend: tre nuove prove per ordine persistito/refresh, esercizio corrente e serie/recupero conservati; risincronizzazione 409; blocco azioni durante il salvataggio e workout chiusi.
- Integrazioni PostgreSQL: ordine persistito e ripresa di un esercizio parziale; scheda originale immutata; retry; revisione obsoleta; esecuzione del nuovo primo esercizio; impossibilità di riaprire completati; input non valido e isolamento utenti/ruoli; riordini identici e differenti concorrenti.
- Workflow Verify attivato anche sui push `fixV2`, senza deploy.

### Prova locale

Dalla root del progetto:

```bash
git fetch origin
git switch fixV2
git pull --ff-only origin fixV2
docker compose up -d postgres
```

Primo terminale (Java 21 e `.env` locale già configurato):

```bash
cd backend
./mvnw spring-boot:run
```

Secondo terminale:

```bash
cd frontend
npm ci
npm run dev
```

1. Aprire una sessione con almeno due esercizi e spostare il secondo in cima con Su. Verificare il cambio dell’esercizio corrente.
2. Completare una serie di un esercizio con più serie, spostarlo sotto un altro e verificare che il recupero non sparisca né riparta.
3. Ricaricare: ordine e serie svolte devono restare. Riportare l’esercizio parziale in cima: deve proporre la serie successiva.
4. Finire l’esercizio scelto: deve avanzare secondo il nuovo ordine. Completati/saltati non devono avere pulsanti di riordino.
5. Aprire lo stesso allenamento in due schede: salvare un ordine nella prima, poi nella seconda. La seconda deve allinearsi allo stato recente, senza sovrascrivere un ordine obsoleto.
6. Controllare che l’ADMIN veda la scheda originale e provare i pulsanti anche da telefono.

L’applicazione della migrazione è automatica all’avvio backend tramite Flyway; non eliminare il database per provarla.
