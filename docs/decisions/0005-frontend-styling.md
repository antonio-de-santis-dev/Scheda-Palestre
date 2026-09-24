# ADR 0005 - Frontend: design system CSS globale a token

**Problema.** §5 lascia libera la scelta fra CSS modulare e "un sistema coerente deciso
all'avvio". Servono mobile first da 360 px, target di 44 px, contrasto AA e temi chiaro e scuro.

**Alternative.** CSS Modules per componente, Tailwind, design system CSS con token.

**Decisione.** Un unico design system CSS a token (`src/shared/styles/tokens.css` +
`global.css`) con classi di componente in stile BEM, generato partendo dalla skill
*ui-ux-pro-max* ("Vibrant & Block-based": arancio energia `#F97316`, verde successo, font
Barlow / Barlow Condensed installati in locale tramite `@fontsource`). Tema scuro con
`prefers-color-scheme`, animazioni ridotte con `prefers-reduced-motion`, icone SVG `lucide-react`.

**Motivazione.** Pochi componenti riutilizzati molte volte, nessuna dipendenza di build
aggiuntiva, token semantici verificati per il contrasto, nessuna richiesta a CDN esterne.

**Conseguenze.** I componenti usano solo token semantici; gli stati sono sempre espressi con
testo e icona, mai soltanto con il colore.
