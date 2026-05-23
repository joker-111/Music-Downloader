# Music Downloader UI Optimization Design

## Goal

Improve the existing single-page music downloader UI without changing its backend API contract or core feature set. The page should feel like a focused desktop/mobile tool for searching, selecting, inspecting, and downloading a track.

## Current Problems

- User-facing Chinese text is corrupted and must be restored to readable Simplified Chinese.
- The page works, but the visual hierarchy is flat: search, results, selected track, download, details, and raw payload compete for attention.
- The download workflow is not prominent enough for the primary use case.
- Empty, loading, warning, and error states are present but inconsistent in tone and placement.
- The layout becomes usable on narrow screens, but the controls and dense detail panels need cleaner stacking.

## Recommended Approach

Use a medium-strength tool UI refresh:

- Keep the current static frontend structure: `index.html`, `styles.css`, and `app.js`.
- Keep all existing API routes and Java backend behavior unchanged.
- Rework the page into a clear workbench:
  - Top area: brand, gateway context, platform selector, keyword search, and search action.
  - Left column: search results with count, empty/loading/error copy, cover thumbnails, title, artist, album, and duration.
  - Right column: selected track summary, quality selector, download-link action, download button, status message, detail tabs, structured detail view, and raw response panel.
- Make the download controls visually primary after a track is selected.
- Keep raw JSON visible because this project also helps inspect gateway responses, but present it as a secondary panel.

## UI Details

### Text

All visible text should be restored to Simplified Chinese, including:

- Page title and brand.
- Search placeholders and button labels.
- Result list, empty state, status, tab labels, detail labels, and download labels.
- Platform display names.

### Layout

Desktop layout:

- Header spans the full width.
- Content uses a two-column grid with a fixed-width result column and flexible detail column.
- The selected track summary appears first in the detail column.
- Download controls sit directly under the selected track summary.
- Structured details and raw response appear as two panels below the tabs.

Tablet and mobile layout:

- Header stacks brand above search controls.
- Results and detail area stack vertically.
- Detail panels become a single column.
- Buttons and selects stay large enough to tap comfortably.

### Visual Style

- Use a calm, utilitarian palette with high contrast and restrained accent color.
- Avoid decorative landing-page patterns; this is an operational tool.
- Use subtle surfaces, clear borders, consistent 8px radii, and stable spacing.
- Ensure selected results, active tabs, disabled buttons, warnings, and errors are visually distinct.

### Interaction States

- Initial state: invite the user to search.
- Searching state: result list and status both show progress.
- Empty result state: explain that no songs were found.
- Selected state: track summary updates immediately, download controls unlock when an id exists.
- Download state: fetching, ready, unavailable, and error messages are clearly differentiated.
- Tab state: active tab remains clear, missing related ids show a warning rather than a broken request.

## Implementation Scope

Modify:

- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/app.js`

Do not modify:

- Java backend APIs.
- Gateway candidate configuration.
- Download proxy behavior.

## Validation

- Run JavaScript syntax validation with Node if available.
- If a local JDK and Maven are available, run the Spring Boot build.
- Start the app if possible and manually inspect:
  - Desktop layout.
  - Mobile-width layout.
  - Initial, searching, empty/error, selected, tab, and download states.

## Out Of Scope

- Login, favorites, history, batch downloads, or persistence.
- New gateway endpoints.
- Replacing the app with a full frontend framework.
- Pixel-perfect browser mockups from the visual companion, because the local environment lacks `bash` for the companion server.
