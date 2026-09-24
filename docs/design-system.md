# Design system GymPlanner

Generato con la skill **ui-ux-pro-max** (`--design-system "fitness gym workout tracker mobile app"`)
e adattato a un'app operativa (vedi ADR 0005).

- **Stile:** Vibrant & Block-based, blocchi netti e alto contrasto.
- **Colori:** primario arancio `#F97316` con testo `#0F172A` (contrasto 7.3:1); testo arancio su
  sfondo chiaro `#C2410C` (5.2:1); successo `#15803D`; errore `#B91C1C`; informazione
  `#1D4ED8`. Tema scuro con varianti chiare dei colori di stato.
- **Tipografia:** Barlow Condensed (titoli e numeri del timer), Barlow (testo), base 16 px.
- **Interazione:** target minimi 44×44 px (64 px per `Fine serie`), focus visibile di 3 px,
  transizioni di 180 ms disattivate con `prefers-reduced-motion`.
- **Layout:** mobile first da 360 px; barra di navigazione inferiore (massimo 5 voci) sotto i
  900 px, barra superiore oltre.
- **Checklist prima della consegna:** nessuna emoji come icona (SVG Lucide), stati espressi con
  testo e icona, contrasto AA, focus visibile, niente scorrimento orizzontale a 360 px.
