# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and Oxlint's TypeScript related rules in your project.

## Docker stack

From the repository root, run `docker compose up -d --build` and open `http://localhost`.
The frontend image builds the Vite application and serves it through Nginx on port 80.
The stack gateway routes `/api`, `/uploads`, and health requests to the backend, so the
production frontend uses the same origin and does not require `VITE_API_BASE_URL`.

`npm run dev` still serves the development UI at `http://localhost:5173` and forwards
API requests to the backend at `http://localhost:8080`.

Run `npm run test:e2e -- e2e/chatbot-flow.spec.js` from this directory for chatbot
browser regression tests. To verify the running Docker services in PowerShell:

```powershell
$env:E2E_BASE_URL = 'http://localhost'
npm run test:e2e -- e2e/docker-stack.spec.js --retries=0
```

The live tests verify backend health, catalog and product pages, customer login and
session restoration, and chatbot recommendations added to a real backend cart.
Set `E2E_USERNAME` and `E2E_PASSWORD` for a dedicated test account. The login test
is skipped when these variables are missing. `npm run test:spa` requires
`E2E_STAFF_USERNAME` and `E2E_STAFF_PASSWORD`. These are Node test variables, not
`VITE_*` variables. Never use a real customer account in automated tests. Each
test clears its own guest cart and logs out its authentication session.

## Environment and GitHub safety

Copy `.env.example` to `.env` for local use. The current public API setting is:

```env
VITE_API_BASE_URL=https://api.dermascan.world/api
```

All `VITE_*` variables are public in browser bundles, even when `.env` is ignored.
Only `VITE_API_BASE_URL` is approved; Vite rejects other `VITE_*` variable names and
API URLs containing credentials, query parameters or fragments. Never place DB
passwords, signing keys, server API keys or access/refresh tokens in frontend config.
The API domain is public configuration, not a secret.

For a separate production frontend, set the same public variable in the hosting
provider's build environment. Rebuild after changing it. Docker ignores local env
files; use `--build-arg VITE_API_BASE_URL=https://api.dermascan.world/api` for a
separate API origin, or keep it empty for the same-origin gateway.

Before committing or pushing, run from `frontend/`:

```sh
npm run security:check
npm run security:history
npm run build
npm run lint
git diff --cached --name-only
```

The local scanner checks frontend tracked/unignored text files, the entire Git
staging area, and optionally reachable frontend text history. It prints only file
paths, line numbers and finding categories, never secret values. Findings block
with exit code 1; review them locally. Synthetic test data can produce false positives.
A clean pattern scan is not a guarantee; also enable GitHub secret scanning and
push protection when available. Dependency audit remains a separate step.

`.gitignore` does not remove previously tracked files or erase history. If a secret
was committed, stop the push and revoke/rotate it first. Removing it from the latest
file is insufficient. Untracking/history cleanup must be reviewed separately; do
not force-push shared history without coordinating with collaborators.

If pushing the existing monorepo, pushing a branch includes its reachable commits,
not just `frontend/`. Backend/mobile secrets in that history remain relevant. A
new frontend-only repository must start from a reviewed source copy, without copying
`.git`, `.env*` (except `.env.example`), dependencies, build output, credentials or
browser reports/screenshots. Do not use `git add -f` to bypass these exclusions.

Browser auth tokens are currently stored in localStorage. This is a separate XSS
risk, not a Git secret; a full migration to HttpOnly cookies requires backend support.
