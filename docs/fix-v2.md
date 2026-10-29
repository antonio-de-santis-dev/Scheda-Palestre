# FixV2 — miglioramenti per passi

Base richiesta: `fixV2` da `4f1f997`. Ogni passo viene provato dal proprietario prima di iniziare il successivo. I passi 1–2 sono stati provati dal proprietario e pubblicati tramite delpy (merge b23220d); i passi successivi vengono sviluppati e verificati separatamente su fixV2.

| Passo | Funzione | Stato |
|---|---|---|
| 1 | Riordinare gli esercizi durante l’allenamento | Verificato e pubblicato su delpy |
| 2 | Esercizi completati in fondo alla lista | Verificato e pubblicato su delpy |
| 3 | Durata allenamento da timestamp backend, nello storico | Integrato in delpy, merge 2d3e34d |
| 4 | Recupero persistente: pausa/ripresa, +30s, salto confermato, controllo versione | Integrato in delpy, merge 13d15e7 |
| 5a | Storico filtrabile con filtri URL | Integrato in delpy, merge c7d55d5 |
| 5b | Risultati effettivi: peso e ripetizioni per serie | Integrato in delpy, merge 1cff5cb |
| 5c | Identità storica degli esercizi | Integrato in delpy, merge de56e3a |
| 5d | Volume registrato e copertura dei dati | Integrato in delpy, merge d19abef |
| 5e | Riepiloghi aggregati sui filtri dello storico | Integrato in delpy, merge b57de4f |
| 5f | Grafici dei progressi mensili | Integrato in delpy, merge 159c11c |
| 5g | Record di peso e ripetizioni per esercizio | Integrato in delpy, merge eadc582 |
| Finale | Verifiche complete e deploy | Da fare |

## Icona del collegamento alla Home

Su fixV2 in attesa della prova: manubrio SVG Lucide fornito dal proprietario, con tracciati invariati, manubrio scuro #111827 su fondo opaco arancione #f97316, mantenendo le dimensioni approvate. Il file vettoriale e la variante maskable restano in public/icons come sorgenti; le versioni PNG sono renderizzate dagli SVG. Apple touch icon 180×180 alla root, icone Android 192×192 e 512×512, varianti maskable separate con il logo nella zona centrale sicura, favicon SVG e PNG 32×32. Metadati comuni nell’HTML e manifest con nome GymPlanner, avvio alla root e modalità standalone; nessun service worker o cache offline. Nginx serve il manifest come application/manifest+json e restituisce 404 per icone mancanti, senza fallback HTML.

Dopo il deploy: aprire il sito HTTPS da Safari e scegliere Condividi → Aggiungi alla schermata Home; su Android usare il comando del browser Aggiungi alla schermata Home/Installa. Per verificare il nuovo artwork ricreare il collegamento se quello esistente conserva la vecchia icona. Verificare avvio, autenticazione, nome e icona da iPhone e Android. La pubblicazione attende l’approvazione di questa modifica su fixV2.

Riferimenti: Apple Safari Web Content Guide (Configuring Web Applications); MDN Manifest icons; web.dev Adaptive icon support in PWAs with maskable icons.

## Fix del 6 ottobre: risultati solo durante il recupero e coriandoli su telefono

Correzione dell’interpretazione iniziale, approvata dal proprietario e integrata in delpy tramite PR #15 (913921e). Durante l’esecuzione di una serie non ci sono input. Fine serie completa la serie senza risultati e avvia il recupero; i campi si riferiscono esplicitamente alla serie appena svolta, anche quando l’esercizio corrente è già cambiato. Salva risultati registra peso e ripetizioni senza far avanzare la sessione né riavviare il timer. I campi sono disponibili solo nel recupero, anche in pausa, e spariscono alla scadenza o dopo il salto. I dati salvati sono ripristinati dopo refresh; quelli non salvati rimangono mancanti. Nessun input nelle serie con recupero configurato a zero.

