# Music Downloader Design

## Goal

Build a Spring Boot web application that connects to `gateway.karpov.cn` and provides a browser UI for searching music, viewing linked details, reading lyrics, and downloading available audio quality variants.

## Shape

The app exposes local APIs under `/api/**` and serves a static single-page UI from `/`. The UI never talks to the gateway directly. Spring Boot owns gateway access, normalization, error handling, and download proxying.

## Gateway Adapter

The gateway does not have discoverable public documentation in this environment, so the adapter uses configurable endpoint candidates in `application.yml`. For each operation it tries candidates in order and accepts the first non-empty JSON/text result. The app supports Meting-style endpoints and common REST-style endpoints out of the box.

Supported operations:

- Search songs
- Song details
- Artist details
- Playlist details
- Album details
- Lyrics
- Download URL lookup and proxied download

## UI Flow

The page has platform selection, search input, result list, detail area, quality selector, and tabs for song, lyric, artist, album, and playlist data. Selecting a search result loads song details; tabs then fetch related data for the selected item. Download uses the selected platform, song id, and requested quality.

## Limits

Music availability, quality, and download success depend on the upstream gateway and the rights/availability of each platform. If the upstream endpoint paths differ, update `music.gateway.candidates` in `application.yml` without changing Java code.
