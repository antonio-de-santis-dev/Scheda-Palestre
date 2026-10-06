# Revisione sicurezza, prestazioni e affidabilità — branch veloce

Data: 6 ottobre 2026. Base: `delpy` commit `0bdd88c289e8bd8c73e375f28c9403d95763dc1a`.
Il branch è destinato alla prova manuale. Il merge e la pubblicazione richiedono l’approvazione del proprietario.

## Perimetro e metodo

Revisione delle aree identity, catalog, workoutplan, assignment, calendar, execution e shared; frontend React, client HTTP, autenticazione/cache, richieste delle pagine, calendario, storico, esecuzione; Flyway, Docker, Nginx, Compose e CI. Esaminate autorizzazioni e proprietà delle risorse, validazioni, query e caricamenti, concorrenza, sessioni, gestione errori e dipendenze npm. Le correzioni seguono problemi concreti riscontrati nel codice; le parti non modificate continuano a essere coperte dalla suite esistente.

L’analisi non è una certificazione di assenza di vulnerabilità e non include un penetration test del servizio online. Nessun accesso al DB Northflank o alle sue metriche: nessuna percentuale di miglioramento dei tempi reali viene inventata. La scansione npm non equivale a una scansione di tutte le dipendenze Maven o dei pacchetti del sistema operativo dei container.

## Problemi corretti

| Area | Prima | Correzione ed effetto |
| --- | --- | --- |
| Tentativi login concorrenti | Due richieste potevano leggere lo stesso contatore e sovrascriversi | Lock pessimista sulla riga dell’account; cinque tentativi paralleli producono il blocco previsto |
| Aggiornamenti account | Un salvataggio vecchio poteva riscrivere hash password e versione sessione | Versione ottimista sull’utente: la scrittura obsoleta viene rifiutata con conflitto, senza ripristinare accessi revocati |
| Ultimo amministratore | Controllo del numero di ADMIN separato dagli aggiornamenti concorrenti | Lock transazionale dedicato prima di eliminazioni/disattivazioni; verifica e modifica serializzate |
| Password | Policy fino a 128 caratteri senza tener conto del limite BCrypt di 72 byte | Limite UTF-8 coerente su frontend e backend; input eccessivo al login/cambio password gestito senza errore 500; password temporanee e hash esistenti non vengono riscritti |
| Serie personalizzate | La lista poteva contenere `null` e provocare un errore applicativo | Validazione degli elementi prima delle modifiche; risposta 400 |
| Assegnazioni multiple | Lock per utente nell’ordine arbitrario della richiesta | Ordine UUID deterministico, riducendo il rischio di deadlock tra assegnazioni concorrenti |
| Storico, Oggi, calendario | Per costruire un riepilogo venivano caricate entità di esercizi e tutte le serie | Aggregazione SQL per gli allenamenti selezionati; lo storico mantiene paginazione, filtri, privacy, volumi e indicatori di dati mancanti |
| Rotazioni del calendario | Due query per ogni scheda attiva | Lookup bulk dei giorni e delle sessioni: due query per il calcolo delle rotazioni, indipendenti dal numero di schede; utilizzato anche per il prossimo allenamento |
| Sessioni omonime | Dettaglio del calendario ricercato per titolo, ambiguo per titoli duplicati | Backend restituisce `sessionId`; frontend seleziona la sessione per UUID e evita la richiesta della scheda per allenamenti già svolti |
| Richieste superate | Cambiando filtro/mese/pagina alcune letture continuavano inutilmente | AbortSignal propagato a storico, statistiche, record, calendario e allenamento; query già annullate non vengono trasmesse |
| Sessione scaduta/cambio utente | Dati privati e letture pendenti potevano restare nella cache | Cancellazione delle query in corso e rimozione dei dati privati prima del cambio di sessione |
| Client HTTP | URL esterni accettati; errori durante lettura risposta non uniformati | Richieste limitate alla stessa origine prima del bootstrap CSRF; errori di trasporto durante la lettura tradotti in ApiError |
| Download pagina fallito | Fallback React Router standard con dettagli tecnici | Pagina di recupero con ricarica e Home; nessun dettaglio dell’eccezione mostrato |
| Dipendenze frontend | `source-map-js` 1.2.1 segnalato con gravità alta | Aggiornamento mirato a 1.2.2; avviso relativo a elaborazione source map negli strumenti di sviluppo/build, non prova di attacco al backend |
| Immagine frontend | Nginx della vecchia linea 1.27 | Immagine ufficiale `nginx:1.30.5-alpine`, versione osservata nella build della linea stable; nuova build verificata in CI. Il tag non è un digest immutabile |
| Asset pubblici | JS/CSS non compressi da questa configurazione | Gzip per asset statici, senza comprimere JSON autenticato; meno byte trasferiti |
| Cache Nginx | HTML e icone senza politica esplicita; JS mancanti potevano ricevere HTML | HTML/manifest/icone rivalidati, bundle con hash immutabili; file statici mancanti restituiscono 404 |
| Header Nginx | `add_header` nella location asset impediva l’eredità degli header di sicurezza | Snippet comune incluso anche nella location asset; nosniff/CSP/frame protection conservati |
| Dimensioni richieste | Limite implicito del proxy | Limite esplicito 256 KiB a Nginx; sufficiente per le richieste applicative attuali, errore 413 per corpi superiori |
| Verifiche CI | Nessun trigger push su veloce né prova runtime del frontend | Trigger dedicato; build container e verifica di SPA, header, cache, manifest, 404 e gzip |