Anche l’ultima serie offre il recupero finale per registrare i risultati: la scadenza deriva dal timestamp backend della serie e dal recupero previsto, sopravvive al refresh e non prolunga la durata definitiva dell’allenamento. Un’interruzione non apre il recupero finale.

`POST /api/me/workouts/{id}/sets/{setId}/results` riceve `{results: {weightKgUsed, repsActual}, expectedExecutionVersion, expectedRestVersion}`. Il lock del workout, il riferimento alla serie del recupero e le revisioni impediscono scritture sulla serie successiva o da schermate obsolete. Durante la finestra i risultati possono essere corretti; dopo rimangono immutabili. Retry identici sono letture senza effetti anche se il timer è scaduto; payload differenti e riferimenti obsoleti sono rifiutati. Ownership, ruoli e validazione dei valori rimangono attivi. V20 aggiunge `rest_set_id` e ripristina le vecchie finestre solo se la serie è identificabile senza ambiguità; tutte le migrazioni precedenti restano invariate.

I coriandoli usano un canvas dedicato sopra il contenuto, dimensionato sul visualViewport del telefono (fallback innerWidth/innerHeight), con aggiornamento dopo resize/scroll del viewport e pulizia al termine. Nessun worker per il rendering; la preferenza di movimento ridotto rimane rispettata e il messaggio testuale resta sempre visibile.

Prova: eseguire una serie senza input, premere Fine serie, compilare e salvare durante il timer, verificare i risultati dopo refresh e nello storico; ripetere con pausa, estensione, salto, cambio esercizio e ultima serie. Su telefono controllare i coriandoli a fine esercizio e allenamento anche con la pagina scorsa.

## Passo 5g: record per esercizio

`GET /api/me/workout-records` usa gli stessi filtri validati dello storico, ownership della sessione e nessuna paginazione. Raggruppa solo per identità immutabile (origine + UUID), mai per nome. Peso massimo e ripetizioni massime sono indipendenti e provengono solo da risultati effettivi di serie completate, anche in esercizi saltati o allenamenti interrotti/in corso. Un dato mancante non impedisce il record dell’altro; zero è un valore registrato. Le serie prive di entrambi i dati rimangono nella copertura, senza inventare record. A parità vince il timestamp di completamento più antico, poi l’UUID della serie per stabilità.

Ogni record conserva il risultato completo della sua serie, timestamp, nome storico e collegamento allo snapshot nell’allenamento originale. Il titolo dell’esercizio usa il più recente snapshot con serie completate nei filtri; rinomina o eliminazione del catalogo non altera le serie originali. Omonimi e origini diverse restano separati; i legacy mostrano i limiti dell’identità. Il frontend conserva filtri e scelta del grafico nei link, distingue caricamento/errori/assenza di serie e mostra la copertura separata di peso e ripetizioni. Nessuna migrazione.

Prova: registrare per lo stesso esercizio 80 kg × 5 e 50 kg × 12; verificare due record diversi e aprire le rispettive serie. Provare dati parziali, zero, omonimi e filtri; ricaricare e confrontare i record con il dettaglio. I record sono stati integrati in delpy tramite PR #14 (eadc582).

## Passo 5f: grafici dei progressi mensili

Lo storico include un grafico a colonne con selettore di volume registrato, durata registrata (asse in minuti, valori esatti in tabella) oppure numero di allenamenti. Ogni colonna rappresenta un mese con allenamenti nei filtri; i mesi senza allenamenti non vengono inventati né disegnati come zero. I mesi sono ordinati cronologicamente, distinguendo gli anni. Valori mancanti: N/D senza colonna; zero registrato: punto sulla linea di base e valore 0; copertura parziale: asterisco e tratteggio. Nome/valore di ogni mese sono disponibili come testo accessibile e titolo SVG. Una tabella apribile espone dati esatti, esiti e copertura di durata/volume. Grafico e tabella scorrono orizzontalmente su schermi piccoli e sono utilizzabili da tastiera. Nessuna nuova libreria o animazione.

