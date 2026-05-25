package cn.karpov.music.service;

import cn.karpov.music.model.MusicModels.DetailResult;
import cn.karpov.music.model.MusicModels.DownloadInfo;
import cn.karpov.music.model.MusicModels.GatewayPayload;
import cn.karpov.music.model.MusicModels.LyricResult;
import cn.karpov.music.model.MusicModels.Platform;
import cn.karpov.music.model.MusicModels.SearchResult;
import cn.karpov.music.model.MusicModels.TrackSummary;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 音乐业务编排模块。
 *
 * <p>这里把不同平台和不同网关返回格式收敛成前端稳定使用的 DTO。</p>
 */
@Service
public class MusicService {
    private static final List<Platform> PLATFORMS = List.of(
            new Platform("netease", "\u7f51\u6613\u4e91\u97f3\u4e50"),
            new Platform("qqmusic", "QQ\u97f3\u4e50")
    );

    private static final Map<String, String> PLATFORM_ALIASES = Map.ofEntries(
            Map.entry("netease", "netease"),
            Map.entry("163", "netease"),
            Map.entry("wy", "netease"),
            Map.entry("wyyy", "netease"),
            Map.entry("\u7f51\u6613\u4e91", "netease"),
            Map.entry("\u7f51\u6613\u4e91\u97f3\u4e50", "netease"),
            Map.entry("qq", "qqmusic"),
            Map.entry("qqmusic", "qqmusic"),
            Map.entry("qq\u97f3\u4e50", "qqmusic"),
            Map.entry("tencent", "qqmusic"),
            Map.entry("\u817e\u8baf", "qqmusic"),
            Map.entry("tencentmusic", "qqmusic")
    );

    private static final Map<String, AudioFormat> AUDIO_FORMATS = Map.ofEntries(
            Map.entry("mp3", new AudioFormat("MP3", "mp3", "audio/mpeg")),
            Map.entry("mpeg", new AudioFormat("MP3", "mp3", "audio/mpeg")),
            Map.entry("audio/mpeg", new AudioFormat("MP3", "mp3", "audio/mpeg")),
            Map.entry("flac", new AudioFormat("FLAC", "flac", "audio/flac")),
            Map.entry("x-flac", new AudioFormat("FLAC", "flac", "audio/flac")),
            Map.entry("audio/flac", new AudioFormat("FLAC", "flac", "audio/flac")),
            Map.entry("audio/x-flac", new AudioFormat("FLAC", "flac", "audio/flac")),
            Map.entry("m4a", new AudioFormat("M4A", "m4a", "audio/mp4")),
            Map.entry("mp4", new AudioFormat("M4A", "m4a", "audio/mp4")),
            Map.entry("audio/mp4", new AudioFormat("M4A", "m4a", "audio/mp4")),
            Map.entry("aac", new AudioFormat("AAC", "aac", "audio/aac")),
            Map.entry("audio/aac", new AudioFormat("AAC", "aac", "audio/aac")),
            Map.entry("wav", new AudioFormat("WAV", "wav", "audio/wav")),
            Map.entry("wave", new AudioFormat("WAV", "wav", "audio/wav")),
            Map.entry("audio/wav", new AudioFormat("WAV", "wav", "audio/wav")),
            Map.entry("audio/x-wav", new AudioFormat("WAV", "wav", "audio/wav")),
            Map.entry("ogg", new AudioFormat("OGG", "ogg", "audio/ogg")),
            Map.entry("oga", new AudioFormat("OGG", "ogg", "audio/ogg")),
            Map.entry("audio/ogg", new AudioFormat("OGG", "ogg", "audio/ogg"))
    );

    private static final AudioFormat DEFAULT_AUDIO_FORMAT = AUDIO_FORMATS.get("mp3");

    private final GatewayClient gatewayClient;

