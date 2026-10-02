# ADR 0015 — PWA, recupero password e avvisi recupero

Stato: implementato in SviluppoV2. Funzioni esterne disabilitate per default.

## Recupero password (Fase 5)

POST `/api/auth/forgot-password` con `{email}` ritorna sempre 202 per email sintatticamente
valide quando il servizio è configurato, anche inesistenti, disattivate o rate limited.
Servizio disabilitato o origine/mittente mancanti/non validi: 503 `RECOVERY_UNAVAILABLE`,
uguale per ogni account, senza simulare una conferma di invio.
CSRF obbligatorio. Richieste per finestra di un'ora: massimo 3 per email e 20 per indirizzo
remoto. L'indirizzo è quello ottenuto dal servlet, non un header inviato dal chiamante;
configurare correttamente il proxy se più utenti condividono l'indirizzo remoto.
Bucket SHA-256, cancellati dopo un'ora; nessuna email in chiaro nei bucket.

Solo account attivi, non eliminati: token CSPRNG di 256 bit, solo SHA-256 nel DB,
validità 20 minuti, monouso, associato a versione delle sessioni e indirizzo email.
Ogni nuova richiesta valida invalida il link precedente. Il link usa un fragment
`/reset-password#token=…`, non query string: non viene mandato in URL ai server/proxy.

POST `/api/auth/reset-password` con `{token,newPassword}`: stessa policy password esistente,
password diversa dalla precedente. Lock sull'utente e rilettura del token contro doppio uso
simultaneo, scadenza/versione/email/account ricontrollati. Nuova password BCrypt,
reset lock login, incremento versione sessioni e rimozione del token. Nessun login automatico.
400 `RESET_LINK_INVALID` per link inesistente/usato/scaduto/revocato.

Invio dopo commit, asincrono con coda limitata, senza logging di token/email/credenziali.
SMTP STARTTLS richiesto, verifica identità server e timeout 5s. Un errore di invio non
rivela l'esistenza dell'account e non blocca il servizio allenamenti. Coda satura scarta
la richiesta di invio: l'utente deve poter riprovare, senza garanzia di consegna.
La health applicativa non dipende dalla raggiungibilità SMTP.

## PWA e push (Fase 4)

Manifest, icone 192/512, service worker registrato solo nella build produzione.
Cache versionata per build: solo risorse statiche `/assets/` e pagina offline generica.
Nessuna risposta API, HTML autenticato o azione utente offline salvata. Navigazioni network-first;
senza rete pagina di riconnessione, non una copia delle schede private.

Dal Profilo USER consenso esplicito; HTTPS e browser compatibile necessari. iOS:
installazione nella schermata Home, secondo supporto/versione del browser.
`GET /api/me/push/config` espone soltanto stato e chiave pubblica VAPID.
`POST /api/me/push/subscribe` accetta endpoint/keys della Push API;
`POST /api/me/push/unsubscribe` rimuove solo il dispositivo posseduto dal chiamante.
CSRF/ruolo USER/sessione invariati. Massimo 5 dispositivi, scadenza 90 giorni senza
rinnovo. Endpoint HTTPS solo provider Google, Mozilla e Apple, niente IP/host arbitrari,
porte custom o redirect HTTP. Chiavi validate per lunghezza/formato.

Gli avvisi riguardano **fine recupero**, anche con pagina chiusa. Il polling (default 10s)
seleziona recuperi terminati da meno di 120s, mai in pausa o allenamenti chiusi.
Claim atomico `SKIP LOCKED` e revisione registrata: al massimo un tentativo per recupero;
send fuori dal lock, ricontrollo revisione/stato prima dell'invio. Rimozione endpoint su
404/410; altri errori non vengono ritentati, notifiche best effort. TTL 120s.
Account disattivati/eliminati o password cambiata non ricevono push tramite le vecchie
sottoscrizioni (versione sessione). Logout prova a rimuovere server e browser subscription.
Messaggio generico nella lock screen, nessun nome/peso/risultato; click solo sulla pagina
Oggi della stessa origine. La consegna dipende da rete, provider e sistema operativo:
non è un cronometro affidabile né un promemoria pianificato dei giorni di allenamento.

Migrazioni V15/V16 additive. Dipendenze: Spring Mail, WebPush Java, provider BC aggiornato.

## Attivazione (Northflank o altro ambiente)

Nessuna credenziale reale è presente in repository/documentazione. Impostare le variabili
**nel backend**, come segreti per password SMTP e chiave privata VAPID:

