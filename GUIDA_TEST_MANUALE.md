# Guida al test manuale di GymPlanner

Questa guida ti accompagna, passo passo, nella prova completa del programma sul tuo PC.
Per ogni passo è indicato **cosa fare** e **cosa devi vedere**. Spunta le caselle man mano.

> Prerequisito: database, backend e frontend avviati (vedi `README.md`, sezione *Guida rapida*).
> Apri **http://localhost:5173**. Per simulare un telefono: `F12` → `Ctrl+Shift+M` → larghezza 360.
> Consiglio: usa una finestra normale per l'ADMIN e una **finestra anonima** per lo USER, così
> puoi restare collegato con entrambi.

---

## Parte A - Primo accesso ADMIN

1. [ ] Vai su http://localhost:5173 → vieni portato su **/login**.
2. [ ] Inserisci una password sbagliata → messaggio **"Credenziali non valide."** (generico, non dice
   se l'utente esiste).
3. [ ] Accedi con `GYM_ADMIN_USERNAME` / `GYM_ADMIN_PASSWORD` del file `.env`
   → vieni portato su **Cambia password** con l'avviso "Cambio obbligatorio".
4. [ ] Prova una nuova password corta (es. `abc`) → errore "Almeno 8 caratteri".
5. [ ] Prova due password diverse nei campi → errore "Le password non coincidono".
6. [ ] Inserisci una password valida (es. `AdminNuova1`) → arrivi alla **Dashboard** con i
   collegamenti rapidi (nessuna statistica).

## Parte B - Cataloghi (ADMIN)

7. [ ] Menu **Gruppi** → aggiungi `Petto`, `Dorso`, `Gambe`. Compaiono nell'elenco con badge "Attivo".
8. [ ] Prova ad aggiungere `petto` (minuscolo) → errore **"Nome già in uso."**, il testo resta nel campo.
9. [ ] Menu **Esercizi** → aggiungi `Panca piana`, `Croci ai cavi`, `Trazioni`, `Rematore`, `Squat`.
10. [ ] Clicca **Modifica** su `Squat`, rinominalo in `Squat bilanciere` → salvato.
11. [ ] Clicca **Disattiva** su `Croci ai cavi` → badge "Disattivato". Con il filtro *Stato: Disattivati*
    lo ritrovi; poi **Riattiva**.

## Parte C - Utenti (ADMIN)

12. [ ] Menu **Utenti** → **Nuovo utente**: Nome `Mario`, Cognome `Rossi`, Username `mario`,
    Email `mario@example.test`, telefono vuoto → **Crea utente**.
13. [ ] Compare un riquadro giallo con la **password temporanea**: annotala (viene mostrata una sola volta).
14. [ ] Crea un secondo utente `anna` / `anna@example.test` e annota anche la sua password.
15. [ ] Prova a creare di nuovo `MARIO` → errore "Username già in uso."
16. [ ] Cerca "ros" nel campo *Cerca* → trovi Mario Rossi.
17. [ ] Apri Mario → vedi stato, dati anagrafici e sezione "Schede assegnate" (vuota).

## Parte D - Scheda (ADMIN)

18. [ ] Menu **Schede** → **Nuova scheda**: nome `Principianti` → **Crea e apri l'editor**.
19. [ ] Il badge dice **"Incompleta"** e un avviso spiega che mancano sessioni/esercizi.
20. [ ] Premi **Aggiungi sessione** due volte (campo vuoto) → compaiono `1. Giorno 1` e `2. Giorno 2`.
21. [ ] In *Giorno 1* scegli il gruppo `Petto` → **Aggiungi sezione**.
22. [ ] **Aggiungi esercizio a Petto**: `Panca piana`, Serie `3`, Ripetizioni `10`, Recupero `60` → salvato,
    riga "3 × 10 · recupero 1:00".
23. [ ] Aggiungi `Trazioni` con **A cedimento (MAX)** spuntato, Serie `2`, Recupero `90` → riga "2 × MAX".
24. [ ] In *Giorno 2* aggiungi la sezione `Dorso` con `Rematore` 3×12, recupero 45.
25. [ ] Il badge diventa **"Pronta"**.
26. [ ] Serie personalizzate: su `Panca piana` clicca ✏️, spunta **Personalizza ogni serie** e imposta
    serie 1 = 12, serie 2 = 10, serie 3 = MAX → salva → la riga mostra "12 / 10 / MAX".
27. [ ] Rimodifica `Panca piana` e porta le serie da 3 a 2 → al salvataggio compare la conferma
    **"Ridurre il numero di serie?"** → conferma.
28. [ ] Riordino: usa le frecce ↑/↓ su una sessione → l'ordine cambia e resta dopo il ricaricamento (F5).
29. [ ] Prova ad aggiungere di nuovo `Petto` in *Giorno 1* → il gruppo non è più proponibile nell'elenco.
30. [ ] Esercizi disattivati: disattiva `Rematore` nel catalogo, torna nell'editor → vedi il badge
    "disattivato" ma la scheda resta "Pronta"; `Rematore` non compare più tra gli esercizi aggiungibili.
    Riattivalo.

## Parte E - Assegnazione (ADMIN)

31. [ ] Menu **Schede** → **Assegna** su `Principianti`.
32. [ ] Seleziona **Mario** e **Anna**, data di inizio = oggi, *Attiva subito* spuntato → **Assegna**
    → "Scheda assegnata a 2 utenti." e due righe con badge **"Attiva"**.
33. [ ] Torna nell'editor: in alto l'avviso **"Scheda condivisa: 2 utenti attivi"**.
34. [ ] Crea una seconda scheda, ad esempio **Duplica** `Principianti` → si apre `Principianti (copia)`
    con la stessa struttura e nessun assegnatario.

## Parte F - Primo accesso e giorni (USER Mario)

35. [ ] In finestra anonima accedi come `mario` con la password temporanea → **cambio password obbligatorio**.
36. [ ] Dopo il cambio arrivi su **Oggi**: messaggio **"Scegli i tuoi giorni"**.
37. [ ] Prova ad aprire http://localhost:5173/admin → pagina **"Accesso negato"**.
38. [ ] **Schede** → vedi `Principianti` con badge "Attiva"; aprendola la vedi **in sola lettura** (MAX visibile).
39. [ ] **Giorni** (dal menu in alto o da *Oggi*): seleziona **tutti e 7 i giorni** (così oggi è di
    allenamento) → **Salva giorni** → "Giorni salvati".
40. [ ] **Calendario** → i giorni alternano `Giorno 1`, `Giorno 2`, `Giorno 1`… (rotazione).

## Parte G - Allenamento guidato e timer (USER Mario)

41. [ ] **Oggi** → vedi l'anteprima di `Giorno 1` (sezioni, esercizi, valori) e **Inizia allenamento**.
42. [ ] Premi **Inizia allenamento** → schermata allenamento: esercizio corrente, **Serie 1/2**,
    ripetizioni, recupero, grande pulsante **Fine serie** visibile senza scorrere (anche a 360 px).
43. [ ] Premi **Fine serie** → parte il **timer "Recupero"** con conto alla rovescia.
44. [ ] Premi **F5** durante il recupero → il timer continua dal punto giusto (non riparte da capo).
45. [ ] Cambia scheda del browser per 20 secondi e torna → il timer è riallineato.
46. [ ] Premi **Fine serie** anche prima che il timer finisca → è permesso (timer indicativo).
47. [ ] Su `Trazioni` premi **Salta esercizio** → conferma → l'esercizio risulta **"Saltato"**.
48. [ ] Completa le serie rimanenti → schermata **"Allenamento completato!"** con il riepilogo.
49. [ ] Torna su **Oggi** → per oggi vedi il badge "Completato" e "Vedi il riepilogo"; non puoi
    ripartire con lo stesso allenamento.

## Parte H - Storico e profilo (USER Mario)

50. [ ] **Storico** → l'allenamento di oggi con "x completati, 1 saltati".
51. [ ] Aprilo → ogni esercizio con il suo stato; `Trazioni` mostra **"Saltato"**; le serie mostrano
    "✓ Completata" o "— Non svolta".
52. [ ] **Profilo** → nome, username, email, ruolo in sola lettura; inserisci un telefono → "Telefono salvato."
53. [ ] Nel profilo prova un telefono non valido (`abc`) → errore sotto il campo.
54. [ ] Cambia password dal profilo (password attuale errata → errore; poi corretta → "Password aggiornata").

## Parte I - Lo storico non cambia se l'ADMIN modifica la scheda

55. [ ] Da ADMIN modifica `Principianti`: cambia le ripetizioni della Panca o elimina `Giorno 1`.
56. [ ] Da Mario riapri lo **Storico** → l'allenamento passato mostra **ancora i valori originali**.

## Parte J - Sicurezza e gestione account (ADMIN + USER)

57. [ ] Da ADMIN apri **Utenti → Anna → Disattiva** (conferma). Se Anna era collegata, alla prossima
    azione viene riportata al login; non può più accedere.
58. [ ] **Riattiva** Anna → può accedere di nuovo.
59. [ ] **Reset password** su Mario → nuova password temporanea mostrata una volta; la sessione di
    Mario viene chiusa e al nuovo accesso deve cambiare password.
60. [ ] Da ADMIN prova a disattivare **te stesso** → pulsante disabilitato / messaggio di errore.
61. [ ] Sbaglia la password di un utente **5 volte** → anche la password giusta viene rifiutata per 15 minuti.
62. [ ] Elimina la scheda `Principianti` (conferma che avvisa della chiusura delle assegnazioni) →
    finisce tra le "Schede eliminate"; Mario su *Oggi* vede "Nessuna scheda attiva"; lo storico resta.
    Poi **Ripristina** la scheda (le assegnazioni restano chiuse: si riassegna).

## Parte K - Accessibilità e mobile

63. [ ] A 360 px di larghezza nessuna pagina scorre in orizzontale; la barra in basso mostra le voci
    principali dello USER.
64. [ ] Con il solo tasto **Tab** si raggiungono tutti i pulsanti, con un contorno di focus visibile.
65. [ ] Gli stati (Attivo, Saltato, Completato…) hanno sempre **testo e icona**, non solo colore.
66. [ ] Con il tema scuro del sistema operativo l'app passa automaticamente ai colori scuri.

---

### Se qualcosa non va

Annota: numero del passo, cosa hai fatto, cosa ti aspettavi, cosa è successo (messaggio o screenshot)
e, se il backend mostra un errore, le ultime righe del suo terminale.