    public MusicService(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    public List<Platform> platforms() {
        return PLATFORMS;
    }

    /**
     * 搜索结果来自不同平台时字段形态差异很大，因此先保留 raw，再提取通用摘要字段。
     */
    public SearchResult search(String platform, String keyword, int page, int limit) {
        String normalizedPlatform = normalizePlatform(platform);
        GatewayPayload payload = gatewayClient.fetch("search", Map.of(
                "platform", normalizedPlatform,
                "keyword", keyword,
                "page", String.valueOf(page),
                "limit", String.valueOf(limit)
        ));
        return new SearchResult(keyword, normalizedPlatform, page, limit, extractTracks(payload.body(), normalizedPlatform), payload.body());
    }

    /**
     * 详情接口共用同一套提取逻辑，type 决定展示摘要时优先关注的字段。
     */
    public DetailResult detail(String type, String platform, String id) {
        String normalizedPlatform = normalizePlatform(platform);
        GatewayPayload payload = gatewayClient.fetch(type, Map.of("platform", normalizedPlatform, "id", id));
        JsonNode body = payload.body();
        String title = firstText(body, "name", "title", "songName", "songname", "albumName");
        String subtitle = buildSubtitle(type, body);
        String cover = firstImageUrl(body, "pic", "cover", "coverUrl", "cover_url", "image", "albumPic", "picUrl", "avatar", "avatarUrl", "avatar_url", "picurl");
        Map<String, String> fields = collectFields(body, type);
        return new DetailResult(type, normalizedPlatform, id, title, subtitle, cover, fields, body);
    }

    /**
     * 下载信息只解析元数据和真实 URL；文件字节由控制器的下载代理再去读取。
     */
    public DownloadInfo downloadInfo(String platform, String id, String quality) {
        return downloadInfo(platform, id, quality, null);
    }

    public DownloadInfo downloadInfo(String platform, String id, String quality, String titleHint) {
        String normalizedPlatform = normalizePlatform(platform);
        GatewayPayload payload = gatewayClient.fetch("download", Map.of(
                "platform", normalizedPlatform,
                "id", id,
                "quality", quality
        ));
        JsonNode body = payload.body();
        String title = firstText(body, "name", "title", "songName", "songname", "filename");
        if (title == null || title.isBlank()) {
            title = firstText(body.get("song"), "name", "title", "songName", "songname", "filename");
        }
        if ((title == null || title.isBlank()) && titleHint != null && !titleHint.isBlank()) {
            title = titleHint;
        }
        String url = findUrl(body);
        if (url == null && body != null && body.isTextual()) {
            String text = body.asText();
            if (text.startsWith("http://") || text.startsWith("https://")) {
                url = text;
            }
        }
        if (title == null || title.isBlank()) {
            title = id;
        }
        AudioFormat format = inferAudioFormat(body, url, quality);
        return new DownloadInfo(
                id,
                normalizedPlatform,
                quality,
                title,
                url,
                format.display(),
                format.extension(),
                format.mimeType(),
                filename(title, normalizedPlatform, quality, format.extension()),
                body
        );
    }

    public LyricResult lyric(String platform, String id) {
        String normalizedPlatform = normalizePlatform(platform);
        GatewayPayload payload = gatewayClient.fetch("lyric", Map.of("platform", normalizedPlatform, "id", id));
        JsonNode body = payload.body();
        return new LyricResult(
                id,
                normalizedPlatform,
                firstText(body, "lyric", "lrc", "value", "content", "lyricText"),
                firstText(body, "tlyric", "translated", "translation", "tlyricText", "trans"),
                body
        );
    }

    private List<TrackSummary> extractTracks(JsonNode raw, String platform) {
        JsonNode list = locateList(raw);
        List<TrackSummary> tracks = new ArrayList<>();
        if (list == null || !list.isArray()) {
            if (raw != null && raw.isObject()) {
                tracks.add(toTrack(raw, platform));
            }
            return tracks;
        }
        for (JsonNode item : list) {
            if (item != null && !item.isNull()) {
                tracks.add(toTrack(item, platform));
            }
        }
        return tracks;
    }

    private TrackSummary toTrack(JsonNode item, String platform) {
        String id = firstText(item, "id", "songid", "songId", "mid", "rid", "hash", "musicid", "trackId");
        String title = firstText(item, "name", "title", "songName", "songname", "trackName");
        String artist = firstText(item, "artist", "artists", "singer", "author", "ar", "artistName");
        String album = firstText(item, "album", "albumName", "albumname", "al", "albumTitle");
        String cover = firstImageUrl(item, "pic", "cover", "coverUrl", "cover_url", "image", "albumPic", "picUrl", "img", "picurl", "imgUrl");
        if (cover == null || cover.isBlank()) {
            cover = firstImageUrl(item.get("album"), "cover", "coverUrl", "cover_url", "pic", "picUrl", "picurl", "img");
        }
        String duration = firstText(item, "duration", "interval", "time", "dt", "length", "durationSeconds", "duration_seconds");
        String artistId = firstText(item, "artistId", "artist_id", "singerId", "singerid", "authorId", "author_id", "arid");
        String albumId = firstText(item, "albumId", "album_id", "albummid", "albumMid", "albumid");
        if (albumId == null || albumId.isBlank()) {
            albumId = firstText(item.get("album"), "id", "mid", "albumId", "album_id");
        }
        String playlistId = firstText(item, "playlistId", "playlist_id", "sheetId", "sheetid", "songListId", "listId");
        return new TrackSummary(id, platform, title, artist, album, cover, duration, artistId, albumId, playlistId, item);
    }

    /**
     * 从常见列表字段里递归定位歌曲数组，兼容 data/result/songs/list 等网关响应形态。
     */
    private JsonNode locateList(JsonNode raw) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        if (raw.isArray()) {
            return raw;
        }
        for (String field : List.of("data", "result", "songs", "list", "items", "trackList", "songList")) {
            JsonNode child = raw.get(field);
            if (child == null || child.isNull()) {
                continue;
            }
            if (child.isArray()) {
                return child;
            }
            JsonNode nested = locateList(child);
            if (nested != null && nested.isArray()) {
                return nested;
            }
        }
        return null;
    }

