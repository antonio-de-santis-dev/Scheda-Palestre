# Musica durante l’allenamento

Branch: `musica`, creato da `delpy`. Nessun merge o deploy automatico su `delpy`.

## Uso

1. Aprire **Musica** dal menu, oppure **Aggiungi canzoni o playlist** nella pagina Oggi.
2. Inserire un nome e il link HTTPS condiviso da YouTube, quindi salvare.
3. Tornare a Oggi e iniziare o riprendere l’allenamento.
4. Nella sezione Musica selezionare un link salvato e premere Play nel player YouTube.
5. Su PC il video si trova a destra della scelta musicale, sopra l’elenco esercizi. Su telefono è tra la scelta musicale e la scheda della serie. Le istruzioni si aprono con **Come funziona**; non ci sono finestre flottanti. **Ferma e chiudi musica** interrompe la riproduzione.

Durante l’allenamento non vengono mostrati moduli per aggiungere o eliminare link. La gestione resta nella pagina Musica. Un link watch con parametro `list` viene salvato come playlist intera: per salvare il singolo video rimuovere `list` dal link. Supportati link watch, youtu.be, playlist, shorts, live e embed, anche da YouTube Music.

I link sono personali e persistono nel database, disponibili su tutti i dispositivi dello stesso account. Non vengono scaricati file audio/video e non serve una chiave YouTube Data API. I nomi dei link salvati sono inseriti dall’utente. Aprendo l’elenco di una playlist, il browser recupera i titoli pubblici da YouTube oEmbed, senza cookie e progressivamente per i brani dell’elenco, con al massimo quattro richieste contemporanee. Nessuna richiesta server verso URL forniti dagli utenti.

## Riproduzione e compatibilità

Il player ufficiale viene caricato soltanto dopo la selezione del link; l’avvio resta manuale. Video e playlist dipendono da disponibilità, restrizioni e autorizzazione all’incorporamento stabilite da YouTube e dal proprietario. Pubblicità e controlli ufficiali restano intatti. Non viene offerta riproduzione audio nascosta o a schermo bloccato. Scorrere gli esercizi nella stessa pagina non interrompe la riproduzione. Il player va in pausa quando la pagina passa in background o un dialogo copre il contenuto; la ripresa è manuale. Cambiando pagina, concludendo/interrompendo l’allenamento o chiudendo il player la riproduzione si interrompe.

La CSP Nginx consente esclusivamente i domini necessari al player: script `www.youtube.com`, `s.ytimg.com` e frame `www.youtube-nocookie.com`; le richieste di metadati sono consentite esclusivamente su `https://www.youtube.com/oembed`. Nessun `unsafe-inline` aggiunto per JavaScript. Il Referer del player e dello script usa `strict-origin-when-cross-origin`; la policy globale resta `same-origin`. Il sito mantiene `frame-ancestors 'none'` e `X-Frame-Options DENY`.

## Backend

Migrazione Flyway V22 crea `music_entries`; non modifica tabelle di allenamenti, serie e recuperi. Vincolo univoco per account/tipo/ID YouTube. Massimo 200 link per account (422 `MUSIC_LIMIT_REACHED`, controllo sotto `UserLock`). Gli account sono eliminati solo logicamente (ADR 0010), quindi il `ON DELETE CASCADE` non scatta mai: i link vengono cancellati dal modulo `music` alla ricezione di `UserEvents.UserDeleted`.

API protette dalla sessione e dal ruolo USER:

- GET `/api/me/music`: lista personale.
- POST `/api/me/music`: `{ "title": "...", "url": "https://..." }`.
- DELETE `/api/me/music/{id}`: elimina soltanto un proprio elemento; 404 per ID di altri utenti. Il frontend considera il 404 come già eliminato (es. da un altro dispositivo) e mostra gli altri errori dentro la finestra di conferma.

Validazione server: HTTPS, host esatti, no credenziali/porte arbitrarie, identificatori verificati, nome non vuoto e massimo 120 caratteri, URL massimo 2048. Query parametrizzate. CSRF e protezioni di sessione esistenti restano applicate.

## Verifica manuale su telefono

- Aggiungere un video e una playlist pubblica; ricaricare la pagina e verificare la persistenza.
- Aprire lo stesso account su un secondo dispositivo; verificare la lista.
- Iniziare una sessione, selezionare musica, premere Play e completare una serie: riproduzione non deve ripartire dall’inizio.
- Scorrere esercizi con il player visibile; controllare accessibilità di Fine serie e controlli recupero.
- Verificare il suono di fine recupero insieme alla musica su Safari iPhone e Chrome Android, anche con cuffie Bluetooth. La gestione dell’audio dipende dal dispositivo.
- Aprire la conferma Salta esercizio/Interrompi: musica in pausa; annullare e riprendere dal player.
- Passare a un’altra app: pausa; tornare e premere Play.
- Chiudere il player e terminare l’allenamento: audio interrotto.
- Provare un link non YouTube, un duplicato, un contenuto privato/non incorporabile e assenza di rete.

## Controlli automatici

