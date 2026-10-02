# Implementazione del mockup sul branch desaig

Il mockup HTML approvato viene applicato al frontend React esistente. Backend, database,
permessi e contratti API conservano il loro comportamento. Il branch di produzione `delpy`
non viene aggiornato da questo lavoro.

## Pagine e componenti

| Area | Risultato |
| --- | --- |
| Navigazione | Menu laterale su desktop, barra inferiore su telefono, avatar e cambio tema |
| Accesso | Marchio, scheda di login e preferenza tema |
| Oggi | Hero con sessione reale, esercizi e serie; programma numerato per gruppi muscolari |
| Allenamento | Serie e ripetizioni grandi, avanzamento, timer circolare e percorso degli esercizi |
| Calendario | Griglia mensile navigabile, selezione del giorno e dettaglio dell'allenamento |
| Le mie schede | Card e tab delle sessioni, mantenendo giorni, durata consigliata e stati |
| Storico | Elenco e riepilogo con i dati degli allenamenti svolti |
| Profilo | Dati personali, telefono, password, scelta del tema ed uscita |
| Dashboard | Quattro contatori reali e collegamenti rapidi |
| Amministrazione | Utenti, dettaglio account, schede, assegnazioni ed editor responsive |
| Catalogo | Gruppi e relativo elenco esercizi, affiancati su desktop e separati su telefono |

## Adattamenti ai dati disponibili

Il file HTML contiene valori di esempio e pulsanti dimostrativi. Nell'applicazione:

- Le statistiche della dashboard sono utenti attivi, schede disponibili, gruppi muscolari attivi
  ed esercizi attivi. Si leggono i totali delle API con `size=1`; non si caricano interi cataloghi.
  Non vengono simulati feed di attività, assegnazioni totali o allenamenti di oggi.
- Il calendario segue la data corrente e chiede solo le settimane del mese visualizzato
  (massimo 42 giorni). Il dettaglio usa la scheda assegnata; se due sessioni hanno lo stesso
  titolo, si propone la scheda completa senza scegliere arbitrariamente una sessione.
- Durate stimate e percentuali delle schede non disponibili nelle API vengono omesse.
- Creazione account, password temporanea, assegnazioni, eliminazione e ripristino mantengono
  le azioni effettivamente supportate. Non vengono introdotti invii email o nuovi ruoli tramite
  pulsanti dimostrativi.
- Serie, ripetizioni e recuperi appartengono alla scheda: il catalogo conserva il suo modello
  di esercizi e gruppi muscolari. Rimangono tutte le operazioni già disponibili.

Il timer evita inoltre di mostrare per un istante 61 secondi quando una nuova risposta del
server avvia un recupero da 60 secondi. Il countdown e la sincronizzazione esistenti restano attivi.

## Verifica

- `npm run lint`: superato.
- `npm run test`: 90 test superati, incluse navigazione, preferenza tema, tab da tastiera,
  conservazione delle bozze, totali della dashboard, calendario e avvio corretto del timer.
- `npm run build`: compilazione TypeScript e produzione Vite superate.
- Controllo Chromium con risposte API simulate: 17 pagine a 320, 380 e 1120 px; ulteriori viste
  scure e timer in recupero. Nessuno scorrimento orizzontale o errore JavaScript nelle viste
  controllate. Verificati Escape, chiusura del menu dal suo sfondo, focus, persistenza e tema
  automatico del dispositivo.
- La workflow Verify viene eseguita anche sui push a `desaig`. Il controllo visuale con API
  simulate non sostituisce una prova con i dati reali della palestra prima del deploy.

## Prova locale

Dalla propria copia del repository, con modifiche personali già salvate:

```bash
git fetch origin
git switch desaig
git pull --ff-only origin desaig
cd frontend
npm ci
npm run lint
npm run test
npm run build
npm run dev
```

Il backend deve essere avviato con la configurazione locale abituale. Vite usa
`http://localhost:8080` come backend predefinito e consente `GYM_BACKEND_URL` per un altro indirizzo.
Verificare almeno accesso come amministratore e utente, editor, calendario, tema e un allenamento
con completamento serie, recupero, salto e interruzione.
