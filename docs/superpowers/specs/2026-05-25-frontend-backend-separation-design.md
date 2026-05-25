# Frontend Backend Separation Design

## Goal

Split the current Spring Boot music downloader into a true frontend and backend structure while preserving the existing user experience and API behavior.

The backend remains responsible for gateway access, response normalization, download proxying, and `/api/**` endpoints. The frontend becomes an independent Vite application with its own package metadata, dev server, build output, and source organization.

## Current State

- Spring Boot serves both API endpoints and static frontend files from `src/main/resources/static`.
- The frontend is currently a single-page vanilla HTML/CSS/JS application.
- Existing scripts build and run the Spring Boot application with the bundled JDK and Maven.
- The repository already has local tool directories and ignored runtime files, including `.tools/`, `.env`, `target/`, and `.tmp-karpov-gateway/`.
- There are existing working tree edits in `MusicService.java`, `app.js`, and `styles.css`; implementation must preserve them and avoid reverting unrelated changes.

## Selected Approach

Use a complete Vite/npm frontend project under `frontend/`.

This keeps frontend development independent from Spring Boot while avoiding a framework migration. The initial implementation stays with vanilla JavaScript because the existing UI is already a working single-page app and does not require component framework semantics to complete this separation.

## Architecture

### Backend

The backend remains a Spring Boot application rooted at the existing Maven project.

Responsibilities:

- Expose local API routes under `/api/**`.
- Normalize platform aliases and gateway response shapes.
- Call Karpov Gateway using configured candidate paths.
- Proxy download bytes through the existing download endpoint.
- Provide configuration through `application.yml` and environment variables.

The backend should no longer be treated as the primary frontend host. Static files may be removed from `src/main/resources/static` or left as a transitional fallback only if needed, but the documented development path should use the separate Vite frontend.

### Frontend

Create `frontend/` as a standalone Vite project.

Expected structure:

```text
frontend/
  index.html
  package.json
  vite.config.js
  src/
    main.js
    api.js
    state.js
    render.js
    styles.css
```

Responsibilities:

- Render the existing music downloader interface.
- Load platform data from `/api/platforms`.
- Search tracks through `/api/search`.
- Fetch song, lyric, and download metadata through backend API routes.
- Keep UI state and DOM rendering separate enough for future maintenance.

## Data Flow

During development:

1. Spring Boot runs on `http://localhost:8080`.
2. Vite runs on its own dev port, normally `http://localhost:5173`.
3. Vite proxies `/api/**` requests to `http://localhost:8080`.
4. Browser requests page assets from Vite and API data from the backend through the proxy.

During production build:

1. `npm run build` creates `frontend/dist`.
2. The backend jar is built independently with Maven.
3. Deployment can serve `frontend/dist` through any static host or reverse proxy while routing `/api/**` to Spring Boot.

## Comments

Add comments only where the code is not obvious.

Recommended comment locations:

- Backend gateway candidate expansion and response rejection logic.
- Backend download proxy behavior, especially why bytes are fetched server-side.
- Frontend API wrapper behavior for non-JSON or failed responses.
- Frontend render helpers where image URLs are normalized or placeholders are selected.

Avoid comments that restate method names or obvious assignments.

## Scripts And Documentation

Update README to explain the separated workflow:

- Start backend from the project root using the existing run script or Maven.
- Start frontend from `frontend/` using npm.
- Build backend and frontend separately.
- Mention that gateway credentials still come from environment variables or `.env`.

Add frontend npm scripts:

- `npm run dev`
- `npm run build`
- `npm run preview`

If useful, add a root convenience script later, but the initial separation should keep backend and frontend commands explicit.

## Error Handling

Backend error responses continue to use the existing `ApiResponse` shape.

Frontend API code should:

- Parse JSON when possible.
- Surface backend `message` values to the status area.
- Preserve raw error payloads in the debug response panel.
- Handle non-JSON responses with a clear fallback message.

## Testing And Verification

Required checks:

- Run backend package verification with the bundled Maven/JDK path or the existing build script.
- Run `npm install` if dependencies are not already present.
- Run `npm run build` in `frontend/`.
- If a dev server is started, verify that the Vite app can load `/api/platforms` through the proxy.

If Node/npm is unavailable, the implementation should still create the project files and report that frontend build verification could not be run locally.

## Non-Goals

- Do not migrate the UI to React, Vue, or another framework in this change.
- Do not redesign the visual interface beyond what is necessary for the move.
- Do not change the public backend API routes unless a bug is found during separation.
- Do not commit `.env`, `.tools`, `target`, `node_modules`, or generated frontend build output unless explicitly requested.
