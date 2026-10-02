# GymPlanner — SviluppoV2

Branch richiesto dal proprietario: `SviluppoV2`, creato da `delpy` al commit `4f1f997`.
Tutte le cinque aree della roadmap sono autorizzate **per fasi**, più la durata degli allenamenti.
Questo documento distingue ciò che il codice implementa dai prossimi incrementi.

| Fase | Funzioni | Stato |
| --- | --- | --- |
| 1 | Durata allenamenti; pausa/ripresa, estensione e salto recupero | Implementata, vedere verifiche sotto |
| 2 | Carichi previsti/usati e ripetizioni effettive per serie | Da sviluppare |
| 3 | Storico avanzato, statistiche, grafici e record usando dati reali | Da sviluppare |
| 4 | PWA e notifiche push | Da sviluppare; richiederà configurazione origine HTTPS e chiavi push |
| 5 | Recupero password via email | Da sviluppare; richiederà servizio invio, credenziali e URL pubblico |

## Fase 1

- Tempo trascorso durante l'allenamento; tempo impiegato nella schermata finale,
  nell'elenco storico e nel dettaglio (anche per gli allenamenti precedenti).
- Durata comprensiva di recuperi e tempo fuori dalla pagina, congelata alla conclusione
  o interruzione. Una pausa del recupero non sospende la durata dell'allenamento.
- Controlli recupero persistenti sul backend: pausa, ripresa, +30 secondi, salto con conferma.
- Protezione delle richieste obsolete e delle estensioni concorrenti.
- Migrazione additiva V12: preserva snapshot e recuperi degli allenamenti già in corso.
- CI attiva anche sui push al branch `SviluppoV2`.

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
validazione, isolamento utenti e due estensioni simultanee. Test della migrazione da V11.
La suite backend completa viene eseguita da GitHub Actions con Java 21 e Docker.
Gli esiti effettivi vengono riportati nella PR; questo elenco non certifica una prova in produzione.

Prova manuale: avviare, completare una serie, mettere il recupero in pausa, ricaricare,
aggiungere 30 secondi, riprendere, saltare con conferma, completare o interrompere.
Verificare che il tempo finale resti invariato dopo refresh nello storico.

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
