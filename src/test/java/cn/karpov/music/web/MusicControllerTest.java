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
import cn.karpov.music.model.MusicModels.SearchResult;
import cn.karpov.music.service.GatewayException;
import cn.karpov.music.service.MusicService;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MusicControllerTest {
    private final MusicService musicService = mock(MusicService.class);
    private final HttpClient httpClient = mock(HttpClient.class);
    private final MusicController controller = new MusicController(musicService, httpClient);

    @Test
    void searchClampsPageAndLimitBeforeCallingService() {
        SearchResult result = new SearchResult("晴天", "netease", 1, 50, List.of(), null);
        when(musicService.search("netease", "晴天", 1, 50)).thenReturn(result);

        ApiResponse<SearchResult> response = controller.search("netease", "晴天", -7, 200);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(result);
        verify(musicService).search("netease", "晴天", 1, 50);
    }

    @Test
    void downloadReturnsNotFoundWhenGatewayHasNoUrl() throws Exception {
        when(musicService.downloadInfo("netease", "42", "MP3_320"))
                .thenReturn(new DownloadInfo("42", "netease", "MP3_320", "晴天", null, "晴天.mp3", null));

        ResponseEntity<byte[]> response = controller.download("netease", "42", "MP3_320");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(httpClient, never()).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
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
}
