package cn.karpov.music.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WebClientConfigTest {
    @Test
    void httpClientUsesConfiguredTimeoutAndFollowsRedirects() {
        GatewayProperties properties = new GatewayProperties();
        properties.setTimeout(Duration.ofSeconds(3));

        HttpClient client = new WebClientConfig().httpClient(properties);

        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(3));
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
    }
}
