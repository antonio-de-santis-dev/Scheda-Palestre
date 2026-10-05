# FixV2 — miglioramenti per passi

Base richiesta: `fixV2` da `4f1f997`. Ogni passo viene provato dal proprietario prima di iniziare il successivo. I passi 1–2 sono stati provati dal proprietario e pubblicati tramite delpy (merge b23220d); i passi successivi vengono sviluppati e verificati separatamente su fixV2.

| Passo | Funzione | Stato |
|---|---|---|
| 1 | Riordinare gli esercizi durante l’allenamento | Verificato e pubblicato su delpy |
| 2 | Esercizi completati in fondo alla lista | Verificato e pubblicato su delpy |
| 3 | Durata allenamento da timestamp backend, nello storico | Implementato, in verifica |
| 4 | Recupero persistente: pausa/ripresa, +30s, salto confermato, controllo versione | Da fare |
| 5 | Storico filtrabile con filtri URL, risultati peso/ripetizioni, statistiche e record | Da fare |
| Finale | Verifiche complete e deploy | Da fare |

## Passo 3: durata definitiva dell’allenamento

Le risposte di esecuzione e i riepiloghi dello storico includono `durationSeconds`: secondi interi fra `startedAt` e `finishedAt`, calcolati dal backend. I due timestamp sono già persistiti e costituiscono la fonte della durata anche dopo riavvio, refresh o apertura da un altro dispositivo. Non occorrono una nuova migrazione né un contatore del browser. Il tempo include recuperi e periodi trascorsi fuori dalla pagina; termina quando l’allenamento viene completato o interrotto, compresa la chiusura dell’assegnazione.

La durata definitiva è `null` mentre l’allenamento è in corso oppure se i timestamp sono mancanti o incoerenti. Lo storico mostra rispettivamente “Durata disponibile alla conclusione” o “Durata: Non disponibile”, senza inventare uno zero. Allenamenti inferiori a un secondo hanno invece durata 0. Sono supportate durate oltre un’ora e oltre 24 ore.

La durata compare nel riepilogo finale, nell’elenco dello storico e nel dettaglio. Vale anche per allenamenti storici precedenti che conservano i timestamp. Ripetere una richiesta di completamento già applicata o di interruzione non prolunga l’allenamento.

Prova locale: concludere o interrompere un allenamento, verificare la durata nel riepilogo finale e nello storico, poi ricaricare e riaprire il dettaglio: il valore deve restare uguale. Durante un allenamento ancora aperto non viene mostrata una durata definitiva. Questo passo rimane su fixV2 fino alla prova del proprietario.

## Passo 1: ordine durante l’esecuzione

Nel percorso Esercizi compare una maniglia da trascinare sugli esercizi ancora da svolgere. Funziona con mouse, touch e penna; da tastiera Spazio seleziona, le frecce spostano, Invio salva ed Esc annulla. L’ordine viene mostrato in anteprima e salvato una volta al rilascio, con scorrimento automatico vicino ai bordi dello schermo. Il primo fra gli incompleti diventa quello corrente. È possibile cambiare prima di iniziare oppure fra le serie, anche durante il recupero. Cambiare ordine non sospende il recupero e non cancella alcuna serie. Tornando a un esercizio parzialmente svolto si riparte dalla prima serie incompleta. Completati/saltati restano chiusi.

Il cambiamento riguarda soltanto questo allenamento: la scheda condivisa dell’ADMIN resta invariata. Le posizioni degli incompleti vengono scambiate negli spazi già disponibili; la lista mostra sempre prima gli incompleti nell’ordine salvato, poi completati e saltati nell’ordine delle loro posizioni. Gli esercizi chiusi non hanno maniglie, non sono destinazioni di trascinamento e non entrano nella richiesta di riordino. La disposizione in fondo si aggiorna dopo il completamento e resta coerente dopo un refresh, senza modificare i risultati registrati.

### API e persistenza

`POST /api/me/workouts/{id}/exercises/reorder`:

```json
{"exerciseIds":["UUID-B","UUID-A"],"expectedVersion":0}
```

La lista contiene esattamente tutti gli esercizi TODO/IN_PROGRESS, senza duplicati. `WorkoutState.executionVersion` aumenta con riordino, completamento serie, salto, interruzione o chiusura assegnazione. La revisione è controllata sotto lock pessimistico del workout. Una replica della stessa ultima modifica non modifica nuovamente l’ordine; uno stato superato restituisce 409 `WORKOUT_STATE_CHANGED` e il frontend risincronizza. Ordini non validi restituiscono 400 `INVALID_EXERCISE_ORDER`. Le risorse altrui restano 404, ADMIN non può usare gli endpoint USER. CSRF resta obbligatorio.

Migrazione additiva `V17__workout_execution_order.sql`: revisioni iniziali 0 e vincolo posizione DEFERRABLE per scambi atomici. Prima di attivare il nuovo esercizio viene eseguito un flush del precedente, conservando il vincolo “un solo IN_PROGRESS”.

**Compatibilità del database:** le migrazioni V12–V16 di SviluppoV2 sono conservate byte per byte, così Flyway può validare anche il database già usato con quel branch. Il riordino è ora V17. Queste migrazioni aggiungono lo schema compatibile, ma questa tranche non abilita le restanti funzionalità V2. I successivi passi aggiungeranno migrazioni da V18 in avanti quando necessarie.

Il primo commit di fixV2 aveva usato V12 per il riordino: il database locale del proprietario aveva già V12 per il recupero e ne rifiutava il checksum. Questa correzione ripristina la V12 applicata e conserva la cronologia. Non eseguire Flyway repair né modificare flyway_schema_history per nascondere il conflitto. Se un altro database ha già applicato la vecchia V12 *di riordino* di fixV2, serve una procedura distinta basata sulla sua cronologia: questa correzione riguarda la linea originale V1–V11 oppure V2–V16.

Dopo un cambio branch avviare una volta con `./mvnw clean spring-boot:run` per rimuovere eventuali risorse residue in target. Poi l’avvio abituale rimane `./mvnw spring-boot:run`.

### Verifica automatica

- Frontend: prove per trascinamento mouse/touch, anteprima e salvataggio singolo, annullamento, completati in fondo e bloccati, invalidazione di una selezione dopo aggiornamenti server; oltre alle prove per ordine persistito/refresh, esercizio corrente e serie/recupero conservati; risincronizzazione 409; blocco azioni durante il salvataggio e workout chiusi.
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

1. Aprire una sessione con almeno due esercizi e trascinare il secondo in cima dalla maniglia. Verificare il cambio dell’esercizio corrente.
2. Completare una serie di un esercizio con più serie, spostarlo sotto un altro e verificare che il recupero non sparisca né riparta.
3. Ricaricare: ordine e serie svolte devono restare. Riportare l’esercizio parziale in cima: deve proporre la serie successiva.
4. Finire l’esercizio scelto: deve avanzare secondo il nuovo ordine. Completati/saltati devono comparire in fondo senza maniglie.
5. Aprire lo stesso allenamento in due schede: salvare un ordine nella prima, poi nella seconda. La seconda deve allinearsi allo stato recente, senza sovrascrivere un ordine obsoleto.
6. Controllare che l’ADMIN veda la scheda originale e provare il trascinamento anche da telefono.

L’applicazione della migrazione è automatica all’avvio backend tramite Flyway; non eliminare il database per provarla.
