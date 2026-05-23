package cn.karpov.music.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {
    @Bean
    RestClient restClient(GatewayProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .defaultHeader("User-Agent", properties.getUserAgent());
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.defaultHeader("X-API-Key", properties.getApiKey().trim());
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey().trim());
        }
        return builder.build();
    }

    @Bean
    HttpClient httpClient(GatewayProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
