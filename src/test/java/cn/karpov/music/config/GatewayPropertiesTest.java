package cn.karpov.music.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewayPropertiesTest {
    @Test
    void defaultsRequireExternalGatewayConfiguration() {
        GatewayProperties properties = new GatewayProperties();

        assertThat(properties.getBaseUrl()).isEmpty();
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getUserAgent()).contains("MusicDownloader");
        assertThat(properties.candidatesFor("missing")).isEmpty();
    }

    @Test
    void candidatesForReturnsConfiguredOperationPaths() {
        GatewayProperties properties = new GatewayProperties();
        properties.setCandidates(Map.of(
                "search", List.of("/{platform}/search?q={keyword}"),
                "download", List.of("/{platform}/songs/{id}/url")
        ));

        assertThat(properties.candidatesFor("search")).containsExactly("/{platform}/search?q={keyword}");
        assertThat(properties.candidatesFor("download")).containsExactly("/{platform}/songs/{id}/url");
        assertThat(properties.candidatesFor("lyric")).isEmpty();
    }

    @Test
    void candidatesForIgnoresBlankEnvironmentPlaceholders() {
        GatewayProperties properties = new GatewayProperties();
        properties.setCandidates(Map.of("search", List.of("", " ", "/{platform}/search?q={keyword}")));

        assertThat(properties.candidatesFor("search")).containsExactly("/{platform}/search?q={keyword}");
    }
}
