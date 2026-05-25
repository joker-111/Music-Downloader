# Environment Gateway Configuration Design

## Goal

Keep remote gateway details out of committed code. Operators should change the gateway host, operation paths, and API key through local environment variables or a local `.env` file that is ignored by Git.

## Approach

`src/main/resources/application.yml` keeps only Spring property names and environment-variable placeholders. It does not contain the real gateway base URL, route paths, or credentials.

The application reads these values:

- `MUSIC_GATEWAY_BASE_URL`
- `MUSIC_GATEWAY_SEARCH_PATH`
- `MUSIC_GATEWAY_SONG_PATH`
- `MUSIC_GATEWAY_DOWNLOAD_PATH`
- `MUSIC_GATEWAY_LYRIC_PATH`
- `MUSIC_GATEWAY_ARTIST_PATH`
- `MUSIC_GATEWAY_PLAYLIST_PATH`
- `MUSIC_GATEWAY_ALBUM_PATH`
- `MUSIC_GATEWAY_API_KEY`

The committed `.env.example` documents the variable names without real values. The untracked `.env` file can hold the user's actual values for local runs.

## Runtime Behavior

`run.ps1` already imports the local `.env` file before starting the app, so local development continues to use `.\run.cmd`.

`GatewayClient` validates the base URL and per-operation path before making a request. If a required value is missing, it throws a `GatewayException` with a clear message such as `Missing gateway base URL.` or `Missing gateway candidate paths for operation: search.`

## Testing

Tests cover URL expansion, failed candidate attempts, and missing configuration errors. The existing controller error handling continues to surface gateway configuration failures through the normal API response shape.
