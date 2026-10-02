# ADR 0014 — Storico filtrabile e progressi misurati

Stato: accettato. Branch: SviluppoV2, Fase 3.

## Comportamento

Lo storico `/api/me/workouts` mantiene la paginazione e accetta `from`, `to` inclusivi
(data programmata), `status` e `search` (max 100 caratteri; sottostringa letterale,
case insensitive, nei nomi snapshot della scheda/sessione). `%` e `_` non sono wildcard.
I filtri sono nell'URL e restano durante la paginazione; un cambio li riporta a pagina 0.
Date invertite e valori non validi producono 400.

`GET /api/me/progress?from=YYYY-MM-DD&to=YYYY-MM-DD` restituisce `from`, `to`,
`generatedOn`, `days`, `exerciseDays`, `records`. Default: ultimi 84 giorni inclusa
la data corrente del calendario della palestra; massimo 366 giorni inclusivi.
I dati appartengono sempre all'utente della sessione; gli ADMIN non accedono a questa API USER.
Tre query raggruppate, nessun caricamento delle collezioni per ogni allenamento.

- `days`: esiti degli allenamenti e durata complessiva dei soli conclusi/interrotti;
  `closedWorkouts` è il denominatore della durata media. In corso non contribuisce alla durata.
- `exerciseDays`: serie completate, massimi peso/ripetizioni e volume per esercizio/data.
  Include le serie effettivamente completate degli allenamenti interrotti e in corso;
  non dipende dall'esito complessivo dell'esercizio. Le serie non svolte sono escluse.
- `volumeKg` = somma di peso usato × ripetizioni effettive, solo quando entrambi presenti;
  `volumeSets` indica la copertura rispetto a `completedSets`. Nessuna sostituzione
  con valori previsti, anche per MAX. Senza misure il volume è NULL, con zero esplicito è 0.
- `records`: massimi distinti di peso usato e ripetizioni effettive su tutto lo storico,
  indipendenti dal filtro temporale. Possono provenire da serie diverse. Non sono una
  stima di 1RM o un confronto a parità di carico, attrezzatura, tecnica o condizioni.

## Identità e compatibilità

V14 conserva `catalog_exercise_id` immutabile nello snapshot dei nuovi allenamenti:
lo stesso esercizio si confronta tra schede e duplicazioni, anche dopo rinomina.
Le etichette vengono dall'ultimo snapshot disponibile nella query, mai dal catalogo attuale.
Non si inventa il collegamento al catalogo per gli allenamenti precedenti.
`legacy_exercise_id` conserva il vecchio `plan_exercise_id` oppure l'ID dello snapshot
quando il collegamento non esiste più, e rimane anche dopo l'eliminazione della voce
originale. Gruppi `catalog:` e `legacy:` restano distinti; non si uniscono per nome.
Nessuna FK dalle nuove identità: lo storico sopravvive alle modifiche del piano.
Nessun risultato precedente viene modificato dalla migrazione.

## Interfaccia

Da Storico → Statistiche e record (`/app/progress`). Controlli adattabili allo schermo,
riepiloghi del periodo, grafico dell'attività per giorno, selezione esercizio/metrica,
record di tutto lo storico. Grafici a barre senza interpolare misure mancanti;
le date prive della misura scelta non appaiono. Lo zero è un'osservazione valida.
Ogni grafico offre titoli e tabella dei valori esatti; le serie molto lunghe scorrono.
Cambio del periodo convalidato sul client e sul server; stati vuoti/errore/riprova.
Le azioni di allenamento invalidano anche la cache delle statistiche.

## Verifiche

Integrazione PostgreSQL: volume/copertura, NULL/zero, durata, richieste per data,
record globali, parziali/in corso/interrotti, identità legacy e catalogo,
privacy e filtri paginati. Migrazione da V11 verifica conservazione snapshot.
Frontend: selezione metrica/esercizio, valori dei grafici, record, filtri,
paginazione, stati vuoti/errore e riprova. Gli esiti effettivi sono nella PR.