`GET /api/me/workout-progress` restituisce una lista `{month, totals}`, dove month è il primo giorno del mese e totals usa il contratto del riepilogo. Una sola query aggregata PostgreSQL; stessi criteri SQL condivisi con i riepiloghi, sempre con ownership della sessione. Date inclusive sul singolo allenamento prima di aggregare: selezionare parte di un mese non include workout fuori intervallo. Il volume e la durata mantengono le regole precedenti e le coperture distinte, senza stime o arrotondamenti aggiuntivi nei dati. Nessuna paginazione e nessuna migrazione.

La scelta del grafico è conservata nell’URL `chart=duration` oppure `chart=workouts`; il default è volume. Cambiare il dato o la pagina non rifà la richiesta dei dati mensili; cambiare i filtri carica il nuovo dataset, nascondendo quello precedente durante il caricamento. I filtri, il grafico e i link al dettaglio rimangono coerenti dopo refresh e navigazione. Errori e retry del grafico sono separati dall’elenco e dai riepiloghi.

Prova locale: nello storico cambiare i tre dati, aprire la tabella e confrontare valori con i riepiloghi nello stesso intervallo. Provare serie senza risultati e serie con zero; verificare N/D, zero e segnalazioni di copertura parziale. Applicare date, esito e nome, cambiare pagina e ricaricare. Da telefono scorrere tutti i mesi; da tastiera usare selettore, area scorrevole e tabella. I record per esercizio restano il passo successivo dopo questa prova.

## Passo 5e: riepilogo aggregato dei filtri

Lo storico mostra un riepilogo di tutti gli allenamenti corrispondenti a `from`, `to`, `status`, `q`, indipendente dalla pagina visualizzata. Riporta numero totale, completati, interrotti e in corso, durata registrata complessiva e volume registrato con copertura dei dati. Cambiare pagina non altera i totali. Cambiare filtro aggiorna riepilogo ed elenco, senza presentare il riepilogo precedente come se appartenesse ai nuovi criteri. Refresh e URL conservano i filtri. Errori e retry del riepilogo sono separati dall’elenco, che rimane consultabile.

`GET /api/me/workout-stats` usa gli stessi criteri validati dello storico: date scheduledDate inclusive, esito e sottostringa case-insensitive dei nomi snapshot con caratteri LIKE cercati letteralmente. L’utente viene sempre dalla sessione; query userId esterne non lo cambiano. Nessuna paginazione. Una sola query SQL con CTE aggrega in PostgreSQL, senza caricare tutti i workout o le serie nel backend, e ottiene conteggi e somme dalla stessa fotografia del database.

La durata somma i secondi interi di ogni allenamento concluso con timestamp coerenti. In corso esclusi; conclusi senza durata valida conteggiati come mancanti. Nessuna durata valida significa null, mentre durate valide inferiori a un secondo sommano a zero. Il volume conserva le regole del passo 5d, inclusi dati parziali e serie completate di allenamenti in corso/interrotti. Durata e volume quindi hanno coperture esplicitamente distinte. Nessuna nuova migrazione.

Prova locale: selezionare un intervallo con più allenamenti ed esiti, confrontare i numeri con lo storico, cambiare pagina e verificare totali invariati. Applicare nome/esito, ricaricare e controllare il riepilogo. Confrontare la somma delle durate finali e dei volumi registrati; dati mancanti e allenamenti ancora in corso devono essere segnalati senza inventare risultati.

## Passo 5d: volume dai risultati registrati

L’elenco dello storico, il dettaglio dell’allenamento e ogni esercizio mostrano il volume registrato in `kg × ripetizioni`. Il backend somma `weightKgUsed × repsActual` esclusivamente per serie completate con entrambi i valori presenti. Usa BigDecimal: niente arrotondamenti durante la somma e nessuna stima dalle ripetizioni previste. Una serie MAX usa le ripetizioni effettive. Serie già svolte di esercizi poi saltati o allenamenti interrotti contribuiscono comunque; serie non svolte non contribuiscono e non sono contate come dati mancanti.

