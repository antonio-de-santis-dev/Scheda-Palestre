# ADR 0012 — Durata degli allenamenti e recupero V2

Autorizzazione: il 2 ottobre 2026 il proprietario ha richiesto tutte le aree della roadmap
per fasi nel branch `SviluppoV2`, aggiungendo il tempo impiegato per ogni allenamento.
Base: `delpy` / `desaig`, commit `4f1f997`.

## Durata

È tempo trascorso, non tempo di lavoro muscolare: `finishedAt - startedAt` per allenamenti
completati o interrotti; `serverTime - startedAt` durante l'esecuzione. Secondi interi,
mai negativi, nessun reset a mezzanotte. Include recupero, pausa del recupero e tempo trascorso
fuori dalla pagina. Gli allenamenti lasciati aperti durante la notte continuano a contare.
I vecchi allenamenti conclusi hanno già i timestamp e non richiedono backfill della durata.
Il valore resta calcolato, evitando una seconda colonna da mantenere sincronizzata.

## Recupero

V12 aggiunge stato persistente al workout senza cambiare serie/ripetizioni dello snapshot:
scadenza, millisecondi residui in pausa, durata visualizzata e revisione.
Il recupero iniziale resta quello della serie. Pausa conserva i millisecondi residui;
ripresa crea una nuova scadenza dal tempo server. Estensione ammessa fra 1 e 300 secondi
per richiesta, fino a un'ora residua; la UI propone +30 secondi. Salto esplicito con conferma.
La fine serie resta bloccata anche quando il recupero è in pausa. Interrompere e saltare
esercizi restano consentiti. Conclusione e interruzione cancellano il recupero.

Le mutazioni acquisiscono lo stesso lock pessimistico della fine serie e verificano
`expectedVersion`. Richieste obsolete o ripetute ricevono 409 `REST_STATE_CHANGED` e
il frontend ricarica lo stato. Non sono ritentate automaticamente: un'estensione non deve
essere applicata due volte dopo un errore di rete. Le nuove serie incrementano la revisione.
V12 conserva il recupero degli allenamenti già in corso, ricavandolo dall'ultima serie completata.

Audio e vibrazione mantengono i limiti del browser; non vengono annunciati durante la pausa.
