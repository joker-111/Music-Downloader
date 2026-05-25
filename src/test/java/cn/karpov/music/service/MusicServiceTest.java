package cn.karpov.music.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.karpov.music.config.GatewayProperties;
import cn.karpov.music.model.MusicModels.DownloadInfo;
import cn.karpov.music.model.MusicModels.GatewayAttempt;
import cn.karpov.music.model.MusicModels.GatewayPayload;
import cn.karpov.music.model.MusicModels.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MusicServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchNormalizesPlatformAndExtractsTrackSummaries() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": {
                    "songs": [
                      {
                        "songId": 42,
                        "songName": "晴天",
                        "singer": [{"id": 7, "name": "周杰伦"}],
                        "album": {"id": 9, "name": "叶惠美", "picUrl": "//img.example.com/cover.jpg"},
                        "duration": 259
                      }
                    ]
                  }
                }
                """);
        MusicService service = new MusicService(new StubGatewayClient(objectMapper, body));

        SearchResult result = service.search("网易云", "晴天", 1, 30);

        assertThat(result.platform()).isEqualTo("netease");
        assertThat(result.tracks()).hasSize(1);
        assertThat(result.tracks().get(0).id()).isEqualTo("42");
        assertThat(result.tracks().get(0).title()).isEqualTo("晴天");
        assertThat(result.tracks().get(0).artist()).isEqualTo("周杰伦");
        assertThat(result.tracks().get(0).album()).isEqualTo("叶惠美");
        assertThat(result.tracks().get(0).albumId()).isEqualTo("9");
        assertThat(result.tracks().get(0).cover()).isEqualTo("https://img.example.com/cover.jpg");
    }

    @Test
    void downloadInfoFindsNestedUrlAndSanitizesFilename() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": {
                    "song": {"name": "A/B:Song"},
                    "urlData": {"url": "https://cdn.example.com/song.mp3"}
                  }
                }
                """);
        MusicService service = new MusicService(new StubGatewayClient(objectMapper, body));

        DownloadInfo info = service.downloadInfo("qq", "abc123", "MP3_320");

        assertThat(info.platform()).isEqualTo("qqmusic");
        assertThat(info.title()).isEqualTo("A/B:Song");
        assertThat(info.url()).isEqualTo("https://cdn.example.com/song.mp3");
        assertThat(info.format()).isEqualTo("MP3");
        assertThat(info.extension()).isEqualTo("mp3");
        assertThat(info.mimeType()).isEqualTo("audio/mpeg");
        assertThat(info.filename()).isEqualTo("A B Song-MP3_320.mp3");
    }

    @Test
    void downloadInfoUsesTitleHintWhenGatewayOnlyReturnsUrl() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": {
                    "url": "https://cdn.example.com/opaque-token"
                  }
                }
                """);
        MusicService service = new MusicService(new StubGatewayClient(objectMapper, body));

        DownloadInfo info = service.downloadInfo("netease", "42", "MP3_320", "晴天/Live");

        assertThat(info.title()).isEqualTo("晴天/Live");
        assertThat(info.filename()).isEqualTo("晴天 Live-MP3_320.mp3");
    }

    @Test
    void flacQualityProducesFlacDownloadMetadata() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": {
                    "song": {"name": "Lossless Song"},
                    "urlData": {"url": "https://cdn.example.com/song"}
                  }
                }
                """);
        MusicService service = new MusicService(new StubGatewayClient(objectMapper, body));

        DownloadInfo info = service.downloadInfo("netease", "42", "FLAC");

        assertThat(info.format()).isEqualTo("FLAC");
        assertThat(info.extension()).isEqualTo("flac");
        assertThat(info.mimeType()).isEqualTo("audio/flac");
        assertThat(info.filename()).isEqualTo("Lossless Song-FLAC.flac");
    }

    @Test
    void gatewayFormatTakesPriorityOverUrlAndQuality() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": {
                    "name": "Source Wins",
                    "format": "flac",
                    "url": "https://cdn.example.com/source.mp3"
                  }
                }
                """);
        MusicService service = new MusicService(new StubGatewayClient(objectMapper, body));

        DownloadInfo info = service.downloadInfo("netease", "42", "MP3_320");

        assertThat(info.format()).isEqualTo("FLAC");
        assertThat(info.extension()).isEqualTo("flac");
        assertThat(info.filename()).isEqualTo("Source Wins-MP3_320.flac");
    }

    @Test
    void urlExtensionTakesPriorityOverQualityFallback() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": {
                    "name": "Url Wins",
                    "url": "https://cdn.example.com/url-wins.m4a?token=abc"
                  }
                }
                """);
        MusicService service = new MusicService(new StubGatewayClient(objectMapper, body));

        DownloadInfo info = service.downloadInfo("netease", "42", "MP3_320");

        assertThat(info.format()).isEqualTo("M4A");
        assertThat(info.extension()).isEqualTo("m4a");
        assertThat(info.mimeType()).isEqualTo("audio/mp4");
        assertThat(info.filename()).isEqualTo("Url Wins-MP3_320.m4a");
    }

    @Test
    void unknownFormatFallsBackToMp3Metadata() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": {
                    "name": "Fallback",
                    "format": "unknown",
                    "url": "https://cdn.example.com/file"
                  }
                }
                """);
        MusicService service = new MusicService(new StubGatewayClient(objectMapper, body));

        DownloadInfo info = service.downloadInfo("netease", "42", "MASTER");

        assertThat(info.format()).isEqualTo("MP3");
        assertThat(info.extension()).isEqualTo("mp3");
        assertThat(info.mimeType()).isEqualTo("audio/mpeg");
        assertThat(info.filename()).isEqualTo("Fallback-MASTER.mp3");
    }

    private static final class StubGatewayClient extends GatewayClient {
        private final JsonNode body;

        StubGatewayClient(ObjectMapper objectMapper, JsonNode body) {
            super(new GatewayProperties(), RestClient.builder().build(), objectMapper);
            this.body = body;
        }

        @Override
        public GatewayPayload fetch(String operation, Map<String, String> params) {
            return new GatewayPayload(body, "stub://" + operation, List.of(new GatewayAttempt("stub://" + operation, true, "ok")));
        }
    }
}