`WorkoutSummary`, `WorkoutState` ed `ExerciseState` includono `volume`, con `recordedKgReps`, `completedSets`, `recordedSets`, `missingWeightSets`, `missingRepsSets`. Se nessuna serie è calcolabile, il volume è null, mostrato come Non disponibile. Uno zero registrato è invece calcolabile e distinto dall’assenza di dati. Con dati incompleti il valore resta la somma conosciuta, viene marcato Dati parziali e il dettaglio riporta quanti pesi e quante ripetizioni mancano. Il numero di serie escluse è `completedSets - recordedSets`: una serie priva di entrambi i campi è esclusa una sola volta.

Il client visualizza il riepilogo backend, coerente fra elenco, dettaglio, azioni e riapertura. I filtri e l’URL rimangono attivi. Nessuna nuova migrazione; il volume è derivato dai risultati persistiti. Per esercizi a corpo libero il valore riguarda solo il peso esplicitamente registrato: non viene inventato un peso corporeo. Non è un indicatore di calorie o lavoro meccanico.

Prova locale: completare 32,75 kg × 8 ripetizioni (262), poi una serie con solo peso: lo storico deve mostrare 262 con dati parziali e ripetizioni mancanti. Provare campi vuoti, zero e MAX, oltre a salto dopo una serie svolta e interruzione. Ricaricare il dettaglio e verificare che i valori restino uguali. Dopo la prova si aggiungeranno riepiloghi aggregati, grafici e record.

## Passo 5c: identità storica degli esercizi

Ogni esercizio restituito nello stato o nel dettaglio storico include `identity: {source, id}`. La chiave completa è la coppia: `CATALOG` + UUID del catalogo oppure `LEGACY` + UUID storico; UUID dei due ambiti non vengono confusi. Nome e gruppo rimangono i valori dello snapshot, ma non sono usati come chiave.

Per i nuovi allenamenti l’UUID del catalogo viene preso dalla struttura pubblica della scheda all’avvio e conservato nella colonna `catalog_exercise_id` della V14 originale. Non ci sono collegamenti JPA o FK al catalogo: rinomina, spostamento, rimozione della voce dalla scheda o eliminazione del catalogo non cambiano l’identità già salvata. Più occorrenze dello stesso esercizio, anche in schede diverse, hanno la stessa identità; esercizi distinti omonimi rimangono distinti.

Per i dati precedenti si conserva `legacy_exercise_id`, che rappresenta la vecchia voce della scheda quando ancora disponibile, oppure il singolo snapshot. La nuova V19 colma solo le identità mancanti dei workout creati da fixV2 dopo V14: usa il vecchio `plan_exercise_id` se ancora presente, altrimenti l’UUID del workout exercise. Non deduce mai un catalogo dal nome o dalla scheda attuale. Se il riferimento originale è già perso, registrazioni separate non vengono unite. Il dettaglio segnala questa limitazione per gli esercizi legacy. Le migrazioni precedenti non vengono modificate; risultati, ordine e timer sono conservati.

Prova locale: aprire un vecchio storico e verificare l’avviso di identità limitata; iniziare un nuovo allenamento e controllare `exercises[].identity` nella risposta API. Rinominarne o rimuoverne l’esercizio dalla scheda tramite ADMIN: riaprendo lo storico nome originario e identità devono restare. Due esercizi distinti con lo stesso nome devono avere UUID diversi. Questa base sarà usata per volume e record nei prossimi passi, dopo la prova.

## Passo 5b: risultati effettivi per serie

