package cn.karpov.music.web;

import cn.karpov.music.model.ApiResponse;
import cn.karpov.music.model.MusicModels.DetailResult;
import cn.karpov.music.model.MusicModels.DownloadInfo;
import cn.karpov.music.model.MusicModels.GatewayError;
import cn.karpov.music.model.MusicModels.LyricResult;
import cn.karpov.music.model.MusicModels.Platform;
import cn.karpov.music.model.MusicModels.SearchResult;
import cn.karpov.music.service.AudioTagService;
import cn.karpov.music.service.AudioTagService.AudioMetadata;
import cn.karpov.music.service.GatewayException;
import cn.karpov.music.service.MusicService;
import jakarta.validation.constraints.NotBlank;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地 HTTP API 模块。
 *
 * <p>前端只和这些 /api 路由交互；网关细节和下载代理都封装在后端。</p>
 */
@Validated
@RestController
@RequestMapping("/api")
public class MusicController {
    private final MusicService musicService;
    private final AudioTagService audioTagService;
    private final HttpClient httpClient;

    public MusicController(MusicService musicService, AudioTagService audioTagService, HttpClient httpClient) {
        this.musicService = musicService;
        this.audioTagService = audioTagService;
        this.httpClient = httpClient;
    }

    @GetMapping("/platforms")
    public ApiResponse<List<Platform>> platforms() {
        return ApiResponse.ok(musicService.platforms());
    }

    @GetMapping("/search")
    public ApiResponse<SearchResult> search(
            @RequestParam(defaultValue = "netease") String platform,
            @RequestParam @NotBlank String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(musicService.search(platform, keyword, Math.max(1, page), Math.min(Math.max(1, limit), 50)));
    }

    @GetMapping("/song/{platform}/{id}")
    public ApiResponse<DetailResult> song(@PathVariable String platform, @PathVariable String id) {
        return ApiResponse.ok(musicService.detail("song", platform, id));
    }

    @GetMapping("/artist/{platform}/{id}")
    public ApiResponse<DetailResult> artist(@PathVariable String platform, @PathVariable String id) {
        return ApiResponse.ok(musicService.detail("artist", platform, id));
    }

    @GetMapping("/playlist/{platform}/{id}")
    public ApiResponse<DetailResult> playlist(@PathVariable String platform, @PathVariable String id) {
        return ApiResponse.ok(musicService.detail("playlist", platform, id));
    }

    @GetMapping("/album/{platform}/{id}")
    public ApiResponse<DetailResult> album(@PathVariable String platform, @PathVariable String id) {
        return ApiResponse.ok(musicService.detail("album", platform, id));
    }

    @GetMapping("/song/{platform}/{id}/lyric")
    public ApiResponse<LyricResult> lyric(@PathVariable String platform, @PathVariable String id) {
        return ApiResponse.ok(musicService.lyric(platform, id));
    }

    @GetMapping("/song/{platform}/{id}/download-info")
    public ApiResponse<DownloadInfo> downloadInfo(
            @PathVariable String platform,
            @PathVariable String id,
            @RequestParam(defaultValue = "MP3_320") String quality,
            @RequestParam(required = false) String title
    ) {
        return ApiResponse.ok(musicService.downloadInfo(platform, id, quality, title));
    }

