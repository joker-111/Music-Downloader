package cn.karpov.music.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.karpov.music.model.ApiResponse;
import cn.karpov.music.model.MusicModels.DownloadInfo;
import cn.karpov.music.model.MusicModels.GatewayAttempt;
import cn.karpov.music.model.MusicModels.GatewayError;
import cn.karpov.music.model.MusicModels.LyricResult;
import cn.karpov.music.model.MusicModels.SearchResult;
import cn.karpov.music.service.AudioTagService;
import cn.karpov.music.service.GatewayException;
import cn.karpov.music.service.MusicService;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MusicControllerTest {
    private final MusicService musicService = mock(MusicService.class);
    private final AudioTagService audioTagService = new AudioTagService();
    private final HttpClient httpClient = mock(HttpClient.class);
    private final MusicController controller = new MusicController(musicService, audioTagService, httpClient);

    @Test
    void searchClampsPageAndLimitBeforeCallingService() {
        SearchResult result = new SearchResult("sunny", "netease", 1, 50, List.of(), null);
        when(musicService.search("netease", "sunny", 1, 50)).thenReturn(result);

        ApiResponse<SearchResult> response = controller.search("netease", "sunny", -7, 200);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(result);
        verify(musicService).search("netease", "sunny", 1, 50);
    }

    @Test
    void downloadReturnsNotFoundWhenGatewayHasNoUrl() throws Exception {
        when(musicService.downloadInfo("netease", "42", "MP3_320", null))
                .thenReturn(new DownloadInfo("42", "netease", "MP3_320", "Sunny", null, "MP3", "mp3", "audio/mpeg", "Sunny.mp3", null));

        ResponseEntity<byte[]> response = controller.download("netease", "42", "MP3_320", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(httpClient, never()).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test
    void downloadUsesInferredFilenameAndContentType() throws Exception {
        HttpResponse<byte[]> upstream = mock(HttpResponse.class);
        when(musicService.downloadInfo("netease", "42", "FLAC", null))
                .thenReturn(new DownloadInfo(
                        "42",
                        "netease",
                        "FLAC",
                        "Lossless",
                        "https://cdn.example.com/lossless.flac",
                        "FLAC",
                        "flac",
                        "audio/flac",
                        "Lossless-FLAC.flac",
                        null
                ));
        when(upstream.statusCode()).thenReturn(200);
        when(upstream.body()).thenReturn(new byte[] {1, 2, 3});
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(upstream);

        ResponseEntity<byte[]> response = controller.download("netease", "42", "FLAC", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("audio/flac");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("Lossless-FLAC.flac");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    void downloadUsesTitleHintAndUtf8FilenameHeader() throws Exception {
        HttpResponse<byte[]> upstream = mock(HttpResponse.class);
        when(musicService.downloadInfo("netease", "42", "MP3_320", "晴天"))
                .thenReturn(new DownloadInfo(
                        "42",
                        "netease",
                        "MP3_320",
                        "晴天",
                        "https://cdn.example.com/opaque",
                        "MP3",
                        "mp3",
                        "audio/mpeg",
                        "晴天-MP3_320.mp3",
                        null
                ));
        when(upstream.statusCode()).thenReturn(200);
        when(upstream.body()).thenReturn(new byte[] {4, 5, 6});
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(upstream);

        ResponseEntity<byte[]> response = controller.download("netease", "42", "MP3_320", "晴天", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("filename*=")
                .contains("%E6%99%B4%E5%A4%A9-MP3_320.mp3");
    }

    @Test
    void downloadEmbedsMp3TagsFromHintsAndLyrics() throws Exception {
        HttpResponse<byte[]> upstream = mock(HttpResponse.class);
        when(musicService.downloadInfo("netease", "42", "MP3_320", "Sunny"))
                .thenReturn(new DownloadInfo(
                        "42",
                        "netease",
                        "MP3_320",
                        "Sunny",
                        "https://cdn.example.com/sunny.mp3",
                        "MP3",
                        "mp3",
                        "audio/mpeg",
                        "Sunny-MP3_320.mp3",
                        null
                ));
        when(musicService.lyric("netease", "42"))
                .thenReturn(new LyricResult("42", "netease", "[00:01.00]Sunny day", null, null));
        when(upstream.statusCode()).thenReturn(200);
        when(upstream.body()).thenReturn(new byte[] {(byte) 0xff, (byte) 0xfb, 0x11, 0x22});
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(upstream);

        ResponseEntity<byte[]> response = controller.download("netease", "42", "MP3_320", "Sunny", "Jay", "Album");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(new String(response.getBody(), 0, Math.min(response.getBody().length, 180), StandardCharsets.ISO_8859_1))
                .contains("ID3", "TIT2", "TPE1", "TALB", "USLT");
        assertThat(response.getBody()[response.getBody().length - 4]).isEqualTo((byte) 0xff);
    }

    @Test
    void lyricFileReturnsUtf8Attachment() {
        when(musicService.lyric("netease", "42"))
                .thenReturn(new LyricResult("42", "netease", "[00:01.00]Sunny day", null, null));
        when(musicService.downloadInfo("netease", "42", "MP3_320", "Sunny"))
                .thenReturn(new DownloadInfo(
                        "42",
                        "netease",
                        "MP3_320",
                        "Sunny",
                        "https://cdn.example.com/sunny.mp3",
                        "MP3",
                        "mp3",
                        "audio/mpeg",
                        "Sunny-MP3_320.mp3",
                        null
                ));

        ResponseEntity<byte[]> response = controller.lyricFile("netease", "42", "MP3_320", "Sunny");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/plain");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("Sunny-MP3_320.lrc");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("[00:01.00]Sunny day");
    }

    @Test
    void lyricFileReturnsNotFoundWhenLyricsAreMissing() {
        when(musicService.lyric("netease", "42"))
                .thenReturn(new LyricResult("42", "netease", null, null, null));

        ResponseEntity<byte[]> response = controller.lyricFile("netease", "42", "MP3_320", "Sunny");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(musicService, never()).downloadInfo(any(), any(), any(), any());
    }

    @Test
    void downloadPackageReturnsTaggedAudioAndLyricFileInZip() throws Exception {
        HttpResponse<byte[]> upstream = mock(HttpResponse.class);
        when(musicService.downloadInfo("netease", "42", "MP3_320", "Sunny"))
                .thenReturn(new DownloadInfo(
                        "42",
                        "netease",
                        "MP3_320",
                        "Sunny",
                        "https://cdn.example.com/sunny.mp3",
                        "MP3",
                        "mp3",
                        "audio/mpeg",
                        "Sunny-MP3_320.mp3",
                        null
                ));
        when(musicService.lyric("netease", "42"))
                .thenReturn(new LyricResult("42", "netease", "[00:01.00]Sunny day", null, null));
        when(upstream.statusCode()).thenReturn(200);
        when(upstream.body()).thenReturn(new byte[] {(byte) 0xff, (byte) 0xfb, 0x11, 0x22});
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(upstream);

        ResponseEntity<byte[]> response = controller.downloadPackage("netease", "42", "MP3_320", "Sunny", "Jay", "Album");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/zip");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("Sunny-MP3_320.zip");
        Map<String, byte[]> entries = unzip(response.getBody());
        assertThat(entries).containsKeys("Sunny-MP3_320.mp3", "Sunny-MP3_320.lrc");
        assertThat(new String(entries.get("Sunny-MP3_320.mp3"), StandardCharsets.ISO_8859_1))
                .contains("ID3", "TIT2", "TPE1", "TALB", "USLT");
        assertThat(new String(entries.get("Sunny-MP3_320.lrc"), StandardCharsets.UTF_8)).isEqualTo("[00:01.00]Sunny day");
    }

    @Test
    void gatewayErrorReturnsAttemptsForDebugPanel() {
        GatewayException exception = new GatewayException(
                "search",
                Map.of("platform", "netease"),
                List.of(new GatewayAttempt("https://gateway.test/search", false, "empty data field"))
        );

        ResponseEntity<ApiResponse<GatewayError>> response = controller.gatewayError(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("empty data field");
        assertThat(response.getBody().data().operation()).isEqualTo("search");
        assertThat(response.getBody().data().attempts()).hasSize(1);
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