Test frontend: salvataggio tramite API, scelta senza modulo durante la sessione, continuità del player nei rerender, pausa sui dialoghi, chiusura, URL video/playlist e assenza di autoplay.

Test backend: formati link e host malevoli; persistenza, duplicati, isolamento tra utenti, cancellazione, accesso anonimo/ADMIN e validazione. Il modulo `music` è incluso nelle regole ArchUnit. La pipeline Verify si attiva anche sui push del branch `musica` e controlla backend PostgreSQL, frontend e container Nginx.

## Correzioni dopo la revisione

- Eliminazione dell’account: i link e i titoli scritti dall’utente vengono rimossi (prima restavano nel database, perché l’eliminazione è logica).
- Limite di 200 link per account: senza limite un utente poteva riempire il database e l’elenco, caricato senza paginazione.
- Pagina Musica: l’errore di eliminazione era mostrato dietro la finestra di conferma (invisibile); ora è dentro la finestra. Un link già eliminato altrove non produce più un errore.
- Messaggi tecnici del backend in inglese come nel resto dell’API; il testo per l’utente arriva dal codice (`MUSIC_DUPLICATE`, `MUSIC_LIMIT_REACHED`).
- Nota sulla CSP: autorizzare `https://www.youtube.com` fra gli script è necessario per l’API ufficiale, ma amplia leggermente la superficie in caso di XSS. Restringerla ai soli percorsi `/iframe_api` e `/s/player/` è possibile, va però provata su un dispositivo reale prima del rilascio.

## Scelta dei brani e riproduzione casuale

Per una playlist, sotto il video compaiono **Casuale** e **Scegli un brano**. I controlli si attivano quando YouTube restituisce l’elenco, talvolta dopo il primo Play. L’elenco mostra i titoli disponibili, il brano attuale in un unico elenco scorrevole, senza paginazione. Se un titolo manca, resta il numero del brano; i contenuti privati o non incorporabili restano soggetti alle restrizioni di YouTube.

**Casuale** usa `setShuffle` del player ufficiale: il brano corrente continua e cambia l’ordine dei successivi. Disattivando il pulsante torna l’ordine originale. La scelta del brano usa l’indice nell’ordine attuale, anche in modalità casuale. I cambi dai comandi nativi e l’avanzamento automatico aggiornano l’indicazione del brano. Il player non viene ricreato da questi controlli. Una nuova playlist riparte in modalità sequenziale.

L’elenco è inizialmente chiuso per mantenere compatta la vista mobile. Scegliendo un brano il video viene riportato in vista; restano le pause su finestra di conferma e pagina nascosta. Lo scorrimento nella stessa pagina non mette più in pausa il video. Per cambiare brano si possono usare l’elenco e i comandi nativi di YouTube.

Verifica manuale: aprire una playlist pubblica, premere Play, scegliere un brano dall’elenco, attivare Casuale e passare al successivo. Disattivare Casuale e controllare l’ordine ripristinato. Provare anche i comandi nativi, una playlist lunga e un titolo non disponibile. Le correzioni della revisione Claude Code (limite link, eliminazione account, errori nel dialogo) restano applicate.

### Revisione dei controlli playlist (Claude Code)

- Aprendo **Scegli un brano** l’elenco porta in vista il brano in riproduzione (prima ripartiva sempre dalla pagina 1, anche con il brano 43 in corso).
- Durante il caricamento del titolo il lettore di schermo annuncia "caricamento del titolo" invece di "titolo non disponibile".
- Rimossi `previousVideo`/`nextVideo` dall’interfaccia del player: dopo la semplificazione dei controlli non erano più usati.
- Da verificare su dispositivo reale (la rete dell’ambiente di revisione blocca YouTube): che `https://www.youtube.com/oembed` risponda con gli header CORS e senza reindirizzamenti (per esempio verso la pagina dei consensi UE). In caso contrario i titoli restano "Brano N": la scelta per numero continua a funzionare.

### Elenco unico dei brani

Rimossa la paginazione: tutti i brani restituiti dal player sono presenti nello stesso elenco, con la grafica e lo scorrimento interno già esistenti. Il brano attuale resta evidenziato e viene portato in vista aprendo l’elenco. I titoli vengono recuperati progressivamente con un massimo di quattro richieste contemporanee; un brano può essere scelto anche prima che arrivi il titolo.

### Revisione dell’elenco unico dei brani (Claude Code)

- I titoli vengono richiesti solo per i brani visibili nell’elenco (più un margine di scorrimento), non per l’intera playlist: aprire una playlist di 200 brani non invia più 200 richieste a YouTube. Senza `IntersectionObserver` (browser molto vecchi) resta il comportamento precedente.
- Coda delle richieste (massimo 4 alla volta): una richiesta annullata passa subito il posto alla successiva, così la coda non può bloccarsi.
- I brani non ancora caricati sono annunciati come "Brano N"; "titolo non disponibile" solo quando la richiesta è davvero fallita.
- Rimossi lo stile `.playlist-pages` non più usato e la ricerca lineare ripetuta degli ID (ora una mappa).