## Database e compatibilità

Nuova migrazione **V21__user_optimistic_lock.sql**: aggiunge `users.version bigint NOT NULL DEFAULT 0`. Non modifica V1–V20, non cancella dati e non richiede ricreazione del database o Flyway repair. La nuova versione è distinta da `session_version`: protegge i salvataggi concorrenti.

Il calendario aggiunge un campo JSON `sessionId`; le altre proprietà restano disponibili. Il rilascio del backend e del frontend deve essere coordinato per usare il nuovo dettaglio calendario. Gli utenti non devono reimpostare la password per questa migrazione. Le nuove password sono limitate a 72 byte UTF-8: caratteri accentati possono occupare più di un byte.

Il job di pulizia temporanea discusso in precedenza non è stato introdotto. Allenamenti, storico, record e schede eliminate logicamente vengono conservati.

## Verifiche

- `npm audit`: zero avvisi dopo l’aggiornamento mirato, secondo il database interrogato il 6 ottobre 2026.
- Frontend: lint, build e suite Vitest; nuove regressioni per limite password Unicode, richieste esterne/cancellate, cache dopo 401, errore download pagina e sessioni omonime.
- Backend: suite Maven con Java 21 e PostgreSQL Testcontainers in GitHub Actions; nuove prove su login simultanei, scritture obsolete, input password eccessivo, serie `null`, identità calendario e assenza di caricamento delle entità WorkoutExercise/WorkoutSet nei riepiloghi.
- Container frontend: `scripts/check-frontend-container.py` contro l’immagine Nginx reale, non contro Vite.
- Suite preesistente copre ruoli/CSRF/privacy, migrazioni, assegnazioni, catalogo, esecuzione, timer, riordino, risultati, volumi, filtri, statistiche e record.
- **Esito finale sul codice `b604b35441dafdf6c08ac536224f73c581e1f5da`: 267 test backend e 152 test frontend superati**, lint e build superati; Compose e container frontend superati. CI: https://github.com/antonio-de-santis-dev/Scheda-Palestre/actions/runs/37468587564
- La verifica del container ha osservato Nginx 1.30.5 e il bundle JavaScript principale di **373.961 byte non compressi / 115.231 byte gzip**, circa 69% di byte in meno per quel file. Non è una misura del tempo di risposta del backend né del caricamento completo su telefono.
- Localmente non sono disponibili Java 21 e Docker: per backend e container l’evidenza è la CI. Le regressioni frontend mirate sono state eseguite anche localmente.
- Una prova nuova ha evidenziato la necessità di attendere l’avvio effettivo del container; il controllo ora tollera i reset di connessione soltanto durante quella attesa. Un test preesistente di navigazione ora attende il commit del DOM prima di controllare il link attivo.

