package cn.karpov.music.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "music.gateway")
public class GatewayProperties {
    private String baseUrl = "https://gateway.karpov.cn";
    private Duration timeout = Duration.ofSeconds(15);
    private String userAgent = "Mozilla/5.0 MusicDownloader/1.0";
    private String apiKey;
    private Map<String, List<String>> candidates = new LinkedHashMap<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Map<String, List<String>> getCandidates() {
        return candidates;
    }

    public void setCandidates(Map<String, List<String>> candidates) {
        this.candidates = candidates;
    }

    public List<String> candidatesFor(String operation) {
        return candidates.getOrDefault(operation, new ArrayList<>());
    }
}