Durante il recupero successivo a Fine serie si possono registrare peso usato (kg) e ripetizioni effettive della serie appena svolta, anche per serie MAX. Entrambi sono facoltativi: un campo vuoto resta `null`, mentre zero è registrato come valore esplicito. Non viene copiato il numero previsto dalla scheda. Peso 0–1000 kg, massimo due decimali; ripetizioni intere 0–1000. Il frontend accetta anche la virgola decimale. Ogni nuova serie apre campi vuoti; i campi compaiono solo durante il recupero, anche in pausa. Scadenza o salto li nascondono; i risultati salvati restano nello storico.

`POST /api/me/workouts/{id}/sets/{setId}/complete` accetta un corpo facoltativo:

```json
{"weightKgUsed":32.75,"repsActual":8}
```

Il salvataggio è atomico sotto il lock dell’allenamento, insieme a completamento, avanzamento e recupero. Una replica con gli stessi risultati restituisce lo stato senza riscrivere timestamp o timer; 30 e 30.00 sono equivalenti. Un corpo diverso su una serie già completata riceve 409 `SET_RESULTS_CHANGED`: i dati salvati sono immutabili e il client si riallinea. I retry di rete conservano il payload catturato al clic. Il vecchio endpoint senza corpo continua a funzionare: registra risultati mancanti e, se ripetuto, non cancella dati esistenti. Accessi altrui, serie non corrente e recupero non terminato mantengono i controlli precedenti.

Si usano le colonne `weight_kg_used` e `reps_actual` già aggiunte dalla V13 originale, senza modificare migrazioni. Il dettaglio storico distingue ripetizioni previste, ripetizioni effettive e peso usato, indica i campi non registrati nelle serie completate e mostra un trattino per quelle non svolte. Non vengono stimati risultati passati dalla scheda.

Prova locale: completare una serie con 32,75 kg e 8 ripetizioni; la serie successiva deve avere campi vuoti. Terminare o interrompere, aprire lo storico e ricaricare: i risultati devono restare. Provare una serie con campi vuoti e una con zero, anche MAX; lo storico deve distinguerle. Provare valori negativi, oltre il limite, peso con tre decimali e ripetizioni frazionarie: non devono completare la serie. Volume, grafici e record saranno aggiunti dopo questa prova.

## Passo 5a: filtri dello storico conservati nell’URL

L’elenco dello storico accetta `from`, `to`, `status` e `q`, oltre a `page` e `size`. Le date si riferiscono alla data dell’allenamento (`scheduledDate`) e sono inclusive; è possibile impostare anche un solo estremo. Esiti: `COMPLETED`, `INTERRUPTED`, `IN_PROGRESS`, oppure tutti. La ricerca è una sottostringa senza distinzione di maiuscole sui nomi storici della scheda o della sessione; non consulta schede rinominate o eliminate. `%`, `_` e backslash sono cercati letteralmente.

La query applica sempre l’utente autenticato insieme ai criteri e calcola la paginazione sui risultati filtrati. L’ordinamento resta dal più recente, con UUID come spareggio deterministico. Intervalli invertiti e nomi oltre 100 caratteri vengono rifiutati, così come date/esiti non validi. Non occorre una migrazione del database.

Il modulo Applica filtri aggiorna l’URL e torna alla prima pagina; Azzera filtri li rimuove. Paginazione, refresh, link al dettaglio, ritorno allo storico e navigazione Indietro mantengono i criteri. I filtri non validi presenti nell’URL sono segnalati prima di interrogare il backend. Le ricerche senza risultati hanno un messaggio dedicato. I risultati della ricerca precedente non vengono presentati come se appartenessero ai nuovi filtri durante il caricamento.

Prova locale: combinare date, esito e nome; passare alla pagina successiva, aprire un dettaglio e tornare allo storico; ricaricare; cambiare i criteri e verificare il ritorno alla prima pagina; azzerare e controllare il ripristino dell’elenco. Statistiche e grafici vengono aggiunti nei passi successivi dopo questa prova.

## Passo 4: recupero avanzato persistente

Integrato in delpy tramite PR #7 (merge 13d15e7), su richiesta del proprietario.

