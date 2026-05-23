# Music Downloader Optimization Design

## Goal

Make the first implementation easier to run, debug, and use. The optimization focuses on source stability, clearer UI states, better gateway diagnostics, and stronger field extraction from heterogeneous music gateway responses.

## Scope

- Replace corrupted user-facing source text with ASCII-safe labels so HTML, JavaScript, and Java source remain valid across local encodings.
- Improve the single-page UI with clearer empty, loading, error, download, and raw payload states.
- Return gateway diagnostic attempts to the frontend when endpoint probing fails.
- Improve normalization for common song fields and download URL shapes.
- Rewrite README with reliable text and concrete troubleshooting steps.

## Out Of Scope

- Reverse engineering a private gateway contract.
- Adding persistence, login, batch downloads, or database storage.
- Guaranteeing access to copyrighted or restricted high-quality audio.

## Validation

Use Node syntax checking for frontend JavaScript. Java compilation still requires a local JDK 17+ and Maven because they are not installed in this environment.
