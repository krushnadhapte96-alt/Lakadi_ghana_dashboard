# ghana-dashboard-web

Frontend for Ghana Dashboard. React 19 + TypeScript, Vite 8, Tailwind CSS v4, React Router 7,
React Query 5, zustand, framer-motion.

## Run locally (Windows)

```powershell
cd ghana-dashboard-web
copy .env.example .env.development
npm install
npm run dev
```

Dev server on `http://localhost:5173`. The API base URL is `VITE_API_BASE_URL` (default
`http://localhost:8080/api/v1`).

## IntelliJ / VS Code

No special run config needed: `npm run dev` from the folder, or a Run target of type "npm" with
script `dev`. Make sure `JAVA_HOME` (for the API in another window) points to JDK 24.

## Scripts

| Command           | Purpose                                  |
| ----------------- | ---------------------------------------- |
| `npm run dev`     | Vite dev server with HMR                 |
| `npm run build`   | `tsc -b` + production bundle (code split) |
| `npm run lint`    | ESLint                                   |
| `npm run preview` | Serve the production build               |
| `npm run test`    | Vitest + React Testing Library (CI mode) |
| `npm run test:watch` | Vitest watch mode                     |

Tests use Vitest with jsdom and React Testing Library; setup lives in `src/test/setup.ts`.

## Structure (src)

```
src/
  api/            axios instance, typed endpoints (notifications, search, dashboard, ...)
  app/            providers (QueryClient, Router) + route table (lazy-loaded pages)
  components/
    beej|costing|customers|documents|expenses|expiry|home|charts|licences|notifications|returns|
    search/       module components incl. CommandPalette and NotificationCenter
    layout/       AppLayout, Header, Sidebar, BottomNav, Drawer, PageTransition
    ui/           generic atoms (Button, Card, Badge, Modal, ...)
  hooks/          shared hooks (notification bell)
  i18n/           en.json, hi.json (1,276 keys each, parity-checked), language store
  pages/          one file per route; UiKit/Design are dev-only
  stores/         zustand: auth, theme
  styles/         globals.css (Tailwind v4 + design tokens + animations)
  utils/          cn, format, convert, entryPoint, sound, posTotals
```

## Notable features

- **Ctrl+K global search** across bills, products, customers, khata, suppliers, orders, batches.
- **Notification bell**: SSE-pushed new orders with a chime, unread groups, per-user read state.
- **i18n**: English + Hindi for the entire app; theme: light/dark, persisted.
- **Lang-friendly layouts** tested at 375 px (mobile), and motion-reduced fallbacks on animations.
