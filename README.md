# GymPlanner

Web app amministrata per creare e assegnare schede di allenamento in palestra.
L'ADMIN gestisce account, cataloghi e schede; lo USER sceglie i giorni, riceve le sessioni in
rotazione, si allena serie per serie con il timer di recupero e consulta lo storico.

| Documento | Contenuto |
| --- | --- |
| [`DOCUMENTAZIONE_IMPLEMENTAZIONE_COMPLETA.md`](DOCUMENTAZIONE_IMPLEMENTAZIONE_COMPLETA.md) | documentazione completa del progetto |
| [`GUIDA_TEST_MANUALE.md`](GUIDA_TEST_MANUALE.md) | prova manuale passo passo |
| [`CLAUDE.md`](CLAUDE.md) | contesto per Claude Code e regole di sviluppo |
| [`MEGA_DOCUMENTAZIONE_GYM_PLANNER_PER_CLAUDE_CODE.md`](MEGA_DOCUMENTAZIONE_GYM_PLANNER_PER_CLAUDE_CODE.md) | specifica |
| [`docs/`](docs/) | architettura, API, decisioni (ADR), avanzamento, design system |

**Stack:** Java 21 · Spring Boot 4 · PostgreSQL 16 · Flyway · React 19 · TypeScript · Vite ·
TanStack Query · Playwright.

## Guida rapida

### Prerequisiti

- **Java 21** (JDK), ad es. `sudo apt install openjdk-21-jdk`. Verifica: `java -version`.
- **Node.js 22+** e npm. Verifica: `node -v`.
- **Docker** con Docker Compose (per PostgreSQL e per i test di integrazione).
- Git.

### Configurazione

```bash
git clone https://github.com/antonio-de-santis-dev/Scheda-Palestre.git
cd Scheda-Palestre
cp .env.example .env        # Windows: copy .env.example .env
```

Apri `.env` e imposta almeno:

- `POSTGRES_PASSWORD` e `DB_PASSWORD` **uguali**;
- `GYM_ADMIN_PASSWORD` con **almeno 8 caratteri, lettere e cifre** (es. `Admin12345`).

I valori di `.env.example` sono solo dimostrativi e `.env` non va mai committato.

### Avvio PostgreSQL

```bash
docker compose up -d postgres
docker compose ps           # postgres deve risultare "healthy"
```

Database e utente vengono creati dal container; le tabelle le crea Flyway all'avvio del backend.

### Avvio backend

Linux/macOS:

```bash
cd backend
set -a && source ../.env && set +a
./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
cd backend
Get-Content ..\.env | ForEach-Object { if ($_ -match '^\s*([^#][^=]*)=(.*)$') { Set-Item "env:$($matches[1].Trim())" $matches[2].Trim() } }
.\mvnw.cmd spring-boot:run
```

Il backend è pronto quando compare `Started GymPlannerApplication` (porta 8080).

### Avvio frontend

In un secondo terminale:

```bash
cd frontend
npm install
npm run dev
```

Apri **http://localhost:5173** (Vite inoltra `/api` al backend).

### Login iniziale

Accedi con `GYM_ADMIN_USERNAME` / `GYM_ADMIN_PASSWORD` del file `.env`: al primo accesso viene
chiesto di cambiare la password. Poi segui [`GUIDA_TEST_MANUALE.md`](GUIDA_TEST_MANUALE.md).

### Esecuzione test

```bash
cd backend && ./mvnw verify                      # 189 test: unit, integrazione (Docker), ArchUnit
cd frontend && npm test                          # 51 test Vitest
cd frontend && npm run lint && npm run typecheck && npm run build
cd frontend && npx playwright install chromium && npm run e2e   # con backend e frontend avviati
```

Dettagli degli E2E: [`frontend/e2e/README.md`](frontend/e2e/README.md).

### Arresto

- Backend e frontend: `Ctrl+C` nei rispettivi terminali.
- PostgreSQL: `docker compose down` (i dati restano nel volume `pgdata`; `docker compose down -v`
  li cancella e al prossimo avvio verrà ricreato l'ADMIN iniziale).

### Avvio completo con Docker (alternativa)

```bash
docker compose --profile app up -d --build
```

Apri **http://localhost:8081** (nginx serve il frontend e inoltra `/api` al backend).
Arresto: `docker compose --profile app down`.

## Problemi frequenti

| Errore | Soluzione |
| --- | --- |
| `release version 21 not supported` | Maven usa Java 17: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| `required variable POSTGRES_PASSWORD is missing` | `.env` vuoto o comando lanciato fuori dalla cartella del progetto |
| `GYM_ADMIN_PASSWORD does not satisfy the password policy` | usare una password con lettere **e** cifre |
| `password authentication failed` | `docker compose down -v` e ripetere `up -d postgres` |

Altri casi: sezione *Troubleshooting* di
[`DOCUMENTAZIONE_IMPLEMENTAZIONE_COMPLETA.md`](DOCUMENTAZIONE_IMPLEMENTAZIONE_COMPLETA.md).
