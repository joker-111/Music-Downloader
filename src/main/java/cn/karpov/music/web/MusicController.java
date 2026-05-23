package cn.karpov.music.web;

import cn.karpov.music.model.ApiResponse;
import cn.karpov.music.model.MusicModels.DetailResult;
import cn.karpov.music.model.MusicModels.DownloadInfo;
import cn.karpov.music.model.MusicModels.GatewayError;
import cn.karpov.music.model.MusicModels.LyricResult;
import cn.karpov.music.model.MusicModels.Platform;
import cn.karpov.music.model.MusicModels.SearchResult;
import cn.karpov.music.service.GatewayException;
import cn.karpov.music.service.MusicService;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
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

@Validated
@RestController
@RequestMapping("/api")
public class MusicController {
    private final MusicService musicService;
    private final HttpClient httpClient;

    public MusicController(MusicService musicService, HttpClient httpClient) {
        this.musicService = musicService;
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
            @RequestParam(defaultValue = "MP3_320") String quality
    ) {
        return ApiResponse.ok(musicService.downloadInfo(platform, id, quality));
    }

    @GetMapping("/song/{platform}/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String platform,
            @PathVariable String id,
            @RequestParam(defaultValue = "MP3_320") String quality
    ) throws IOException, InterruptedException {
        DownloadInfo info = musicService.downloadInfo(platform, id, quality);
        if (info.url() == null || info.url().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(info.url()))
                .header("User-Agent", "Mozilla/5.0 MusicDownloader/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            return ResponseEntity.status(response.statusCode()).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(info.filename())
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(response.body());
    }

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiResponse<GatewayError>> gatewayError(GatewayException ex) {
        GatewayError error = new GatewayError(ex.getOperation(), ex.getParams(), ex.getAttempts());
        return ResponseEntity.badRequest().body(ApiResponse.fail(error, "Gateway endpoint probing failed: current music.gateway.base-url or candidates did not return music API JSON. Configure a working gateway in application.yml."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> unexpectedError(Exception ex) {
        return ResponseEntity.internalServerError().body(ApiResponse.fail(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
    }
}