```properties
GYM_RECOVERY_ENABLED=true
GYM_PUBLIC_URL=https://URL-PUBBLICO-FRONTEND
GYM_MAIL_FROM=ACCOUNT-GMAIL
GYM_SMTP_HOST=smtp.gmail.com
GYM_SMTP_PORT=587
GYM_SMTP_USERNAME=ACCOUNT-GMAIL
GYM_SMTP_PASSWORD=PASSWORD-PER-APP-SENZA-SPAZI
GYM_PUSH_ENABLED=true
GYM_VAPID_PUBLIC_KEY=CHIAVE-PUBBLICA
GYM_VAPID_PRIVATE_KEY=CHIAVE-PRIVATA-RISERVATA
GYM_VAPID_SUBJECT=mailto:ACCOUNT-GMAIL
```

Generare una coppia VAPID sulla propria macchina:
`node scripts/generate-vapid.mjs`. Scrive `.env.vapid` (0600, ignorato da git), non
sovrascrive una coppia esistente e non stampa le chiavi. Copiare le due variabili nei
segreti/variabili backend e conservare la coppia: cambiarla richiede nuove sottoscrizioni.
Il frontend pubblico deve avere origine HTTPS, proxy `/api` stesso dominio e build pubblicata.
Per il solo recupero password in sviluppo è ammesso HTTP su localhost/127.0.0.1/::1;
PWA/push mantengono i propri requisiti di origine sicura e build.
In locale dev il worker non si registra; usare build + preview per provare la PWA.

## Verifiche e limiti

Suite backend: privacy risposta, hash/scadenza/rimpiazzo link, policy, sessioni, CSRF/rate
limiting; ownership/allowlist/dispositivi, recuperi in pausa, claim singolo, rimozione 410,
revoca sessioni; costruzione cifrata VAPID senza rete esterna.
Frontend: invio/validazione/ripristino, link scaduto, consenso e rollback push,
service worker non intercetta API/scritture/cross-origin e usa offline generico.
Non equivalgono a una consegna reale SMTP o push su dispositivi; provarla nell'ambiente
HTTPS dopo configurazione. Nessun invio di prova a indirizzi reali viene eseguito dai test.

## Configurazione email locale

Dalla root: `python3 scripts/configure-recovery.py --url http://localhost:5173 --sender ACCOUNT-GMAIL`.
Password per app richiesta con input nascosto (non passarla come argomento o nel codice).
Il comando aggiorna soltanto le variabili di recupero nella `.env` ignorata da git,
preserva le variabili DB, normalizza i separatori della password e scrive con permessi 0600.
Riavviare il backend, poi richiedere il recupero usando l'email dell'account GymPlanner
che si vuole recuperare: il mittente SMTP non sostituisce l'email registrata nel DB.
Un 400 richiede di leggere il JSON Problem Details (code/errors) della risposta nella
scheda Network del browser; un 401 di `/api/auth/me` prima del login è normale.

### Diagnosi invio SMTP

Da root: `python3 scripts/check-smtp.py`. Legge la `.env` locale (variabili del terminale
hanno precedenza), verifica connessione, STARTTLS/certificato e autenticazione.
Non invia email e non stampa username/password, risposte SMTP o token.
Il backend registra un codice diagnostico fisso tra parentesi:
`SMTP_AUTHENTICATION_FAILED`, `SMTP_TLS_FAILED`, `SMTP_TIMEOUT`, `SMTP_DNS_FAILED`,
`SMTP_CONNECTION_FAILED` oppure `SMTP_DELIVERY_FAILED`. Nessuna exception completa viene loggata.
Se la verifica SMTP riesce ma manca l'email, confrontare configurazione effettiva del backend,
riavvio dopo modifica `.env`, email dell'account, spam e limiti richieste (3/email/ora).

## Diagnosi dell'invio Java dopo SMTP_OK

`scripts/check-smtp.py` prova connessione, TLS e autenticazione Python; non prova
la serializzazione o l'invio del messaggio dal backend Java. Un esito SMTP_OK
non garantisce la consegna del recupero password.

Il log di `RecoveryMail` include ora `types=[...]`: solo i nomi delle classi
delle eccezioni annidate e, per i rifiuti SMTP Angus, il codice numerico.
Non include messaggi di eccezione, risposte testuali SMTP, indirizzi,
credenziali o token. Distingue inoltre mittente/destinatario/messaggio rifiutati
e sender/provider mancanti. Dopo aggiornamento e riavvio, una nuova richiesta
di recupero fornisce la riga necessaria a diagnosticare l'errore Java.

`RecoverySmtpTransportTest` usa l'autoconfigurazione reale e il trasporto JavaMail
contro un server SMTP locale di test, verificando invio e contenuto UTF-8.
Non contatta Gmail e non dimostra la consegna nella casella dell'utente.
