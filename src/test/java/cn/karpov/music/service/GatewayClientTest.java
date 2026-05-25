package cn.karpov.music.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cn.karpov.music.config.GatewayProperties;
import cn.karpov.music.model.MusicModels.GatewayPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GatewayClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void candidateUrlExpandsAndEncodesParameters() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl("https://gateway.test/api");
        properties.setCandidates(Map.of("search", List.of("/{platform}/search?q={keyword}&page={page}")));
        GatewayClient client = new GatewayClient(properties, RestClient.builder().build(), objectMapper);

        String url = client.candidateUrl("search", Map.of(
                "platform", "netease",
                "keyword", "晴天 live",
                "page", "1"
        ));

        assertThat(url).isEqualTo("https://gateway.test/api/netease/search?q=%E6%99%B4%E5%A4%A9+live&page=1");
    }

    @Test
    void fetchSkipsHtmlLoginPageAndReturnsNextJsonCandidate() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl("https://gateway.test/api");
        properties.setCandidates(Map.of("song", List.of("/bad/{id}", "/good/{id}")));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://gateway.test/api/bad/42"))
                .andRespond(withSuccess("<html>login</html>", MediaType.TEXT_HTML));
        server.expect(once(), requestTo("https://gateway.test/api/good/42"))
                .andRespond(withSuccess("{\"data\":{\"id\":42,\"name\":\"晴天\"}}", MediaType.APPLICATION_JSON));
        GatewayClient client = new GatewayClient(properties, builder.build(), objectMapper);

        GatewayPayload payload = client.fetch("song", Map.of("platform", "netease", "id", "42"));

        assertThat(payload.url()).isEqualTo("https://gateway.test/api/good/42");
        assertThat(payload.body().at("/data/name").asText()).isEqualTo("晴天");
        assertThat(payload.attempts()).hasSize(2);
        assertThat(payload.attempts().get(0).success()).isFalse();
        assertThat(payload.attempts().get(1).success()).isTrue();
        server.verify();
    }

    @Test
    void fetchThrowsGatewayExceptionWithAllAttemptsWhenCandidatesFail() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl("https://gateway.test/api");
        properties.setCandidates(Map.of("search", List.of("/empty", "/error")));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://gateway.test/api/empty"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://gateway.test/api/error"))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"upstream\"}", MediaType.APPLICATION_JSON));
        GatewayClient client = new GatewayClient(properties, builder.build(), objectMapper);

        assertThatThrownBy(() -> client.fetch("search", Map.of("platform", "netease")))
                .isInstanceOf(GatewayException.class)
                .satisfies(error -> {
                    GatewayException exception = (GatewayException) error;
                    assertThat(exception.getOperation()).isEqualTo("search");
                    assertThat(exception.getAttempts()).hasSize(2);
                    assertThat(exception.getAttempts()).allMatch(attempt -> !attempt.success());
                });
        server.verify();
    }
}
