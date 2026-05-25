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
        assertThat(info.filename()).isEqualTo("A B Song-MP3_320.mp3");
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
