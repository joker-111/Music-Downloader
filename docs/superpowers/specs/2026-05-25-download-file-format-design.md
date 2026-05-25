# Download File Format Design

## Goal

Expose the downloadable audio file format in the download metadata and use that format when serving proxied downloads. A FLAC download should be presented as FLAC, saved with a `.flac` extension, and returned with an audio-specific content type instead of always using `.mp3` and `application/octet-stream`.

## Scope

- Add file format metadata to the backend `DownloadInfo` DTO.
- Infer format from gateway metadata, download URL, and selected quality.
- Generate sanitized filenames with the inferred extension.
- Return a matching `Content-Type` from the proxied download endpoint.
- Show the inferred file format in the frontend download information panel.
- Keep existing download routes, quality selector values, and raw response display.

Out of scope:

- Converting audio between formats.
- Adding a new manual format selector.
- Guaranteeing that upstream content bytes always match the inferred format.
- Changing gateway candidate paths or platform coverage.

## Format Inference

The backend infers a normalized audio format using three signals in priority order:

1. Gateway response fields such as `format`, `type`, `fileType`, `file_type`, `ext`, `extension`, `mimeType`, and `mime_type`.
2. The download URL path extension, ignoring query strings and fragments.
3. The selected quality parameter as a fallback.

Known formats:

- `mp3`: display `MP3`, extension `mp3`, MIME type `audio/mpeg`
- `flac`: display `FLAC`, extension `flac`, MIME type `audio/flac`
- `m4a`: display `M4A`, extension `m4a`, MIME type `audio/mp4`
- `aac`: display `AAC`, extension `aac`, MIME type `audio/aac`
- `wav`: display `WAV`, extension `wav`, MIME type `audio/wav`
- `ogg`: display `OGG`, extension `ogg`, MIME type `audio/ogg`

If no reliable signal exists, `MP3` remains the compatibility fallback so existing behavior continues to produce a usable filename.

## Backend Design

`MusicModels.DownloadInfo` gains:

- `format`: normalized display value such as `MP3` or `FLAC`
- `extension`: lowercase file extension without a dot
- `mimeType`: response media type string

`MusicService.downloadInfo` will:

- Fetch the gateway response as it does today.
- Extract title and URL with the current logic.
- Infer audio format using the priority order above.
- Generate `filename` from sanitized title, quality, and inferred extension.
- Return the raw gateway body unchanged.

`MusicController.download` will:

- Reuse `DownloadInfo.mimeType` for the response `Content-Type`.
- Keep `Content-Disposition: attachment` with the inferred filename.
- Keep returning `404` when no download URL is available.
- Keep passing through upstream HTTP error statuses.

## Frontend Design

The download information detail panel adds a `文件格式` row between `音质` and `下载地址`. It displays `DownloadInfo.format` when present and falls back to the existing quality label when older or unexpected backend data is returned.

No new control is added. Users still select quality, then fetch a download link, then download through the existing button.

## Testing

Backend tests cover:

- `FLAC` quality produces `format=FLAC`, `extension=flac`, `mimeType=audio/flac`, and a `.flac` filename.
- Gateway-provided format fields take priority over URL and quality.
- URL extension takes priority over quality fallback.
- Unknown formats fall back to MP3-compatible metadata.
- The download controller uses the inferred filename and content type.

Frontend/static tests cover:

- Download detail rendering includes the file format label.
- Existing download-info and download route usage stays unchanged.

## Error Handling

Format inference is best-effort. Invalid, empty, or unsupported format signals are ignored and the next signal is tried. If all signals fail, the service falls back to MP3 metadata instead of failing the download-info request.
