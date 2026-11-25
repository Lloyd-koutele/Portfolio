# CLAUDE.md — Portfolio (Spring Boot + React/TS)

Flipbook portfolio: a public book whose pages are composed in a Canva-like canvas editor, plus visit tracking.
Source of truth: `Cahier des charges — Portfolio (Spring Boot + React TS).pdf` (11-step plan, all done).
Deviations from it are listed in README → Décisions.

## Stack
- **backend/** — Java 21 (Gradle toolchain), Spring Boot 3.5, **Gradle** (not Maven), Spring Modulith 1.4,
  PostgreSQL 16 + Flyway, JPA/JdbcTemplate, Spring Security (JWT in HttpOnly cookie via spring-security-oauth2-jose),
  Bucket4j, Apache Tika, uap-java, springdoc. Tests: JUnit 5, Testcontainers, MockMvc.
- **frontend/** — npm workspaces, two apps like a newspaper: `apps/book` (public reading app, served at `/`) and
  `apps/admin` (editing, served at `/admin/`), plus `packages/shared` (imported as `@shared/...`).
  React 18, TypeScript strict, Vite, Tailwind CSS 4, React Router 7, TanStack Query 5, Zustand + Immer, @dnd-kit,
  react-moveable, perfect-freehand, react-pageflip, Recharts, html-to-image.
  Tests: Vitest projects (shared, book, admin) + Testing Library, Playwright (e2e/).
- Database: **PostgreSQL 16** everywhere (dev compose, prod compose, Testcontainers).

## Architecture
- Backend = **Spring Modulith**: each direct sub-package of `com.portfolio` is a module:
  `config`, `common` (shared), `auth`, `media`, `settings`, `page`, `visit`, `stats`, `profile`.
  - Public API of a module = types in its root package (`PageApi`, `MediaApi`, `MediaUsage`, `SettingsApi`,
    `AdminPrincipal`…). Implementation lives in `<module>.internal`, never referenced from another module.
  - Dependencies: page → settings → media; visit → page, settings, auth; stats → page; profile → page, media, visit.
    Media never depends on
    pages: modules that reference media implement `MediaUsage` instead.
  - `ModularityTest` verifies boundaries and must stay green.
  - Inside a module: controller → service → repository, DTOs separate from entities, errors via `ApiException`
    → RFC 7807 `ProblemDetail` (`GlobalExceptionHandler`).
- **Book structure**: COVER, TABLE_OF_CONTENTS (fixed pages, `page.kind`), then `project`s with ordered
  sheets (`PROJECT_SHEET`, `page.project_id`, `page.position` = position in the project), then CONTACT. Order is
  computed by `BookOrder`; page numbers and the TOC (`TocEntry`: project, first page) are computed on the working
  copy for the admin and on the latest edition for readers. The `toc` element stores only its style; entries come from `TocContext`.
- **Newspaper model**: pages/projects are the working copy (autosaved by the editor). Readers only see the latest
  `book_edition` (JSON snapshot of the whole ordered book, `BookEditions`). One button, « Publier le site »
  (`POST /api/admin/book/publish`), creates a new edition; `publication.upToDate` in `GET /api/admin/book` drives
  the indicator. No per-page publish. The book refreshes every 30 s (ETag → 304 when unchanged).
- Frontend: `packages/shared/src/{page-renderer,book,api,i18n,ui,types,app,test}`;
  `apps/book/src` (BookPage, routes); `apps/admin/src/{app,features/*,i18n}` (admin texts live in the admin app).
  ESLint forbids book → admin imports and shared → app imports.
  - **One `PageRenderer`** for the book, the editor and thumbnails; element types in `elementRegistry.ts`.
  - Editor state: `features/editor/store/editorStore.ts`; every edit goes through `applyChange` (history 100);
    drags use `beginGesture/updateGesture/endGesture`. Screen → logical conversion only in `geometry.ts#toLogical`.
- **CV**: one PDF (`cv_document`, `profile/internal/CvService`), uploaded in Réglages, downloaded via
  `GET /api/public/cv/download?source=book|home`; each download goes through `visit.DownloadLog` into the `download`
  table (same IP rules, admin excluded, 13-month purge) and is listed by `GET /api/admin/stats/downloads`.
- **Book themes**: rules in `packages/shared/src/themes/bookThemes.ts` (`applyTheme`, `BOOK_THEMES`); the admin
  (`features/themes`) re-colours every page, re-renders thumbnails off-screen, then `POST /api/admin/book/theme`
  saves all pages + the backdrop atomically (`page/internal/BookThemeService`, versions checked, 409 BOOK_CHANGED).
  A `theme_snapshot` copy (last 10) allows `POST /api/admin/book/theme/{id}/revert`; snapshot media count as used.
- **Admin home & sign-in**: `/admin` (index) is the public home page (`features/home`, data from
  `GET /api/public/profile`, edited in Réglages); `/admin/login` = password (+ remember-me, 30 days) or Google;
  guarded routes are the other `/admin/*`. Google = hand-written OIDC + PKCE (`GoogleSignIn`): `/api/auth/google`
  → Google → `GOOGLE_REDIRECT_URI` (`/api/auth/google/callback`, may be on localhost) → one-time ticket →
  `ADMIN_SITE_URL/api/auth/google/complete` (binding cookie). 2FA = TOTP (`Totp`, `TwoFactorService`, recovery
  codes): when enabled, the first step only sets `PORTFOLIO_MFA` (5 min) and `POST /api/auth/2fa/verify` opens the
  session. Tests fake Google with `FakeGoogleIdentityClient` (registered in `TestcontainersConfiguration`).
- Media: images (5 MB, sanitized) and videos (MP4/WebM, `MEDIA_MAX_VIDEO_MB`, streamed, served with byte ranges).
  A `video` element is either an embed (`youtube`/`vimeo` + `videoId`) or `provider: "file"` + `mediaId`.
- Traefik (`traefik/`, file provider, no Docker socket) terminates TLS and routes `PUBLIC_HOST` / `ADMIN_HOST` to
  Nginx. `APP_ORIGIN` is a comma-separated list of every public origin (the API only sees HTTP).
- Nginx (`frontend/docker/templates/default.conf.template`): one server per domain (`PUBLIC_HOST` = book only,
  `ADMIN_HOST` = admin only) plus a default server with both. Use `@shared/util/uuid`, never `crypto.randomUUID()`
  directly (missing on plain-HTTP local domains).
- Visual reference: FlipHTML5 "engineering portfolio" template (spirit only, no code or assets copied).

## Conventions
- Code, identifiers, API, commit messages: **English**. UI texts: **French**, only via
  `packages/shared/src/i18n/fr.ts` (shared) and `apps/admin/src/i18n/fr.ts` (admin only).
- Java style: opening brace alone on its own line; `else` / `catch` / `finally` on a new line (Checkstyle, 120 cols).
- Frontend: ESLint (typescript-eslint strict, react-hooks) + Prettier (single quotes, width 100).
  No `dangerouslySetInnerHTML` (lint rule). Links: http/https/mailto only, checked client and server side.
- Integration tests: annotate with `@IntegrationTest`, reset data with `TestData.reset`, send CSRF with
  `TestRequests.xsrf()` (never Spring's `csrf()`), log in with `TestRequests.adminSession`.
- No secrets in the repo: everything via environment variables, documented in `.env.example`.
- No new dependency without a one-line justification in README → Décisions.
- **Never commit**: the owner commits themselves. Finish with green checks, then stop and summarise.

## Commands
```bash
docker compose -f docker-compose.dev.yml up -d     # PostgreSQL for local dev (port 5433)
cd backend && ./gradlew bootRun                    # API on :8080 (reads ../.env)
cd backend && ./gradlew check                      # tests + Checkstyle + coverage rules (Docker needed)
cd frontend && npm run dev                         # book :5173 (also forwards /admin to the admin app on :5174)
cd frontend && npm test && npm run lint && npm run format:check && npm run build
cd frontend && npm run e2e                         # Playwright against E2E_BASE_URL (default :5173)
./traefik/generate-local-certs.sh                  # local HTTPS certificate (mkcert) for both domains
docker compose up -d --build                       # Traefik (HTTPS 443, HTTP 80 → HTTPS) → Nginx → API → PostgreSQL
# Local domains (hosts file): https://portfolio-public (book) and https://portfolio-admin (admin)
cd frontend && E2E_BASE_URL=https://portfolio-admin E2E_ADMIN_URL=https://portfolio-admin E2E_PUBLIC_URL=https://portfolio-public npm run e2e
```