Il timer usa `rest_ends_at`, `rest_remaining_millis`, `rest_duration_seconds` e `rest_version` già presenti dalla V12 originale. Pausa e ripresa conservano i millisecondi residui; +30 secondi funziona sia durante il conto alla rovescia sia in pausa. Il salto richiede conferma e termina soltanto il recupero, conservando esercizi e serie. Completare l’allenamento o interromperlo chiude anche il recupero. Riordinare o saltare un esercizio conserva il recupero ancora attivo.

`POST /api/me/workouts/{id}/rest` riceve:

```json
{"action":"PAUSE","expectedVersion":1,"expectedExecutionVersion":1}
```

Azioni: `PAUSE`, `RESUME`, `EXTEND` (sempre +30 secondi), `SKIP`. Il lock del workout serializza i comandi con il completamento serie e il riordino. Una modifica aumenta entrambe le revisioni; duplicati e richieste obsolete ricevono 409 `REST_STATE_CHANGED`, senza applicare due volte l’azione. Il frontend rilegge lo stato aggiornato. Anche la conferma del salto conserva le revisioni del timer selezionato, così non può saltare un recupero successivo. Le risorse altrui restano 404; gli ADMIN non accedono agli endpoint USER; CSRF resta richiesto.

`WorkoutState` include `restPaused`, `restRemainingMillis` e `restVersion`; `WAIT_FOR_REST` vale anche in pausa. La serie successiva rimane bloccata finché il recupero termina o viene saltato. A zero, +30 non può riaprire un recupero già finito. In pausa non ci sono tick né avvisi di scadenza.

La nuova V18 riallinea dal più recente set completato i timer legacy di fixV2 ancora a revisione 0. Non modifica le migrazioni applicate né timer V2 già controllati o in pausa. I risultati delle serie restano invariati.

Verifiche: ciclo pausa/estensione/refresh/ripresa/salto e mantenimento serie/riordino; richieste concorrenti e duplicate; rifiuto di revisioni precedenti, timer scaduti, input invalidi e accessi altrui; migrazione di un timer legacy preservando un timer V2 in pausa; frontend con conferma, blocco azioni e risincronizzazione. Per la prova locale completare una serie, mettere in pausa, ricaricare, aggiungere 30 secondi, riprendere, annullare poi confermare il salto. La migrazione si applica automaticamente all’avvio senza eliminare il database.

## Passo 3: durata definitiva dell’allenamento

Le risposte di esecuzione e i riepiloghi dello storico includono `durationSeconds`: secondi interi fra `startedAt` e `finishedAt`, calcolati dal backend. I due timestamp sono già persistiti e costituiscono la fonte della durata anche dopo riavvio, refresh o apertura da un altro dispositivo. Non occorrono una nuova migrazione né un contatore del browser. Il tempo include recuperi e periodi trascorsi fuori dalla pagina; termina quando l’allenamento viene completato o interrotto, compresa la chiusura dell’assegnazione.

La durata definitiva è `null` mentre l’allenamento è in corso oppure se i timestamp sono mancanti o incoerenti. Lo storico mostra rispettivamente “Durata disponibile alla conclusione” o “Durata: Non disponibile”, senza inventare uno zero. Allenamenti inferiori a un secondo hanno invece durata 0. Sono supportate durate oltre un’ora e oltre 24 ore.

La durata compare nel riepilogo finale, nell’elenco dello storico e nel dettaglio. Vale anche per allenamenti storici precedenti che conservano i timestamp. Ripetere una richiesta di completamento già applicata o di interruzione non prolunga l’allenamento.

Prova locale: concludere o interrompere un allenamento, verificare la durata nel riepilogo finale e nello storico, poi ricaricare e riaprire il dettaglio: il valore deve restare uguale. Durante un allenamento ancora aperto non viene mostrata una durata definitiva. Il proprietario ha richiesto il rilascio della durata tramite delpy, completato con il merge 2d3e34d (PR #6).

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
