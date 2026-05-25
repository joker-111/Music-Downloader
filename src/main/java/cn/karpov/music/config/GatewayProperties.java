package cn.karpov.music.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Karpov Gateway 的外部化配置。
 *
 * <p>候选路径按操作类型分组，GatewayClient 会按顺序尝试这些路径，
 * 这样网关 REST 路由变化时通常只需要改配置，不需要改 Java 代码。</p>
 */
@Validated
@ConfigurationProperties(prefix = "music.gateway")
public class GatewayProperties {
    private String baseUrl = "";
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

    /**
     * 返回指定操作的候选路径；没有配置时返回空列表，让调用方统一处理失败。
     */
    public List<String> candidatesFor(String operation) {
        return candidates.getOrDefault(operation, new ArrayList<>()).stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .toList();
    }
}
