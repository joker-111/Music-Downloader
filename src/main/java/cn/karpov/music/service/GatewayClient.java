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

    public GatewayPayload fetch(String operation, Map<String, String> params) {
        List<GatewayAttempt> attempts = new ArrayList<>();
        List<String> platformVariants = platformVariants(params.get("platform"));
        for (String candidate : properties.candidatesFor(operation)) {
            for (String platformVariant : platformVariants) {
                String url = properties.getBaseUrl() + expand(candidate, withPlatform(params, platformVariant));
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
            case "netease" -> List.of("netease", "163");
            case "qq", "tencent" -> List.of("qq", "tencent");
            case "kugou" -> List.of("kugou");
            case "kuwo" -> List.of("kuwo");
            case "migu" -> List.of("migu");
            default -> List.of(platform);
        };
    }

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
        List<String> candidates = properties.candidatesFor(operation);
        if (candidates.isEmpty()) {
            return null;
        }
        return properties.getBaseUrl() + expand(candidates.get(0), params);
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
