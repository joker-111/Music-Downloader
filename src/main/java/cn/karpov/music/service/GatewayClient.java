package cn.karpov.music.service;

import cn.karpov.music.config.GatewayProperties;
import cn.karpov.music.model.MusicModels.GatewayAttempt;
import cn.karpov.music.model.MusicModels.GatewayPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Karpov Gateway 访问模块。
 *
 * <p>它负责展开候选路径、尝试平台别名、解析响应，并过滤登录页或空数据等不可用结果。</p>
 */
@Service
public class GatewayClient {
    private final GatewayProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GatewayClient(GatewayProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 按配置顺序尝试网关候选路径，直到拿到一个非空且可用的 JSON 响应。
     */
    public GatewayPayload fetch(String operation, Map<String, String> params) {
        List<GatewayAttempt> attempts = new ArrayList<>();
        String baseUrl = configuredBaseUrl(attempts);
        List<String> candidates = configuredCandidates(operation, attempts);
        if (!attempts.isEmpty()) {
            throw new GatewayException(operation, params, attempts);
        }
        List<String> platformVariants = platformVariants(params.get("platform"));
        for (String candidate : candidates) {
            for (String platformVariant : platformVariants) {
                String url = baseUrl + expand(candidate, withPlatform(params, platformVariant));
                try {
                    String body = restClient.get()
                            .uri(URI.create(url))
                            .retrieve()
                            .body(String.class);
                    JsonNode json = parseBody(body);
                    if (json != null && !isEmptyGatewayResult(json)) {
                        attempts.add(new GatewayAttempt(url, true, "ok"));
                        return new GatewayPayload(json, url, attempts);
                    }
                    attempts.add(new GatewayAttempt(url, false, describeEmptyResult(json)));
                } catch (RestClientResponseException ex) {
                    attempts.add(new GatewayAttempt(url, false, "HTTP " + ex.getStatusCode().value() + ": " + describeResponseBody(ex.getResponseBodyAsString())));
                } catch (Exception ex) {
                    attempts.add(new GatewayAttempt(url, false, ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                }
            }
        }
        throw new GatewayException(operation, params, attempts);
    }

    private Map<String, String> withPlatform(Map<String, String> params, String platform) {
        if (platform == null) {
            return params;
        }
        Map<String, String> copy = new java.util.LinkedHashMap<>(params);
        copy.put("platform", platform);
        return copy;
    }

    private List<String> platformVariants(String platform) {
        if (platform == null || platform.isBlank()) {
            return List.of("");
        }
        return switch (platform) {
            case "netease" -> List.of("netease");
            case "qq", "tencent", "qqmusic" -> List.of("qqmusic");
            default -> List.of(platform);
        };
    }

    /**
     * 将非 JSON 文本包装成 value，前端仍可在调试面板看到完整返回内容。
     */
    private JsonNode parseBody(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return objectMapper.readTree(trimmed);
        }
        return objectMapper.createObjectNode().put("value", trimmed);
    }

    public String candidateUrl(String operation, Map<String, String> params) {
        String baseUrl = configuredBaseUrl(new ArrayList<>());
        List<String> candidates = properties.candidatesFor(operation);
        if (candidates.isEmpty()) {
            return null;
        }
        return baseUrl + expand(candidates.get(0), params);
    }

    public JsonNode emptyObject() {
        return objectMapper.createObjectNode();
    }

    public ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }

    public ArrayNode arrayNode() {
        return objectMapper.createArrayNode();
    }

    private String configuredBaseUrl(List<GatewayAttempt> attempts) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            attempts.add(new GatewayAttempt("music.gateway.base-url", false, "Missing gateway base URL. Set MUSIC_GATEWAY_BASE_URL in .env or the process environment."));
            return "";
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

    private List<String> configuredCandidates(String operation, List<GatewayAttempt> attempts) {
        List<String> candidates = properties.candidatesFor(operation);
        if (candidates.isEmpty()) {
            attempts.add(new GatewayAttempt("music.gateway.candidates." + operation, false, "Missing gateway candidate paths for operation: " + operation + ". Set the matching MUSIC_GATEWAY_*_PATH value."));
        }
        return candidates;
    }

    /**
     * 网关有时会返回登录页、空 data 或业务错误码；这些响应不能当作可用音乐数据。
     */
    private boolean isEmptyGatewayResult(JsonNode json) {
        if (json == null || json.isNull()) {
            return true;
        }
        if (json.isArray()) {
            return json.isEmpty();
        }
        if (json.isObject()) {
            JsonNode value = json.get("value");
            if (value != null && value.isTextual() && isRedirectOrHtml(value.asText())) {
                return true;
            }
            JsonNode code = first(json, "code", "status");
            JsonNode message = first(json, "message", "msg", "error");
            if (code != null && code.asInt(200) >= 400 && message != null) {
                return true;
            }
            JsonNode data = json.get("data");
            return data != null && (data.isNull() || (data.isArray() && data.isEmpty()));
        }
        if (json.isTextual()) {
            return isRedirectOrHtml(json.asText());
        }
        return false;
    }

    private String describeEmptyResult(JsonNode json) {
        if (json == null || json.isNull()) {
            return "empty response";
        }
        if (json.isArray() && json.isEmpty()) {
            return "empty array response";
        }
        if (json.isObject()) {
            JsonNode value = json.get("value");
            if (value != null && value.isTextual() && isRedirectOrHtml(value.asText())) {
                return "gateway returned HTML or login page instead of music API JSON";
            }
            JsonNode code = first(json, "code", "status");
            JsonNode message = first(json, "message", "msg", "error");
            if (code != null && code.asInt(200) >= 400 && message != null) {
                return "gateway error " + code.asText() + ": " + message.asText();
            }
            JsonNode data = json.get("data");
            if (data != null && (data.isNull() || (data.isArray() && data.isEmpty()))) {
                return "empty data field";
            }
        }
        if (json.isTextual() && isRedirectOrHtml(json.asText())) {
            return "gateway returned HTML or login page instead of music API JSON";
        }
        return "empty or unsupported gateway response";
    }

    private String describeResponseBody(String body) {
        if (isRedirectOrHtml(body)) {
            return "gateway returned HTML or login page instead of music API JSON";
        }
        if (body == null || body.isBlank()) {
            return "empty response body";
        }
        String trimmed = body.trim().replaceAll("\\s+", " ");
        try {
            JsonNode json = objectMapper.readTree(trimmed);
            JsonNode code = first(json, "code", "status", "error");
            JsonNode message = first(json, "message", "msg", "error_description");
            if (message != null && message.isTextual()) {
                return code == null ? message.asText() : "gateway error " + code.asText() + ": " + message.asText();
            }
        } catch (Exception ignored) {
            // Fall through to the compact raw body below.
        }
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "..." : trimmed;
    }

    private boolean isRedirectOrHtml(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isBlank()
                || trimmed.startsWith("/login")
                || trimmed.startsWith("login")
                || trimmed.contains("login?next=")
                || trimmed.startsWith("<!doctype")
                || trimmed.startsWith("<html");
    }

    private JsonNode first(JsonNode json, String... fields) {
        for (String field : fields) {
            JsonNode node = json.get(field);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    /**
     * 只替换模板中的已知参数，并在替换前做 URL 编码，避免中文关键词破坏查询串。
     */
    private String expand(String template, Map<String, String> params) {
        String value = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", encode(entry.getValue()));
        }
        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
