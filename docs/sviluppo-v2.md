# GymPlanner — SviluppoV2

Branch richiesto dal proprietario: `SviluppoV2`, creato da `delpy` al commit `4f1f997`.
Tutte le cinque aree della roadmap sono autorizzate **per fasi**, più la durata degli allenamenti.
Questo documento distingue ciò che il codice implementa dai prossimi incrementi.

| Fase | Funzioni | Stato |
| --- | --- | --- |
| 1 | Durata allenamenti; pausa/ripresa, estensione e salto recupero | Implementata, vedere verifiche sotto |
| 2 | Carichi previsti/usati e ripetizioni effettive per serie | Implementata, ADR 0013 |
| 3 | Storico avanzato, statistiche, grafici e record usando dati reali | Implementata, ADR 0014 |
| 4 | PWA e notifiche push | Implementata; attivazione ambiente HTTPS e VAPID, ADR 0015 |
| 5 | Recupero password via email | Implementata; attivazione SMTP e URL pubblico, ADR 0015 |

## Fase 1

- Tempo trascorso durante l'allenamento; tempo impiegato nella schermata finale,
  nell'elenco storico e nel dettaglio (anche per gli allenamenti precedenti).
- Durata comprensiva di recuperi e tempo fuori dalla pagina, congelata alla conclusione
  o interruzione. Una pausa del recupero non sospende la durata dell'allenamento.
- Controlli recupero persistenti sul backend: pausa, ripresa, +30 secondi, salto con conferma.
- Protezione delle richieste obsolete e delle estensioni concorrenti.
- Migrazione additiva V12: preserva snapshot e recuperi degli allenamenti già in corso.
- CI attiva anche sui push al branch `SviluppoV2`.

## Fase 2

- ADMIN: peso previsto generale e pesi distinti nelle serie personalizzate; duplicazione preserva i valori.
- USER: peso usato e ripetizioni effettive facoltativi prima di Fine serie, inclusi MAX e zero.
- Storico: confronto fra valori previsti e registrati per serie; campi assenti indicati esplicitamente.
- Kg con massimo due decimali, input frontend con virgola o punto. Nessuna compilazione automatica dei risultati.
- Migrazione V13 additiva; vecchi risultati NULL, snapshot dei nuovi carichi immutabili.
- Doppie richieste idempotenti e risultati atomici sotto lock PostgreSQL.

Dettagli e contratto: [ADR 0013](decisions/0013-planned-loads-and-set-results.md).

## Fase 3

- Storico filtrabile per date inclusive, esito e nome della scheda/sessione, con filtri nell'URL.
- Pagina Statistiche e record accessibile dallo Storico; default ultime 12 settimane, periodo fino a 366 giorni.
- Totali di attività, serie, durata dei conclusi/media, volume e copertura dei dati registrati.
- Grafici per giorno: allenamenti conclusi e carico/ripetizioni/volume per esercizio, con tabella alternativa.
- Record di peso e ripetizioni su tutto lo storico, indipendenti dal periodo.
- Identità catalogo preservata nei nuovi snapshot; vecchi snapshot separati, nessun abbinamento inventato per nome.
- V14 additiva, nessun backfill di risultati.

Contratto e calcoli: [ADR 0014](decisions/0014-progress-and-history.md).

## Fasi 4–5

- PWA installabile, icone e pagina offline generica; cache solo statica, nessun dato personale.
- Consenso push da Profilo; avviso a fine recupero, rimozione subscription al logout e revoca dopo cambio password.
- Recupero password da Login, link monouso 20 minuti, token solo hash in DB, limiti richieste e sessioni precedenti invalidate.
- Servizi esterni disabilitati per default; configurazione runtime in [ADR 0015](decisions/0015-recovery-and-pwa-push.md).

## API

`WorkoutState`: nuovi campi `durationSeconds` (trascorsi/finali), `restPaused`,
`restRemainingSeconds` (arrotondamento per eccesso), `restVersion`.
`WorkoutSummary`: `durationSeconds`, nullo mentre in corso, finale per gli allenamenti chiusi.

`POST /api/me/workouts/{id}/rest`:

```json
{"action":"EXTEND","expectedVersion":1,"seconds":30}
```

Azioni: `PAUSE`, `RESUME`, `EXTEND`, `SKIP`. Solo `EXTEND` richiede `seconds` (1–300).
La risposta contiene lo stato completo. Proprietà controllata dalla sessione (404 su utenti
altrui), CSRF invariato. Errori: `REST_STATE_CHANGED` 409; `REST_NOT_ACTIVE`,
`REST_ALREADY_PAUSED`, `REST_NOT_PAUSED`, `REST_LIMIT_EXCEEDED` 422.

## Verifica

Frontend: lint, build e suite Vitest. Test nuovi per durata, recupero in pausa e storico.
Backend: test unitari della durata e delle transizioni; integrazioni PostgreSQL per persistenza,
validazione, isolamento utenti e due estensioni simultanee. Test della migrazione da V11, conservazione snapshot e colonne nullable V13.
Fase 2: test su carichi personalizzati/duplicazione, valori effettivi, validazione decimali,
risultati concorrenti, dati assenti e zero; test frontend di editor, esecuzione e storico.
La suite backend completa viene eseguita da GitHub Actions con Java 21 e Docker.
Gli esiti effettivi vengono riportati nella PR; questo elenco non certifica una prova in produzione.

Prova manuale: avviare, completare una serie, mettere il recupero in pausa, ricaricare,
aggiungere 30 secondi, riprendere, saltare con conferma, completare o interrompere.
Verificare che il tempo finale resti invariato dopo refresh nello storico.
Per la Fase 2: impostare carichi nell'editor, avviare l'allenamento, registrare peso e ripetizioni,
concludere/interrompere e verificare lo storico. Lasciare una serie senza dati per verificare
"Non registrato". Cambiare la scheda dopo l'avvio: i carichi dello snapshot restano identici.

Per la Fase 3: in Storico applicare date/esito/nome e cambiare pagina; i filtri restano.
Aprire Statistiche e record, cambiare periodo ed esercizio/metrica, aprire le tabelle dei grafici.
Confrontare il volume con kg × ripetizioni delle sole serie con entrambi i valori;
verificare che una serie senza risultati non aggiunga volume e che 0 rimanga 0.
I record rimangono globali cambiando periodo. Un utente diverso non vede questi dati.

## Avvio locale

```bash
git fetch origin
git switch SviluppoV2
git pull --ff-only origin SviluppoV2
# PostgreSQL locale avviato e .env configurato
cd backend
./mvnw spring-boot:run
# Secondo terminale dalla root
cd frontend
npm ci
npm run dev
```

Nessun merge o deploy viene eseguito da questo incremento.
