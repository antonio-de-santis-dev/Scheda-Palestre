# ADR 0011 - Durata relativa e avviso sonoro di recupero

La durata consigliata è un numero facoltativo di settimane intere (1–520) sulla scheda
condivisa. Ogni assegnazione ha una propria data di scadenza: `start_date + duration_weeks`.
L'avviso compare dal giorno `scadenza - 7 giorni`, nel fuso della palestra; fino al giorno
stesso della scadenza dice «in scadenza», dal giorno successivo «terminata». Non blocca
l'allenamento. Per esempio: inizio 01/01/2026, tre settimane, scadenza 22/01/2026,
primo avviso 15/01/2026. Due utenti con date di inizio diverse hanno avvisi diversi.

V10 aggiunge `duration_weeks` e lascia la vecchia colonna `expires_on` inalterata per
conservare i valori precedenti. Non esiste una conversione affidabile da una data
assoluta condivisa alle settimane relative per assegnazioni con inizi diversi: le schede
già presenti rimangono senza durata relativa finché l'ADMIN non imposta le settimane.

La guida vocale è rimossa. L'utente può attivare un avviso audio facoltativo alla fine
del recupero e scegliere tre clip brevi ricavate dagli allegati. L'opzione e il suono
sono salvati nel browser; un'anteprima al clic aiuta ad abilitare la riproduzione nei
browser che la limitano. Il messaggio testuale e la vibrazione restano disponibili.