## Prova manuale

Dalla cartella del progetto:

```bash
git fetch origin
git switch veloce
git pull --ff-only origin veloce
docker compose up -d postgres
```

Primo terminale, con Java 21 attivo e il consueto `.env` già configurato:

```bash
cd backend
./mvnw clean spring-boot:run
```

Secondo terminale:

```bash
cd frontend
npm ci
npm run dev -- --host 0.0.0.0
```

Aprire http://localhost:5173. La pulizia di `target` nel comando Maven evita migrazioni stale quando si cambia ramo. Non eliminare il volume PostgreSQL.

1. Accesso ADMIN e USER, logout, cambio account: i dati del primo utente non devono ricomparire nel secondo.
2. Cambio password con password normale e con stringa molto lunga/Unicode: errore comprensibile per quella eccessiva, nessun errore server.
3. Su un utente di prova, cinque accessi errati: blocco di 15 minuti; usare un account di test, non l’ADMIN principale.
4. Creare/modificare scheda, esercizi e serie personalizzate; assegnarla a uno o più utenti, scegliere giorni e prossima sessione.
5. Calendario con due sessioni intitolate ugualmente ma esercizi diversi: selezionando i giorni deve apparire la sessione corretta.
6. Allenamento completo: fine serie, recupero, pausa/ripresa, +30, salto con conferma, riordino, input peso/ripetizioni solo nel recupero, recupero finale, refresh e interruzione.
7. Storico: filtri e pagina nell’URL, volumi anche parziali/zero, durata, grafici, record e dettaglio. Confrontare gli importi con i risultati inseriti.
8. Cambiare velocemente filtri e mesi su rete rallentata: verificare che il risultato finale corrisponda all’ultima selezione e che le richieste superate risultino annullate.
9. PC e telefono: menu, navigazione, icona Home, feedback e coriandoli.

**Compressione e cache si provano nel container**, non con `npm run dev`. Dal progetto, dopo aver arrestato Vite se necessario:

```bash
docker build -t gym-frontend-check frontend
docker run -d --name gym-frontend-check --add-host gym-planner-backend:127.0.0.1 -p 127.0.0.1:18080:80 gym-frontend-check
python3 scripts/check-frontend-container.py
docker rm -f gym-frontend-check
```

Questa prova è solo del frontend statico: non collega il backend e non modifica il database.

## Aspetti da verificare sull’infrastruttura

- Tempi reali p50/p95, memoria, CPU, pool connessioni e query più lente su un ambiente di test con un volume rappresentativo: senza misure non è corretto ridimensionare questi parametri alla cieca.
- HTTPS e `GYM_COOKIE_SECURE=true` in produzione, backend raggiungibile soltanto tramite il percorso previsto, attendibilità degli header inoltrati dal proxy.
- Rate limiting per IP al proxy/ingresso: serve verificare come Northflank comunica l’IP reale per evitare di limitare tutti gli utenti come se fossero un solo proxy. Il blocco per account è verificato; non equivale a protezione completa contro traffico distribuito o tentativi su nomi inesistenti.
- Sessioni servlet in memoria: con più repliche backend servono affinità o session store condiviso. Un riavvio può richiedere nuovo login; l’allenamento resta persistito nel DB.
- Scansione completa di dipendenze Maven e immagini container, backup e aggiornamenti periodici. Un tag Docker aggiornato non certifica che ogni pacchetto del container sia privo di vulnerabilità.

## Fonti tecniche

- Advisory source-map-js: https://github.com/advisories/GHSA-68fv-2mgg-jv7q
- Ereditarietà add_header: https://nginx.org/en/docs/http/ngx_http_headers_module.html
- Advisory ufficiali Nginx: https://nginx.org/en/security_advisories.html
- Password storage Spring Security: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