    /**
     * 平台别名归一化，前端、文档和用户输入可以使用更自然的名称。
     */
    private String normalizePlatform(String platform) {
        if (platform == null) {
            return "netease";
        }
        String key = platform.trim().toLowerCase(Locale.ROOT);
        return PLATFORM_ALIASES.getOrDefault(key, key);
    }

    private String buildSubtitle(String type, JsonNode body) {
        List<String> bits = new ArrayList<>();
        if ("song".equals(type)) {
            addIfPresent(bits, firstText(body, "artist", "artists", "singer", "author", "ar", "artistName"));
            addIfPresent(bits, firstText(body, "album", "albumName", "albumname", "al", "albumTitle"));
        } else if ("artist".equals(type)) {
            addIfPresent(bits, firstText(body, "desc", "description", "intro", "content"));
            addIfPresent(bits, firstText(body, "fans", "followers", "count"));
        } else if ("album".equals(type) || "playlist".equals(type)) {
            addIfPresent(bits, firstText(body, "artist", "artists", "creator", "singer", "author"));
            addIfPresent(bits, firstText(body, "publishTime", "publish_time", "year", "date"));
            addIfPresent(bits, firstText(body, "trackCount", "songCount", "count", "total"));
        } else {
            addIfPresent(bits, firstText(body, "desc", "description", "intro", "content"));
        }
        return String.join(" / ", bits);
    }

