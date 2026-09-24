# GymPlanner

Web app amministrata per creare e assegnare schede di allenamento in palestra.
L'ADMIN gestisce account, cataloghi e schede; lo USER sceglie i giorni, riceve le sessioni in
rotazione, si allena serie per serie con il timer di recupero e consulta lo storico.

- Specifica: [`MEGA_DOCUMENTAZIONE_GYM_PLANNER_PER_CLAUDE_CODE.md`](MEGA_DOCUMENTAZIONE_GYM_PLANNER_PER_CLAUDE_CODE.md)
- Architettura: [`docs/architecture.md`](docs/architecture.md) · API: [`docs/api.md`](docs/api.md) ·
  Decisioni: [`docs/decisions/`](docs/decisions/) · Avanzamento: [`docs/progress.md`](docs/progress.md)

## Guida rapida

### Prerequisiti

- Java 21 (JDK)
- Node.js 22+ e npm
- Docker con Docker Compose (per PostgreSQL e per i test di integrazione con Testcontainers)

### Configurazione

```bash
cp .env.example .env
# modifica .env: almeno POSTGRES_PASSWORD, DB_PASSWORD e GYM_ADMIN_PASSWORD
```

I valori di `.env.example` sono solo dimostrativi. `.env` non va mai committato.

### Avvio PostgreSQL

```bash
docker compose up -d postgres
```

### Avvio backend

```bash
cd backend
set -a && source ../.env && set +a     # esporta le variabili (Linux/macOS)
./mvnw spring-boot:run
```

Il backend ascolta su `http://localhost:8080`; Flyway crea lo schema al primo avvio.

### Avvio frontend

```bash
cd frontend
npm install
npm run dev
```

Apri `http://localhost:5173`: Vite inoltra `/api` al backend.

### Login iniziale

Accedi con `GYM_ADMIN_USERNAME` e `GYM_ADMIN_PASSWORD` definiti in `.env`.
Al primo accesso viene chiesto di cambiare la password.

### Esecuzione test

```bash
cd backend && ./mvnw verify        # unit, integrazione (Testcontainers), ArchUnit
cd frontend && npm test            # Vitest + Testing Library
cd frontend && npm run lint && npm run build
```

### Arresto

- backend e frontend: `Ctrl+C` nei rispettivi terminali;
- PostgreSQL: `docker compose down` (i dati restano nel volume `pgdata`;
  `docker compose down -v` li cancella).
