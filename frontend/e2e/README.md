# Test end-to-end (Playwright)

Coprono i flussi principali della specifica (§16), eseguiti su un sistema reale a 360 px:

1. ADMIN login → crea USER → USER primo accesso → cambio password
2. ADMIN crea catalogo → crea scheda → assegna
3. USER sceglie i giorni → vede l'allenamento di oggi
4. USER avvia → completa serie → timer → conclude
5. USER salta un esercizio → lo storico mostra "Saltato"
6. ADMIN modifica la scheda → lo storico passato resta invariato

Aggiunti nel branch `modifiche`:

- notifiche "Nuova scheda creata" / "Scheda modificata" / "Scheda assegnata" (che non riappaiono dopo il refresh);
- catalogo unificato con deep link `?group=` e combobox nell'editor;
- "Fine serie" bloccato durante il recupero e messaggio "Esercizio completato";
- seconda scheda attiva (duplicazione + assegnazione), giorni divisi fra due schede, giorno occupato non selezionabile;
- report attività ed eliminazione di un account con doppia conferma (login poi rifiutato).

Aggiunti con i Task 1-6 (`e2e/task-flows.spec.ts` e `e2e/layout.spec.ts`):

- recupero tra esercizi impostato dall'ADMIN e mostrato durante l'allenamento;
- nome della sessione usato come titolo ovunque;
- giorno saltato con anteprima, ripristino e spostamento in un giorno libero;
- storico organizzato per scheda, anche per una scheda non più attiva;
- ultimo peso della stessa serie mostrato (mai precompilato);
- schermate principali ADMIN e USER a 360 px senza scorrimento orizzontale né controlli tagliati, con nomi lunghi
  (`LAYOUT_SHOTS=/percorso` salva anche le schermate).

Gli elenchi di gruppi e utenti sono paginati: i test cercano il nome appena creato invece di
dipendere dalla prima pagina, così restano stabili anche su un database con molti dati.

## Esecuzione

1. Avvia PostgreSQL, backend e frontend come nel `README.md` (backend su 8080, Vite su 5173).
2. Installa il browser una sola volta: `npx playwright install chromium`.
3. Esegui:

```bash
cd frontend
npm run e2e
```

Variabili opzionali:

| Variabile | Default | Significato |
| --- | --- | --- |
| `E2E_BASE_URL` | `http://localhost:5173` | indirizzo del frontend |
| `E2E_ADMIN_USERNAME` | `admin` | `GYM_ADMIN_USERNAME` |
| `E2E_ADMIN_PASSWORD` | `Admin12345` | `GYM_ADMIN_PASSWORD` iniziale (usata solo su database nuovo) |
| `E2E_ADMIN_NEW_PASSWORD` | `AdminE2e2026` | password impostata dai test al primo accesso ADMIN |

Su un database nuovo i test eseguono anche il cambio password obbligatorio dell'ADMIN; nelle
esecuzioni successive usano `E2E_ADMIN_NEW_PASSWORD`. Ogni esecuzione crea dati con nomi univoci,
quindi la suite è ripetibile. Se hai già cambiato a mano la password dell'ADMIN, imposta
`E2E_ADMIN_NEW_PASSWORD` con la password attuale.

Report HTML: `npx playwright show-report`.
