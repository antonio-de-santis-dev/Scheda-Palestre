# ADR 0004 - Decisioni aperte O-01…O-07

**Problema.** La specifica (§17) elenca decisioni aperte da chiudere prima dell'Incremento 3.
Il proprietario ha autorizzato ad adottare le proposte consigliate quando non bloccano.

**Decisione.** Tutte le proposte consigliate sono adottate:

| Id | Decisione adottata | Implementazione |
| --- | --- | --- |
| O-01 | Un giorno pianificato consuma la sessione anche se non svolto | Rotazione puramente calendariale (`RotationCalculator`) |
| O-02 | Un esercizio `SKIPPED` non si riprende nello stesso allenamento | Nessun endpoint di ripresa; lo skip è ammesso solo sull'esercizio `IN_PROGRESS` |
| O-03 | Recupero dopo l'ultima serie di un esercizio, tranne l'ultima serie dell'ultimo esercizio | `restEndsAt` calcolato dall'ultima serie completata se l'allenamento resta `IN_PROGRESS` |
| O-04 | Nessuna interruzione automatica a mezzanotte | L'allenamento resta `IN_PROGRESS`; la schermata "Oggi" chiede di riprenderlo o interromperlo |
| O-05 | Nessun allenamento fuori calendario | L'avvio richiede un giorno pianificato e una data entro ±1 giorno dalla data del server (tolleranza fusi) |
| O-06 | Timer informativo | Il backend non rifiuta la serie successiva durante il recupero |
| O-07 | Copia dei giorni dalla precedente assegnazione attiva | `copySchedule` (default `true`) nella richiesta di assegnazione/attivazione |

**Conseguenze.** Le scelte sono isolate in punti precisi del codice e dei test, quindi
modificabili senza impatti trasversali.
