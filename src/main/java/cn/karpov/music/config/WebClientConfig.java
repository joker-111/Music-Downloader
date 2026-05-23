package cn.karpov.music.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {
    @Bean
    RestClient restClient(GatewayProperties properties) {
        return RestClient.builder()
                .defaultHeader("User-Agent", properties.getUserAgent())
                .build();
    }

    @Bean
    HttpClient httpClient(GatewayProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