    /**
     * 通过后端转发下载字节，避免前端直接暴露或受限于上游临时 URL、跨域和鉴权细节。
     */
    @GetMapping("/song/{platform}/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String platform,
            @PathVariable String id,
            @RequestParam(defaultValue = "MP3_320") String quality,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String album
    ) throws IOException, InterruptedException {
        DownloadInfo info = musicService.downloadInfo(platform, id, quality, title);
        if (info.url() == null || info.url().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        HttpResponse<byte[]> response = fetchAudio(info);
        if (response.statusCode() >= 400) {
            return ResponseEntity.status(response.statusCode()).build();
        }
        String lyrics = safeLyric(platform, id);
        byte[] body = audioTagService.writeTags(
                response.body(),
                new AudioMetadata(info.extension(), info.title(), artist, album, lyrics)
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(info.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(downloadMediaType(info.mimeType()))
                .body(body);
    }

    @GetMapping("/song/{platform}/{id}/download-package")
    public ResponseEntity<byte[]> downloadPackage(
            @PathVariable String platform,
            @PathVariable String id,
            @RequestParam(defaultValue = "MP3_320") String quality,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String album
    ) throws IOException, InterruptedException {
        DownloadInfo info = musicService.downloadInfo(platform, id, quality, title);
        if (info.url() == null || info.url().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        HttpResponse<byte[]> response = fetchAudio(info);
        if (response.statusCode() >= 400) {
            return ResponseEntity.status(response.statusCode()).build();
        }
        String lyrics = safeLyric(platform, id);
        byte[] audio = audioTagService.writeTags(
                response.body(),
                new AudioMetadata(info.extension(), info.title(), artist, album, lyrics)
        );
        byte[] packageBytes = zipPackage(info, audio, lyrics);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(packageFilename(info), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(packageBytes);
    }

    @GetMapping("/song/{platform}/{id}/lyric-file")
    public ResponseEntity<byte[]> lyricFile(
            @PathVariable String platform,
            @PathVariable String id,
            @RequestParam(defaultValue = "MP3_320") String quality,
            @RequestParam(required = false) String title
    ) {
        String lyrics = safeLyric(platform, id);
        if (lyrics == null || lyrics.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        DownloadInfo info = musicService.downloadInfo(platform, id, quality, title);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(lyricFilename(info), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body(lyrics.getBytes(StandardCharsets.UTF_8));
    }

    private MediaType downloadMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private HttpResponse<byte[]> fetchAudio(DownloadInfo info) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(info.url()))
                .header("User-Agent", "Mozilla/5.0 MusicDownloader/1.0")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private byte[] zipPackage(DownloadInfo info, byte[] audio, String lyrics) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addZipEntry(zip, info.filename(), audio);
            if (lyrics != null && !lyrics.isBlank()) {
                addZipEntry(zip, lyricFilename(info), lyrics.getBytes(StandardCharsets.UTF_8));
            }
        }
        return output.toByteArray();
    }

    private void addZipEntry(ZipOutputStream zip, String filename, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(filename));
        zip.write(bytes);
        zip.closeEntry();
    }

    private String safeLyric(String platform, String id) {
        try {
            LyricResult lyric = musicService.lyric(platform, id);
            return joinLyrics(lyric);
        } catch (Exception ex) {
            return null;
        }
    }

    private String joinLyrics(LyricResult lyric) {
        if (lyric == null) {
            return null;
        }
        List<String> parts = new java.util.ArrayList<>();
        if (lyric.lyric() != null && !lyric.lyric().isBlank()) {
            parts.add(lyric.lyric());
        }
        if (lyric.translated() != null && !lyric.translated().isBlank()) {
            parts.add(lyric.translated());
        }
        return String.join("\n\n", parts);
    }

    private String lyricFilename(DownloadInfo info) {
        return filenameWithExtension(info, "lrc");
    }

    private String packageFilename(DownloadInfo info) {
        return filenameWithExtension(info, "zip");
    }

    private String filenameWithExtension(DownloadInfo info, String targetExtension) {
        String filename = info.filename();
        String extension = info.extension();
        if (filename != null
                && extension != null
                && filename.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT))) {
            return filename.substring(0, filename.length() - extension.length() - 1) + "." + targetExtension;
        }
        return (filename == null || filename.isBlank() ? info.id() : filename) + "." + targetExtension;
    }

    /**
     * 网关失败会带上所有候选路径尝试结果，方便前端调试面板定位是路径、凭据还是上游问题。
     */
    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiResponse<GatewayError>> gatewayError(GatewayException ex) {
        GatewayError error = new GatewayError(ex.getOperation(), ex.getParams(), ex.getAttempts());
        String message = ex.getAttempts().isEmpty()
                ? "Gateway request failed."
                : ex.getAttempts().get(ex.getAttempts().size() - 1).message();
        return ResponseEntity.badRequest().body(ApiResponse.fail(error, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> unexpectedError(Exception ex) {
        return ResponseEntity.internalServerError().body(ApiResponse.fail(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
    }
}
