# streem-setup

Local web tool for provisioning streem-backend instances.

Handles: new org bootstrap, facility / use-case / property additions, facility–usecase
and usecase–property mappings, licenses, feature flags. Runs locally, connects directly
to target Postgres databases.

## Stack

- Backend: Spring Boot **4.0.5**, Java **25**, JdbcTemplate, SQLite (local store), PostgreSQL (targets)
- Frontend: React 18 + Vite 5 + TypeScript
- Distribution: single fat JAR with embedded frontend

## Requirements

- Java 25
- Node 18+ (only for dev; not needed once the JAR is built)
- Gradle 9.0+ comes with the wrapper (`./gradlew`)

## Development

Two terminals:

```bash
# terminal 1 — backend on :8765
cd backend
./gradlew bootRun

# terminal 2 — frontend dev server on :5173 (proxies /api → :8765)
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>.

## Production build (single JAR)

```bash
cd frontend && npm run build           # outputs to backend/src/main/resources/static
cd ../backend && ./gradlew bootJar
java -jar build/libs/streem-setup.jar
```

Open <http://localhost:8765>.

## Local state

| File | What |
|---|---|
| `~/.streem-setup/store.db` | SQLite: connection configs + audit log |
| `~/.streem-setup/key`      | 32-byte AES key used to encrypt connection passwords |

Both files are created on first launch. Treat them like `~/.pgpass` — protect with
filesystem permissions.

## Status

| Feature | Status |
|---|---|
| Connection CRUD (add / edit / delete / test) | ✓ Phase 1 |
| `new-org` operation | TODO Phase 2 |
| `add-facility`, `add-usecase`, `add-property`, mappings | TODO Phase 3 |
| `add-license` (facilities x use cases, state presets) | ✓ |
| Feature flags toggle | TODO Phase 4 |
| Audit log viewer | TODO Phase 4 |
| Master-password key | TODO Phase 5 |

## Architecture

Three layers, single process:

```
React UI (browser, :5173 dev / :8765 prod)
   ↓ HTTP /api/*
Spring Boot (Undertow, bound to 127.0.0.1)
   ├─ Local SQLite store (own state)
   └─ Target Postgres (per-request, decrypted on demand)
```

Operations are **preview → execute** pairs. Every write runs in a single
transaction against the selected target. Every run is appended to the local
audit log.