    private Map<String, String> collectFields(JsonNode body, String type) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        addField(fields, "id", firstText(body, "id", "songid", "songId", "mid", "rid", "hash"));
        addField(fields, "title", firstText(body, "name", "title", "songName", "songname"));
        addField(fields, "artist", firstText(body, "artist", "artists", "singer", "author", "ar"));
        addField(fields, "album", firstText(body, "album", "albumName", "albumname", "al"));
        addField(fields, "cover", firstImageUrl(body, "pic", "cover", "coverUrl", "cover_url", "image", "albumPic", "picUrl", "picurl", "img"));
        addField(fields, "duration", firstText(body, "duration", "interval", "time", "dt", "length", "durationSeconds", "duration_seconds"));
        addField(fields, "artistId", firstText(body, "artistId", "artist_id", "singerId", "singerid", "authorId", "author_id"));
        addField(fields, "albumId", firstText(body, "albumId", "album_id", "albummid", "albumMid", "albumid"));
        addField(fields, "playlistId", firstText(body, "playlistId", "playlist_id", "sheetId", "sheetid", "songListId"));
        addField(fields, "description", firstText(body, "desc", "description", "intro", "content"));
        addField(fields, "lyric", firstText(body, "lyric", "lrc", "value", "content", "lyricText"));
        addField(fields, "translated", firstText(body, "tlyric", "translated", "translation", "trans"));
        addField(fields, "url", findUrl(body));
        addField(fields, "type", type);
        collectInterestingEntries(fields, body, 0);
        return fields;
    }

    private void collectInterestingEntries(Map<String, String> fields, JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > 2 || fields.size() >= 24) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (fields.size() >= 24) {
                    return;
                }
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isInterestingKey(key)) {
                    String text = scalar(value);
                    if (text != null && !text.isBlank()) {
                        fields.putIfAbsent(key, text);
                    }
                }
                collectInterestingEntries(fields, value, depth + 1);
            });
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                if (index++ >= 4 || fields.size() >= 24) {
                    break;
                }
                collectInterestingEntries(fields, item, depth + 1);
            }
        }
    }

    private boolean isInterestingKey(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return lower.contains("name")
                || lower.contains("title")
                || lower.contains("artist")
                || lower.contains("singer")
                || lower.contains("album")
                || lower.contains("cover")
                || lower.contains("pic")
                || lower.contains("url")
                || lower.contains("lyric")
                || lower.equals("lrc")
                || lower.equals("trans")
                || lower.equals("quality")
                || lower.equals("format")
                || lower.equals("filetype")
                || lower.equals("file_type")
                || lower.equals("ext")
                || lower.equals("extension")
                || lower.equals("mimetype")
                || lower.equals("mime_type")
                || lower.equals("available")
                || lower.contains("desc")
                || lower.contains("count")
                || lower.contains("time")
                || lower.contains("id");
    }

    private void addField(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.putIfAbsent(key, value);
        }
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber()) {
            return node.asText();
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            String extracted = scalar(value);
            if (extracted != null && !extracted.isBlank()) {
                return extracted;
            }
        }
        if (node.isObject()) {
            for (String container : List.of("data", "result", "song", "audio", "lyric", "info", "detail")) {
                JsonNode child = node.get(container);
                String nested = firstText(child, fields);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    /**
     * 图片字段经常是对象、数组、协议相对地址或裸域名；这里统一转换成浏览器可加载 URL。
     */
    private String firstImageUrl(JsonNode node, String... fields) {
        String value = firstImageText(node, fields);
        return normalizeImageUrl(value);
    }

    private String firstImageText(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber()) {
            return node.asText();
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            String extracted = imageScalar(value);
            if (extracted != null && !extracted.isBlank()) {
                return extracted;
            }
        }
        if (node.isObject()) {
            for (String container : List.of("data", "result", "song", "audio", "info", "detail", "album")) {
                JsonNode child = node.get(container);
                String nested = firstImageText(child, fields);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String imageScalar(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                String text = imageScalar(item);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
            return null;
        }
        if (value.isObject()) {
            for (String field : List.of("url", "src", "coverUrl", "cover_url", "picUrl", "picurl", "imgUrl", "image", "img", "pic", "avatarUrl", "avatar_url", "cover", "value")) {
                String nested = imageScalar(value.get(field));
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String normalizeImageUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("data:image/")) {
            return trimmed;
        }
        if (!trimmed.contains(" ")
                && trimmed.contains(".")
                && (trimmed.contains("/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))) {
            return "https://" + trimmed;
        }
        return null;
    }

    private String scalar(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : value) {
                String text = scalar(item);
                if (text != null && !text.isBlank()) {
                    values.add(text);
                }
            }
            return String.join(" / ", values);
        }
        if (value.isObject()) {
            for (String field : List.of("name", "title", "url", "id", "mid", "text", "value", "lrc", "trans")) {
                String nested = scalar(value.get(field));
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String findUrl(JsonNode body) {
        String direct = firstText(body, "url", "src", "download", "link", "play_url", "playUrl", "musicUrl", "music_url");
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        if (body == null || body.isNull()) {
            return null;
        }
        if (body.isTextual()) {
            String text = body.asText();
            if (text.startsWith("http://") || text.startsWith("https://")) {
                return text;
            }
        }
        if (body.isObject()) {
            for (String container : List.of("data", "result", "song", "audio", "info", "urlData")) {
                String nested = findUrl(body.get(container));
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        if (body.isArray()) {
            for (JsonNode item : body) {
                String nested = findUrl(item);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    /**
     * 文件名会进入 Content-Disposition，必须剔除 Windows 和浏览器都不喜欢的路径字符。
     */
    private AudioFormat inferAudioFormat(JsonNode body, String url, String quality) {
        AudioFormat gatewayFormat = audioFormatFromSignal(firstText(body, "format", "type", "fileType", "file_type", "ext", "extension", "mimeType", "mime_type"));
        if (gatewayFormat != null) {
            return gatewayFormat;
        }
        AudioFormat urlFormat = audioFormatFromSignal(extensionFromUrl(url));
        if (urlFormat != null) {
            return urlFormat;
        }
        AudioFormat qualityFormat = audioFormatFromSignal(quality);
        return qualityFormat == null ? DEFAULT_AUDIO_FORMAT : qualityFormat;
    }

    private AudioFormat audioFormatFromSignal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        AudioFormat exact = AUDIO_FORMATS.get(normalized);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, AudioFormat> entry : AUDIO_FORMATS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String extensionFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url.trim();
        int query = normalized.indexOf('?');
        if (query >= 0) {
            normalized = normalized.substring(0, query);
        }
        int fragment = normalized.indexOf('#');
        if (fragment >= 0) {
            normalized = normalized.substring(0, fragment);
        }
        int slash = normalized.lastIndexOf('/');
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1);
    }

    private String filename(String title, String platform, String quality, String extension) {
        String cleaned = Arrays.stream(title.split("[\\\\/:*?\"<>|]+"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" "));
        if (cleaned.isBlank()) {
            cleaned = platform;
        }
        return cleaned + "-" + quality + "." + extension;
    }

    private record AudioFormat(String display, String extension, String mimeType) {
    }
}
