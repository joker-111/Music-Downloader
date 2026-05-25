package cn.karpov.music.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 后端出站 HTTP 客户端配置。
 *
 * <p>RestClient 用于访问网关 JSON API，JDK HttpClient 用于下载代理读取歌曲字节。</p>
 */
@Configuration
public class WebClientConfig {
    /**
     * 网关请求统一附加 User-Agent；如果配置了 API Key，同时兼容 header 和 Bearer 两种认证形式。
     */
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

    /**
     * 下载代理使用 JDK HttpClient，显式启用跳转跟随以兼容音乐平台常见的临时下载地址。
     */
    @Bean
    HttpClient httpClient(GatewayProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
