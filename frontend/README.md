# Phoenix Lighting Designer (frontend)

Interface React construite avec Vite pour piloter le backend Phoenix Lighting Designer.

## Démarrage

```bash
npm install
npm run dev
```

L'application est servie sur [http://localhost:5173](http://localhost:5173).

## Configuration API

- `VITE_API_BASE_URL` : URL par défaut utilisée par le client HTTP (par défaut `/api`).
- `VITE_BACKEND_PROXY_TARGET` : si défini, Vite proxy les appels `http://localhost:5173/api` vers cette URL en développement.

## Commandes

- `npm run dev` : démarre le serveur de développement Vite.
- `npm run build` : build de production (bundler Rolldown).
- `npm run lint` : exécute ESLint.

Les couleurs et l'état sont synchronisés avec les endpoints REST (`/api/overrides`, `/api/start`, `/api/stop`, `/api/define-area`).
