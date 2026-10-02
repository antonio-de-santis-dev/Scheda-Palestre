# Design system GymPlanner

Interfaccia aggiornata dal file approvato **GymPlanner – Mockup definitivo definitivo.html**,
conservando componenti React, regole applicative e API del programma.

- **Colori:** arancio `#F97316` con testo scuro `#0F172A`; testo arancio su superfici chiare
  `#C2410C`; sfondo `#F1F5F9`, superfici bianche, bordi `#E2E8F0`, navigazione `#111827`.
  Successo, errore e avvisi hanno colori e descrizioni testuali dedicati.
- **Tipografia:** Barlow Condensed per titoli e numeri, Barlow per testi, base 16 px.
  I font sono ospitati localmente.
- **Tema:** preferenza automatica del dispositivo oppure chiaro/scuro scelto dalla testata
  o dal profilo. La scelta resta nel browser; una scelta manuale prevale sul dispositivo.
- **Layout:** mobile first da 320 px; navigazione inferiore con cinque voci sotto 760 px.
  Da 760 px il pulsante menu apre un pannello laterale modale. La larghezza massima del
  contenuto è 70 rem; calendari, editor e profili usano due colonne dove lo spazio lo permette.
- **Interazione:** pulsanti alti almeno 44 px, pulsante principale di allenamento alto 60 px,
  focus visibile, navigazione da tastiera e supporto a `prefers-reduced-motion`.
  Il menu gestisce Escape, clic sullo sfondo e ripristino del focus tramite un dialog nativo.
- **Sessioni:** tab A/B/C selezionabili con frecce, Home ed End. Le bozze dell'editor restano
  disponibili cambiando sessione. Le sessioni in sola lettura hanno esercizi numerati.
- **Allenamento:** esercizio corrente, avanzamento delle serie e recupero circolare a sinistra;
  percorso degli esercizi a destra su desktop e sotto su telefono. I tempi vengono dal server.
- **Calendario:** mese completo, settimane da lunedì a domenica e dettaglio del giorno selezionato.
  Esiti e sessioni programmate hanno testi accessibili oltre agli indicatori visivi.

I token comuni sono in `frontend/src/shared/styles/tokens.css`, i componenti base in
`global.css` e le composizioni del mockup in `design.css`. Dettagli e verifiche sono riportati
in [design-mockup.md](design-mockup.md).
