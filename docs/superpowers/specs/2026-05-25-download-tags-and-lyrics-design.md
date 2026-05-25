# Download Tags And Lyrics Design

## Goal

Downloaded songs should keep useful music metadata. The main download should provide one package containing the tagged audio file and a same-name `.lrc` file for players that prefer external lyrics.

## Scope

- Pass selected track title, artist, and album from the frontend to download routes.
- Fetch lyrics server-side during audio download and lyric-file download.
- Embed metadata in MP3 ID3v2.3 tags and FLAC Vorbis Comment tags.
- Keep unsupported audio formats downloadable unchanged.
- Add a sidecar `.lrc` endpoint for compatibility.
- Add a package download endpoint that returns tagged audio and `.lrc` together.
- Make the UI main download button use the package endpoint.
- Keep existing search, detail, lyric display, and download-info behavior.

Out of scope:

- Audio transcoding.
- Editing cover art.
- Tag writing for M4A, AAC, WAV, or OGG.
- Returning multiple browser downloads from a single click.

## Backend Design

`MusicController.download` accepts optional `artist` and `album` query parameters in addition to the existing title hint. After it retrieves upstream bytes, it asks `MusicService` for lyrics. If lyrics are available, the service builds a metadata context and attempts to tag the audio bytes.

Tagging is best-effort:

- MP3: remove any existing ID3v2 tag at the front, prepend a new ID3v2.3 tag with `TIT2`, `TPE1`, `TALB`, and `USLT` frames.
- FLAC: locate the `fLaC` stream marker, rewrite the Vorbis Comment metadata block, preserve existing comments except keys being replaced, and add `TITLE`, `ARTIST`, `ALBUM`, and `LYRICS`.
- Unsupported or malformed files: return the original bytes.

`GET /api/song/{platform}/{id}/lyric-file` returns UTF-8 `text/plain` with a `.lrc` attachment filename. It uses the selected title when available and falls back to the song id.

`GET /api/song/{platform}/{id}/download-package` returns a UTF-8 zip attachment. The zip contains the same tagged audio bytes produced by `/download` and, when lyrics are available, a same-name `.lrc` file. If lyrics are missing, the zip still contains the tagged audio file.

## Frontend Design

When a user fetches download info, the frontend builds one shared query string containing quality, title, artist, and album from the selected track. The main download button points to `/download-package`.

The existing lyric tab remains unchanged. Individual `/download` and `/lyric-file` backend endpoints remain available as compatibility fallbacks, but the UI no longer shows a separate lyric-file button.

## Testing

Backend tests cover:

- Download passes title, artist, and album hints into the service.
- MP3 downloads receive an ID3 tag containing title, artist, album, and lyrics.
- The lyric-file endpoint returns UTF-8 `.lrc` content with an attachment filename.
- The package endpoint returns a zip containing tagged audio and `.lrc`.
- Missing lyrics return `404` for the lyric-file endpoint.
- Existing content type and filename behavior remains intact.

Frontend/static tests cover:

- Download links include artist and album query parameters.
- The main download button points to the package endpoint.

## Error Handling

Metadata enrichment must not block the audio download. If lyric lookup or tag writing fails, the controller serves the original upstream bytes with the same headers. The package endpoint still returns a zip containing audio if lyrics are missing. The lyric-file endpoint is stricter and returns `404` when no lyric text is available.
