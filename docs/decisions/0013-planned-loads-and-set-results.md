# ADR 0013 — Carichi previsti e risultati per serie

Seconda fase V2, autorizzata dal proprietario dopo la verifica della Fase 1.
Branch `SviluppoV2`, base `05ae9d5`.

## Regole

- Unità: kg, da 0 a 1000, massimo due decimali. Zero significa nessun carico esterno;
  NULL significa non indicato/non registrato. Non si deduce il peso corporeo.
- ADMIN: peso previsto generale per esercizio oppure per ciascuna serie personalizzata.
  Attivare la personalizzazione copia il valore generale nelle nuove righe; una riga lasciata
  vuota resta senza peso previsto, non eredita un valore nascosto.
- USER: peso usato e ripetizioni effettive facoltativi, registrati atomicamente con Fine serie.
  Ripetizioni effettive intere da 0 a 1000, anche per serie MAX. Non vengono copiate dai valori
  previsti: nessun dato viene inventato quando l'utente lascia vuoto un campo.
- Avvio: `weightKgPlanned` viene copiato nello snapshot di ciascuna WorkoutSet.
  Modifiche/duplicazioni successive della scheda non alterano allenamenti già iniziati.
- Ripetere Fine serie mantiene i risultati della prima richiesta riuscita. Il lock del workout
  impedisce di mescolare peso e ripetizioni di richieste simultanee. Il recupero resta bloccante.
- Non viene aggiunta una modifica retroattiva dei risultati: questa fase registra i valori
  alla conclusione della serie e li mostra nel dettaglio storico.

## Persistenza e API

V13 aggiunge colonne nullable a plan_exercises, plan_sets e workout_sets.
Nessun backfill di pesi o ripetizioni: i vecchi record restano senza risultati registrati.
CHECK DB per intervalli e per vietare risultati su serie non completate. Migrazioni precedenti immutate.

PlanStructure.Exercise e Set: `plannedWeightKg`.
WorkoutState.SetState: `weightKgPlanned`, `weightKgUsed`, `repsActual`.

Il body facoltativo di `POST /api/me/workouts/{id}/sets/{setId}/complete` è:

```json
{"weightKgUsed":22.75,"repsActual":9}
```

Richieste senza body continuano a funzionare; proprietà e CSRF invariati.
Input non valido: 400. La validazione impedisce arrotondamenti/troncamenti silenziosi.
Gli aggiornamenti ADMIN sostituiscono la configurazione completa: omettere il peso lo cancella.

I dati di questa fase sono la base delle statistiche V2 successive; nessun grafico simulato.
