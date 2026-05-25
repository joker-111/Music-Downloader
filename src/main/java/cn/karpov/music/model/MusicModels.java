package cn.karpov.music.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * 音乐业务使用的轻量 DTO 集合。
 *
 * <p>raw 字段保留上游网关原始 JSON，方便前端调试面板展示，也避免过早丢失平台差异字段。</p>
 */
public final class MusicModels {
    private MusicModels() {
    }

    public record Platform(String id, String name) {
    }

    public record TrackSummary(
            String id,
            String platform,
            String title,
            String artist,
            String album,
            String cover,
            String duration,
            String artistId,
            String albumId,
            String playlistId,
            JsonNode raw
    ) {
    }

    public record SearchResult(
            String keyword,
            String platform,
            int page,
            int limit,
            List<TrackSummary> tracks,
            JsonNode raw
    ) {
    }

    public record DetailResult(
            String type,
            String platform,
            String id,
            String title,
            String subtitle,
            String cover,
            Map<String, String> fields,
            JsonNode raw
    ) {
    }

    public record DownloadInfo(
            String id,
            String platform,
            String quality,
            String title,
            String url,
            String filename,
            JsonNode raw
    ) {
    }

    public record LyricResult(
            String id,
            String platform,
            String lyric,
            String translated,
            JsonNode raw
    ) {
    }

    public record GatewayAttempt(String url, boolean success, String message) {
    }

    public record GatewayPayload(JsonNode body, String url, List<GatewayAttempt> attempts) {
    }

    public record GatewayError(String operation, Map<String, String> params, List<GatewayAttempt> attempts) {
    }
}
